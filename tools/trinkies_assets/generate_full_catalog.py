#!/usr/bin/env python3
"""Regenerate the complete 300-item Trinkies asset pack deterministically.

The original 30-row vertical slice stays hand-authored in generate_slice.py. This tool adds
45 new family/theme combinations per category (270 additional items), reuses the shared
Blockbench/texture/animation serializer, then writes the full catalog, SHA-256 manifest,
300-row roster, and transparent shaded 3D model preview icons/sheet.
"""
from __future__ import annotations

import hashlib
import json
import shutil
import sys
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from tools.trinkies_assets import full_catalog  # noqa: E402
from tools.trinkies_assets import generate_slice as base  # noqa: E402

ASSET_ROOT = base.ASSET_ROOT
ITEM_ROOT = base.ITEM_ROOT
DOCS_ROOT = base.DOCS_ROOT
CATEGORY_ORDER = ("hat", "suit", "back", "pet", "emote", "aura")
CATEGORY_LABEL = {
    "hat": "HATS", "suit": "SUITS", "back": "BACK & WINGS",
    "pet": "PETS", "emote": "EMOTES", "aura": "AURAS & TRAILS",
}


def draw_full_model_preview_sheet(records: list[dict[str, Any]]) -> Path:
    columns, cell_w, cell_h = 10, 174, 106
    margin, header = 12, 34
    rows = (len(records) + columns - 1) // columns
    image = Image.new("RGB", (margin * 2 + columns * cell_w, margin * 2 + header + rows * cell_h), (19, 22, 27))
    draw = ImageDraw.Draw(image)
    try:
        title_font = ImageFont.truetype("DejaVuSans.ttf", 17)
        name_font = ImageFont.truetype("DejaVuSans.ttf", 11)
        detail_font = ImageFont.truetype("DejaVuSans.ttf", 8)
    except OSError:
        title_font = name_font = detail_font = ImageFont.load_default()
    draw.text((margin, 7), "TRINKIES · FULL 300 · SHADED 3D MODEL PREVIEWS · NOT IN-GAME RENDERS",
              fill=(231, 239, 236), font=title_font)
    rarity_color = base.RARITY_ACCENT_RGB
    for index, record in enumerate(records):
        item = record["item"]
        x = margin + (index % columns) * cell_w
        y = margin + header + (index // columns) * cell_h
        color = rarity_color[item["rarity"]]
        draw.rectangle((x + 2, y + 2, x + cell_w - 3, y + cell_h - 3), fill=(31, 35, 41), outline=color, width=1)
        preview_path = ITEM_ROOT / item["id"].split(":", 1)[1] / "preview.png"
        with Image.open(preview_path) as source_preview:
            preview = source_preview.convert("RGBA")
            preview.thumbnail((58, 58), Image.Resampling.NEAREST)
            image.paste(preview, (x + 6, y + 6), preview)
        draw.text((x + 70, y + 7), item["name"][:17], fill=(243, 245, 240), font=name_font)
        draw.text((x + 70, y + 25), item["id"].split(":", 1)[1][:18], fill=(164, 180, 185), font=detail_font)
        draw.text((x + 70, y + 41), f"{CATEGORY_LABEL[item['category']]} · {item['rarity'].upper()}",
                  fill=color, font=detail_font)
        draw.text((x + 70, y + 56), f"{item['shape'][:18]}", fill=(130, 146, 151), font=detail_font)
    output = DOCS_ROOT / "full-catalog-model-preview-sheet.png"
    image.save(output, format="PNG", optimize=True, compress_level=9)
    return output


def write_roster(records: list[dict[str, Any]]) -> Path:
    lines = [
        "# Full Trinkies catalog roster",
        "",
        "This generated roster has 300 unique catalog IDs: 50 per category. The initial 30 remain the original vertical slice; the 270 additions are nine distinct model families across five material themes per category. Theme index also changes model geometry. The generated asset set is structurally validated, but not Blockbench-reviewed or verified in Minecraft.",
        "",
        "| Category | ID | Name | Form / palette recipe | Rarity |",
        "|---|---|---|---|---|",
    ]
    for category in CATEGORY_ORDER:
        for record in records:
            item = record["item"]
            if item["category"] != category:
                continue
            lines.append(
                f"| {category} | `{item['id']}` | {item['name']} | `{item['shape']}` · "
                f"{', '.join(item['palette'][:2])} | {item['rarity']} |"
            )
    lines += ["", "See `full-catalog-model-preview-sheet.png` for shaded 3D renders of the generated cube models. These are launcher/catalog previews, not Minecraft screenshots.", ""]
    output = DOCS_ROOT / "ROSTER.md"
    output.write_text("\n".join(lines), encoding="utf-8")
    return output


def remove_stale_managed_items(expected_slugs: set[str]) -> None:
    """Remove only obsolete item folders recorded in the previous generated hash manifest."""
    manifest_path = ASSET_ROOT / "manifest.json"
    if not manifest_path.is_file():
        return
    try:
        previous = json.loads(manifest_path.read_text(encoding="utf-8"))
        managed_paths = previous.get("sha256", {})
        if not isinstance(managed_paths, dict):
            raise ValueError("manifest sha256 must be an object")
    except (OSError, json.JSONDecodeError, ValueError) as error:
        raise SystemExit(f"Refusing to prune stale catalog folders with an invalid prior manifest: {error}")
    managed_slugs = {Path(path).parts[1] for path in managed_paths if len(Path(path).parts) >= 3 and Path(path).parts[0] == "items"}
    for slug in sorted(managed_slugs - expected_slugs):
        item_dir = ITEM_ROOT / slug
        if item_dir.is_dir() and item_dir.resolve().parent == ITEM_ROOT.resolve():
            shutil.rmtree(item_dir)
            print(f"Removed obsolete manifest-managed item folder: {slug}")


def generate() -> None:
    rows = full_catalog.all_rows()
    if len(rows) != 300:
        raise SystemExit(f"Expected 300 item rows, got {len(rows)}")
    item_slugs = [f"{row[0]}_{row[1]}" for row in rows]
    if len(set(item_slugs)) != len(item_slugs):
        raise SystemExit("The full roster contains duplicate item IDs")
    remove_stale_managed_items(set(item_slugs))
    original_shape_parts = base.shape_parts

    def shape_parts(shape: str) -> list[dict[str, Any]]:
        if shape.startswith("full_"):
            return full_catalog.generated_parts(shape)
        return original_shape_parts(shape)

    base.shape_parts = shape_parts
    ITEM_ROOT.mkdir(parents=True, exist_ok=True)
    DOCS_ROOT.mkdir(parents=True, exist_ok=True)
    records = [base.item_record(row) for row in rows]
    category_counts = {category: 0 for category in CATEGORY_ORDER}
    catalog_items = []
    for record in records:
        item = record["item"]
        category_counts[item["category"]] += 1
        folder = Path("items") / item["id"].split(":", 1)[1]
        catalog_items.append({
            "id": item["id"], "category": item["category"], "name": item["name"],
            "rarity": item["rarity"], "description": item["description"],
            "item": (folder / "item.json").as_posix(),
            "texture": (folder / item["texture"]).as_posix(),
            "preview": (folder / "preview.png").as_posix(),
            "shape": item["shape"], "palette": item["palette"],
            "local_render_support": item["category"] == "hat",
        })
    if category_counts != {category: 50 for category in CATEGORY_ORDER}:
        raise SystemExit(f"Unexpected category counts: {category_counts}")
    catalog = {
        "schema": base.SCHEMA_VERSION,
        "status": "full_catalog_unverified_in_game",
        "item_count": len(records),
        "category_counts": category_counts,
        "items": catalog_items,
    }
    (ASSET_ROOT / "catalog.json").write_text(json.dumps(catalog, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    file_hashes = {}
    for path in sorted(path for path in ASSET_ROOT.rglob("*") if path.is_file() and path.name != "manifest.json"):
        file_hashes[path.relative_to(ASSET_ROOT).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    manifest = {"schema": base.SCHEMA_VERSION, "item_count": len(records), "sha256": file_hashes}
    (ASSET_ROOT / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    # Preserve the original 30-item model/animation illustration as a vertical-slice reference;
    # the 300-entry roster and model-preview sheet are generated separately above.
    base.write_turnaround_sheet(records[:30])
    base.write_animation_strip(records[:30])
    roster_path = write_roster(records)
    sheet_path = draw_full_model_preview_sheet(records)
    print(f"Generated {len(records)} items with category counts {category_counts}")
    print(f"Catalog: {ASSET_ROOT / 'catalog.json'}")
    print(f"Manifest: {ASSET_ROOT / 'manifest.json'}")
    print(f"Roster: {roster_path}")
    print(f"Model-preview sheet: {sheet_path}")


if __name__ == "__main__":
    generate()
