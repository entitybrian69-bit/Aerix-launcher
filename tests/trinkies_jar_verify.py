#!/usr/bin/env python3
"""Verify the built client-only Trinkies JAR contains the full shared catalog pack."""
import hashlib
import json
from collections import Counter
import sys
import zipfile
from pathlib import Path

EXPECTED_COUNTS = {"hat": 50, "suit": 50, "back": 50, "pet": 50, "emote": 50, "aura": 50}


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: trinkies_jar_verify.py PATH_TO_JAR", file=sys.stderr)
        return 2

    jar_path = Path(sys.argv[1])
    try:
        with zipfile.ZipFile(jar_path) as jar:
            names = set(jar.namelist())
            descriptor = json.loads(jar.read("fabric.mod.json"))
            if descriptor.get("id") != "trinkies":
                raise ValueError("fabric.mod.json has an unexpected mod ID")
            if descriptor.get("environment") != "client":
                raise ValueError("Trinkies must be client-only")
            entrypoints = descriptor.get("entrypoints", {}).get("client", [])
            if "aerix.client.mod.AerixClientMod" not in entrypoints:
                raise ValueError("client entrypoint is missing")

            catalog = json.loads(jar.read("assets/trinkies/catalog.json"))
            if catalog.get("status") != "full_catalog_unverified_in_game":
                raise ValueError("catalog does not declare its unverified full-catalog status")
            items = catalog.get("items")
            if not isinstance(items, list) or len(items) != 300 or catalog.get("item_count") != 300:
                raise ValueError("expected exactly 300 catalog entries")
            counts: dict[str, int] = {}
            for item in items:
                category = item.get("category")
                counts[category] = counts.get(category, 0) + 1
                for field in ("item", "texture", "preview"):
                    relative_path = item.get(field)
                    if not isinstance(relative_path, str) or not relative_path.startswith("items/"):
                        raise ValueError(f"invalid {field} asset path for {item.get('id')}")
                    jar_pathname = f"assets/trinkies/{relative_path}"
                    if jar_pathname not in names:
                        raise ValueError(f"catalog asset is missing from the JAR: {jar_pathname}")
            if counts != EXPECTED_COUNTS or catalog.get("category_counts") != EXPECTED_COUNTS:
                raise ValueError(f"unexpected category counts: {counts}")
            rarity_counts = Counter(item.get("rarity") for item in items)
            if rarity_counts != Counter({"common": 121, "epic": 64, "legendary": 61, "mythic": 49, "eternal": 5}):
                raise ValueError(f"unexpected rarity distribution: {rarity_counts}")
            eternal_ids = {item.get("id") for item in items if item.get("rarity") == "eternal"}
            expected_eternal_ids = {
                "trinkies:hat_full_frost_circlet",
                "trinkies:suit_full_crimson_mechanist",
                "trinkies:back_full_aurora_drone",
                "trinkies:pet_full_moss_crab",
                "trinkies:aura_full_aurora_spectrum",
            }
            if eternal_ids != expected_eternal_ids:
                raise ValueError(f"unexpected Eternal items: {sorted(eternal_ids)}")

            manifest_name = "assets/trinkies/manifest.json"
            if manifest_name not in names:
                raise ValueError("asset manifest is missing from the mod JAR")
            manifest = json.loads(jar.read(manifest_name))
            hashes = manifest.get("sha256")
            if manifest.get("item_count") != 300 or not isinstance(hashes, dict):
                raise ValueError("asset manifest is missing the generated full-catalog file hashes")
            packaged_paths = {
                name.removeprefix("assets/trinkies/")
                for name in names
                if name.startswith("assets/trinkies/") and not name.endswith("/") and name != manifest_name
            }
            if set(hashes) != packaged_paths:
                raise ValueError("asset manifest paths do not match the files packaged in the JAR")
            for relative_path, expected_hash in hashes.items():
                if not isinstance(relative_path, str) or relative_path.startswith("/") or ".." in Path(relative_path).parts:
                    raise ValueError(f"invalid manifest path: {relative_path}")
                pathname = f"assets/trinkies/{relative_path}"
                if pathname not in names:
                    raise ValueError(f"manifest asset is missing from the JAR: {pathname}")
                actual_hash = hashlib.sha256(jar.read(pathname)).hexdigest()
                if actual_hash != expected_hash:
                    raise ValueError(f"asset hash mismatch in JAR: {pathname}")
    except (OSError, KeyError, json.JSONDecodeError, zipfile.BadZipFile, ValueError) as error:
        message = f"Trinkies JAR verification failed: {error}"
        try:
            with zipfile.ZipFile(jar_path) as jar:
                candidates = [name for name in jar.namelist() if name == "fabric.mod.json" or name.startswith("assets/trinkies/")]
                message += f"; relevant entries: {candidates[:24]}"
        except (OSError, zipfile.BadZipFile):
            pass
        print(message, file=sys.stderr)
        escaped = message.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
        print(f"::error title=Trinkies JAR verification failed::{escaped}")
        return 1

    print(f"Verified {jar_path}: client-only Trinkies, 300 catalog items, categories={counts}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
