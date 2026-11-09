#!/usr/bin/env python3
"""Deterministically build the first 5 × 6 Trinkies asset slice and its preview sheets.

All art here is original procedural pixel art and cube geometry. No game, launcher, or
third-party textures are sampled. The JSON/PNG outputs are committed; this script is the
reproducible source of truth for the generated vertical-slice assets.
"""
from __future__ import annotations

import base64
import colorsys
import hashlib
import json
import math
from pathlib import Path
from typing import Any

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
ASSET_ROOT = ROOT / "trinkies_assets" / "trinkies"
ITEM_ROOT = ASSET_ROOT / "items"
DOCS_ROOT = ROOT / "docs" / "wardrobe"
SCHEMA_VERSION = 1
PREVIEW_SIZE = 128

# The current wardrobe uses four named tiers plus exactly five one-off Eternal items.
# The legacy source labels are normalized when item.json/catalog.json are written.
RARITY_REMAP = {
    "common": "common",
    "uncommon": "common",
    "rare": "epic",
    "epic": "legendary",
    "legendary": "mythic",
}
ETERNAL_ITEM_IDS = frozenset({
    "trinkies:hat_full_frost_circlet",
    "trinkies:suit_full_crimson_mechanist",
    "trinkies:back_full_aurora_drone",
    "trinkies:pet_full_moss_crab",
    "trinkies:aura_full_aurora_spectrum",
})
RARITY_ACCENT_RGB = {
    "common": (116, 232, 132),
    "epic": (206, 151, 255),
    "legendary": (255, 221, 112),
    "mythic": (255, 112, 112),
    "eternal": (225, 233, 240),
}

# Slug, display name, description, rarity, tags, shape, base colour, accent,
# material recipe, attachment bone, animation recipe, emissive mask, suit mode.
ROWS = [
    ("hat", "copper_aviator", "Copper Aviator", "A riveted sky-pilot cap with twin teal lenses and a low wind brim.", "uncommon", ["flight", "metal", "goggles"], "aviator_cap", "#A76036", "#7FE6D2", "metal", "head", "hat_wind", True, None),
    ("hat", "tideglass_circlet", "Tideglass Circlet", "A sea-glass circlet with a faceted center jewel and side fins.", "rare", ["water", "glass", "jewel"], "tide_circlet", "#287B83", "#D0FFF0", "glass", "head", "circlet_glint", True, None),
    ("hat", "mosscap_hood", "Mosscap Hood", "A soft moss-green hood crowned with a stitched mushroom silhouette.", "common", ["moss", "cloth", "forest"], "mushroom_hood", "#637449", "#E7B6C9", "wool", "head", "hood_bob", False, None),
    ("hat", "orbit_lantern_halo", "Orbit Lantern Halo", "A brass halo carrying two tiny lanterns on offset arms.", "epic", ["light", "brass", "orbit"], "lantern_halo", "#89602F", "#FFD982", "metal", "head", "lantern_sway", True, None),
    ("hat", "basalt_horn_circlet", "Basalt Horn Circlet", "A dark basalt brow guard with asymmetric amethyst horns.", "rare", ["basalt", "crystal", "horns"], "basalt_helm", "#464D5B", "#C08BFF", "stone", "head", "horn_shiver", True, None),

    ("suit", "copper_diver", "Copper Diver Suit", "A pressure suit with a ceramic chest plate, paired air canisters, and weighted boots.", "rare", ["deep-sea", "copper", "pressure"], "diver_suit", "#187D7B", "#E7A75F", "metal", "body", "suit_breathe", True, "overlay"),
    ("suit", "willow_wayfinder", "Willow Wayfinder", "A layered trail coat with leaf clasps, a map case, and reinforced cuffs.", "uncommon", ["forest", "cloth", "travel"], "willow_suit", "#49643D", "#E4B86F", "cloth", "body", "suit_breathe", False, "overlay"),
    ("suit", "prism_cartographer", "Prism Cartographer", "A geometer's suit with a faceted compass, bright shoulder tabs, and glass vials.", "epic", ["prism", "maps", "glass"], "prism_suit", "#4D4E91", "#F5CC69", "enamel", "body", "suit_breathe", True, "overlay"),
    ("suit", "basalt_stargazer", "Basalt Stargazer", "A charcoal observatory robe with a raised collar and stitched constellation plates.", "rare", ["night", "stone", "stars"], "stargazer_suit", "#49364F", "#A9A8EC", "wool", "body", "suit_breathe", True, "overlay"),
    ("suit", "tidal_mechanist", "Tidal Mechanist", "A blue workshop uniform with a tool harness, brass fasteners, and fin-shaped guards.", "uncommon", ["ocean", "tools", "leather"], "mechanist_suit", "#315F85", "#E6A55D", "leather", "body", "suit_breathe", False, "overlay"),

    ("back", "cicada_glasswings", "Cicada Glasswings", "Four translucent geometric wings held by a small jade-and-copper harness.", "epic", ["wings", "insect", "glass"], "cicada_wings", "#307B68", "#C8FFE9", "glass", "body", "wing_flutter", True, None),
    ("back", "windup_satchel", "Windup Satchel", "A clockwork courier pack with a wooden frame, brass crank, and canvas pockets.", "uncommon", ["travel", "clockwork", "leather"], "windup_pack", "#865337", "#E4BB63", "leather", "body", "pack_tick", False, None),
    ("back", "ribbon_kite_banner", "Ribbon Kite Banner", "A compact banner pack trailing two cut-paper ribbons and a tiny kite tail.", "common", ["wind", "paper", "ribbon"], "kite_banner", "#C56E58", "#F4DDB0", "cloth", "body", "ribbon_sway", False, None),
    ("back", "emberleaf_glider", "Emberleaf Glider", "Paired autumn leaves form a small articulated glider around a bark spine.", "rare", ["leaves", "glider", "wood"], "emberleaf_glider", "#557548", "#F6A34E", "wood", "body", "wing_flutter", True, None),
    ("back", "tidal_bell_drone", "Tidal Bell Drone", "A floating brass bell drone with three stabilizer fins and a blue glass lens.", "epic", ["drone", "bell", "ocean"], "bell_drone", "#3A6680", "#A0ECF5", "metal", "body", "drone_orbit", True, None),

    ("pet", "amethyst_wisp", "Amethyst Wisp", "A shy hovering crystal sprite with a warm core and two orbiting shards.", "rare", ["crystal", "floater", "glow"], "wisp_pet", "#614799", "#E6D7FF", "gem", "root", "pet_float", True, None),
    ("pet", "sootbubble", "Sootbubble", "A round soot-coloured puff with a pale belly, pebble feet, and a curl tail.", "common", ["blob", "soft", "stone"], "soot_pet", "#434A55", "#C5D6D8", "wool", "root", "pet_bounce", False, None),
    ("pet", "moss_lantern_moth", "Moss Lantern Moth", "A tiny moss-winged moth with a lantern abdomen and curled feelers.", "epic", ["moth", "moss", "light"], "moth_pet", "#536E48", "#FFD47B", "fur", "root", "pet_flutter", True, None),
    ("pet", "paper_comet_sprite", "Paper Comet Sprite", "A folded-paper sky sprite with a long striped tail and teal fins.", "uncommon", ["paper", "sky", "comet"], "comet_pet", "#D9C79C", "#4B9CB0", "paper", "root", "pet_glide", False, None),
    ("pet", "clockwork_pebble_crab", "Clockwork Pebble Crab", "A pocket-sized pebble crab with copper feet, a shell dial, and bead eyes.", "rare", ["crab", "clockwork", "pebble"], "pebble_crab", "#70523F", "#F3A963", "metal", "root", "pet_scuttle", True, None),

    ("emote", "origami_greeting", "Origami Greeting", "A quick greeting that unfolds a small paper pennant and tucks it away.", "common", ["greeting", "paper", "one-shot"], "origami_prop", "#D9CFAF", "#5E9D8C", "paper", "right_arm", "emote_wave", False, None),
    ("emote", "spark_wand_cheer", "Spark Wand Cheer", "A two-beat cheer with a star wand flourish and a grounded finish.", "uncommon", ["cheer", "spark", "one-shot"], "spark_wand", "#50457F", "#F3D45B", "gem", "right_arm", "emote_cheer", True, None),
    ("emote", "marigold_curtain_call", "Marigold Curtain Call", "A gentle bow with a hand-tied marigold bouquet held close to the chest.", "rare", ["bow", "flower", "one-shot"], "marigold_bouquet", "#9B4D65", "#FFD36B", "cloth", "right_arm", "emote_bow", False, None),
    ("emote", "pocket_drumline", "Pocket Drumline", "A short seated rhythm with a small lacquered travel drum prop.", "uncommon", ["rhythm", "drum", "loop"], "pocket_drum", "#566373", "#D9975C", "wood", "body", "emote_drum", False, None),
    ("emote", "moonlit_ribbon_bow", "Moonlit Ribbon Bow", "A formal bow traced by two pale ribbons that settle at the shoulders.", "epic", ["moon", "ribbon", "one-shot"], "moon_ribbons", "#38476F", "#BDD8FF", "cloth", "body", "emote_bow", True, None),

    ("aura", "monsoon_motes", "Monsoon Motes", "A slow orbit of cool droplets with sparse, low-brightness glints.", "common", ["water", "orbit", "particles"], "monsoon_ring", "#3D7EA1", "#B9ECF7", "glass", "root", "aura_orbit", True, None),
    ("aura", "aurora_chime", "Aurora Chime", "Three quiet arcs drift behind the shoulders like a windless aurora.", "rare", ["aurora", "arcs", "particles"], "aurora_arcs", "#425B91", "#8CF2D2", "glass", "root", "aura_sweep", True, None),
    ("aura", "ember_marigold", "Ember Marigold", "Warm petal-shaped pixels rise in a sparse loop and fade before the head.", "uncommon", ["ember", "petals", "particles"], "marigold_aura", "#A44F37", "#FFD27A", "enamel", "root", "aura_rise", True, None),
    ("aura", "glass_rune_ring", "Glass Rune Ring", "A restrained ring of four lilac runes turns slowly around the waist.", "epic", ["runes", "glass", "particles"], "rune_ring", "#5A4785", "#D2B9FF", "crystal", "root", "aura_orbit", True, None),
    ("aura", "star_silt_trail", "Star Silt Trail", "A faint trail of angular star-dust motes follows the player's last steps.", "rare", ["stars", "trail", "particles"], "star_silt", "#63708D", "#F1DEA2", "dust", "root", "aura_trail", True, None),
]

RIG_PIVOTS = {
    "root": (0.0, 0.0, 0.0),
    "head": (0.0, 12.0, 0.0),
    "body": (0.0, 2.0, 0.0),
    "left_arm": (5.0, 6.0, 0.0),
    "right_arm": (-5.0, 6.0, 0.0),
    "left_leg": (1.9, -4.0, -0.1),
    "right_leg": (-1.9, -4.0, -0.1),
    "left_wing": (4.0, 5.0, -2.0),
    "right_wing": (-4.0, 5.0, -2.0),
    "tail": (0.0, -1.0, -4.0),
    "pet_root": (0.0, 0.0, 0.0),
    "pet_left_wing": (3.0, 2.0, 0.0),
    "pet_right_wing": (-3.0, 2.0, 0.0),
    "pet_tail": (0.0, -1.0, -3.0),
    "prop": (0.0, 0.0, 0.0),
    "aura_orbit": (0.0, 2.0, 0.0),
}


def box(name: str, start: tuple[float, float, float], end: tuple[float, float, float], bone: str, rotation=(0, 0, 0)) -> dict[str, Any]:
    return {"name": name, "from": list(start), "to": list(end), "bone": bone, "rotation": list(rotation)}


def shape_parts(shape: str) -> list[dict[str, Any]]:
    """Small, hand-authored cube silhouettes. Coordinates are bone-local model pixels."""
    templates: dict[str, list[dict[str, Any]]] = {
        "aviator_cap": [
            box("crown", (-4, 1, -4), (4, 5, 4), "head"), box("visor", (-4, 0, -5), (4, 1, 2), "head"),
            box("left_goggle", (-4, -1, 3), (-1, 1, 4), "head"), box("right_goggle", (1, -1, 3), (4, 1, 4), "head"),
            box("strap", (-4, -2, -3), (4, -1, -2), "head"), box("ear_flap_left", (-5, -2, -1), (-4, 0, 2), "head"),
        ],
        "tide_circlet": [
            box("front_band", (-4, 0, 3), (4, 2, 4), "head"), box("side_band_left", (-4, 0, -3), (-3, 2, 3), "head"),
            box("side_band_right", (3, 0, -3), (4, 2, 3), "head"), box("center_gem", (-1, 2, 3), (1, 4, 4), "head"),
            box("fin_left", (-5, 1, -2), (-4, 4, 1), "head"), box("fin_right", (4, 1, -2), (5, 4, 1), "head"),
        ],
        "mushroom_hood": [
            box("hood_shell", (-4, 0, -4), (4, 5, 4), "head"), box("mushroom_cap", (-5, 4, -4), (5, 6, 4), "head"),
            box("cap_ridge", (-2, 6, -3), (2, 7, 3), "head"), box("front_fold", (-4, -1, 3), (4, 0, 4), "head"),
            box("chin_tie", (-1, -5, 2), (1, -3, 3), "head"),
        ],
        "lantern_halo": [
            box("halo_front", (-4, 6, 2), (4, 7, 3), "head"), box("halo_left", (-4, 6, -3), (-3, 7, 2), "head"),
            box("halo_right", (3, 6, -3), (4, 7, 2), "head"), box("halo_back", (-3, 6, -3), (3, 7, -2), "head"),
            box("arm_left", (-5, 4, -1), (-4, 6, 0), "head"), box("arm_right", (4, 4, -1), (5, 6, 0), "head"),
            box("lantern_left", (-6, 1, -1), (-4, 4, 1), "head"), box("lantern_right", (4, 1, -1), (6, 4, 1), "head"),
        ],
        "basalt_helm": [
            box("brow", (-4, 1, 3), (4, 3, 4), "head"), box("crown_plate", (-4, 3, -4), (4, 5, 4), "head"),
            box("left_cheek", (-4, -2, 1), (-3, 1, 4), "head"), box("right_cheek", (3, -2, 1), (4, 1, 4), "head"),
            box("horn_left_base", (-5, 3, -1), (-3, 5, 2), "head"), box("horn_left_tip", (-7, 4, -1), (-5, 7, 1), "head", (0, 0, -18)),
            box("horn_right_base", (3, 3, -1), (5, 5, 2), "head"), box("horn_right_tip", (5, 4, -1), (7, 7, 1), "head", (0, 0, 18)),
        ],
        "diver_suit": [
            box("torso_shell", (-4.5, -7, -3), (4.5, 5, 3), "body"), box("chest_plate", (-3, -3, 3), (3, 2, 4), "body"),
            box("collar", (-3, 4, -2), (3, 6, 3), "body"), box("air_tank_left", (-6, -3, -4), (-4, 4, -2), "body"),
            box("air_tank_right", (4, -3, -4), (6, 4, -2), "body"), box("left_shoulder", (-7, 3, -2), (-4, 6, 2), "left_arm"),
            box("right_shoulder", (4, 3, -2), (7, 6, 2), "right_arm"), box("left_cuff", (-2, -5, -2), (2, -3, 2), "left_arm"),
            box("right_cuff", (-2, -5, -2), (2, -3, 2), "right_arm"), box("left_boot", (-2.2, -6, -2.5), (2.2, -3, 2.5), "left_leg"),
            box("right_boot", (-2.2, -6, -2.5), (2.2, -3, 2.5), "right_leg"), box("valve", (-1, -1, 4), (1, 1, 5), "body"),
        ],
        "willow_suit": [
            box("coat_body", (-4.5, -8, -3), (4.5, 5, 3), "body"), box("long_front_panel", (-2, -8, 3), (2, 3, 4), "body"),
            box("leaf_collar", (-4, 3, 2), (4, 5, 4), "body"), box("map_case", (-6, 0, -4), (-3, 4, -2), "body"),
            box("left_shoulder", (-7, 3, -2), (-4, 6, 2), "left_arm"), box("right_shoulder", (4, 3, -2), (7, 6, 2), "right_arm"),
            box("left_cuff", (-2.5, -4, -2), (2.5, -2, 2), "left_arm"), box("right_cuff", (-2.5, -4, -2), (2.5, -2, 2), "right_arm"),
            box("left_boot", (-2, -6, -2), (2, -3, 2), "left_leg"), box("right_boot", (-2, -6, -2), (2, -3, 2), "right_leg"),
            box("leaf_pin", (-1, 2, 4), (1, 4, 5), "body"), box("belt", (-4, -5, 2), (4, -4, 3), "body"),
        ],
        "prism_suit": [
            box("torso", (-4.5, -7, -3), (4.5, 5, 3), "body"), box("compass_plate", (-2, -1, 3), (2, 3, 4), "body"),
            box("collar_left", (-4, 4, 2), (-1, 6, 4), "body"), box("collar_right", (1, 4, 2), (4, 6, 4), "body"),
            box("left_shoulder", (-7, 3, -2), (-4, 6, 2), "left_arm"), box("right_shoulder", (4, 3, -2), (7, 6, 2), "right_arm"),
            box("left_vial", (-3, 0, -3), (-1, 4, -2), "left_arm"), box("right_vial", (1, 0, -3), (3, 4, -2), "right_arm"),
            box("left_guard", (-2.2, -6, -2.5), (2.2, -1, 2.5), "left_leg"), box("right_guard", (-2.2, -6, -2.5), (2.2, -1, 2.5), "right_leg"),
            box("belt", (-4, -5, 2), (4, -4, 3), "body"), box("star_badge", (-1, 1, 4), (1, 2, 5), "body"),
        ],
        "stargazer_suit": [
            box("robe_body", (-4.5, -8, -3), (4.5, 5, 3), "body"), box("high_collar", (-4, 4, 2), (4, 7, 4), "body"),
            box("left_sash", (-4, -6, 3), (-2, 4, 4), "body"), box("right_sash", (2, -6, 3), (4, 4, 4), "body"),
            box("left_shoulder", (-7, 3, -2), (-4, 6, 2), "left_arm"), box("right_shoulder", (4, 3, -2), (7, 6, 2), "right_arm"),
            box("left_cuff", (-3, -5, -2), (2, -3, 2), "left_arm"), box("right_cuff", (-2, -5, -2), (3, -3, 2), "right_arm"),
            box("left_boot", (-2, -6, -2), (2, -3, 2), "left_leg"), box("right_boot", (-2, -6, -2), (2, -3, 2), "right_leg"),
            box("star_plate_one", (-3, 1, 4), (-1, 2, 5), "body"), box("star_plate_two", (1, -2, 4), (3, -1, 5), "body"),
        ],
        "mechanist_suit": [
            box("jacket", (-4.5, -7, -3), (4.5, 5, 3), "body"), box("tool_harness", (-5, 1, 3), (5, 3, 4), "body"),
            box("wrench_case", (-6, -3, -4), (-4, 1, -2), "body"), box("fin_collar", (-4, 4, 2), (4, 6, 4), "body"),
            box("left_shoulder", (-7, 3, -2), (-4, 6, 2), "left_arm"), box("right_shoulder", (4, 3, -2), (7, 6, 2), "right_arm"),
            box("left_glove", (-3, -6, -2), (2, -3, 2), "left_arm"), box("right_glove", (-2, -6, -2), (3, -3, 2), "right_arm"),
            box("left_knee", (-2.3, -4, 2), (2.3, -1, 3), "left_leg"), box("right_knee", (-2.3, -4, 2), (2.3, -1, 3), "right_leg"),
            box("left_boot", (-2, -7, -2), (2, -4, 2), "left_leg"), box("right_boot", (-2, -7, -2), (2, -4, 2), "right_leg"),
        ],
        "cicada_wings": [
            box("harness", (-2, -3, -3), (2, 5, -1), "body"), box("left_wing_root", (-2, 2, -2), (0, 4, 0), "left_wing"),
            box("left_wing_upper", (-10, 2, -2), (-1, 7, -1), "left_wing", (0, 0, -12)),
            box("left_wing_lower", (-9, -3, -2), (-1, 2, -1), "left_wing", (0, 0, 14)),
            box("right_wing_root", (0, 2, -2), (2, 4, 0), "right_wing"),
            box("right_wing_upper", (1, 2, -2), (10, 7, -1), "right_wing", (0, 0, 12)),
            box("right_wing_lower", (1, -3, -2), (9, 2, -1), "right_wing", (0, 0, -14)),
            box("left_vein", (-7, 4, -0.5), (-2, 5, 0), "left_wing"), box("right_vein", (2, 4, -0.5), (7, 5, 0), "right_wing"),
            box("harness_buckle", (-1, 0, 0), (1, 2, 1), "body"), box("lower_strap", (-3, -4, -2), (3, -3, -1), "body"),
        ],
        "windup_pack": [
            box("pack_shell", (-4, -4, -5), (4, 4, -2), "body"), box("top_flap", (-4, 4, -5), (4, 6, -2), "body"),
            box("left_pocket", (-6, -3, -4), (-4, 0, -2), "body"), box("right_pocket", (4, -3, -4), (6, 0, -2), "body"),
            box("crank_axle", (-1, 1, -7), (1, 2, -5), "body"), box("crank_handle", (-4, 2, -7), (4, 3, -6), "body"),
            box("strap_left", (-5, 2, -2), (-4, 5, 0), "body"), box("strap_right", (4, 2, -2), (5, 5, 0), "body"),
            box("lower_buckle", (-1, -5, -3), (1, -3, -2), "body"), box("wood_frame", (-5, -5, -6), (5, 5, -5), "body"),
            box("gear_cap", (-2, 3, -8), (2, 5, -6), "body"), box("small_dial", (-1, 0, -8), (1, 2, -7), "body"),
        ],
        "kite_banner": [
            box("backplate", (-2, -3, -4), (2, 4, -2), "body"), box("mast", (-0.5, 3, -5), (0.5, 12, -4), "body"),
            box("kite_left", (-7, 7, -5), (0, 12, -4), "body", (0, 0, -10)), box("kite_right", (0, 7, -5), (7, 12, -4), "body", (0, 0, 10)),
            box("ribbon_left_upper", (-5, 4, -4), (-2, 7, -3), "tail"), box("ribbon_left_lower", (-7, 0, -4), (-4, 4, -3), "tail", (0, 0, 12)),
            box("ribbon_right_upper", (2, 4, -4), (5, 7, -3), "tail"), box("ribbon_right_lower", (4, 0, -4), (7, 4, -3), "tail", (0, 0, -12)),
            box("kite_knot", (-1, 6, -3), (1, 8, -2), "body"), box("mast_tip", (-1, 11, -5), (1, 13, -4), "body"),
            box("tail_weight", (-1, -5, -4), (1, -3, -3), "tail"),
        ],
        "emberleaf_glider": [
            box("spine", (-1, -4, -4), (1, 5, -2), "body"), box("left_leaf_base", (-3, 2, -3), (0, 5, -2), "left_wing"),
            box("left_leaf_tip", (-10, 4, -3), (-2, 8, -2), "left_wing", (0, 0, -16)),
            box("left_lower_leaf", (-8, -3, -3), (-1, 1, -2), "left_wing", (0, 0, 12)),
            box("right_leaf_base", (0, 2, -3), (3, 5, -2), "right_wing"),
            box("right_leaf_tip", (2, 4, -3), (10, 8, -2), "right_wing", (0, 0, 16)),
            box("right_lower_leaf", (1, -3, -3), (8, 1, -2), "right_wing", (0, 0, -12)),
            box("left_vein", (-6, 1, -1), (-5, 6, 0), "left_wing"), box("right_vein", (5, 1, -1), (6, 6, 0), "right_wing"),
            box("harness", (-3, -4, -2), (3, -2, 0), "body"), box("seed_pod", (-1, 0, 0), (1, 2, 1), "body"),
        ],
        "bell_drone": [
            box("bell_body", (-3, -2, -5), (3, 4, -1), "body"), box("bell_lip", (-4, -3, -5), (4, -2, -1), "body"),
            box("top_cap", (-2, 4, -4), (2, 5, -2), "body"), box("glass_lens", (-1, 0, 0), (1, 2, 1), "body"),
            box("fin_left", (-8, 0, -4), (-3, 2, -2), "left_wing"), box("fin_right", (3, 0, -4), (8, 2, -2), "right_wing"),
            box("fin_upper", (-1, 4, -4), (1, 8, -2), "aura_orbit"), box("fin_lower", (-1, -7, -4), (1, -3, -2), "aura_orbit"),
            box("ring_left", (-7, -1, -3), (-6, 3, -2), "aura_orbit"), box("ring_right", (6, -1, -3), (7, 3, -2), "aura_orbit"),
            box("bell_clapper", (-1, -5, -4), (1, -3, -2), "tail"), box("harness", (-2, 3, -1), (2, 4, 1), "body"),
        ],
        "wisp_pet": [
            box("core", (-4, -4, -4), (4, 4, 4), "pet_root"), box("top_shard", (-1, 4, -1), (1, 7, 1), "pet_root"),
            box("left_shard", (-7, 0, -1), (-4, 2, 1), "pet_root"), box("right_shard", (4, -1, -1), (7, 1, 1), "pet_root"),
            box("eye_left", (-2, 0, 4), (-1, 1, 5), "pet_root"), box("eye_right", (1, 0, 4), (2, 1, 5), "pet_root"),
            box("lower_glow", (-2, -6, -2), (2, -4, 2), "pet_root"), box("orbit_left", (-8, 3, -2), (-6, 5, 0), "tail"),
            box("orbit_right", (6, -4, -1), (8, -2, 1), "tail"), box("side_glint_left", (-5, 2, 1), (-4, 3, 2), "pet_root"),
            box("side_glint_right", (4, -3, 1), (5, -2, 2), "pet_root"), box("base_shard", (-1, -8, -1), (1, -5, 1), "tail"),
        ],
        "soot_pet": [
            box("puff_body", (-5, -4, -4), (5, 4, 4), "pet_root"), box("belly", (-3, -3, 4), (3, 0, 5), "pet_root"),
            box("ear_left", (-5, 3, 1), (-3, 6, 3), "pet_root"), box("ear_right", (3, 3, 1), (5, 6, 3), "pet_root"),
            box("foot_left", (-4, -6, 1), (-2, -4, 3), "pet_root"), box("foot_right", (2, -6, 1), (4, -4, 3), "pet_root"),
            box("eye_left", (-3, 1, 4), (-2, 2, 5), "pet_root"), box("eye_right", (2, 1, 4), (3, 2, 5), "pet_root"),
            box("curl_tail_a", (-2, -2, -6), (0, 0, -4), "pet_tail"), box("curl_tail_b", (0, 0, -7), (2, 2, -5), "pet_tail"),
            box("cheek_left", (-5, 0, 3), (-4, 1, 4), "pet_root"), box("cheek_right", (4, 0, 3), (5, 1, 4), "pet_root"),
        ],
        "moth_pet": [
            box("abdomen", (-2, -4, -2), (2, 2, 3), "pet_root"), box("thorax", (-3, 1, 1), (3, 4, 4), "pet_root"),
            box("lantern", (-1, -5, 2), (1, -2, 4), "pet_root"), box("left_wing_upper", (-8, 2, -1), (-2, 7, 1), "pet_left_wing"),
            box("left_wing_lower", (-7, -2, -1), (-2, 2, 1), "pet_left_wing"), box("right_wing_upper", (2, 2, -1), (8, 7, 1), "pet_right_wing"),
            box("right_wing_lower", (2, -2, -1), (7, 2, 1), "pet_right_wing"), box("antenna_left", (-3, 4, 3), (-2, 8, 4), "pet_root"),
            box("antenna_right", (2, 4, 3), (3, 8, 4), "pet_root"), box("eye_left", (-2, 2, 4), (-1, 3, 5), "pet_root"),
            box("eye_right", (1, 2, 4), (2, 3, 5), "pet_root"), box("tail", (-1, -7, -1), (1, -4, 1), "pet_tail"),
        ],
        "comet_pet": [
            box("paper_head", (-3, 1, -3), (3, 7, 3), "pet_root"), box("paper_body", (-3, -4, -2), (3, 2, 2), "pet_root"),
            box("nose", (-1, 0, 3), (1, 2, 5), "pet_root"), box("fin_left", (-6, 1, -1), (-3, 4, 1), "pet_left_wing"),
            box("fin_right", (3, 1, -1), (6, 4, 1), "pet_right_wing"), box("comet_tail_a", (-2, -5, -2), (2, -3, 0), "pet_tail"),
            box("comet_tail_b", (-3, -8, -3), (3, -5, -1), "pet_tail"), box("comet_tail_c", (-2, -11, -2), (2, -8, 0), "pet_tail"),
            box("eye_left", (-2, 3, 3), (-1, 4, 4), "pet_root"), box("eye_right", (1, 3, 3), (2, 4, 4), "pet_root"),
            box("fold_top", (-1, 7, -2), (1, 9, 0), "pet_root"), box("fold_side", (-4, 0, -2), (-3, 2, 0), "pet_root"),
        ],
        "pebble_crab": [
            box("shell", (-5, -1, -4), (5, 4, 4), "pet_root"), box("shell_dial", (-2, 2, 4), (2, 4, 5), "pet_root"),
            box("left_claw", (-8, -1, 2), (-5, 2, 5), "pet_left_wing"), box("right_claw", (5, -1, 2), (8, 2, 5), "pet_right_wing"),
            box("leg_left_front", (-7, -3, 2), (-5, -1, 3), "pet_left_wing"), box("leg_left_back", (-7, -3, -3), (-5, -1, -2), "pet_left_wing"),
            box("leg_right_front", (5, -3, 2), (7, -1, 3), "pet_right_wing"), box("leg_right_back", (5, -3, -3), (7, -1, -2), "pet_right_wing"),
            box("eye_left", (-3, 3, 4), (-2, 5, 5), "pet_root"), box("eye_right", (2, 3, 4), (3, 5, 5), "pet_root"),
            box("antenna_left", (-4, 4, 3), (-3, 7, 4), "pet_root"), box("antenna_right", (3, 4, 3), (4, 7, 4), "pet_root"),
        ],
        "origami_prop": [
            box("folded_flag", (-4, -1, -1), (4, 3, 1), "prop"), box("flag_fold_left", (-4, 0, 1), (-1, 2, 2), "prop"),
            box("flag_fold_right", (1, 0, 1), (4, 2, 2), "prop"), box("handle", (-0.5, -7, -0.5), (0.5, -1, 0.5), "prop"),
            box("ribbon", (-2, 3, -1), (2, 4, 1), "prop"),
        ],
        "spark_wand": [
            box("wand_stem", (-0.5, -8, -0.5), (0.5, 2, 0.5), "prop"), box("star_core", (-2, 3, -1), (2, 7, 1), "prop"),
            box("star_tip_top", (-1, 7, -1), (1, 9, 1), "prop"), box("star_tip_left", (-4, 4, -1), (-2, 6, 1), "prop"),
            box("star_tip_right", (2, 4, -1), (4, 6, 1), "prop"), box("grip", (-1, -7, -1), (1, -5, 1), "prop"),
        ],
        "marigold_bouquet": [
            box("stems", (-1, -7, -1), (1, 2, 1), "prop"), box("flower_center", (-2, 2, 1), (2, 5, 2), "prop"),
            box("petal_left", (-5, 2, 0), (-2, 5, 1), "prop"), box("petal_right", (2, 2, 0), (5, 5, 1), "prop"),
            box("petal_top", (-2, 5, 0), (2, 7, 1), "prop"), box("leaf_left", (-4, -2, 0), (-1, 0, 1), "prop"),
            box("leaf_right", (1, -4, 0), (4, -2, 1), "prop"),
        ],
        "pocket_drum": [
            box("drum_shell", (-4, -3, -3), (4, 4, 3), "prop"), box("drum_head", (-3, 4, -2), (3, 5, 2), "prop"),
            box("left_rim", (-5, 2, -2), (-4, 4, 2), "prop"), box("right_rim", (4, 2, -2), (5, 4, 2), "prop"),
            box("strap", (-1, -5, -3), (1, -3, -2), "prop"), box("stick_left", (-6, 5, -1), (-5, 10, 0), "prop"),
            box("stick_right", (5, 5, 0), (6, 10, 1), "prop"),
        ],
        "moon_ribbons": [
            box("left_ribbon_upper", (-7, 3, -1), (-2, 6, 1), "prop"), box("left_ribbon_lower", (-9, -1, -1), (-5, 3, 1), "tail"),
            box("right_ribbon_upper", (2, 3, -1), (7, 6, 1), "prop"), box("right_ribbon_lower", (5, -1, -1), (9, 3, 1), "tail"),
            box("center_knot", (-2, 1, 1), (2, 3, 2), "prop"), box("moon_charm", (-1, -2, 1), (1, 0, 2), "prop"),
        ],
        "monsoon_ring": [
            box("drop_north", (-1, 4, -1), (1, 7, 1), "aura_orbit"), box("drop_east", (5, 1, -1), (7, 3, 1), "aura_orbit"),
            box("drop_south", (-1, -4, -1), (1, -2, 1), "aura_orbit"), box("drop_west", (-7, 1, -1), (-5, 3, 1), "aura_orbit"),
            box("ring_front_left", (-5, -1, 2), (-2, 0, 3), "aura_orbit"), box("ring_front_right", (2, -1, 2), (5, 0, 3), "aura_orbit"),
        ],
        "aurora_arcs": [
            box("arc_left_upper", (-8, 2, -2), (-6, 8, -1), "aura_orbit"), box("arc_left_lower", (-6, -3, -2), (-4, 2, -1), "aura_orbit"),
            box("arc_center", (-1, 5, -2), (1, 9, -1), "aura_orbit"), box("arc_right_upper", (6, 2, -2), (8, 8, -1), "aura_orbit"),
            box("arc_right_lower", (4, -3, -2), (6, 2, -1), "aura_orbit"), box("chime_left", (-8, -4, -1), (-6, -2, 1), "aura_orbit"),
            box("chime_right", (6, -4, -1), (8, -2, 1), "aura_orbit"),
        ],
        "marigold_aura": [
            box("petal_north", (-2, 6, -1), (2, 8, 1), "aura_orbit"), box("petal_nw", (-6, 4, -1), (-3, 6, 1), "aura_orbit"),
            box("petal_ne", (3, 4, -1), (6, 6, 1), "aura_orbit"), box("petal_west", (-8, 0, -1), (-6, 2, 1), "aura_orbit"),
            box("petal_east", (6, 0, -1), (8, 2, 1), "aura_orbit"), box("petal_southwest", (-6, -4, -1), (-3, -2, 1), "aura_orbit"),
            box("petal_southeast", (3, -4, -1), (6, -2, 1), "aura_orbit"), box("petal_south", (-2, -6, -1), (2, -4, 1), "aura_orbit"),
        ],
        "rune_ring": [
            box("rune_north", (-2, 6, -1), (2, 8, 1), "aura_orbit"), box("rune_east", (6, -1, -1), (8, 3, 1), "aura_orbit"),
            box("rune_south", (-2, -8, -1), (2, -6, 1), "aura_orbit"), box("rune_west", (-8, -1, -1), (-6, 3, 1), "aura_orbit"),
            box("rune_nw", (-6, 4, -1), (-4, 6, 1), "aura_orbit"), box("rune_ne", (4, 4, -1), (6, 6, 1), "aura_orbit"),
            box("rune_sw", (-6, -5, -1), (-4, -3, 1), "aura_orbit"), box("rune_se", (4, -5, -1), (6, -3, 1), "aura_orbit"),
        ],
        "star_silt": [
            box("star_left", (-8, 1, -1), (-5, 4, 1), "aura_orbit"), box("star_right", (5, -2, -1), (8, 1, 1), "aura_orbit"),
            box("star_front", (-1, 5, 2), (2, 8, 3), "aura_orbit"), box("star_back", (-2, -5, -4), (1, -2, -3), "aura_orbit"),
            box("silt_high", (-4, 7, -2), (-2, 9, 0), "aura_orbit"), box("silt_low", (2, -8, 1), (4, -6, 3), "aura_orbit"),
        ],
    }
    try:
        return templates[shape]
    except KeyError as exc:
        raise ValueError(f"unknown original shape: {shape}") from exc


def hex_rgb(value: str) -> tuple[int, int, int]:
    return tuple(int(value[i:i + 2], 16) for i in (1, 3, 5))  # type: ignore[return-value]


def rgb_hex(value: tuple[int, int, int]) -> str:
    return "#%02X%02X%02X" % value


def make_palette(base_hex: str, accent_hex: str) -> list[tuple[int, int, int]]:
    red, green, blue = hex_rgb(base_hex)
    hue, saturation, value = colorsys.rgb_to_hsv(red / 255, green / 255, blue / 255)
    ramp = [
        (hue - 0.035, min(1, saturation * 0.94), value * 0.36),
        (hue - 0.018, min(1, saturation * 0.90), value * 0.53),
        (hue, saturation, value),
        (hue + 0.018, saturation * 0.82, min(1, value * 1.17)),
        (hue + 0.060, saturation * 0.60, min(1, value * 1.37)),
    ]
    colors = [tuple(round(c * 255) for c in colorsys.hsv_to_rgb(h % 1, s, v)) for h, s, v in ramp]
    accent = hex_rgb(accent_hex)
    colors += [accent]
    ah, ass, av = colorsys.rgb_to_hsv(*(channel / 255 for channel in accent))
    colors.append(tuple(round(c * 255) for c in colorsys.hsv_to_rgb((ah + 0.035) % 1, ass * 0.68, min(1, av * 1.2))))
    # Preserve the curated shadows/highlights but ensure each JSON palette has 5–8 distinct colours.
    unique: list[tuple[int, int, int]] = []
    for color in colors:
        if color not in unique:
            unique.append(color)
    if len(unique) < 5:
        unique.extend([(min(255, c[0] + 11), min(255, c[1] + 7), min(255, c[2] + 5)) for c in unique[:5 - len(unique)]])
    return unique[:8]


def stable_seed(text: str) -> int:
    return int.from_bytes(hashlib.sha256(text.encode("utf-8")).digest()[:4], "big")


def make_texture(size: int, palette: list[tuple[int, int, int]], material: str, seed: int) -> Image.Image:
    tile = 16 if size >= 64 else 8
    cells = size // tile
    rng = np.random.default_rng(seed)
    pixels = np.zeros((size, size, 4), dtype=np.uint8)
    face_light = (4, 2, 3, 1, 0, 2)  # top, east, north, west, bottom, south
    material_id = {"wool": 0, "metal": 1, "glass": 2, "gem": 3, "leather": 4, "cloth": 5,
                   "wood": 6, "paper": 7, "stone": 8, "enamel": 9, "fur": 10, "crystal": 11, "dust": 12}.get(material, 0)
    for cell_y in range(cells):
        for cell_x in range(cells):
            cell_index = cell_y * cells + cell_x
            light = face_light[cell_index % len(face_light)]
            yy, xx = np.mgrid[0:tile, 0:tile]
            # Each material uses a low-frequency directional recipe, not unstructured speckle.
            wave = np.sin((xx + cell_index * 1.7) / (2.8 + material_id % 4))
            if material in ("metal", "enamel"):
                structure = ((xx + cell_index) % 7 == 1).astype(np.int16) * 2 - ((xx + cell_index) % 11 == 0).astype(np.int16)
            elif material in ("glass", "gem", "crystal"):
                structure = (((xx + yy + cell_index) % 9 == 0).astype(np.int16) * 2) - ((xx - yy + cell_index) % 13 == 0).astype(np.int16)
            elif material in ("cloth", "paper", "wool", "fur"):
                structure = ((xx % 4 == 0).astype(np.int16) + (yy % 5 == 0).astype(np.int16)) - 1
            elif material == "wood":
                structure = ((xx % 6 == 0).astype(np.int16) * 2) - ((xx % 13 == 0).astype(np.int16))
            elif material == "leather":
                structure = ((yy % 6 == 0).astype(np.int16) * 2) - ((xx % 13 == 1).astype(np.int16))
            else:
                structure = ((xx + 2 * yy + cell_index) % 11 == 0).astype(np.int16)
            controlled_noise = rng.normal(0, 0.12, (tile, tile))
            level = np.clip(light + np.rint(wave * 0.26 + structure * 0.32 + controlled_noise).astype(np.int16), 0, len(palette) - 1)
            accent_band = ((xx + yy + cell_index) % max(7, tile // 2) == 0)
            level[accent_band] = len(palette) - 1
            # Baked edge AO, with a warmer top-left edge highlight and darker lower seam.
            level[0, :] = min(len(palette) - 1, light + 1)
            level[-1, :] = max(0, light - 2)
            level[:, 0] = np.maximum(0, level[:, 0] - 1)
            level[:, -1] = np.maximum(0, level[:, -1] - 1)
            tile_pixels = np.asarray(palette, dtype=np.uint8)[level]
            pixels[cell_y * tile:(cell_y + 1) * tile, cell_x * tile:(cell_x + 1) * tile, :3] = tile_pixels
            pixels[cell_y * tile:(cell_y + 1) * tile, cell_x * tile:(cell_x + 1) * tile, 3] = 255
    return Image.fromarray(pixels, "RGBA")


def make_emissive_mask(size: int, seed: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    tile = 16 if size >= 64 else 8
    cells = size // tile
    # A few original, symmetrical light motifs; deterministic placement per item.
    for index in range(cells * cells):
        if (index + seed) % 7 in (0, 3):
            x = (index % cells) * tile
            y = (index // cells) * tile
            draw.rectangle((x + tile // 2 - 1, y + 2, x + tile // 2 + 1, y + tile - 3), fill=255)
            draw.point((x + 2, y + 2), fill=190)
    return mask


def cube_uuid(item_id: str, name: str, index: int) -> str:
    raw = hashlib.sha256(f"{item_id}|{name}|{index}".encode()).hexdigest()[:32]
    return f"{raw[:8]}-{raw[8:12]}-{raw[12:16]}-{raw[16:20]}-{raw[20:]}"


def build_bbmodel(item_id: str, name: str, size: int, texture_png: bytes, parts: list[dict[str, Any]]) -> dict[str, Any]:
    tile = 16 if size >= 64 else 8
    cells = size // tile
    faces_order = ["north", "east", "south", "west", "up", "down"]
    elements = []
    groups: dict[str, list[str]] = {
        bone: [] for bone in ("root", "head", "body", "left_arm", "right_arm", "left_leg", "right_leg")
    }
    face_no = 0
    for index, part in enumerate(parts):
        uuid = cube_uuid(item_id, part["name"], index)
        group = part["bone"]
        groups.setdefault(group, []).append(uuid)
        faces = {}
        for face in faces_order:
            cell = face_no % (cells * cells)
            u, v = cell % cells * tile, cell // cells * tile
            faces[face] = {"uv": [u, v, u + tile, v + tile], "texture": 0, "rotation": 0}
            face_no += 1
        elements.append({
            "name": part["name"], "from": part["from"], "to": part["to"],
            "autouv": 0, "color": index % 8, "origin": list(RIG_PIVOTS.get(group, (0.0, 0.0, 0.0))),
            "rotation": part["rotation"], "uuid": uuid, "faces": faces,
        })
    outliner = []
    for group, children in sorted(groups.items()):
        group_uuid = cube_uuid(item_id, f"group_{group}", len(outliner))
        outliner.append({
            "name": group, "origin": list(RIG_PIVOTS.get(group, (0.0, 0.0, 0.0))),
            "color": len(outliner) % 8, "uuid": group_uuid, "export": True,
            "locked": False, "visibility": True, "autouv": 0, "children": children,
        })
    source = "data:image/png;base64," + base64.b64encode(texture_png).decode("ascii")
    texture_uuid = cube_uuid(item_id, "texture", 0)
    return {
        "meta": {"format_version": "4.9", "model_format": "free", "box_uv": False},
        "name": name,
        "model_identifier": item_id,
        "visible_box": [1, 1, 1],
        "variable_placeholders": "",
        "resolution": {"width": size, "height": size},
        "elements": elements,
        "outliner": outliner,
        "textures": [{
            "uuid": texture_uuid, "id": "0", "name": "texture.png", "path": "texture.png",
            "folder": "", "namespace": "", "group": "", "width": size, "height": size,
            "uv_width": size, "uv_height": size, "particle": False, "use_as_default": True,
            "saved": True, "source": source,
        }],
    }


def clip(duration: float, loop: str, bones: dict[str, list[dict[str, Any]]], blend_in=0.18, blend_out=0.2) -> dict[str, Any]:
    return {"duration": duration, "loop_mode": loop, "blend_in": blend_in, "blend_out": blend_out, "bones": bones}


def key(time: float, position=(0, 0, 0), rotation=(0, 0, 0), scale=(1, 1, 1), easing="ease_in_out") -> dict[str, Any]:
    return {"time": time, "position": list(position), "rotation": list(rotation), "scale": list(scale), "easing": easing}


def build_animations(category: str, motion: str, bones: list[str]) -> dict[str, Any]:
    idle_duration = 2.0
    idle_keys = [key(0), key(0.5), key(1.0), key(1.5), key(idle_duration)]
    signature_duration = 2.4
    movement = {
        "hat_wind": ("head", (0, 0, 3)), "circlet_glint": ("gem", (0, 8, 0)),
        "hood_bob": ("head", (0, 0, 2)), "lantern_sway": ("head", (0, 0, 5)),
        "horn_shiver": ("head", (0, 0, 1.5)), "suit_breathe": ("body", (0, 0, 0.7)),
        "wing_flutter": ("left_wing", (0, 0, 0)), "pack_tick": ("body", (0, 0, 0.4)),
        "ribbon_sway": ("tail", (0, 0, 0)), "drone_orbit": ("aura_orbit", (0, 20, 0)),
        "pet_float": ("pet_root", (0, 0, 0)), "pet_bounce": ("pet_root", (0, 0, 0)),
        "pet_flutter": ("pet_left_wing", (0, 0, 0)), "pet_glide": ("pet_root", (0, 0, 0)),
        "pet_scuttle": ("pet_left_wing", (0, 0, 0)), "emote_wave": ("right_arm", (0, 0, 0)),
        "emote_cheer": ("right_arm", (0, 0, 0)), "emote_bow": ("body", (0, 0, 0)),
        "emote_drum": ("right_arm", (0, 0, 0)), "aura_orbit": ("aura_orbit", (0, 35, 0)),
        "aura_sweep": ("aura_orbit", (0, 0, 8)), "aura_rise": ("aura_orbit", (0, 0, 0)),
        "aura_trail": ("aura_orbit", (0, 12, 0)),
    }
    bone, amount = movement[motion]
    if bone not in bones:
        bone = "root" if "root" in bones else bones[0]
    if motion in ("wing_flutter", "pet_flutter"):
        idle_track = [key(0), key(0.5, rotation=(0, 0, -18)), key(1.0), key(1.5, rotation=(0, 0, 18)), key(2.0)]
    elif motion in ("ribbon_sway", "pet_scuttle"):
        idle_track = [key(0), key(0.5, rotation=(0, 0, -10)), key(1.0), key(1.5, rotation=(0, 0, 10)), key(2.0)]
    elif motion in ("pet_bounce", "pet_float"):
        height = 1.2 if motion == "pet_bounce" else 0.7
        idle_track = [key(0), key(0.5, position=(0, height, 0)), key(1.0), key(1.5, position=(0, height, 0)), key(2.0)]
    elif motion == "pet_glide":
        idle_track = [key(0), key(0.5, position=(0.4, 0, 0)), key(1.0), key(1.5, position=(-0.4, 0, 0)), key(2.0)]
    elif motion == "aura_rise":
        idle_track = [key(0), key(0.5, position=(0, 1, 0)), key(1.0, position=(0, 2, 0)), key(1.5, position=(0, 1, 0)), key(2.0)]
    elif motion in ("aura_orbit", "aura_trail", "drone_orbit"):
        idle_track = [key(0), key(0.5, rotation=(0, 90, 0)), key(1.0, rotation=(0, 180, 0)), key(1.5, rotation=(0, 270, 0)), key(2.0, rotation=(0, 360, 0))]
    else:
        idle_track = [key(0), key(0.5, rotation=amount), key(1.0), key(1.5, rotation=tuple(-v for v in amount)), key(2.0)]
    bones_map: dict[str, list[dict[str, Any]]] = {bone: idle_track}
    if category == "pet" and "pet_tail" in bones:
        bones_map["pet_tail"] = [key(0), key(0.5, rotation=(0, -10, 0)), key(1), key(1.5, rotation=(0, 10, 0)), key(2)]
    if category == "back" and "right_wing" in bones:
        bones_map["right_wing"] = [key(0), key(0.5, rotation=(0, 0, -12)), key(1), key(1.5, rotation=(0, 0, 12)), key(2)]
    if category == "aura":
        bones_map["aura_orbit"] = idle_track
    clips: dict[str, Any] = {"idle": clip(idle_duration, "loop", bones_map)}
    clips["equip"] = clip(0.55, "once", {
        "root": [key(0, scale=(0.88, 0.88, 0.88), easing="spring"), key(0.22, scale=(1.06, 1.06, 1.06), easing="cubic"), key(0.55)],
    }, blend_in=0.05, blend_out=0.18)
    if category == "pet":
        clips["signature"] = clip(2.8, "ping_pong", {
            "pet_root": [key(0), key(0.7, position=(0, 2, 0), rotation=(0, 0, -12), easing="spring"), key(1.4), key(2.1, position=(0, -1, 0), rotation=(0, 0, 12)), key(2.8)],
        }, blend_in=0.2, blend_out=0.3)
    elif category == "emote":
        play_duration = 3.0 if motion in ("emote_drum", "emote_wave") else 2.2
        play_loop = "loop" if motion == "emote_drum" else "once"
        if motion == "emote_bow":
            action_track = [key(0), key(0.55, rotation=(22, 0, 0), easing="cubic"), key(1.1, rotation=(22, 0, 0)), key(1.7, rotation=(0, 0, 0), easing="spring"), key(play_duration)]
        elif motion == "emote_wave":
            action_track = [key(0), key(0.45, rotation=(-45, 0, -35)), key(0.8, rotation=(-45, 0, 35)), key(1.15, rotation=(-45, 0, -35)), key(1.5), key(play_duration)]
        elif motion == "emote_drum":
            action_track = [key(0), key(0.35, rotation=(-35, 0, 0)), key(0.7), key(1.05, rotation=(-35, 0, 0)), key(1.4), key(2.1, rotation=(-35, 0, 0)), key(3.0)]
        else:
            action_track = [key(0), key(0.5, rotation=(-25, 0, 18)), key(1.0, rotation=(-25, 0, -18)), key(1.6, rotation=(0, 0, 0), easing="spring"), key(play_duration)]
        action_bone = "body" if motion in ("emote_bow", "emote_drum") or bone == "body" else "right_arm"
        clips["play"] = clip(play_duration, play_loop, {action_bone: action_track}, blend_in=0.12, blend_out=0.24)
    return {"schema": 1, "clips": clips}


def item_record(row: tuple[Any, ...]) -> dict[str, Any]:
    category, slug, name, description, rarity, tags, shape, base, accent, material, bone, motion, emissive, suit_mode = row
    item_id = f"trinkies:{category}_{slug}"
    source_rarity = rarity
    rarity = "eternal" if item_id in ETERNAL_ITEM_IDS else RARITY_REMAP.get(source_rarity, "")
    if not rarity:
        raise ValueError(f"unsupported source rarity {source_rarity!r} for {item_id}")
    size = 32 if category == "aura" else (64 if category in ("hat", "emote") else 128)
    parts = shape_parts(shape)
    seed = stable_seed(item_id)
    palette = make_palette(base, accent)
    texture = make_texture(size, palette, material, seed)
    animation = build_animations(category, motion, sorted({part["bone"] for part in parts} | {"root"}))
    item_dir = ITEM_ROOT / f"{category}_{slug}"
    item_dir.mkdir(parents=True, exist_ok=True)
    texture_path = item_dir / "texture.png"
    texture.save(texture_path, format="PNG", optimize=False, compress_level=9)
    texture_bytes = texture_path.read_bytes()

    model = build_bbmodel(item_id, name, size, texture_bytes, parts)
    (item_dir / "model.bbmodel").write_text(json.dumps(model, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (item_dir / "animations.json").write_text(json.dumps(animation, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if emissive:
        make_emissive_mask(size, seed).save(item_dir / "emissive.png", format="PNG", optimize=False, compress_level=9)

    item = {
        "id": item_id,
        "category": category,
        "name": name,
        "description": description,
        "rarity": rarity,
        "tags": tags,
        "model": "model.bbmodel",
        "texture": "texture.png",
        "attach": {"bone": bone, "offset": [0, 0, 0]},
        "animations": list(animation["clips"].keys()),
        "animation_file": "animations.json",
        "version": SCHEMA_VERSION,
        "palette": [rgb_hex(color) for color in palette],
        "shape": shape,
    }
    if emissive:
        item["emissive_mask"] = "emissive.png"
    if category == "aura":
        item["particle_sheet"] = "texture.png"
    if suit_mode:
        item["suit_mode"] = suit_mode
    (item_dir / "item.json").write_text(json.dumps(item, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    record = {"item": item, "parts": parts, "palette_rgb": palette, "texture_size": size, "seed": seed, "animation": animation}
    write_model_preview(record)
    return record


def transform_point(point: tuple[float, float, float], bone: str, clip_data: dict[str, Any], time: float) -> tuple[float, float, float]:
    pivot = np.array(RIG_PIVOTS.get(bone, (0, 0, 0)), dtype=float)
    local = np.array(point, dtype=float)
    track = clip_data.get("bones", {}).get(bone)
    if not track:
        return tuple(local + pivot)
    before, after = track[0], track[-1]
    for a, b in zip(track, track[1:]):
        if a["time"] <= time <= b["time"]:
            before, after = a, b
            break
    span = max(1e-8, after["time"] - before["time"])
    amount = max(0.0, min(1.0, (time - before["time"]) / span))
    easing = before.get("easing", "linear")
    if easing == "ease_in_out":
        amount = amount * amount * (3 - 2 * amount)
    elif easing == "cubic":
        amount = amount * amount * amount
    elif easing == "spring":
        amount = max(0.0, min(1.12, amount + math.sin(amount * math.pi * 2) * 0.07 * (1 - amount)))
    pos = np.array(before["position"], dtype=float) * (1 - amount) + np.array(after["position"], dtype=float) * amount
    rot = np.deg2rad(np.array(before["rotation"], dtype=float) * (1 - amount) + np.array(after["rotation"], dtype=float) * amount)
    sx = np.array(before["scale"], dtype=float) * (1 - amount) + np.array(after["scale"], dtype=float) * amount
    point_local = local * sx
    cx, cy, cz = np.cos(rot)
    sxr, syr, szr = np.sin(rot)
    rx = np.array([[1, 0, 0], [0, cx, -sxr], [0, sxr, cx]])
    ry = np.array([[cy, 0, syr], [0, 1, 0], [-syr, 0, cy]])
    rz = np.array([[cz, -szr, 0], [szr, cz, 0], [0, 0, 1]])
    return tuple(pivot + pos + rz @ ry @ rx @ point_local)


def shade(color: tuple[int, int, int], amount: float) -> tuple[int, int, int]:
    return tuple(max(0, min(255, round(channel * amount))) for channel in color)


def draw_cube_preview(draw: ImageDraw.ImageDraw, part: dict[str, Any], palette: list[tuple[int, int, int]], clip_data: dict[str, Any], time: float, center: tuple[float, float], scale: float, yaw: float) -> None:
    x0, y0, z0 = part["from"]
    x1, y1, z1 = part["to"]
    corners = [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    points = []
    for point in corners:
        wx, wy, wz = transform_point(point, part["bone"], clip_data, time)
        rx = wx * math.cos(yaw) + wz * math.sin(yaw)
        rz = -wx * math.sin(yaw) + wz * math.cos(yaw)
        sx = center[0] + (rx - rz) * 0.47 * scale
        sy = center[1] - wy * 0.72 * scale + (rx + rz) * 0.25 * scale
        points.append((sx, sy, rx + rz + wy * 0.2))
    # Corner index is x*4 + y*2 + z under the comprehension above.
    def poly(indices: tuple[int, ...], color: tuple[int, int, int]) -> None:
        draw.polygon([(points[i][0], points[i][1]) for i in indices], fill=color, outline=(18, 20, 24))
    base = palette[(len(part["name"]) + int(abs(x0 + y0 + z0))) % len(palette)]
    # The visible +Y, +Z, and +X faces get separate baked directional values.
    faces = [
        (sum(points[i][2] for i in (2, 3, 6, 7)) / 4, (2, 3, 7, 6), shade(base, 1.12)),
        (sum(points[i][2] for i in (1, 3, 5, 7)) / 4, (1, 3, 7, 5), shade(base, 0.82)),
        (sum(points[i][2] for i in (4, 5, 6, 7)) / 4, (4, 5, 7, 6), shade(base, 0.95)),
    ]
    for _, indices, color in sorted(faces, key=lambda face: face[0]):
        poly(indices, color)


def write_model_preview(record: dict[str, Any]) -> Path:
    """Rasterize one transparent, shaded isometric icon from the item's actual cube geometry."""
    item = record["item"]
    clips = record["animation"]["clips"]
    clip_name = "idle" if "idle" in clips else ("play" if "play" in clips else next(iter(clips)))
    clip_data = clips[clip_name]
    yaw = math.radians(34)
    projected: list[tuple[float, float, float]] = []
    for part in record["parts"]:
        x0, y0, z0 = part["from"]
        x1, y1, z1 = part["to"]
        for point in ((x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)):
            wx, wy, wz = transform_point(point, part["bone"], clip_data, 0.0)
            rx = wx * math.cos(yaw) + wz * math.sin(yaw)
            rz = -wx * math.sin(yaw) + wz * math.cos(yaw)
            projected.append(((rx - rz) * 0.47, -wy * 0.72 + (rx + rz) * 0.25, rx + rz + wy * 0.2))
    if not projected:
        raise ValueError(f"cannot render empty Trinkies model: {item['id']}")
    min_x = min(point[0] for point in projected)
    max_x = max(point[0] for point in projected)
    min_y = min(point[1] for point in projected)
    max_y = max(point[1] for point in projected)
    projected_width = max(max_x - min_x, 1.0)
    projected_height = max(max_y - min_y, 1.0)
    scale = min((PREVIEW_SIZE - 18) / projected_width,
                (PREVIEW_SIZE - 18) / projected_height, 8.0)
    center = (
        (PREVIEW_SIZE - projected_width * scale) / 2 - min_x * scale,
        (PREVIEW_SIZE - projected_height * scale) / 2 - min_y * scale,
    )

    image = Image.new("RGBA", (PREVIEW_SIZE, PREVIEW_SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    bottom = center[1] + max_y * scale
    shadow_width = max(10, min(44, projected_width * scale * 0.38))
    shadow_height = max(4, shadow_width * 0.24)
    shadow_center = center[0] + (min_x + max_x) * scale / 2
    draw.ellipse((shadow_center - shadow_width / 2, bottom - shadow_height / 2 + 2,
                  shadow_center + shadow_width / 2, bottom + shadow_height / 2 + 2),
                 fill=(12, 16, 20, 56))
    for part in sorted(record["parts"], key=lambda p: sum(p["from"]) + sum(p["to"])):
        draw_cube_preview(draw, part, record["palette_rgb"], clip_data, 0.0, center, scale, yaw)

    output = ITEM_ROOT / item["id"].split(":", 1)[1] / "preview.png"
    image.save(output, format="PNG", optimize=False, compress_level=9)
    return output


def font(size: int = 12) -> ImageFont.ImageFont:
    try:
        return ImageFont.truetype("DejaVuSans.ttf", size)
    except OSError:
        return ImageFont.load_default()


def write_turnaround_sheet(records: list[dict[str, Any]]) -> Path:
    cell_w, cell_h = 250, 164
    margin, header_h, category_gutter = 16, 36, 82
    image = Image.new("RGB", (margin * 2 + category_gutter + cell_w * 5, margin * 2 + header_h + cell_h * 6), (19, 22, 27))
    draw = ImageDraw.Draw(image)
    draw.text((margin, 8), "TRINKIES · VERTICAL SLICE · GENERATED MODEL TURNAROUNDS", fill=(231, 239, 236), font=font(18))
    labels = {"hat": "HATS", "suit": "SUITS", "back": "BACK & WINGS", "pet": "PETS", "emote": "EMOTES", "aura": "AURAS & TRAILS"}
    by_category: dict[str, list[dict[str, Any]]] = {}
    for record in records:
        by_category.setdefault(record["item"]["category"], []).append(record)
    for row, category in enumerate(("hat", "suit", "back", "pet", "emote", "aura")):
        y = margin + header_h + row * cell_h
        draw.text((margin, y + 5), labels[category], fill=(111, 203, 120), font=font(13))
        for col, record in enumerate(by_category[category]):
            x = margin + category_gutter + col * cell_w
            tile = record["texture_size"]
            rarity_color = RARITY_ACCENT_RGB[record["item"]["rarity"]]
            draw.rounded_rectangle((x + 50, y + 2, x + cell_w - 4, y + cell_h - 4), radius=5, fill=(31, 35, 41), outline=rarity_color, width=2)
            draw.text((x + 58, y + 7), record["item"]["name"], fill=(243, 245, 240), font=font(12))
            clip_name = "play" if "play" in record["animation"]["clips"] else ("signature" if "signature" in record["animation"]["clips"] else "idle")
            clip_data = record["animation"]["clips"][clip_name]
            bounds = []
            for part in record["parts"]:
                pivot = RIG_PIVOTS.get(part["bone"], (0, 0, 0))
                bounds.extend([(part["from"][i] + pivot[i]) for i in range(3)])
                bounds.extend([(part["to"][i] + pivot[i]) for i in range(3)])
            extent = max(max(bounds) - min(bounds), 10) if bounds else 16
            render_scale = min(3.5, 78 / extent)
            for frame, (yaw, time) in enumerate(((0.15, 0), (1.35, clip_data["duration"] * 0.28), (2.65, clip_data["duration"] * 0.62))):
                cx = x + 91 + frame * 58
                cy = y + 103
                draw.line((cx - 16, cy + 31, cx + 16, cy + 31), fill=(60, 68, 75), width=1)
                for part in sorted(record["parts"], key=lambda p: sum(p["from"]) + sum(p["to"])):
                    draw_cube_preview(draw, part, record["palette_rgb"], clip_data, time, (cx, cy), render_scale, yaw)
            draw.text((x + 58, y + cell_h - 24), f"{clip_name.upper()} · 3 SAMPLE ANGLES", fill=(167, 179, 181), font=font(9))
    output = DOCS_ROOT / "vertical-slice-contact-sheet.png"
    image.save(output, format="PNG", optimize=False, compress_level=9)
    return output


def write_animation_strip(records: list[dict[str, Any]]) -> Path:
    cell_w, cell_h = 260, 126
    image = Image.new("RGB", (cell_w * 5, cell_h * 6), (19, 22, 27))
    draw = ImageDraw.Draw(image)
    by_category: dict[str, list[dict[str, Any]]] = {}
    for record in records:
        by_category.setdefault(record["item"]["category"], []).append(record)
    for row, category in enumerate(("hat", "suit", "back", "pet", "emote", "aura")):
        for col, record in enumerate(by_category[category]):
            x, y = col * cell_w, row * cell_h
            draw.rounded_rectangle((x + 3, y + 3, x + cell_w - 4, y + cell_h - 4), radius=4, fill=(29, 33, 39), outline=(67, 77, 85), width=1)
            draw.text((x + 10, y + 7), record["item"]["name"], fill=(235, 239, 234), font=font(11))
            clip_name = "play" if "play" in record["animation"]["clips"] else ("signature" if "signature" in record["animation"]["clips"] else "idle")
            clip_data = record["animation"]["clips"][clip_name]
            bounds = []
            for part in record["parts"]:
                pivot = RIG_PIVOTS.get(part["bone"], (0, 0, 0))
                bounds += [part["from"][i] + pivot[i] for i in range(3)] + [part["to"][i] + pivot[i] for i in range(3)]
            extent = max(max(bounds) - min(bounds), 10) if bounds else 16
            render_scale = min(2.5, 45 / extent)
            for frame in range(4):
                time = clip_data["duration"] * frame / 3
                cx = x + 41 + frame * 57
                cy = y + 81
                for part in sorted(record["parts"], key=lambda p: sum(p["from"]) + sum(p["to"])):
                    draw_cube_preview(draw, part, record["palette_rgb"], clip_data, time, (cx, cy), render_scale, 0.25)
                draw.text((cx - 10, y + 108), str(frame + 1), fill=(154, 168, 169), font=font(8))
            draw.text((x + 210, y + 108), clip_name, fill=(113, 203, 120), font=font(8))
    output = DOCS_ROOT / "vertical-slice-animation-strip.png"
    image.save(output, format="PNG", optimize=False, compress_level=9)
    return output


def generate() -> None:
    if len(ROWS) != 30:
        raise SystemExit(f"Expected exactly 30 vertical-slice items, found {len(ROWS)}")
    ITEM_ROOT.mkdir(parents=True, exist_ok=True)
    DOCS_ROOT.mkdir(parents=True, exist_ok=True)
    records = [item_record(row) for row in ROWS]
    catalog_items = []
    for record in records:
        item = record["item"]
        folder = Path("items") / item["id"].split(":", 1)[1]
        catalog_items.append({"id": item["id"], "category": item["category"], "name": item["name"],
                              "rarity": item["rarity"], "item": (folder / "item.json").as_posix(),
                              "texture": (folder / item["texture"]).as_posix(),
                              "preview": (folder / "preview.png").as_posix()})
    catalog = {"schema": SCHEMA_VERSION, "status": "vertical_slice_unverified_in_game", "items": catalog_items}
    (ASSET_ROOT / "catalog.json").write_text(json.dumps(catalog, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    file_hashes = {}
    for path in sorted(p for p in ASSET_ROOT.rglob("*") if p.is_file() and p.name != "manifest.json"):
        file_hashes[path.relative_to(ASSET_ROOT).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    manifest = {"schema": SCHEMA_VERSION, "item_count": len(records), "sha256": file_hashes}
    (ASSET_ROOT / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    turnaround = write_turnaround_sheet(records)
    animation_strip = write_animation_strip(records)
    print(f"Generated {len(records)} original items across 6 categories")
    print(f"Catalog: {ASSET_ROOT / 'catalog.json'}")
    print(f"Manifest: {ASSET_ROOT / 'manifest.json'}")
    print(f"Turnarounds: {turnaround}")
    print(f"Animation sample strip: {animation_strip}")


if __name__ == "__main__":
    generate()
