#!/usr/bin/env python3
"""Import only an explicitly audited native payload. This does not grant a license."""
import argparse
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import tempfile

PERMISSIVE = {"MIT", "Apache-2.0", "ISC", "BSD-2-Clause", "BSD-3-Clause", "Zlib", "MIT OR Apache-2.0", "Apache-2.0 OR MIT", "Unicode-3.0", "Unicode-DFS-2016", "CC0-1.0", "BSL-1.0", "0BSD", "Unlicense"}
SYSTEM = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so", "libEGL.so", "libGLESv2.so", "libGLESv3.so", "libvulkan.so"}
ABIS = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40), "x86": (1, 3), "x86_64": (2, 62)}


def require(value, message):
    if not value:
        raise ValueError(message)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare(args):
    audit_path = args.approval.resolve()
    audit = json.loads(audit_path.read_text())
    inventory = json.loads(args.inventory.read_text())
    revision = inventory["sourceRevision"]
    redistribution_approved = audit.get("redistributionApproved") is True
    experimental = getattr(args, "experimental_mit_declaration", False) and audit.get("experimentalUserAuthorized") is True
    require((redistribution_approved or experimental) and audit.get("reviewedBy"), "Named redistribution review required")
    require(audit.get("sourceRevision") == revision, "Approval must cover the pinned source revision")
    require(re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", args.version), "Bundle version must be numeric semver")
    expected = {(c["name"], c["version"]) for c in inventory["components"]}
    components = audit["components"]
    require(len(components) == len(expected) and {(c["name"], c["version"]) for c in components} == expected,
            "Every locked component, including workspace crates, requires an explicit license conclusion")
    notices = []
    for component in components:
        tokens = set(re.findall(r"[A-Za-z0-9.+-]+", component["licenseConclusion"])) - {"AND", "OR"}
        require(bool(tokens) and tokens <= PERMISSIVE, "Unknown/copyleft license requires a separate reviewed distribution method")
        require(component.get("evidence"), "License evidence required")
        for evidence in component["evidence"]:
            path = audit_path.parent / evidence["file"]
            require(path.resolve().is_relative_to(audit_path.parent) and not path.is_symlink(), "Evidence must stay in the approval directory")
            require(digest(path) == evidence["sha256"], "License evidence checksum mismatch")
            notices.append(f"\n--- {component['name']} {component['version']} ({component['licenseConclusion']}) ---\n" + path.read_text())
    require(notices, "License and copyright texts required")
    notice_bytes = "".join(notices).encode()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    previous = json.loads((output / "index.json").read_text()) if (output / "index.json").exists() else None
    releases = []
    with tempfile.TemporaryDirectory(prefix="rustgl-import-", dir=output.parent) as staging_name:
        staging = pathlib.Path(staging_name)
        for abi, layout in ABIS.items():
            approved = [a for a in audit["artifacts"] if a["abi"] == abi]
            require(any(a["name"] == "librust_gl.so" for a in approved), f"Missing {abi} renderer approval")
            names = {a["name"] for a in approved}
            require(len(names) == len(approved), "Duplicate artifact")
            entries = []
            directory = staging / args.version / abi
            directory.mkdir(parents=True)
            for artifact in approved:
                name = artifact["name"]
                require(re.fullmatch(r"lib[A-Za-z0-9_+.\-]+\.so", name), "Invalid library basename")
                path = args.input / abi / name
                require(not path.is_symlink() and path.is_file(), "Plain native file required")
                data = path.read_bytes()
                require(0 < len(data) <= 128 * 1024 * 1024, "Native file size out of bounds")
                require(len(data) == artifact["bytes"] and digest(path) == artifact["sha256"], "Unapproved native bytes")
                require(len(data) >= 64 and data[:4] == b"\x7fELF" and data[4] == layout[0] and data[5] == 1
                        and int.from_bytes(data[16:18], "little") == 3 and int.from_bytes(data[18:20], "little") == layout[1], "ELF ABI/type mismatch")
                details = subprocess.check_output([args.readelf, "-dW", "-lW", str(path)], text=True)
                needed = set(re.findall(r"\(NEEDED\).*?\[([^\]]+)\]", details))
                require(needed <= SYSTEM | names, f"Unresolved native dependencies: {needed - SYSTEM - names}")
                loads = [line.split() for line in details.splitlines() if line.strip().startswith("LOAD ")]
                require(loads and all(int(line[-1], 16) >= 16384 for line in loads), "16 KiB ELF load alignment required")
                require(not any("RWE" in "".join(line[6:-1]) for line in loads), "Writable-executable segment rejected")
                shutil.copyfile(path, directory / name)
                entries.append({"name": name, "bytes": len(data), "sha256": digest(path)})
            (directory / "NOTICE.txt").write_bytes(notice_bytes)
            entries.append({"name": "NOTICE.txt", "bytes": len(notice_bytes), "sha256": hashlib.sha256(notice_bytes).hexdigest()})
            releases.append({"version": args.version, "revision": revision, "abi": abi, "files": entries})
        require(not (output / args.version).exists(), "Immutable bundle version already exists; use a new version")
        os.replace(staging / args.version, output / args.version)
    old = previous["releases"] if previous else []
    index = {"schema": 1, "redistributionApproved": redistribution_approved, "experimentalUserAuthorized": experimental, "backend": "gles", "releases": releases + old}
    temporary = output / "index.json.tmp"
    with temporary.open("w") as stream:
        json.dump(index, stream, indent=2); stream.write("\n"); stream.flush(); os.fsync(stream.fileno())
    os.replace(temporary, output / "index.json")
    print(f"Imported {len(releases)} ABI payloads; APK signing authenticates index and bytes. No Vulkan qualification implied.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--experimental-mit-declaration", action="store_true", help="Explicit experimental policy; does not claim legal clearance")
    parser.add_argument("--approval", required=True, type=pathlib.Path)
    parser.add_argument("--input", required=True, type=pathlib.Path, help="audited native files arranged by Android ABI")
    parser.add_argument("--version", required=True)
    parser.add_argument("--readelf", required=True, help="absolute path to NDK llvm-readelf")
    parser.add_argument("--inventory", type=pathlib.Path, default=pathlib.Path("docs/development/rustgl-cargo-inventory.json"))
    parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path("app_pojavlauncher/src/main/assets/renderer-bundles/rustgl"))
    try:
        prepare(parser.parse_args())
    except (ValueError, KeyError, OSError) as error:
        parser.exit(1, f"Import refused: {error}\n")
