#!/usr/bin/env python3
"""Pinned experimental source build; upstream README licensing basis was explicitly accepted."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import urllib.request

REVISION = "eb22559581cf400af29470205a9ebbff31e0db33"
TARGETS = {
    "arm64-v8a": ("aarch64-linux-android", "aarch64-linux-android26"),
    "armeabi-v7a": ("armv7-linux-androideabi", "armv7a-linux-androideabi26"),
    "x86": ("i686-linux-android", "i686-linux-android26"),
    "x86_64": ("x86_64-linux-android", "x86_64-linux-android26"),
}
PERMISSIVE = {"MIT", "Apache-2.0", "ISC", "BSD-2-Clause", "BSD-3-Clause", "Zlib", "Unicode-3.0", "Unicode-DFS-2016", "CC0-1.0", "BSL-1.0", "0BSD", "Unlicense"}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def supported_license(expression):
    if not expression:
        return False
    tokens = set(re.findall(r"[A-Za-z0-9.+-]+", expression)) - {"AND", "OR"}
    return bool(tokens) and tokens <= PERMISSIVE


def github_package_licenses(package, directory, destination):
    """Use the exact cargo-published VCS commit, never a moving branch or guessed tag."""
    vcs_path = directory / ".cargo_vcs_info.json"
    repo = re.match(r"https://github.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\.git)?(?:/|$)", package.get("repository") or "")
    if not repo or not vcs_path.is_file():
        return []
    vcs = json.loads(vcs_path.read_text())
    commit = vcs.get("git", {}).get("sha1", "")
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("Unpinned license evidence")
    base = "https://api.github.com/repos/" + repo[1]
    def get(path):
        request = urllib.request.Request(base + path, headers={"User-Agent": "Aerix-experimental-license-audit", "Accept": "application/vnd.github+json"})
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.load(response)
    tree = get("/git/trees/" + commit + "?recursive=1")
    if tree.get("truncated"):
        raise ValueError("License source tree truncated")
    package_path = Path(vcs.get("path_in_vcs", "."))
    parents = {"."} | {str(parent) for parent in package_path.parents} | {str(package_path)}
    found = []
    for item in tree["tree"]:
        path = Path(item["path"])
        if item["type"] != "blob" or str(path.parent) not in parents or not path.name.upper().startswith(("LICENSE", "LICENCE", "COPYING", "COPYRIGHT", "NOTICE")):
            continue
        blob = get("/git/blobs/" + item["sha"])
        data = base64.b64decode(blob["content"])
        if hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest() != item["sha"]:
            raise ValueError("License Git object hash mismatch")
        target = destination / (package["name"] + "-" + package["version"] + "-vcs-" + path.name)
        target.write_bytes(data)
        found.append(target)
    print(f"License evidence: {package['name']} {package['version']} from {repo[1]}@{commit}")
    return found


def run(args):
    source = args.source.resolve()
    if subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip() != REVISION:
        raise ValueError("Unexpected renderer revision")
    if subprocess.check_output(["git", "status", "--porcelain"], cwd=source, text=True).strip():
        raise ValueError("Renderer source must be clean before compilation")
    report = args.report.resolve()
    report.mkdir(parents=True, exist_ok=True)
    evidence_dir = report / "evidence"
    evidence_dir.mkdir(exist_ok=True)
    meta = json.loads(subprocess.check_output(["cargo", "metadata", "--locked", "--all-features", "--format-version", "1"], cwd=source, text=True))
    inventory = json.loads(args.inventory.read_text())
    expected = {(c["name"], c["version"]) for c in inventory["components"]}
    actual = {(p["name"], p["version"]) for p in meta["packages"]}
    if expected != actual:
        raise ValueError(f"Dependency inventory drift: missing={expected-actual}, extra={actual-expected}")
    components = []
    for package in sorted(meta["packages"], key=lambda p: (p["name"], p["version"])):
        directory = Path(package["manifest_path"]).parent
        workspace = package["source"] is None
        license_expression = package["license"] or ("MIT" if workspace and directory.is_relative_to(source) else None)
        if not supported_license(license_expression):
            raise ValueError(f"Unreviewed dependency license: {package['name']} {license_expression}")
        files = [p for p in directory.iterdir() if p.is_file() and
                 p.name.upper().startswith(("LICENSE", "LICENCE", "COPYING", "COPYRIGHT", "NOTICE"))]
        if package.get("license_file"):
            files.append(directory / package["license_file"])
        if workspace:
            files.append(source / "README.md")
        files = sorted(set(files))
        if not files and package["source"] and package["source"].startswith("git+"):
            for parent in directory.parents:
                files = [p for p in parent.iterdir() if p.is_file() and
                         p.name.upper().startswith(("LICENSE", "LICENCE", "COPYING", "COPYRIGHT", "NOTICE"))]
                if files or (parent / ".git").exists():
                    break
        if not files:
            files = github_package_licenses(package, directory, evidence_dir)
        if not files:
            raise ValueError(f"License text absent: {package['name']} {package['version']} repository={package.get('repository')} source={package['source']} vcs={(directory / '.cargo_vcs_info.json').exists()} entries={[p.name for p in directory.iterdir()][:30]}")
        evidence = []
        for i, path in enumerate(files):
            name = re.sub(r"[^A-Za-z0-9_.-]", "_", f"{package['name']}-{package['version']}-{i}-{path.name}")
            target = evidence_dir / name
            target.write_text(path.read_text(errors="replace"))
            evidence.append({"file": f"evidence/{name}", "sha256": digest(target)})
        components.append({"name": package["name"], "version": package["version"],
                           "licenseConclusion": license_expression, "evidence": evidence,
                           "basis": "upstream README declaration; LICENSE missing; experimental acceptance only" if workspace else "exact Cargo package license declaration and packaged texts"})
    # Preserve compiler/runtime distribution notices in addition to Cargo package texts.
    sysroot = Path(subprocess.check_output(["rustc", "--print", "sysroot"], text=True).strip())
    rust_docs = sysroot / "share/doc/rust"
    tool_notices = []
    for path in rust_docs.rglob("*"):
        if path.is_file() and (path.name.upper().startswith(("LICENSE", "COPYRIGHT"))) and path.stat().st_size < 8 * 1024 * 1024:
            # HTML reference pages are excluded except the distribution copyright document.
            if path.suffix == ".html" and path.name != "COPYRIGHT.html":
                continue
            target = evidence_dir / ("rust-toolchain-" + hashlib.sha256(str(path.relative_to(rust_docs)).encode()).hexdigest()[:12] + "-" + path.name)
            shutil.copyfile(path, target)
            tool_notices.append({"file": f"evidence/{target.name}", "sha256": digest(target)})
    if not tool_notices:
        raise ValueError("Rust toolchain license/copyright material missing; install rust-docs")
    components[0]["evidence"].extend(tool_notices)
    native = report / "native"
    bin_dir = args.ndk.resolve() / "toolchains/llvm/prebuilt/linux-x86_64/bin"
    for abi, (triple, clang) in TARGETS.items():
        env = os.environ.copy()
        upper = triple.upper().replace("-", "_")
        underscored = triple.replace("-", "_")
        env[f"CARGO_TARGET_{upper}_LINKER"] = str(bin_dir / (clang + "-clang"))
        env[f"CC_{underscored}"] = str(bin_dir / (clang + "-clang"))
        env[f"AR_{underscored}"] = str(bin_dir / "llvm-ar")
        env["RUSTFLAGS"] = "-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384"
        subprocess.run(["cargo", "build", "--locked", "--release", "-p", "gl-compat", "--target", triple], cwd=source, env=env, check=True)
        output = native / abi / "librust_gl.so"
        output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source / "target" / triple / "release/librust_gl.so", output)
    artifacts = [{"abi": abi, "name": "librust_gl.so", "bytes": (native / abi / "librust_gl.so").stat().st_size,
                  "sha256": digest(native / abi / "librust_gl.so")} for abi in TARGETS]
    approval = {"redistributionApproved": False, "experimentalUserAuthorized": True,
                "reviewedBy": "Experimental build policy: user accepted upstream README MIT basis; no legal clearance claimed",
                "sourceRevision": REVISION, "components": components, "artifacts": artifacts,
                "bundleVersion": "0.3.1", "upstreamNativeVersion": "0.3.0",
                "integration": "System EGL/GLES namespace; native source unchanged",
                "toolchain": subprocess.check_output(["rustc", "-Vv"], text=True),
                "ndk": args.ndk.name}
    approval_path = report / "experimental-approval.json"
    approval_path.write_text(json.dumps(approval, indent=2) + "\n")
    subprocess.run([sys.executable, str(Path(__file__).with_name("prepare_rustgl_bundle.py")),
                    "--approval", str(approval_path), "--input", str(native), "--version", "0.3.1",
                    "--inventory", str(args.inventory.resolve()), "--output", str(args.output.resolve()),
                    "--readelf", str(bin_dir / "llvm-readelf"), "--experimental-mit-declaration"], check=True)
    print("::notice title=RustGL bundled source build::" + json.dumps({"revision": REVISION, "artifacts": artifacts, "licenses": len(components), "experimental": True}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--inventory", type=Path, default=Path("docs/development/rustgl-cargo-inventory.json"))
    parser.add_argument("--output", type=Path, default=Path("app_pojavlauncher/src/main/assets/renderer-bundles/rustgl"))
    try:
        run(parser.parse_args())
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        print(f"::error title=RustGL source build::{error}")
        raise
