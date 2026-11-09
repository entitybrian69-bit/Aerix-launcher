#!/usr/bin/env python3
"""Verify the built client-only Trinkies JAR includes the shared vertical-slice catalog."""
import json
import sys
import zipfile
from pathlib import Path


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: trinkies_jar_verify.py PATH_TO_JAR", file=sys.stderr)
        return 2

    jar_path = Path(sys.argv[1])
    try:
        with zipfile.ZipFile(jar_path) as jar:
            descriptor = json.loads(jar.read("fabric.mod.json"))
            if descriptor.get("id") != "trinkies":
                raise ValueError("fabric.mod.json has an unexpected mod ID")
            if descriptor.get("environment") != "client":
                raise ValueError("Trinkies must be client-only")
            entrypoints = descriptor.get("entrypoints", {}).get("client", [])
            if "aerix.client.mod.AerixClientMod" not in entrypoints:
                raise ValueError("client entrypoint is missing")

            catalog = json.loads(jar.read("assets/trinkies/catalog.json"))
            if catalog.get("status") != "vertical_slice_unverified_in_game":
                raise ValueError("catalog does not declare its unverified vertical-slice status")
            items = catalog.get("items")
            if not isinstance(items, list) or len(items) != 30:
                raise ValueError("expected exactly 30 catalog entries")
            counts: dict[str, int] = {}
            for item in items:
                category = item.get("category")
                counts[category] = counts.get(category, 0) + 1
            if counts != {"hat": 5, "suit": 5, "back": 5, "pet": 5, "emote": 5, "aura": 5}:
                raise ValueError(f"unexpected category counts: {counts}")
            if "assets/trinkies/manifest.json" not in jar.namelist():
                raise ValueError("asset manifest is missing from the mod JAR")
    except (OSError, KeyError, json.JSONDecodeError, zipfile.BadZipFile, ValueError) as error:
        print(f"Trinkies JAR verification failed: {error}", file=sys.stderr)
        return 1

    print(f"Verified {jar_path}: client-only Trinkies, 30 catalog assets, categories={counts}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
