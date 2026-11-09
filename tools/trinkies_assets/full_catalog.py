"""Curated extension roster and deterministic geometry recipes for the full 300-item catalog.

The first 30 items remain hand-authored in generate_slice.py. This module contributes 45
additional silhouettes per category (nine families times five material themes), bringing each
of the six categories to 50 unique IDs. The family changes the model form; the theme index also
changes one or more geometric dimensions, so these are not palette-only recolors.
"""
from __future__ import annotations

import math
from typing import Any

from . import generate_slice as base

# (name, short concept, tags, material, motion, attachment bone)
FAMILIES: dict[str, list[tuple[str, str, list[str], str, str, str]]] = {
    "hat": [
        ("spire", "Spire Crown", ["crown", "spire"], "metal", "horn_shiver", "head"),
        ("hood", "Canopy Hood", ["hood", "canopy"], "cloth", "hood_bob", "head"),
        ("halo", "Orbit Halo", ["halo", "lantern"], "metal", "lantern_sway", "head"),
        ("aviator", "Aviator Cap", ["flight", "goggles"], "leather", "hat_wind", "head"),
        ("circlet", "Tide Circlet", ["circlet", "jewel"], "glass", "circlet_glint", "head"),
        ("visor", "Skyguard Visor", ["visor", "guard"], "enamel", "hat_wind", "head"),
        ("antlers", "Moonbranch Antlers", ["antlers", "branch"], "wood", "horn_shiver", "head"),
        ("wreath", "Petal Wreath", ["wreath", "petal"], "cloth", "circlet_glint", "head"),
        ("mask", "Relic Mask", ["mask", "relic"], "stone", "hood_bob", "head"),
    ],
    "suit": [
        ("diver", "Pressure Diver Suit", ["diver", "pressure"], "metal", "suit_breathe", "body"),
        ("wayfinder", "Wayfinder Coat", ["travel", "coat"], "cloth", "suit_breathe", "body"),
        ("cartographer", "Cartographer Uniform", ["maps", "compass"], "leather", "suit_breathe", "body"),
        ("stargazer", "Stargazer Robe", ["night", "stars"], "wool", "suit_breathe", "body"),
        ("mechanist", "Mechanist Harness", ["tools", "workshop"], "metal", "suit_breathe", "body"),
        ("samurai", "Lamellar Guard Suit", ["lamellar", "guard"], "enamel", "suit_breathe", "body"),
        ("ranger", "Canopy Ranger Suit", ["forest", "ranger"], "leather", "suit_breathe", "body"),
        ("alchemist", "Alchemist Apron", ["alchemy", "vials"], "cloth", "suit_breathe", "body"),
        ("sentinel", "Sentinel Plate Suit", ["sentinel", "armor"], "stone", "suit_breathe", "body"),
    ],
    "back": [
        ("wings", "Glasswing Harness", ["wings", "flight"], "glass", "wing_flutter", "body"),
        ("satchel", "Courier Satchel", ["courier", "pack"], "leather", "pack_tick", "body"),
        ("banner", "Sky Banner", ["banner", "ribbon"], "cloth", "ribbon_sway", "body"),
        ("glider", "Leaf Glider", ["glider", "leaves"], "wood", "wing_flutter", "body"),
        ("drone", "Bell Drone", ["drone", "clockwork"], "metal", "drone_orbit", "body"),
        ("shield", "Wayfarer Shield", ["shield", "crest"], "stone", "pack_tick", "body"),
        ("tail", "Comet Tail Rig", ["tail", "comet"], "cloth", "ribbon_sway", "body"),
        ("lantern", "Beacon Lantern Pack", ["lantern", "light"], "metal", "pack_tick", "body"),
        ("wingpack", "Clockwork Wingpack", ["wingpack", "gear"], "metal", "wing_flutter", "body"),
    ],
    "pet": [
        ("wisp", "Crystal Wisp", ["wisp", "crystal"], "crystal", "pet_float", "root"),
        ("blob", "Sootbubble", ["blob", "soft"], "wool", "pet_bounce", "root"),
        ("moth", "Lanternwing Moth", ["moth", "wings"], "fur", "pet_flutter", "root"),
        ("crab", "Pebble Crab", ["crab", "clockwork"], "metal", "pet_scuttle", "root"),
        ("bird", "Kite Finch", ["bird", "flight"], "feather", "pet_flutter", "root"),
        ("dragon", "Pocket Drake", ["dragon", "tail"], "scale", "pet_glide", "root"),
        ("turtle", "Mossback Turtle", ["turtle", "shell"], "stone", "pet_bounce", "root"),
        ("bot", "Clockwork Sprout Bot", ["robot", "sprout"], "metal", "pet_scuttle", "root"),
        ("fox", "Cloudfox Cub", ["fox", "forest"], "fur", "pet_glide", "root"),
    ],
    "emote": [
        ("greeting", "Paper Greeting", ["greeting", "paper"], "paper", "emote_wave", "right_arm"),
        ("cheer", "Star Wand Cheer", ["cheer", "star"], "gem", "emote_cheer", "right_arm"),
        ("bow", "Marigold Bow", ["bow", "flower"], "cloth", "emote_bow", "body"),
        ("drum", "Pocket Drumline", ["rhythm", "drum"], "wood", "emote_drum", "body"),
        ("dance", "Ribbon Dance", ["dance", "ribbon"], "cloth", "emote_cheer", "right_arm"),
        ("salute", "Wayfinder Salute", ["salute", "badge"], "leather", "emote_wave", "right_arm"),
        ("clap", "Spark Clap", ["clap", "spark"], "enamel", "emote_cheer", "body"),
        ("victory", "Victory Pennant", ["victory", "banner"], "paper", "emote_wave", "right_arm"),
        ("twirl", "Moonlit Twirl", ["twirl", "moon"], "glass", "emote_bow", "body"),
    ],
    "aura": [
        ("monsoon", "Monsoon Orbit", ["water", "orbit"], "glass", "aura_orbit", "root"),
        ("aurora", "Aurora Veil", ["aurora", "arc"], "glass", "aura_sweep", "root"),
        ("ember", "Ember Petals", ["ember", "petals"], "enamel", "aura_rise", "root"),
        ("rune", "Glass Rune Ring", ["runes", "ring"], "crystal", "aura_orbit", "root"),
        ("star", "Star Silt Trail", ["stars", "trail"], "dust", "aura_trail", "root"),
        ("snow", "Snowfall Halo", ["snow", "halo"], "glass", "aura_rise", "root"),
        ("leaf", "Willow Whorl", ["leaf", "whorl"], "wood", "aura_sweep", "root"),
        ("vortex", "Violet Vortex", ["vortex", "spiral"], "crystal", "aura_orbit", "root"),
        ("spectrum", "Prism Spectrum", ["prism", "spectrum"], "glass", "aura_trail", "root"),
    ],
}

# Five palette/material recipes per category. The same theme is used across different silhouettes,
# while the unique family geometry and theme-index proportions make every row more than a recolor.
THEMES: dict[str, list[tuple[str, str, str, str]]] = {
    "hat": [
        ("Frost", "frost", "#347A9C", "#D2F7FF"),
        ("Sunstone", "sunstone", "#B66A35", "#FFD079"),
        ("Verdant", "verdant", "#426F52", "#D7ED8A"),
        ("Moonlit", "moonlit", "#4D568B", "#D0C8FF"),
        ("Tide", "tide", "#287B83", "#9AE8E1"),
    ],
    "suit": [
        ("Crimson", "crimson", "#7D3340", "#F4B96F"),
        ("Glacier", "glacier", "#426D86", "#C4F1F4"),
        ("Willow", "willow", "#4B6845", "#D4BE79"),
        ("Violet", "violet", "#51466F", "#D0A8F5"),
        ("Copper", "copper", "#896044", "#F2C57A"),
    ],
    "back": [
        ("Aurora", "aurora", "#3B746E", "#9BF0D2"),
        ("Ember", "ember", "#8D4D37", "#FFC36F"),
        ("Cloud", "cloud", "#586D93", "#D4E5FF"),
        ("Canopy", "canopy", "#4E7048", "#D1DE83"),
        ("Brass", "brass", "#826334", "#FFE08A"),
    ],
    "pet": [
        ("Amethyst", "amethyst", "#654F99", "#E6D5FF"),
        ("Moss", "moss", "#587048", "#D4E68D"),
        ("Cinder", "cinder", "#4D515D", "#E0A68C"),
        ("Tidal", "tidal", "#367889", "#B9F3EF"),
        ("Saffron", "saffron", "#A66B32", "#FFE083"),
    ],
    "emote": [
        ("Marigold", "marigold", "#9B4D65", "#FFD36B"),
        ("Origami", "origami", "#8D8060", "#C4ECD1"),
        ("Starlight", "starlight", "#4C5786", "#F4E2A0"),
        ("Carnival", "carnival", "#8A4C86", "#F2A75C"),
        ("Breeze", "breeze", "#3D758B", "#D8F4E5"),
    ],
    "aura": [
        ("Monsoon", "monsoon", "#3D7EA1", "#B9ECF7"),
        ("Aurora", "aurora", "#425B91", "#8CF2D2"),
        ("Ember", "ember", "#A44F37", "#FFD27A"),
        ("Moonstone", "moonstone", "#5A4785", "#D2B9FF"),
        ("Stardust", "stardust", "#63708D", "#F1DEA2"),
    ],
}

MATERIALS = {
    "metal": "metal", "cloth": "cloth", "lantern": "metal", "leather": "leather",
    "glass": "glass", "enamel": "enamel", "wood": "wood", "stone": "stone",
    "crystal": "crystal", "wool": "wool", "fur": "fur", "feather": "feather",
    "scale": "scale", "gem": "gem", "paper": "paper", "dust": "dust",
}


def extra_rows() -> list[tuple[Any, ...]]:
    """Return exactly 270 deterministic rows, nine form families × five themes × six categories."""
    rows: list[tuple[Any, ...]] = []
    rarities = ("common", "uncommon", "rare", "epic", "legendary")
    for category in ("hat", "suit", "back", "pet", "emote", "aura"):
        families = FAMILIES[category]
        themes = THEMES[category]
        assert len(families) == 9 and len(themes) == 5
        for theme_index, (theme_name, theme_slug, base_hex, accent_hex) in enumerate(themes):
            for family_index, (family, label, tags, material, motion, bone) in enumerate(families):
                slug = f"full_{theme_slug}_{family}"
                name = f"{theme_name} {label}"
                description = (
                    f"An original {label.lower()} with a {theme_name.lower()} {material} finish, "
                    f"family-specific silhouette, and a {motion.replace('_', ' ')} accent. "
                    "Designed as a distinct voxel form for the Trinkies local catalog."
                )
                item_tags = list(dict.fromkeys([theme_slug, *tags, material]))
                shape = f"full_{category}_{family}_{theme_index}"
                emissive = category == "aura" or (theme_index + family_index) % 2 == 0
                suit_mode = "overlay" if category == "suit" else None
                rows.append((
                    category, slug, name, description, rarities[(theme_index + family_index) % len(rarities)],
                    item_tags, shape, base_hex, accent_hex, MATERIALS[material], bone, motion, emissive, suit_mode,
                ))
    if len(rows) != 270:
        raise AssertionError(f"expected 270 extension rows, got {len(rows)}")
    return rows


def _cube(name: str, start: tuple[float, float, float], end: tuple[float, float, float], bone: str, rotation=(0, 0, 0)) -> dict[str, Any]:
    return base.box(name, start, end, bone, rotation)


def _hat_parts(family: str, variant: int) -> list[dict[str, Any]]:
    width = 3.7 + variant * 0.22
    crown_h = 2.0 + (variant % 3) * 0.45
    d = 3.4 + (variant % 2) * 0.35
    band = _cube("band", (-width, -0.2, -d), (width, 0.8, d), "head")
    if family == "spire":
        return [band, _cube("crown", (-width + .5, .7, -d + .5), (width - .5, 1.8 + crown_h, d - .5), "head"),
                _cube("center_spire", (-.8, 1.8 + crown_h, -1), (.8, 3.0 + crown_h, 1), "head"),
                _cube("side_gem", (-1.2, 1.2, d), (1.2, 2.6, d + .6), "head")]
    if family == "hood":
        return [band, _cube("hood_shell", (-width, .5, -d), (width, 3.0 + crown_h, d), "head"),
                _cube("brow_fold", (-width, -.8, d), (width, .5, d + .8), "head"),
                _cube("left_flap", (-width - .7, -1.8, -.4), (-width, .2, 2.4), "head"),
                _cube("right_flap", (width, -1.8, -.4), (width + .7, .2, 2.4), "head")]
    if family == "halo":
        thickness = .65 + variant * .08
        return [
            _cube("halo_front", (-width, 5.0, 1.5), (width, 5.0 + thickness, 2.4), "head"),
            _cube("halo_back", (-width, 5.0, -2.4), (width, 5.0 + thickness, -1.5), "head"),
            _cube("halo_left", (-width, 5.0, -1.5), (-width + thickness, 5.0 + thickness, 1.5), "head"),
            _cube("halo_right", (width - thickness, 5.0, -1.5), (width, 5.0 + thickness, 1.5), "head"),
            _cube("support_left", (-2.8, 3.5, -.3), (-2.1, 5.0, .3), "head"),
            _cube("support_right", (2.1, 3.5, -.3), (2.8, 5.0, .3), "head"),
            _cube("orbit_lantern", (-.8, 5.0 + thickness, -.7), (.8, 7.0 + thickness, .7), "head")]
    if family == "aviator":
        return [band, _cube("cap_dome", (-width, .7, -d), (width, 3.2 + crown_h, d), "head"),
                _cube("visor", (-width - .2, .2, 1.8), (width + .2, .8, d + 1.0), "head"),
                _cube("left_goggle", (-width, -1.0, d), (-.4, .5, d + .8), "head"),
                _cube("right_goggle", (.4, -1.0, d), (width, .5, d + .8), "head")]
    if family == "circlet":
        return [band, _cube("center_gem", (-1.0, .8, d), (1.0, 2.8 + crown_h * .25, d + .8), "head"),
                _cube("left_fin", (-width - .9, .5, -.5), (-width, 3.2, 1.4), "head"),
                _cube("right_fin", (width, .5, -.5), (width + .9, 3.2, 1.4), "head"),
                _cube("rear_band", (-width, .1, -d), (width, .7, -d + .8), "head")]
    if family == "visor":
        return [band, _cube("helmet_shell", (-width, .6, -d), (width, 3.2 + crown_h, d), "head"),
                _cube("visor_brim", (-width - .5, .1, d), (width + .5, 1.2, d + 1.2), "head"),
                _cube("visor_glint", (-width + .4, .3, d + 1.2), (width - .4, .6, d + 1.35), "head"),
                _cube("cheek_left", (-width, -1.3, .4), (-width + .8, .3, d), "head"),
                _cube("cheek_right", (width - .8, -1.3, .4), (width, .3, d), "head")]
    if family == "antlers":
        return [band, _cube("antler_left_trunk", (-width - .5, .8, -.2), (-width + .3, 4.2 + crown_h, .8), "head", (0, 0, -12)),
                _cube("antler_left_tine", (-width - 1.8, 2.8, -.2), (-width - .5, 3.6, .7), "head", (0, 0, -18)),
                _cube("antler_right_trunk", (width - .3, .8, -.2), (width + .5, 3.6 + crown_h, .8), "head", (0, 0, 12)),
                _cube("antler_right_tine", (width + .5, 2.2, -.2), (width + 1.8, 3.0, .7), "head", (0, 0, 18))]
    if family == "wreath":
        petals = [band]
        for i, x in enumerate((-2.8, -1.4, 0.0, 1.4, 2.8)):
            petals.append(_cube(f"petal_{i}", (x - .65, 1.0 + (i % 2) * .35, d - .15),
                                (x + .65, 2.0 + crown_h * .2, d + .65), "head", (0, 0, (i - 2) * 7)))
        petals.append(_cube("leaf_side", (-width, .2, -1), (width, .8, 1), "head"))
        return petals
    # Relic mask: brow, cheek guards, nose bridge, and paired raised side fins.
    return [band, _cube("mask_brow", (-width, .7, d), (width, 2.0, d + 1.0), "head"),
            _cube("nose_bridge", (-.8, -1.4, d), (.8, .9, d + 1.0), "head"),
            _cube("cheek_left", (-width, -2.0, d), (-1.9, -.7, d + .9), "head"),
            _cube("cheek_right", (1.9, -2.0, d), (width, -.7, d + .9), "head"),
            _cube("side_fin_left", (-width - .8, .1, -.4), (-width, 2.4, .3), "head"),
            _cube("side_fin_right", (width, .1, -.4), (width + .8, 2.4, .3), "head")]


def _suit_parts(family: str, variant: int) -> list[dict[str, Any]]:
    w = 4.4 + variant * .12
    length = 7.0 + (variant % 3) * .7
    parts = [_cube("torso_shell", (-w, -length, -3), (w, 5.0, 3), "body"),
             _cube("collar", (-3.0, 4.0, 2.5), (3.0, 6.0, 3.8), "body"),
             _cube("chest_emblem", (-1.5, 0.0, 3.0), (1.5, 2.5, 4.0), "body")]
    if family == "diver":
        parts += [_cube("tank_left", (-6.2, -2, -5), (-4.4, 4.0, -2.5), "body"), _cube("tank_right", (4.4, -2, -5), (6.2, 4, -2.5), "body"),
                  _cube("pressure_valve", (-.7, 2, 4), (.7, 3.5, 4.8), "body")]
    elif family == "wayfinder":
        parts += [_cube("coat_panel", (-2.1, -length, 3.0), (2.1, 3.2, 4.0), "body"),
                  _cube("map_case", (-6, -1, -4), (-3.8, 3, -2), "body"), _cube("belt", (-4.4, -4, 2.7), (4.4, -3, 3.5), "body")]
    elif family == "cartographer":
        parts += [_cube("compass_plate", (-2, -1, 3.2), (2, 3, 4.1), "body"),
                  _cube("left_vial", (-3.5, -1, -3.8), (-2, 3, -3), "body"), _cube("right_vial", (2, -1, -3.8), (3.5, 3, -3), "body")]
    elif family == "stargazer":
        parts += [_cube("robe_sash", (-3.2, -length, 3.1), (-1.8, 4, 4.1), "body"),
                  _cube("star_plate_left", (-3, 1, 4), (-1.2, 2.5, 4.8), "body"),
                  _cube("star_plate_right", (1.2, -2, 4), (3, -.5, 4.8), "body")]
    elif family == "mechanist":
        parts += [_cube("tool_harness", (-5.3, 1.5, 3.2), (5.3, 3.0, 4.1), "body"),
                  _cube("wrench_case", (-6, -3, -4.5), (-4.2, 1, -2.5), "body"),
                  _cube("gear_core", (-1, -2, 4), (1, 0, 5), "body")]
    elif family == "samurai":
        parts += [_cube("lamellar_plate", (-4.8, -2, 3.1), (4.8, 2.7, 4.1), "body"),
                  _cube("waist_tasset", (-4.6, -length, 2.9), (4.6, -4.5, 4), "body"),
                  _cube("crest", (-.7, 5.8, 3), (.7, 7.4, 4), "body")]
    elif family == "ranger":
        parts += [_cube("shoulder_leaf_left", (-6.8, 2.5, -2), (-4.1, 5.7, 2.2), "left_arm"),
                  _cube("shoulder_leaf_right", (4.1, 2.5, -2), (6.8, 5.7, 2.2), "right_arm"),
                  _cube("leaf_clasp", (-1, 2, 4), (1, 4, 4.8), "body")]
    elif family == "alchemist":
        parts += [_cube("apron", (-3.7, -length, 3.0), (3.7, 1.5, 4), "body"),
                  _cube("flask_left", (-5.7, -1.4, 2), (-4.4, 2.4, 3), "body"),
                  _cube("flask_right", (4.4, -1.4, 2), (5.7, 2.4, 3), "body")]
    else:
        parts += [_cube("sentinel_breastplate", (-4.7, -3.2, 3.1), (4.7, 3.5, 4.2), "body"),
                  _cube("crest", (-1, 5.6, 2.4), (1, 7.3, 3.7), "body"),
                  _cube("knee_guard_left", (-2.3, -5.4, 2.3), (2.3, -2.4, 3.1), "left_leg"),
                  _cube("knee_guard_right", (-2.3, -5.4, 2.3), (2.3, -2.4, 3.1), "right_leg")]
    for side, arm in (("left", "left_arm"), ("right", "right_arm")):
        parts.append(_cube(f"{side}_shoulder", (-7, 3, -2), (-4, 6, 2), arm))
        parts.append(_cube(f"{side}_cuff", (-2.4, -5, -2), (2.4, -3.2, 2), arm))
    for side, leg in (("left", "left_leg"), ("right", "right_leg")):
        parts.append(_cube(f"{side}_boot", (-2.2, -6.4, -2.4), (2.2, -3.6, 2.4), leg))
    return parts


def _back_parts(family: str, variant: int) -> list[dict[str, Any]]:
    scale = 1.0 + variant * .07
    panel = 2.5 * scale
    if family == "wings":
        return [_cube("harness", (-2, -2, -3), (2, 3, -2), "body"),
                _cube("left_wing_upper", (0, 0, -1), (panel + 1, 5 * scale, 0), "left_wing", (0, 0, -12 - variant)),
                _cube("left_wing_lower", (0, -4 * scale, -1), (panel, 0, 0), "left_wing", (0, 0, 10 + variant)),
                _cube("right_wing_upper", (-panel - 1, 0, -1), (0, 5 * scale, 0), "right_wing", (0, 0, 12 + variant)),
                _cube("right_wing_lower", (-panel, -4 * scale, -1), (0, 0, 0), "right_wing", (0, 0, -10 - variant)),
                _cube("wing_lens", (-.8, 0, -3.2), (.8, 1.6, -2.9), "body")]
    if family == "satchel":
        return [_cube("pack_body", (-3.3, -4.2, -5.2), (3.3, 3.4, -2.8), "body"),
                _cube("pack_flap", (-3.1, 1.1, -5.5), (3.1, 3.6, -2.5), "body"),
                _cube("front_pocket", (-2.4, -3.1, -5.8), (2.4, -.8, -5), "body"),
                _cube("left_strap", (-4.2, -4, -4), (-3.4, 4, -2.2), "body"),
                _cube("right_strap", (3.4, -4, -4), (4.2, 4, -2.2), "body")]
    if family == "banner":
        return [_cube("backplate", (-1.7, -2, -2.8), (1.7, 2, -2), "body"),
                _cube("mast", (-.35, -5, -3.2), (.35, 6 * scale, -2.5), "body"),
                _cube("banner_cloth", (-3.5 * scale, 1, -3.1), (3.5 * scale, 5.8 * scale, -2.7), "body"),
                _cube("left_ribbon", (-3.5 * scale, -4.5, -3.2), (-2.2, 1.2, -2.7), "tail"),
                _cube("right_ribbon", (2.2, -4.5, -3.2), (3.5 * scale, 1.2, -2.7), "tail")]
    if family == "glider":
        return [_cube("spine", (-1, -4, -3.2), (1, 4, -2.4), "body"),
                _cube("left_leaf_wing", (-1, -1, -2), (4.5 * scale, 5.0 * scale, -.5), "left_wing", (0, 0, -18)),
                _cube("left_lower_leaf", (-1, -5 * scale, -2), (3.5, 0, -.5), "left_wing", (0, 0, 14)),
                _cube("right_leaf_wing", (-4.5 * scale, -1, -2), (1, 5 * scale, -.5), "right_wing", (0, 0, 18)),
                _cube("right_lower_leaf", (-3.5, -5 * scale, -2), (1, 0, -.5), "right_wing", (0, 0, -14))]
    if family == "drone":
        return [_cube("drone_core", (-2, -1.5, -4.5), (2, 2.5, -2.5), "body"),
                _cube("fin_left", (-4 * scale, 0, -3.7), (-1.7, .8, -3.1), "body", (0, 0, -14)),
                _cube("fin_right", (1.7, 0, -3.7), (4 * scale, .8, -3.1), "body", (0, 0, 14)),
                _cube("fin_top", (-.7, 2.4, -3.6), (.7, 4.3 * scale, -3.0), "body"),
                _cube("lens", (-.8, -.6, -4.9), (.8, .8, -4.4), "body")]
    if family == "shield":
        return [_cube("shield_outer", (-4.2 * scale, -5.5 * scale, -3.5), (4.2 * scale, 5.0 * scale, -2.7), "body"),
                _cube("shield_center", (-3.3 * scale, -4.5 * scale, -3.8), (3.3 * scale, 4.0 * scale, -3.4), "body"),
                _cube("crest", (-1.1, -1, -4), (1.1, 2.2, -3.8), "body")]
    if family == "tail":
        return [_cube("tail_root", (-1.2, -1.5, -3), (1.2, .7, -2.2), "body"),
                _cube("tail_upper", (-1, -4.2 * scale, -3.7), (1, -1.5, -2.7), "tail", (0, 0, 12)),
                _cube("tail_lower", (-.8, -6.5 * scale, -3.3), (.8, -3.2 * scale, -2.5), "tail", (0, 0, -18)),
                _cube("tail_tip", (-1.4, -8 * scale, -3.4), (1.4, -6.8 * scale, -2.3), "tail")]
    if family == "lantern":
        return [_cube("frame_top", (-2.4, 2, -4), (2.4, 2.8, -2.4), "body"),
                _cube("frame_bottom", (-2.4, -4, -4), (2.4, -3.2, -2.4), "body"),
                _cube("frame_left", (-2.4, -3.2, -4), (-1.7, 2, -2.4), "body"),
                _cube("frame_right", (1.7, -3.2, -4), (2.4, 2, -2.4), "body"),
                _cube("glass_core", (-1.6, -2.8, -3.6), (1.6, 1.7, -2.8), "body")]
    return [_cube("pack_frame", (-3.4, -3, -3.5), (3.4, 3, -2.3), "body"),
            _cube("gear_left", (-5.0 * scale, 1, -3), (-3.0, 3.5, -2.1), "body"),
            _cube("gear_right", (3.0, 1, -3), (5.0 * scale, 3.5, -2.1), "body"),
            _cube("left_wing", (0, -1, -1), (4.7 * scale, 4.0 * scale, 0), "left_wing", (0, 0, -18)),
            _cube("right_wing", (-4.7 * scale, -1, -1), (0, 4.0 * scale, 0), "right_wing", (0, 0, 18))]


def _pet_parts(family: str, variant: int) -> list[dict[str, Any]]:
    scale = .92 + variant * .05
    w = 2.5 * scale
    body = _cube("body", (-w, -w, -w), (w, w, w), "pet_root")
    if family == "wisp":
        return [body, _cube("core", (-1, -1, w), (1, 1, w + 1.2), "pet_root"),
                _cube("shard_left", (-3.4, -.4, 0), (-2.3, 1.5, .8), "pet_root", (0, 0, -20)),
                _cube("shard_right", (2.3, -.4, 0), (3.4, 1.5, .8), "pet_root", (0, 0, 20))]
    if family == "blob":
        return [_cube("rounded_core", (-3.2 * scale, -2.4 * scale, -2.4), (3.2 * scale, 2.4 * scale, 2.4), "pet_root"),
                _cube("belly", (-2, -2, 2.3), (2, -.4, 3), "pet_root"),
                _cube("foot_left", (-2.8, -3.2, 0), (-1.2, -2.4, 1.2), "pet_root"),
                _cube("foot_right", (1.2, -3.2, 0), (2.8, -2.4, 1.2), "pet_root"),
                _cube("curl_tail", (1.8, -1, -3.4), (3.6, .8, -2.4), "pet_tail", (0, 0, 25))]
    if family == "moth":
        return [body, _cube("left_wing", (-6 * scale, -1, -.2), (-.5, 3.0 * scale, .3), "pet_left_wing", (0, 0, -12)),
                _cube("right_wing", (.5, -1, -.2), (6 * scale, 3.0 * scale, .3), "pet_right_wing", (0, 0, 12)),
                _cube("lantern_abdomen", (-1.3, -2.5, -1), (1.3, -.4, 1.4), "pet_root"),
                _cube("antenna_left", (-2, 2, .2), (-.8, 4, .7), "pet_root", (0, 0, -15)),
                _cube("antenna_right", (.8, 2, .2), (2, 4, .7), "pet_root", (0, 0, 15))]
    if family == "crab":
        return [_cube("shell", (-3.2 * scale, -.7, -2.2), (3.2 * scale, 2.4, 2.2), "pet_root"),
                _cube("dial", (-1.2, .5, 2.2), (1.2, 1.8, 2.9), "pet_root"),
                _cube("claw_left", (-5, -.8, 1), (-3.1, .8, 2.2), "pet_root"),
                _cube("claw_right", (3.1, -.8, 1), (5, .8, 2.2), "pet_root"),
                *[_cube(f"leg_{i}", (x, -2.4, -1.5), (x + 1, -1.5, .5), "pet_root") for i, x in enumerate((-3, -1, 1, 2))]]
    if family == "bird":
        return [_cube("body", (-2.4, -2, -2.5), (2.4, 2, 2.5), "pet_root"),
                _cube("beak", (-.8, -.4, 2.5), (.8, .5, 4), "pet_root"),
                _cube("left_wing", (-5.5 * scale, -1, -1), (-2, 1.5, 1), "pet_left_wing", (0, 0, -15)),
                _cube("right_wing", (2, -1, -1), (5.5 * scale, 1.5, 1), "pet_right_wing", (0, 0, 15)),
                _cube("tail", (-1.5, -3, -3.5), (1.5, -1.2, -2.2), "pet_tail")]
    if family == "dragon":
        return [body, _cube("snout", (-1.5, -.6, 2.4), (1.5, .7, 4.1), "pet_root"),
                _cube("horn_left", (-3.1, 1.7, -.2), (-1.8, 3.5, 1), "pet_root", (0, 0, -15)),
                _cube("horn_right", (1.8, 1.7, -.2), (3.1, 3.5, 1), "pet_root", (0, 0, 15)),
                _cube("wing_left", (-5.5 * scale, .5, -1), (-2.1, 2.7, .5), "pet_left_wing", (0, 0, -20)),
                _cube("wing_right", (2.1, .5, -1), (5.5 * scale, 2.7, .5), "pet_right_wing", (0, 0, 20)),
                _cube("tail", (-1.2, -2.5, -4), (1.2, -1, -1.4), "pet_tail", (0, 0, 10))]
    if family == "turtle":
        return [_cube("shell", (-3.1 * scale, -.4, -2.8), (3.1 * scale, 2.7, 2.8), "pet_root"),
                _cube("shell_ridge", (-1.4, 2.5, -1.1), (1.4, 3.4, 1.1), "pet_root"),
                _cube("head", (-1.4, -.8, 2.3), (1.4, 1.2, 4), "pet_root"),
                *[_cube(f"foot_{i}", (x, -2.2, z), (x + 1.3, -.8, z + 1.3), "pet_root") for i, (x, z) in enumerate(((-3, -2), (2, -2), (-3, 1), (2, 1)))]]
    if family == "bot":
        return [_cube("body", (-2.8, -2.8, -2.4), (2.8, 2.8, 2.4), "pet_root"),
                _cube("screen", (-1.8, .2, 2.4), (1.8, 1.8, 3.0), "pet_root"),
                _cube("antenna", (-.4, 2.8, 0), (.4, 4.4, .6), "pet_root"),
                _cube("leaf_left", (-3.8, 1.5, -.5), (-1, 3, .6), "pet_root", (0, 0, -20)),
                _cube("leaf_right", (1, 1.5, -.5), (3.8, 3, .6), "pet_root", (0, 0, 20)),
                _cube("leg_left", (-2.2, -4, -.5), (-.8, -2.7, .8), "pet_root"),
                _cube("leg_right", (.8, -4, -.5), (2.2, -2.7, .8), "pet_root")]
    # Cloudfox cub: ears, muzzle, and a multi-segment tail produce a different silhouette.
    return [_cube("body", (-2.8, -2.4, -2.5), (2.8, 2.1, 2.5), "pet_root"),
            _cube("muzzle", (-1.4, -1.5, 2.2), (1.4, .2, 3.8), "pet_root"),
            _cube("ear_left", (-3.5, 1.8, .2), (-2.0, 4.2, 1.1), "pet_root", (0, 0, -12)),
            _cube("ear_right", (2.0, 1.8, .2), (3.5, 4.2, 1.1), "pet_root", (0, 0, 12)),
            _cube("tail_base", (-1.0, -1.4, -4.4), (1.0, 1.3, -2.4), "pet_tail"),
            _cube("tail_tip", (-1.6, -.7, -6.4), (1.6, .8, -4.3), "pet_tail", (0, 0, -12))]


def _emote_parts(family: str, variant: int) -> list[dict[str, Any]]:
    bone = "body" if family in ("bow", "drum", "clap", "twirl") else "right_arm"
    s = 1.0 + variant * .08
    if family == "greeting":
        return [_cube("paper_stick", (-.35, -3, 2), (.35, 3.3 * s, 2.7), bone),
                _cube("folded_pennant", (-2.8 * s, 1.0, 2), (2.8 * s, 4.5 * s, 2.5), bone),
                _cube("pennant_fold", (-2.8 * s, .3, 2), (0, 1.2, 2.6), bone)]
    if family == "victory":
        return [_cube("victory_staff", (-.4, -3.5, 1.8), (.4, 4.0 * s, 2.5), bone),
                _cube("victory_banner", (-3.2 * s, 1.2, 2), (3.2 * s, 4.6 * s, 2.5), bone),
                _cube("victory_star_h", (-1.2, 2.1, 2.6), (1.2, 2.8, 3.2), bone),
                _cube("victory_star_v", (-.4, 1.5, 2.6), (.4, 3.4, 3.2), bone)]
    if family == "cheer":
        return [_cube("wand", (-.25, -3.5, 2), (.25, 2.7 * s, 2.7), bone),
                _cube("star_horizontal", (-2.0 * s, 1.8, 2.3), (2.0 * s, 2.7, 3.1), bone),
                _cube("star_vertical", (-.55, 1.0, 2.2), (.55, 3.5 * s, 3.2), bone)]
    if family == "bow":
        return [_cube("bouquet_stem", (-.45, -2.7, 2), (.45, 1.4, 2.8), bone),
                *[_cube(f"flower_{i}", (x - .7, y - .7, 2.2), (x + .7, y + .7, 3.1), bone)
                  for i, (x, y) in enumerate(((-1.1, 2), (0, 2.8), (1.1, 2)))],
                _cube("ribbon", (-1.2, -1.5, 2), (1.2, -.5, 3), bone)]
    if family == "drum":
        return [_cube("drum_body", (-2.6 * s, -2.6, 1.5), (2.6 * s, 2.6, 4.0), bone),
                _cube("drum_head", (-2.8 * s, 2.2, 1.6), (2.8 * s, 2.9, 4.1), bone),
                _cube("stick_left", (-3.2, 2.8, 1.0), (-2.7, 5.5 * s, 1.4), bone),
                _cube("stick_right", (2.7, 2.8, 1.0), (3.2, 5.5 * s, 1.4), bone)]
    if family == "dance":
        return [_cube("ribbon_staff", (-.3, -3.5, 2), (.3, 3.5 * s, 2.5), bone),
                _cube("ribbon_left", (-3.5 * s, 1, 2), (-.4, 2.1, 2.6), bone, (0, 0, -18)),
                _cube("ribbon_right", (.4, 1, 2), (3.5 * s, 2.1, 2.6), bone, (0, 0, 18)),
                _cube("ribbon_tip", (-1, 3, 2), (1, 4.1, 2.6), bone)]
    if family == "salute":
        return [_cube("badge_plate", (-2.3, -1.8, 2), (2.3, 2.7 * s, 3), bone),
                _cube("badge_star", (-1.0, .2, 3), (1.0, 2.2, 3.8), bone),
                _cube("badge_pin", (-.3, -2.4, 3), (.3, -.2, 3.7), bone)]
    if family == "clap":
        return [_cube("spark_left", (-3.2 * s, -1.5, 2), (-1.5, 1.5, 3), bone),
                _cube("spark_right", (1.5, -1.5, 2), (3.2 * s, 1.5, 3), bone),
                _cube("spark_up", (-.6, 1.8, 2), (.6, 4.0 * s, 3), bone),
                _cube("spark_cross", (-2.0, .5, 2), (2.0, 1.2, 3), bone)]
    # Twirl prop: two crossed ribbons, each a distinct strip with a varied pivot/angle.
    return [_cube("twirl_core", (-1.3, -1.3, 2), (1.3, 1.3, 3), bone),
            _cube("twirl_ribbon_a", (-4.0 * s, -.5, 2), (4.0 * s, .5, 2.7), bone, (0, 0, 25)),
            _cube("twirl_ribbon_b", (-.5, -4.0 * s, 2), (.5, 4.0 * s, 2.7), bone, (0, 0, -25))]


def _aura_parts(family: str, variant: int) -> list[dict[str, Any]]:
    w = 4.0 + variant * .28
    y = 0.5 + (variant % 3) * .4
    if family == "snow":
        arm = 1.6 + variant * .12
        return [_cube("flake_vertical", (-.45, y - arm, 1.8), (.45, y + arm, 2.7), "aura_orbit"),
                _cube("flake_horizontal", (-arm, y - .45, 1.8), (arm, y + .45, 2.7), "aura_orbit"),
                _cube("flake_diagonal_left", (-arm, y - arm, 2.0), (arm, y + arm, 2.5), "aura_orbit", (0, 0, 45)),
                _cube("flake_diagonal_right", (-arm, y - arm, 2.0), (arm, y + arm, 2.5), "aura_orbit", (0, 0, -45)),
                _cube("flake_center", (-.6, y - .6, 2.8), (.6, y + .6, 3.5), "aura_orbit")]
    if family in ("monsoon", "rune"):
        count = 4 + variant % 2
        pieces = []
        for i in range(count):
            a = 360 * i / count
            x = math.sin(math.radians(a)) * w
            z = math.cos(math.radians(a)) * w
            pieces.append(_cube(f"orbit_{i}", (x - .55, y - .7, z - .35), (x + .55, y + .7, z + .35), "aura_orbit", (0, a, 0)))
        if family == "rune":
            pieces.append(_cube("rune_core", (-.55, y - .7, -w), (.55, y + .7, -w + .5), "aura_orbit"))
        return pieces
    if family == "aurora":
        return [_cube("arc_left", (-w, 1, -2), (-w + .8, 5.5 + y, 2), "aura_orbit", (0, 0, -12)),
                _cube("arc_center", (-w + 1, 4.8 + y, -2), (w - 1, 5.6 + y, 2), "aura_orbit"),
                _cube("arc_right", (w - .8, 1, -2), (w, 5.5 + y, 2), "aura_orbit", (0, 0, 12)),
                _cube("spark_center", (-.6, 5.4 + y, 1.8), (.6, 6.6 + y, 2.4), "aura_orbit")]
    if family == "spectrum":
        return [_cube("spectrum_left", (-w, .4, -2), (-w + .6, 4.8 + y, 2), "aura_orbit", (0, 0, -18)),
                _cube("spectrum_inner_left", (-w + 1.2, 1.4, -1.5), (-w + 1.8, 5.7 + y, 1.5), "aura_orbit", (0, 0, -10)),
                _cube("spectrum_crown", (-w + 1.5, 5.6 + y, -1.5), (w - 1.5, 6.2 + y, 1.5), "aura_orbit"),
                _cube("spectrum_inner_right", (w - 1.8, 1.4, -1.5), (w - 1.2, 5.7 + y, 1.5), "aura_orbit", (0, 0, 10)),
                _cube("spectrum_right", (w - .6, .4, -2), (w, 4.8 + y, 2), "aura_orbit", (0, 0, 18)),
                _cube("prism_core", (-.7, 5.3 + y, 1.5), (.7, 6.7 + y, 2.4), "aura_orbit")]
    if family == "ember":
        return [_cube("rising_left", (-w, -1, 2), (-w + 1, 1.2 + y, 3), "aura_orbit", (0, 0, -15)),
                _cube("rising_center", (-.7, 1 + y, 2), (.7, 3.3 + y, 3), "aura_orbit"),
                _cube("rising_right", (w - 1, -.5, 2), (w, 1.8 + y, 3), "aura_orbit", (0, 0, 15)),
                _cube("petal_top", (-1, 3 + y, 2), (1, 4.2 + y, 3), "aura_orbit")]
    if family == "leaf":
        return [_cube("leaf_left", (-w, -.6, 2), (-w + 1.4, 1.6 + y, 3), "aura_orbit", (0, 0, -28)),
                _cube("leaf_center", (-.8, 1 + y, 2), (.8, 3.1 + y, 3), "aura_orbit", (0, 0, 18)),
                _cube("leaf_right", (w - 1.4, -.6, 2), (w, 1.6 + y, 3), "aura_orbit", (0, 0, 28)),
                _cube("twig_crossbar", (-w + 1, 1.2, 1.7), (w - 1, 1.7, 2.5), "aura_orbit"),
                _cube("leaf_tip", (-.5, 3 + y, 2), (.5, 4.5 + y, 2.8), "aura_orbit")]
    if family == "vortex":
        return [_cube("vortex_low", (-w, -1, -1), (w, -.4, 1), "aura_orbit", (0, 0, 18)),
                _cube("vortex_mid", (-w + 1, 1 + y, -1), (w - 1, 1.6 + y, 1), "aura_orbit", (0, 0, -18)),
                _cube("vortex_high", (-w + 2, 3 + y, -1), (w - 2, 3.6 + y, 1), "aura_orbit", (0, 0, 18)),
                _cube("vortex_core", (-.6, 1 + y, 1.5), (.6, 2.3 + y, 2.2), "aura_orbit")]
    # Star trail: offset four-point stars around the hips and upper back.
    return [_cube("star_left_h", (-w - 1, 1, 1.5), (-w + 1, 1.8, 2.4), "aura_orbit"),
            _cube("star_left_v", (-w - .4, .4, 1.5), (-w + .4, 2.4, 2.4), "aura_orbit"),
            _cube("star_right_h", (w - 1, -2, 1.5), (w + 1, -1.2, 2.4), "aura_orbit"),
            _cube("star_right_v", (w - .4, -2.6, 1.5), (w + .4, -.6, 2.4), "aura_orbit")]


def generated_parts(shape: str) -> list[dict[str, Any]]:
    """Build a family-specific, variant-scaled model for each extension row."""
    fields = shape.split("_")
    if len(fields) != 4 or fields[0] != "full" or fields[1] not in FAMILIES:
        raise ValueError(f"invalid full-catalog shape recipe: {shape}")
    category, family = fields[1], fields[2]
    variant = int(fields[3])
    if not 0 <= variant < 5 or family not in {r[0] for r in FAMILIES[category]}:
        raise ValueError(f"unknown full-catalog family: {shape}")
    parts = {
        "hat": _hat_parts,
        "suit": _suit_parts,
        "back": _back_parts,
        "pet": _pet_parts,
        "emote": _emote_parts,
        "aura": _aura_parts,
    }[category](family, variant)
    # The full 128px UV atlas is split into 64 16px cells; six cube faces per element
    # need at least eleven cubes to provide complete explicit UV coverage. Fill remaining
    # cells with small, visible rivets/eyes/fasteners appropriate to the attachment.
    required_cubes = 11 if category in ("suit", "back", "pet") else 0
    while len(parts) < required_cubes:
        index = len(parts)
        x = -3.5 + (index % 4) * 2.0
        y = 1.8 - (index // 4) * 1.4
        if category == "suit":
            parts.append(_cube(f"suit_rivet_{index}", (x, y, 4.05 + index * .015),
                               (x + .45, y + .5, 4.35 + index * .015), "body"))
        elif category == "back":
            parts.append(_cube(f"back_fastener_{index}", (x, y, -5.9 - index * .015),
                               (x + .5, y + .45, -5.6 - index * .015), "body"))
        else:
            eye_x = -1.5 if index % 2 == 0 else 1.0
            eye_y = .35 if index % 3 else -.4
            z = 3.05 + index * .015
            parts.append(_cube(f"pet_face_detail_{index}", (eye_x, eye_y, z),
                               (eye_x + .45, eye_y + .45, z + .25), "pet_root"))
    family_data = next(row for row in FAMILIES[category] if row[0] == family)
    detail_bone = family_data[5]
    detail_x = -2.0 + variant * .85
    detail_size = .45 + variant * .08
    if category == "hat":
        detail_from, detail_to = (detail_x, 2.4, 3.95), (detail_x + detail_size, 2.9 + detail_size, 4.45)
    elif category == "suit":
        detail_from, detail_to = (detail_x, 3.1, 4.25), (detail_x + detail_size, 3.1 + detail_size, 4.65)
    elif category == "back":
        detail_from, detail_to = (detail_x, 3.0, -6.45), (detail_x + detail_size, 3.0 + detail_size, -6.05)
    elif category == "pet":
        detail_from, detail_to = (detail_x * .55, 1.8, 3.15), (detail_x * .55 + detail_size, 2.2 + detail_size, 3.6)
    elif category == "emote":
        detail_from, detail_to = (detail_x * .5, 3.3, 3.0), (detail_x * .5 + detail_size, 3.7 + detail_size, 3.5)
    else:
        detail_from, detail_to = (detail_x, 2.0 + variant * .2, 3.15), (detail_x + detail_size, 2.5 + variant * .2, 3.65)
    parts.append(_cube(f"theme_detail_{variant}", detail_from, detail_to, detail_bone))
    return parts


def all_rows() -> list[tuple[Any, ...]]:
    return list(base.ROWS) + extra_rows()
