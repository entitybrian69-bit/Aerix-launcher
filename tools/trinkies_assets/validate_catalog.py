#!/usr/bin/env python3
"""Validate generated Trinkies item contracts, visual budgets, UVs, and packed size."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
import zipfile
from pathlib import Path, PurePosixPath
from typing import Any

from jsonschema import Draft202012Validator
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSET_ROOT = ROOT / "trinkies_assets" / "trinkies"
ITEM_ROOT = ASSET_ROOT / "items"
SCHEMAS = ROOT / "docs" / "wardrobe" / "schema"
ITEM_SCHEMA = json.loads((SCHEMAS / "item.schema.json").read_text(encoding="utf-8"))
ANIMATION_SCHEMA = json.loads((SCHEMAS / "animation.schema.json").read_text(encoding="utf-8"))
ITEM_VALIDATOR = Draft202012Validator(ITEM_SCHEMA)
ANIMATION_VALIDATOR = Draft202012Validator(ANIMATION_SCHEMA)

EXPECTED_CATEGORIES = ("hat", "suit", "back", "pet", "emote", "aura")
CATEGORY_TEXTURE_SIZE = {"hat": 64, "suit": 128, "back": 128, "pet": 128, "emote": 64, "aura": 32}
CATEGORY_CUBE_BUDGET = {"hat": 40, "suit": 80, "back": 60, "pet": 70, "emote": 30, "aura": 24}
MAX_COMPRESSED_BYTES = 15 * 1024 * 1024


class ValidationError(Exception):
    pass


def fail(message: str) -> None:
    raise ValidationError(message)


def safe_item_path(item_dir: Path, relative: str) -> Path:
    path_part = PurePosixPath(relative)
    if path_part.is_absolute() or ".." in path_part.parts:
        fail(f"unsafe asset path: {relative}")
    path = (item_dir / Path(*path_part.parts)).resolve()
    try:
        path.relative_to(item_dir.resolve())
    except ValueError:
        fail(f"asset path escapes item directory: {relative}")
    if not path.is_file():
        fail(f"missing item asset: {path}")
    return path


def assert_close(a: float, b: float, label: str) -> None:
    if abs(a - b) > 1e-5:
        fail(f"loop boundary pop in {label}: {a} != {b}")


def validate_loop_boundaries(item_id: str, animations: dict[str, Any]) -> None:
    for clip_name, clip in animations["clips"].items():
        if clip["loop_mode"] not in ("loop", "ping_pong"):
            continue
        for bone, frames in clip["bones"].items():
            first, last = frames[0], frames[-1]
            for field in ("position", "scale"):
                for axis, (a, b) in enumerate(zip(first[field], last[field])):
                    assert_close(a, b, f"{item_id}/{clip_name}/{bone}/{field}[{axis}]")
            for axis, (a, b) in enumerate(zip(first["rotation"], last["rotation"])):
                # 0° and 360° describe the same pose; normalize Euler differences for loop checks.
                delta = ((a - b + 180.0) % 360.0) - 180.0
                assert_close(delta, 0.0, f"{item_id}/{clip_name}/{bone}/rotation[{axis}]")
            times = [frame["time"] for frame in frames]
            if times != sorted(times) or times[0] != 0 or times[-1] != clip["duration"]:
                fail(f"non-monotonic or incomplete keyframe track: {item_id}/{clip_name}/{bone}")


def luminance(hex_color: str) -> float:
    channels = [int(hex_color[index:index + 2], 16) / 255.0 for index in (1, 3, 5)]
    linear = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]


def validate_item(item_path: Path, seen_ids: set[str]) -> tuple[str, int, int]:
    item_dir = item_path.parent
    item = json.loads(item_path.read_text(encoding="utf-8"))
    errors = sorted(ITEM_VALIDATOR.iter_errors(item), key=lambda e: list(e.path))
    if errors:
        fail(f"{item_path.relative_to(ROOT)}: {errors[0].message} at {list(errors[0].path)}")
    item_id = item["id"]
    if item_id in seen_ids:
        fail(f"duplicate item ID: {item_id}")
    seen_ids.add(item_id)
    category = item["category"]
    if item_dir.name != item_id.split(":", 1)[1]:
        fail(f"directory slug must match namespaced item ID: {item_path}")
    if len(item["palette"]) < 5 or len(item["palette"]) > 8:
        fail(f"palette must have 5–8 colours: {item_id}")
    if max(map(luminance, item["palette"])) - min(map(luminance, item["palette"])) < 0.2:
        fail(f"palette shading ramp has insufficient luminance range: {item_id}")

    model_path = safe_item_path(item_dir, item["model"])
    texture_path = safe_item_path(item_dir, item["texture"])
    animation_path = safe_item_path(item_dir, item["animation_file"])
    model = json.loads(model_path.read_text(encoding="utf-8"))
    animations = json.loads(animation_path.read_text(encoding="utf-8"))
    errors = sorted(ANIMATION_VALIDATOR.iter_errors(animations), key=lambda e: list(e.path))
    if errors:
        fail(f"{animation_path.relative_to(ROOT)}: {errors[0].message} at {list(errors[0].path)}")
    if set(item["animations"]) != set(animations["clips"]):
        fail(f"item animation references do not match clip list: {item_id}")
    validate_loop_boundaries(item_id, animations)
    for clip_name, clip in animations["clips"].items():
        for bone, track in clip["bones"].items():
            if any(frame["time"] > clip["duration"] for frame in track):
                fail(f"keyframe is past clip end: {item_id}/{clip_name}/{bone}")
    if category == "emote":
        for clip_name, clip in animations["clips"].items():
            if clip_name == "play" and not 1.0 <= clip["duration"] <= 8.0:
                fail(f"emote duration outside 1–8 seconds: {item_id}")
        if len(model.get("elements", [])) > 30:
            fail(f"emote prop model exceeds 30 cubes: {item_id}")

    if model.get("meta", {}).get("model_format") != "free" or model.get("meta", {}).get("format_version") != "4.9":
        fail(f"model must be Blockbench-compatible free-format v4.9: {item_id}")
    resolution = model.get("resolution", {})
    texture_size = CATEGORY_TEXTURE_SIZE.get(category)
    if texture_size is None:
        fail(f"no texture budget declared for category {category}")
    if (resolution.get("width"), resolution.get("height")) != (texture_size, texture_size):
        fail(f"texture/model resolution for {item_id} must be {texture_size}×{texture_size}")
    with Image.open(texture_path) as texture:
        texture.load()
        if texture.size != (texture_size, texture_size):
            fail(f"texture size exceeds category budget: {item_id} {texture.size}")
        used_colors = len(set(texture.convert("RGBA").getdata()))
        if used_colors > 8:
            fail(f"pixel texture uses more than 8 colors: {item_id} ({used_colors})")

    texture_bytes = texture_path.read_bytes()
    textures = model.get("textures", [])
    if not textures or textures[0].get("source") != "data:image/png;base64," + __import__("base64").b64encode(texture_bytes).decode("ascii"):
        fail(f"Blockbench model does not embed the exact referenced texture: {item_id}")
    elements = model.get("elements", [])
    if not elements or len(elements) > CATEGORY_CUBE_BUDGET[category]:
        fail(f"cube budget/empty model violation: {item_id} has {len(elements)} cubes")
    element_keys = set()
    coverage = [[False] * texture_size for _ in range(texture_size)]
    for element in elements:
        start, end = element.get("from", []), element.get("to", [])
        if len(start) != 3 or len(end) != 3 or any(a >= b for a, b in zip(start, end)):
            fail(f"degenerate cube: {item_id}/{element.get('name')}")
        key = (element.get("name"), tuple(start), tuple(end), tuple(element.get("origin", [])))
        if key in element_keys:
            fail(f"identical overlapping cube/possible z-fight: {item_id}/{element.get('name')}")
        element_keys.add(key)
        faces = element.get("faces", {})
        if set(faces) != {"north", "east", "south", "west", "up", "down"}:
            fail(f"cube must define all six faces: {item_id}/{element.get('name')}")
        for face_name, face in faces.items():
            uv = face.get("uv", [])
            if len(uv) != 4:
                fail(f"invalid UV rectangle: {item_id}/{element.get('name')}/{face_name}")
            u0, v0, u1, v1 = uv
            if u0 < 0 or v0 < 0 or u1 > texture_size or v1 > texture_size or u0 >= u1 or v0 >= v1:
                fail(f"UV outside texture bounds: {item_id}/{element.get('name')}/{face_name}")
            x0, y0, x1, y1 = map(int, (u0, v0, u1, v1))
            for y in range(y0, y1):
                coverage[y][x0:x1] = [True] * (x1 - x0)
    if not all(all(row) for row in coverage):
        uncovered = sum(not value for row in coverage for value in row)
        fail(f"unused UV texels: {item_id} leaves {uncovered} of {texture_size * texture_size} texels unreferenced")

    if item.get("emissive_mask"):
        mask_path = safe_item_path(item_dir, item["emissive_mask"])
        with Image.open(mask_path) as mask:
            if mask.size != (texture_size, texture_size):
                fail(f"emissive mask size differs from texture: {item_id}")
            if mask.mode not in ("L", "1", "RGB", "RGBA"):
                fail(f"unsupported emissive mask channel type: {item_id}")
    if item.get("particle_sheet"):
        particle_path = safe_item_path(item_dir, item["particle_sheet"])
        with Image.open(particle_path) as particle:
            if particle.size != (32, 32):
                fail(f"aura particle sheet must be 32×32: {item_id}")
    outliner = model.get("outliner", [])
    group_names = {group.get("name") for group in outliner}
    required_bones = {"root", "head", "body", "left_arm", "right_arm", "left_leg", "right_leg"}
    if not required_bones.issubset(group_names):
        fail(f"model is missing shared player rig bones: {item_id} missing {sorted(required_bones - group_names)}")
    if item["attach"]["bone"] not in group_names:
        fail(f"attachment bone is absent from Blockbench outliner: {item_id}/{item['attach']['bone']}")
    cube_ids = {element.get("uuid") for element in elements}
    grouped_ids = {child for group in outliner for child in group.get("children", []) if isinstance(child, str)}
    if cube_ids != grouped_ids:
        fail(f"model outliner does not contain every cube exactly once: {item_id}")
    return category, len(elements), texture_size


def validate_manifest() -> None:
    manifest_path = ASSET_ROOT / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    actual = {
        path.relative_to(ASSET_ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(ASSET_ROOT.rglob("*")) if path.is_file() and path.name != "manifest.json"
    }
    if manifest.get("sha256") != actual:
        fail("asset manifest SHA-256 list does not match files; run generate_slice.py")


def packed_size() -> int:
    archive = ASSET_ROOT.parent / ".trinkies_asset_budget.tmp.zip"
    try:
        with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
            for path in sorted(p for p in ASSET_ROOT.rglob("*") if p.is_file()):
                output.write(path, path.relative_to(ASSET_ROOT.parent).as_posix())
        return archive.stat().st_size
    finally:
        archive.unlink(missing_ok=True)


def validate(require_slice: bool) -> None:
    item_paths = sorted(ITEM_ROOT.glob("*/item.json"))
    if not item_paths:
        fail("no generated item.json files found")
    seen: set[str] = set()
    counts = {category: 0 for category in EXPECTED_CATEGORIES}
    for item_path in item_paths:
        category, cubes, _ = validate_item(item_path, seen)
        if category not in counts:
            fail(f"unregistered slice category: {category}")
        counts[category] += 1
        print(f"OK {item_path.parent.name}: {cubes} cubes")
    if require_slice and counts != {category: 5 for category in EXPECTED_CATEGORIES}:
        fail(f"vertical slice must contain exactly five items/category: {counts}")
    catalog = json.loads((ASSET_ROOT / "catalog.json").read_text(encoding="utf-8"))
    catalog_ids = [entry.get("id") for entry in catalog.get("items", [])]
    if set(catalog_ids) != seen or len(catalog_ids) != len(seen):
        fail("catalog.json item IDs do not match the validated item set")
    if catalog.get("status") != "vertical_slice_unverified_in_game":
        fail("catalog must retain the in-game verification status")
    if len(seen) != 30 and require_slice:
        fail(f"expected exactly 30 slice items, found {len(seen)}")
    validate_manifest()
    size = packed_size()
    if size >= MAX_COMPRESSED_BYTES:
        fail(f"compressed asset pack exceeds 15 MiB: {size} bytes")
    print(f"Validated {len(seen)} items: {counts}")
    print(f"Compressed asset pack: {size:,} bytes (budget < {MAX_COMPRESSED_BYTES:,})")
    print("In-game rendering and device screenshots are not part of this offline validator.")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--require-slice", action="store_true", help="require exactly five items in each of six categories")
    args = parser.parse_args()
    try:
        validate(args.require_slice)
    except (ValidationError, OSError, json.JSONDecodeError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
