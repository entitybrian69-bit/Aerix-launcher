#!/usr/bin/env python3
"""Fail if a universal Aerix APK is missing core native launch/render libraries."""

import sys
import zipfile
from pathlib import Path

SUPPORTED_ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
REQUIRED_LIBRARIES = (
    # CMake-built launch/runtime bridge, AWT and windowing stack.
    "libpojavexec.so",
    "libpojavexec_awt.so",
    "libawt_headless.so",
    "libawt_xawt.so",
    "libmobileglues_info_getter.so",
    "libmojoexec.so",
    "libglfw.so",
    "libSDL3.so",
    # Baseline packaged renderer and Java/native runtime support.
    "libgl4es_114.so",
    "libfreetype.so",
    "libjnidispatch.so",
    "libunpack200.so",
    # Renderer AARs currently committed in app_pojavlauncher/libs.
    "libmobileglues.so",
    "libSimpleFPEWrapper.so",
    "libng_gl4es.so",
    "libEGL_angle.so",
    "libGLESv2_angle.so",
    "libshaderc.so",
    "libspirv-cross-c-shared.so",
)
REQUIRED_LIBRARIES_BY_ABI = {
    # MojoExec's Turnip namespace loader is only built for arm64-v8a.
    "arm64-v8a": ("liblinkerhook.so",),
}


def verify(path: Path) -> list[str]:
    errors = []
    with zipfile.ZipFile(path) as apk:
        names = set(apk.namelist())
    for abi in SUPPORTED_ABIS:
        libraries = REQUIRED_LIBRARIES + REQUIRED_LIBRARIES_BY_ABI.get(abi, ())
        for library in libraries:
            entry = f"lib/{abi}/{library}"
            if entry not in names:
                errors.append(f"missing {entry}")
    return errors


def main() -> int:
    if len(sys.argv) != 2:
        print(f"usage: {Path(sys.argv[0]).name} APK", file=sys.stderr)
        return 2
    apk = Path(sys.argv[1])
    if not apk.is_file():
        print(f"APK not found: {apk}", file=sys.stderr)
        return 2
    try:
        errors = verify(apk)
    except zipfile.BadZipFile:
        print(f"Not a valid APK/ZIP archive: {apk}", file=sys.stderr)
        return 2
    if errors:
        print("ABI verification failed:", file=sys.stderr)
        for error in errors:
            print(f"  {error}", file=sys.stderr)
        return 1
    print(
        f"Verified {len(SUPPORTED_ABIS)} ABIs and "
        f"{len(REQUIRED_LIBRARIES)} required native libraries in {apk}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
