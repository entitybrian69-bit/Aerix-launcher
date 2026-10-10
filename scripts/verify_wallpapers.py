#!/usr/bin/env python3
"""Verify the Aerix wallpaper catalog and its packaged JPEG assets."""
from pathlib import Path
import re
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/WallpaperUtils.java"
ASSET_DIR = ROOT / "app_pojavlauncher/src/main/assets/aerix_wallpapers"
EXPECTED_COUNT = 25


def jpeg_dimensions(data: bytes):
    if len(data) < 4 or data[:2] != b"\xff\xd8":
        raise ValueError("not a JPEG stream")
    index = 2
    frame_markers = {0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF}
    while index + 4 <= len(data):
        if data[index] != 0xFF:
            index += 1
            continue
        while index < len(data) and data[index] == 0xFF:
            index += 1
        if index >= len(data):
            break
        marker = data[index]
        index += 1
        if marker in (0xD8, 0xD9) or 0xD0 <= marker <= 0xD7 or marker == 0x01:
            continue
        if index + 2 > len(data):
            break
        length = struct.unpack_from(">H", data, index)[0]
        if length < 2 or index + length > len(data):
            break
        if marker in frame_markers:
            if length < 7:
                break
            height, width = struct.unpack_from(">HH", data, index + 3)
            return width, height
        index += length
    raise ValueError("JPEG dimensions not found")


def main() -> int:
    source = SOURCE.read_text(encoding="utf-8")
    catalog = re.findall(r'new Wallpaper\("([^"]+)"', source)
    if len(catalog) != EXPECTED_COUNT:
        print(f"Expected {EXPECTED_COUNT} wallpaper catalog entries, found {len(catalog)}", file=sys.stderr)
        return 1
    if len(set(catalog)) != len(catalog):
        print("Duplicate wallpaper IDs found", file=sys.stderr)
        return 1

    files = {path.stem: path for path in ASSET_DIR.glob("*.jpg")}
    missing = sorted(set(catalog) - set(files))
    extra = sorted(set(files) - set(catalog))
    if missing or extra:
        print(f"Wallpaper asset mismatch; missing={missing}, extra={extra}", file=sys.stderr)
        return 1

    total_bytes = 0
    for wallpaper_id in catalog:
        path = files[wallpaper_id]
        data = path.read_bytes()
        if len(data) > 2 * 1024 * 1024:
            print(f"{path.relative_to(ROOT)} exceeds the 2 MiB asset budget", file=sys.stderr)
            return 1
        try:
            width, height = jpeg_dimensions(data)
        except ValueError as exc:
            print(f"{path.relative_to(ROOT)}: {exc}", file=sys.stderr)
            return 1
        if width <= 0 or height <= 0 or abs(width * 9 - height * 16) > width:
            print(f"{path.relative_to(ROOT)} has unexpected dimensions {width}x{height}", file=sys.stderr)
            return 1
        total_bytes += len(data)

    print(f"Verified {len(catalog)} catalog entries and {len(files)} landscape JPEG assets ({total_bytes:,} bytes).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
