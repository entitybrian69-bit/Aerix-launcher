#!/usr/bin/env python3
"""Per-category garment art for Trinkies cosmetics.

Every item ships a 64x64 patch laid out in the Minecraft *player skin* format. The patch is what
actually reaches the game: the launcher composites it onto the account skin and the in-game mod
hot-swaps the result, so the pixels authored here are the pixels rendered on the character. The
preview render therefore textures a real player model with `base skin + patch`, which is why a suit
shows sleeves, a collar and boots, and a hat sits on the head instead of floating as a blob.

`attachments` add the geometry a flat texture cannot express - horns, wings, a shoulder pet, a prop
held in hand, aura motes - and are what the card art shows around the character.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Any, Callable, Sequence

import numpy as np

from . import mc_skin_render as sk
from .mc_skin_render import Attachment, Box, Patch

Color = tuple[int, int, int]


@dataclass
class Palette:
    """Seven-stop ramp derived from the item's base/accent colours, shared by patch and geometry."""

    shade0: Color
    shade1: Color
    base: Color
    light1: Color
    light2: Color
    accent: Color
    accent_light: Color
    trim: Color

    @classmethod
    def from_hex(cls, palette: Sequence[str], emissive: bool = False) -> "Palette":
        colors = [sk.hex_rgb(value) for value in palette]
        if not colors:
            colors = [(96, 108, 122)]
        if len(colors) < 6:
            # A two-stop palette is base + accent; derive the ramp between them.
            base = colors[0]
            accent = colors[-1] if len(colors) > 1 else sk.mix_rgb(base, (255, 255, 255), 0.5)
            colors = [sk.scale_rgb(base, 0.48), sk.scale_rgb(base, 0.7), base,
                      sk.mix_rgb(base, accent, 0.4), sk.mix_rgb(base, (255, 255, 255), 0.3), accent]
        while len(colors) < 7:
            colors.append(sk.scale_rgb(colors[2], 1.18))
        shade0, shade1, base, light1, light2, accent = colors[0], colors[1], colors[2], colors[3], colors[4], colors[5]
        accent_light = colors[6] if len(colors) > 6 else sk.mix_rgb(accent, (255, 255, 255), 0.45)
        trim = sk.mix_rgb(base, accent, 0.35)
        if emissive:
            base = sk.mix_rgb(base, accent_light, 0.18)
            light1 = sk.mix_rgb(light1, accent_light, 0.25)
        return cls(shade0, shade1, base, light1, light2, accent, accent_light, trim)

    def dark(self, amount: float = 0.72) -> Callable[[Color], Color]:
        return lambda color: sk.scale_rgb(color, amount)

    def lightened(self, color: Color, amount: float = 0.3) -> Color:
        return sk.mix_rgb(color, (255, 255, 255), amount)

    def shadowed(self, color: Color, amount: float = 0.3) -> Color:
        return sk.mix_rgb(color, (8, 10, 14), amount)


@dataclass
class ItemArt:
    patch: np.ndarray
    cape: np.ndarray | None = None
    attachments: list[Attachment] = field(default_factory=list)
    pose: dict[str, tuple[float, float, float]] = field(default_factory=dict)
    frames: list[np.ndarray] = field(default_factory=list)
    silhouette: tuple[int, int, int, int] = (10, 12, 16, 205)

    @property
    def has_cape(self) -> bool:
        return self.cape is not None and bool(self.cape[..., 3].any())


def box(origin: Sequence[float], size: Sequence[float], *, color: Color | None = None,
        uv_part: str | None = None, alpha: int = 255, inflate: float = 0.0,
        glow: float = 0.0) -> Box:
    """`glow` brightens a solid attachment toward its own light, standing in for an emissive pass."""
    if color is not None and glow > 0.0:
        color = sk.mix_rgb(color, sk.scale_rgb(color, 1.9), min(1.0, glow))
        color = tuple(min(255, int(round(c))) for c in color)  # type: ignore[assignment]
    return Box(origin=tuple(float(v) for v in origin), size=tuple(float(v) for v in size),
               uv_part=uv_part, color=color, alpha=alpha, inflate=inflate)


def attach(placement: str, origin: Sequence[float], size: Sequence[float], color: Color | None,
            *, uv_part: str | None = None, rotation: Sequence[float] = (0.0, 0.0, 0.0),
            pivot: Sequence[float] = (0.0, 0.0, 0.0), translate: Sequence[float] = (0.0, 0.0, 0.0),
            alpha: int = 255) -> Attachment:
    return Attachment(box=box(origin, size, color=color, uv_part=uv_part, alpha=alpha),
                      layer=placement, rotation=tuple(float(v) for v in rotation),
                      pivot=tuple(float(v) for v in pivot), translate=tuple(float(v) for v in translate))


def mirrored(placement_left: str, placement_right: str, origin: Sequence[float], size: Sequence[float],
             color: Color, **kwargs: Any) -> list[Attachment]:
    """Add the same cube on both arms/legs; the x axis is mirrored for the other side."""
    left = attach(placement_left, origin, size, color, **kwargs)
    x0, y0, z0 = origin
    w, h, d = size
    right_origin = (-x0 - w, y0, z0)
    right = attach(placement_right, right_origin, size, color, **kwargs)
    return [left, right]


# ---------------------------------------------------------------------------
# Hats
# ---------------------------------------------------------------------------
# Which texel rows of a face a garment covers is the whole craft of a skin: the hat layer sits half
# a pixel outside the head, so covering the front face's eye rows would hide the player's face.
def paint_hat(patch: Patch, pal: Palette, family: str, variant: int) -> None:
    base = pal.base
    dark = pal.shadowed(base, 0.4)
    lite = pal.lightened(base, 0.22)
    accent = pal.accent
    glow = pal.accent_light
    band_top, band_bottom = 0, 8
    rows = {
        "spire": 3, "hood": 6, "halo": 1, "aviator": 5, "circlet": 2, "visor": 5,
        "antlers": 2, "wreath": 2, "mask": 1,
    }.get(family, 3)
    cover_crown = family not in ("halo", "antlers", "wreath")

    if cover_crown:
        patch.face("hat", "top", base)
        patch.face("hat", "bottom", dark)
    for name in sk.SIDE_FACES:
        u, v, w, h = patch.face_rect("hat", name)
        patch.rect(u, v, w, h, dark)                        # default: shadowed, so nothing is bare
        for row in range(rows):
            color = base if row % 2 == 0 or family not in ("aviator", "visor") else lite
            patch.cell("hat", name, 0, row, color, cw=w)
        if family in ("hood", "visor"):                     # these wrap further down the sides
            for row in range(rows, 6):
                patch.cell("hat", name, 0, row, base, cw=w)
            for row in range(6, 8):
                patch.cell("hat", name, 0, row, dark, cw=w)
        if family == "mask":
            for row in range(3, 7):
                patch.cell("hat", name, 0, row, base, cw=w)
    # The front face keeps the eyes clear for every family except the mask.
    fw = patch.face_rect("hat", "front")[2]
    if family == "mask":
        patch.face("hat", "front", base)
        for x in (1, 2, 5, 6):
            patch.cell("hat", "front", x, 3, dark)
            patch.cell("hat", "front", x, 4, sk.mix_rgb(glow, dark, 0.35))
        patch.cell("hat", "front", 3, 6, accent, cw=2)
        patch.outline("hat", "front", dark)
    else:
        # Only the brow rows of the hat layer may cover the face, or the player loses their eyes.
        for row in range(min(rows, 2)):
            patch.cell("hat", "front", 0, row, base if row else lite, cw=fw)
        if family in ("aviator", "hood"):
            for x in (0, 1, fw - 2, fw - 1):
                patch.cell("hat", "front", x, 2, base)
                patch.cell("hat", "front", x, 3, sk.mix_rgb(base, dark, 0.4))
    # Family trim.
    if family == "aviator":                                 # goggles across the brow
        for x, colour in ((1, accent), (2, glow), (5, accent), (6, glow)):
            patch.cell("hat", "front", x, 2, colour)
            patch.cell("hat", "front", x, 3, sk.mix_rgb(accent, dark, 0.5))
        patch.cell("hat", "front", 0, 4, dark, cw=fw)
        for name in ("left", "right"):
            patch.cell("hat", name, 0, 3, accent, cw=2, ch=2)
            patch.cell("hat", name, 0, 5, dark, cw=2)
    elif family == "visor":
        patch.cell("hat", "front", 0, 3, sk.mix_rgb(glow, base, 0.35), cw=fw, ch=1)
        patch.cell("hat", "front", 0, 4, (glow[0], glow[1], glow[2], 190), cw=fw)
        for name in sk.SIDE_FACES:
            u, v, w, h = patch.face_rect("hat", name)
            patch.rect(u, v + 4, w, 1, lite)
    elif family == "circlet" or family == "wreath":
        for name in sk.SIDE_FACES:
            u, v, w, h = patch.face_rect("hat", name)
            patch.rect(u, v + 1, w, 1, accent)
            for gem_x in range(0, w, 2 + (variant % 3)):
                patch.rect(u + gem_x, v, 1, 1, glow)
    elif family == "hood":
        patch.cell("hat", "front", 0, 0, lite, cw=fw)
        for x in (0, fw - 1):
            for row in range(1, 6):
                patch.cell("hat", "front", x, row, dark)
        for name in ("left", "right", "back"):
            u, v, w, h = patch.face_rect("hat", name)
            patch.rect(u, v + 7, w, 1, dark)
        # The hood drapes onto the shoulders.
        for name in ("front", "left", "right", "back"):
            u, v, w, h = patch.face_rect("jacket", name)
            patch.rect(u, v, w, 2, base)
            patch.rect(u, v + 2, w, 1, dark)
    elif family == "spire":
        for name in sk.SIDE_FACES:
            u, v, w, h = patch.face_rect("hat", name)
            patch.rect(u, v + 2, w, 1, accent)
            for ridge in range(1, w, 2):
                patch.rect(u + ridge, v, 1, 2, lite)
    elif family == "antlers":
        patch.band("hat", 0, accent, height=1)
        patch.band("hat", 1, base, height=1)
    elif family == "halo":
        patch.band("hat", 1, sk.mix_rgb(accent, base, 0.5), height=1)


def hat_attachments(family: str, pal: Palette, variant: int) -> list[Attachment]:
    base, dark, lite = pal.base, pal.shadowed(pal.base, 0.55), pal.lightened(pal.base, 0.25)
    accent, glow = pal.accent, pal.accent_light
    out: list[Attachment] = []
    if family == "spire":
        height = 3.0 + (variant % 3) * 0.8
        out.append(attach("hat", (-1.6, 8.0, -1.6), (3.2, height, 3.2), lite))
        out.append(attach("hat", (-0.9, 8.0 + height, -0.9), (1.8, 2.2, 1.8), accent))
        for x in (-3.4, 3.4):
            out.append(attach("hat", (x - 0.7, 7.2, -0.7), (1.4, 1.6, 1.4), base))
    elif family == "hood":
        out.append(attach("hat", (-4.6, 3.0, -4.8), (9.2, 5.4, 1.4), base))       # cowl behind
        out.append(attach("hat", (-4.6, 0.2, -4.9), (9.2, 3.0, 1.2), dark))
        for x in (-4.7, 3.5):
            out.append(attach("hat", (x, 0.4, -1.4), (1.2, 4.6, 3.0), base))      # side flaps
    elif family == "halo":
        radius = 5.4 + (variant % 3) * 0.4
        for index in range(10):
            angle = index * (2 * math.pi / 10)
            x = math.sin(angle) * radius
            z = math.cos(angle) * radius
            out.append(Attachment(box=box((x - 0.75, 0.0, z - 0.75), (1.5, 0.85, 1.5), color=accent),
                                  layer="hat", translate=(0.0, 11.2, 0.0),
                                  rotation=(0.0, math.degrees(angle), 0.0)))
        out.append(attach("hat", (-1.1, 10.2, 3.6), (2.2, 2.2, 2.2), glow))        # lantern
    elif family == "aviator":
        out.append(attach("hat", (-5.2, 5.4, 2.2), (10.4, 1.1, 2.6), dark))        # peak
        for x in (-4.9, 3.7):
            out.append(attach("hat", (x, 2.0, -1.6), (1.2, 3.4, 3.2), sk.mix_rgb(base, dark, 0.3)))
        out.append(attach("hat", (-2.6, 5.9, -2.6), (5.2, 1.0, 5.2), lite))
    elif family == "circlet":
        out.append(attach("hat", (-1.3, 8.2, 2.9), (2.6, 2.4, 1.0), glow))
        for x in (-5.0, 3.9):
            out.append(attach("hat", (x, 6.2, -1.0), (1.1, 3.2, 2.2), accent))
    elif family == "visor":
        out.append(attach("hat", (-5.4, 4.6, 2.4), (10.8, 2.4, 1.4),
                          sk.mix_rgb(glow, base, 0.25), alpha=210))
        out.append(attach("hat", (-5.0, 8.0, -3.0), (10.0, 1.4, 6.0), lite))
        for x in (-5.4, 4.0):
            out.append(attach("hat", (x, 1.4, -1.0), (1.4, 4.4, 3.2), base))
    elif family == "antlers":
        for side, sign in (("left", -1.0), ("right", 1.0)):
            trunk_x = sign * 3.4
            out.append(Attachment(box=box((trunk_x - 0.6, 8.0, -0.6), (1.2, 4.6 + variant % 3, 1.2),
                                          color=base), layer="hat",
                                  rotation=(0.0, 0.0, sign * -16.0), translate=(0.0, 0.6, 0.0)))
            out.append(Attachment(box=box((trunk_x + sign * 1.4, 11.0, -0.5), (1.0, 2.4, 1.0),
                                          color=lite), layer="hat",
                                  rotation=(0.0, 0.0, sign * -38.0)))
            out.append(Attachment(box=box((trunk_x - sign * 0.4, 9.6, 1.2), (1.0, 1.0, 2.0), color=accent),
                                  layer="hat"))
    elif family == "wreath":
        for index, angle in enumerate((-0.9, -0.45, 0.0, 0.45, 0.9)):
            x = math.sin(angle) * 4.6
            z = math.cos(angle) * 4.6
            color = accent if index % 2 == 0 else glow
            out.append(attach("hat", (x - 0.8, 7.4 + (index % 2) * 0.6, z - 0.8), (1.6, 1.2, 1.6), color))
    elif family == "mask":
        out.append(attach("hat", (-1.0, 5.2, 4.1), (2.0, 3.0, 0.9), dark))
        for x in (-4.9, 3.6):
            out.append(attach("hat", (x, 5.0, 1.4), (1.3, 3.6, 2.4), base))
    return out


# ---------------------------------------------------------------------------
# Suits
# ---------------------------------------------------------------------------
def paint_suit(patch: Patch, pal: Palette, family: str, variant: int) -> None:
    """Dress every limb: the inner blocks carry the fabric, the overlay blocks the plating."""
    base, dark, lite = pal.base, pal.shadowed(pal.base, 0.58), pal.lightened(pal.base, 0.2)
    plate, accent, glow = pal.trim, pal.accent, pal.accent_light
    shade0 = pal.shadowed(base, 0.42)

    # --- torso: fabric on the body layer, plating and trim on the jacket overlay ---------------
    for name in sk.SIDE_FACES:
        u, v, w, h = patch.face_rect("body", name)
        patch.rect(u, v, w, h, base)
        patch.rect(u, v, w, 1, lite)
        patch.rect(u, v + h - 3, w, 3, shade0)                       # hem / waist
    patch.face("body", "top", lite)
    patch.face("body", "bottom", shade0)
    # jacket overlay: collar, lapels, belt, hem flare
    for name in sk.SIDE_FACES:
        u, v, w, h = patch.face_rect("jacket", name)
        patch.rect(u, v, w, 2, plate)
        patch.rect(u, v + h - 4, w, 2, dark)
        patch.rect(u, v + h - 5, w, 1, accent)
        patch.rect(u, v, 1, h, sk.mix_rgb(base, dark, 0.35))          # side seams
        patch.rect(u + w - 1, v, 1, h, sk.mix_rgb(base, dark, 0.35))
    fu, fv, fw, fh = patch.face_rect("jacket", "front")
    patch.rect(fu + fw // 2 - 1, fv + 2, 2, fh - 6, dark)             # placket
    for row in range(3, fh - 3, 3):
        patch.rect(fu + fw // 2 - 1, fv + row, 2, 1, glow)            # buttons
    patch.rect(fu, fv + fh - 6, fw, 2, sk.mix_rgb(accent, dark, 0.25))  # belt
    patch.rect(fu + fw // 2 - 1, fv + fh - 6, 3, 2, glow)             # buckle
    bu, bv, bw, bh = patch.face_rect("jacket", "back")
    patch.rect(bu + 1, bv + 3, bw - 2, bh - 8, sk.mix_rgb(plate, dark, 0.3))  # back plate
    patch.rect(bu + 2, bv + 4, bw - 4, 2, lite)

    # --- sleeves, gloves -------------------------------------------------------------------------
    for part, other in (("right_arm", "left_arm"), ("left_arm", "right_arm")):
        for name in sk.SIDE_FACES:
            u, v, w, h = patch.face_rect(part, name)
            patch.rect(u, v, w, h, base)
            patch.rect(u, v + 6, w, 1, plate)
            patch.rect(u, v + 7, w, h - 9, sk.mix_rgb(base, lite, 0.4))
            patch.rect(u, v + h - 2, w, 2, accent)                     # glove cuff
        patch.face(part, "top", lite)
        for name in sk.SIDE_FACES:
            u, v, w, h = patch.face_rect(other + "_hat", name)
        for hat in (part + "_hat",):
            for name in sk.SIDE_FACES:
                u, v, w, h = patch.face_rect(hat, name)
                patch.rect(u, v, w, 3, plate)                           # shoulder cap
                patch.rect(u, v + 3, w, 1, lite)
                patch.rect(u, v + h - 3, w, 3, accent)                 # gauntlet
            patch.face(hat, "top", lite)

    # --- trousers, knee plates, boots --------------------------------------------------------------
    for part in ("right_leg", "left_leg"):
        for name in sk.SIDE_FACES:
            u, v, w, h = patch.face_rect(part, name)
            patch.rect(u, v, w, h, sk.mix_rgb(base, dark, 0.22))
            patch.rect(u, v + h - 4, w, 4, dark)                        # boot
            patch.rect(u, v + h - 5, w, 1, accent)
        patch.face(part, "bottom", shade0)
        for name in sk.SIDE_FACES:
            u, v, w, h = patch.face_rect(part + "_hat", name)
            patch.rect(u + 1, v + 3, max(1, w - 2), 3, plate)          # knee plate
            patch.rect(u + 1, v + 4, max(1, w - 2), 1, lite)
            patch.rect(u, v + 1, w, 1, sk.mix_rgb(base, lite, 0.5))     # belt loop
    # A suit keeps the head bare; only the collar of some families reaches the neck.
    if family in ("stargazer", "samurai", "sentinel"):
        for name in sk.SIDE_FACES:
            u, v, w, h = patch.face_rect("hat", name)
            patch.rect(u, v + 5, w, 3, plate)
            patch.rect(u, v + 5, w, 1, lite)

    # Family-specific hardware. Every mark is face-relative: a bake that assumed absolute UV
    # coordinates would silently land on the wrong limb the moment the layout changed.
    if family == "diver":
        for name in ("right_arm", "left_arm"):
            patch.band(name, 2, glow, height=1)                       # rebreather charge line
        patch.band("jacket", 4, plate, height=2)                       # weight belt
        patch.band("jacket", -1, accent, height=1)
    elif family == "wayfinder":
        u, v, w, h = patch.face_rect("jacket", "front")
        patch.rect(u + 1, v + 5, 3, 3, sk.mix_rgb(lite, base, 0.4))    # chart pocket
        patch.band("jacket", 3, dark, height=1)                        # baldric
    elif family == "cartographer":
        u, v, w, h = patch.face_rect("jacket", "back")
        patch.rect(u + 2, v + 1, 4, 3, glow)                           # folded map glow
        for x in (1, 5):
            patch.rect(u + x, v + 5, 2, 4, accent)                     # satchel clasps
    elif family == "stargazer":
        for index, (x, y) in enumerate(((1, 2), (4, 4), (6, 1), (2, 7), (5, 9), (3, 5))):
            for name in ("front", "back"):
                u, v, w, h = patch.face_rect("jacket", name)
                patch.rect(u + x, v + y, 1, 1, glow if index % 2 == 0 else lite)
        patch.band("jacket", -1, accent, height=1)
    elif family == "samurai":
        for row in range(3):                                           # laminar cuirass
            patch.band("jacket", 4 + row * 2, lite if row % 2 == 0 else accent, height=1)
    elif family == "ranger":
        patch.band("jacket", 0, sk.mix_rgb(base, (40, 60, 30), 0.55), height=2)   # mossy yoke
        for name in sk.SIDE_FACES:
            u, v, w, h = patch.face_rect("jacket", name)
            for x in (1, 3, 5):
                patch.rect(u + x, v + 3, 1, 6, accent)                # lacing
    elif family == "alchemist":
        u, v, w, h = patch.face_rect("jacket", "front")
        patch.rect(u + 2, v + 2, 4, 7, sk.mix_rgb(lite, (230, 220, 190), 0.5))    # apron
        patch.band("jacket", 8, accent, height=1)                                  # tool belt
    elif family == "sentinel":
        for row in range(0, 10, 2):
            patch.band("jacket", row, lite, height=1)
        u, v, w, h = patch.face_rect("jacket", "front")
        patch.rect(u + 3, v + 3, 2, 2, glow)                           # core
    else:
        patch.band("jacket", -1, accent, height=1)


def suit_attachments(family: str, pal: Palette, variant: int) -> list[Attachment]:
    base, dark, lite = pal.base, pal.shadowed(pal.base, 0.55), pal.lightened(pal.base, 0.22)
    accent, glow, plate = pal.accent, pal.accent_light, pal.trim
    out: list[Attachment] = []
    if family == "diver":
        for x in (-3.4, 2.2):
            out.append(attach("jacket", (x, 1.2, -3.6), (1.2, 4.6, 1.0), plate))
        out.append(attach("jacket", (-1.0, 4.4, -3.4), (2.0, 1.2, 0.8), accent))
        for x in (-6.0, 4.6):
            out.append(attach("right_arm" if x < 0 else "left_arm", (-2.4, 8.2, -2.4), (1.4, 1.2, 1.4), glow))
    elif family == "wayfinder":
        out.append(attach("jacket", (-3.6, 2.2, -3.8), (2.6, 3.4, 1.0), sk.mix_rgb(lite, base, 0.4)))
        out.append(attach("jacket", (-2.2, -0.5, 3.2), (4.4, 4.6, 0.8), dark))      # coat tail
        for x in (-4.8, 4.0):
            out.append(attach("jacket", (x, 4.2, -1.4), (0.9, 2.2, 2.8), accent))
    elif family == "cartographer":
        out.append(attach("jacket", (-1.6, 2.6, 3.4), (3.2, 2.6, 0.9), glow))
        for x in (-5.4, 4.2):
            out.append(attach("jacket", (x, 1.0, -1.6), (1.2, 2.8, 1.2), accent))
    elif family == "stargazer":
        out.append(attach("jacket", (-4.4, -1.0, -3.6), (8.8, 6.0, 0.8), sk.mix_rgb(base, dark, 0.4)))
        for index in range(3):
            out.append(attach("jacket", (-2.4 + index * 2.2, 1.4 + (index % 2) * 1.6, -4.4),
                              (0.9, 0.9, 0.7), glow))
    elif family == "mechanist":
        out.append(attach("jacket", (-4.2, 1.0, 3.2), (8.4, 1.4, 0.9), accent))
        for x, w in ((-6.0, 1.6), (4.4, 1.6)):
            out.append(attach("jacket", (x, -1.0, -2.4), (w, 3.4, 2.4), plate))
        out.append(attach("jacket", (-1.0, -2.2, 3.6), (2.0, 2.0, 0.8), glow))
    elif family == "samurai":
        for row in range(3):
            out.append(attach("jacket", (-5.0 + row * 0.2, -2.0 - row * 1.2, 3.0 - row * 0.2),
                              (10.0 - row * 0.4, 1.1, 0.8), lite if row % 2 == 0 else accent))
        for x in (-6.2, 4.6):
            out.append(attach("jacket", (x, 3.0, -2.0), (1.6, 3.0, 4.0), plate))
    elif family == "ranger":
        out.append(attach("jacket", (2.0, 1.0, -4.2), (1.6, 5.2, 0.9), sk.mix_rgb(lite, base, 0.5)))
        out.append(attach("jacket", (2.2, 6.2, -4.0), (1.2, 1.4, 1.2), accent))
        for x in (-5.6, 4.2):
            out.append(attach("jacket", (x, -0.4, -1.2), (1.4, 2.6, 2.4), dark))
    elif family == "alchemist":
        for x, colour in ((-4.6, glow), (-3.0, accent), (3.4, glow)):
            out.append(attach("jacket", (x, 2.4, -3.6), (1.2, 2.2, 1.0), colour))
        out.append(attach("jacket", (-2.2, -1.4, 3.4), (4.4, 3.0, 0.7), lite))
    elif family == "sentinel":
        out.append(attach("jacket", (-5.2, 3.4, -4.2), (10.4, 5.4, 1.2), plate))
        out.append(attach("jacket", (-2.0, 4.6, -5.4), (4.0, 3.0, 1.2), glow))
        for x in (-6.0, 4.4):
            out.append(attach("jacket", (x, 3.6, -1.8), (1.6, 3.4, 3.4), dark))
    # Every suit shares a collar that reads against the neck.
    out.append(attach("jacket", (-3.4, 10.4, -2.6), (6.8, 2.0, 5.2), plate,
                      rotation=(6.0, 0.0, 0.0), pivot=(0.0, 11.4, 0.0)))
    return out


# ---------------------------------------------------------------------------
# Back items (wings, packs, banners): a cape patch plus real 3D hardware
# ---------------------------------------------------------------------------
def paint_back(patch: Patch, cape: Patch, pal: Palette, family: str, variant: int) -> None:
    base, dark, lite = pal.base, pal.shadowed(pal.base, 0.55), pal.lightened(pal.base, 0.24)
    accent, glow = pal.accent, pal.accent_light
    # Straps across the jacket so the item looks attached to the body, not floating.
    for name in sk.SIDE_FACES:
        u, v, w, h = patch.face_rect("jacket", name)
        patch.rect(u + 1, v + 4, max(1, w - 2), 1, dark)
        patch.rect(u + 1, v + 8, max(1, w - 2), 1, dark)
    u, v, w, h = cape.face_rect("cape", "front")
    cape.rect(u, v, w, h, dark)                                   # cape backing
    bu, bv, bw, bh = cape.face_rect("cape", "back")
    cape.rect(bu, bv, bw, bh, sk.mix_rgb(base, dark, 0.35))
    if family in ("wings", "glider", "wingpack"):
        span = w - 1
        for row in range(h):
            taper = int(round((1.0 - abs(row - h * 0.42) / (h * 0.7)) * (span * 0.46)))
            taper = max(1, taper + (variant % 2))
            cape.rect(u + w // 2 - taper, v + row, taper * 2, 1,
                      lite if row % 3 == 0 else base)
        cape.rect(u + 1, v + 2, w - 2, 1, accent)
    elif family in ("satchel", "lantern", "drone"):
        cape.rect(u + 2, v + 3, w - 4, h - 7, plate_colour(pal))
        cape.rect(u + 3, v + 5, w - 6, 3, lite)
        cape.rect(u + w // 2 - 1, v + h - 5, 3, 3, glow)
    elif family in ("banner", "tail"):
        cape.rect(u + w // 2 - 1, v, 3, h, sk.mix_rgb(accent, lite, 0.35))
        for row in (4, 9, 13):
            cape.rect(u + 1, v + row, w - 2, 2, base)
            cape.rect(u + 2, v + row + 1, w - 4, 1, dark)
    else:                                                          # shield, misc
        cape.rect(u + 2, v + 2, w - 4, h - 5, plate_colour(pal))
        cape.outline("cape", "front", sk.mix_rgb(base, dark, 0.5))
        cape.rect(u + w // 2 - 2, v + h // 2 - 2, 5, 5, glow)


def plate_colour(pal: Palette) -> Color:
    return sk.mix_rgb(pal.trim, pal.base, 0.4)


def wing_shape(pal: Palette, variant: int, *, side: int, rows: int, sweep: float,
               membrane_alpha: int = 235) -> list[Attachment]:
    """A layered feather/membrane wing rooted at the shoulder, mirrored across the spine."""
    out: list[Attachment] = []
    base, lite, accent = pal.base, pal.lightened(pal.base, 0.26), pal.accent
    root_x = side * 2.2
    for row in range(rows):
        length = 5.0 + row * 1.45 + (variant % 3) * 0.55
        y = 9.6 - row * 1.75
        z = -4.4 - row * 0.35
        x0 = root_x if side > 0 else root_x - length
        color = base if row % 2 else lite
        out.append(Attachment(
            box=box((x0, y, z), (length, 1.55, 1.0), color=color, alpha=membrane_alpha),
            layer="jacket",
            rotation=(0.0, side * (10.0 + row * 3.0), side * (-14.0 + sweep + row * 6.0)),
            pivot=(root_x, 10.6, z + 0.5),
        ))
        out.append(Attachment(                                   # feather tip catches the light
            box=box((x0 + (length - 1.6) if side > 0 else x0 + 0.0, y - 0.5, z + 0.15), (1.6, 2.2, 0.7),
                    color=lite, alpha=210),
            layer="jacket",
            rotation=(0.0, side * (10.0 + row * 3.0), side * (-14.0 + sweep + row * 6.0)),
            pivot=(root_x, 10.6, z + 0.5),
        ))
    out.append(Attachment(box=box((root_x - 1.1, 10.4, -4.9), (2.2, 2.4, 1.6), color=accent),
                          layer="jacket"))
    return out


def back_attachments(family: str, pal: Palette, variant: int) -> list[Attachment]:
    base, dark, lite = pal.base, pal.shadowed(pal.base, 0.55), pal.lightened(pal.base, 0.26)
    accent, glow, plate = pal.accent, pal.accent_light, plate_colour(pal)
    out: list[Attachment] = []
    if family in ("wings", "glider"):
        rows = 4 if family == "wings" else 3
        out.extend(wing_shape(pal, variant, side=1, rows=rows, sweep=6.0 if family == "wings" else 16.0))
        out.extend(wing_shape(pal, variant, side=-1, rows=rows, sweep=6.0 if family == "wings" else 16.0))
        if family == "glider":
            out.append(attach("jacket", (-2.0, 3.0, -4.4), (4.0, 6.0, 1.0), sk.mix_rgb(base, dark, 0.3)))
    elif family == "wingpack":
        for side in (1, -1):
            out.append(Attachment(box=box((0.0, 5.0, -2.4), (4.4, 5.4, 1.6), color=plate),
                                  layer="jacket", translate=(side * 2.0, 0.0, 0.0),
                                  rotation=(0.0, side * -14.0, 0.0), pivot=(side * 2.0, 8.0, -2.0)))
            for row in range(3):
                out.append(Attachment(box=box((0.2, 6.2 - row * 1.7, -2.0), (5.0, 1.0, 0.9), color=lite),
                                      layer="jacket", translate=(side * 2.2, 0.0, 0.0),
                                      rotation=(0.0, side * (6 + row * 6), side * (18 - row * 14)),
                                      pivot=(side * 2.2, 8.0, -2.0)))
        out.append(attach("jacket", (-2.4, 4.0, -4.6), (4.8, 4.8, 1.2), accent))
    elif family in ("satchel", "lantern"):
        out.append(attach("jacket", (-3.4, 2.0, -4.8), (6.8, 5.4, 2.0), plate))
        out.append(attach("jacket", (-2.4, 5.6, -4.9), (4.8, 1.4, 1.8), lite))
        out.append(attach("jacket", (-1.4, 3.2, -5.6), (2.8, 2.0, 0.9), glow if family == "lantern" else accent))
        if family == "lantern":
            out.append(attach("jacket", (-1.0, 7.0, -4.6), (2.0, 2.4, 2.0),
                              sk.mix_rgb(glow, (255, 255, 235), 0.35), alpha=225))
        for side in (1, -1):
            out.append(Attachment(box=box((0.0, 5.4, -1.0), (1.0, 3.6, 1.0), color=dark), layer="jacket",
                                  translate=(side * 2.6, 0.0, 0.6), rotation=(0.0, 0.0, side * 12),
                                  pivot=(side * 2.6, 8.0, 0.0)))
    elif family == "banner":
        out.append(attach("jacket", (-0.6, 5.0, -4.4), (1.2, 9.0, 1.2), sk.mix_rgb(lite, base, 0.4)))
        out.append(Attachment(box=box((-3.0, 0.0, -0.5), (6.0, 5.2, 0.5), color=accent, alpha=235),
                              layer="jacket", translate=(0.0, 10.6, -4.6), rotation=(0.0, 0.0, 4.0)))
        out.append(attach("jacket", (-2.0, 3.0, -5.0), (4.0, 2.2, 1.0), plate))
    elif family == "drone":
        out.append(attach("jacket", (-2.6, 7.6, -6.0), (5.2, 3.6, 3.0), plate,
                          rotation=(0.0, 0.0, 0.0)))
        out.append(attach("jacket", (-3.4, 6.4, -6.4), (6.8, 1.4, 3.8), lite))
        out.append(attach("jacket", (-1.2, 5.4, -5.6), (2.4, 1.2, 2.4), glow))
        for side in (1, -1):
            out.append(Attachment(box=box((0.0, 0.0, -1.0), (2.6, 0.8, 2.0), color=accent),
                                  layer="jacket", translate=(side * 5.0, 9.4, -5.4),
                                  rotation=(0.0, side * 22, side * -10)))
    elif family == "shield":
        out.append(attach("jacket", (-3.6, 1.4, -4.6), (7.2, 8.0, 1.2), plate))
        out.append(attach("jacket", (-2.6, 2.6, -5.6), (5.2, 5.2, 1.0), lite))
        out.append(attach("jacket", (-1.2, 4.2, -6.4), (2.4, 2.4, 0.9), glow))
    elif family == "tail":
        for index in range(5):
            out.append(Attachment(
                box=box((-1.0, -1.4, -0.6), (2.0, 2.6, 0.7),
                        color=lite if index % 2 == 0 else accent, alpha=230),
                layer="jacket", translate=(0.0, 6.6 - index * 1.7, -3.4 - index * 1.0),
                rotation=(18.0 + index * 6.0, 0.0, (index % 2 - 0.5) * 10.0)))
        out.append(attach("jacket", (-1.6, 5.4, -4.4), (3.2, 2.0, 1.4), plate))
    return out


# ---------------------------------------------------------------------------
# Pets: baked onto the head layer for the game, modelled as a companion for the card
# ---------------------------------------------------------------------------
def paint_pet(patch: Patch, pal: Palette, family: str, variant: int) -> None:
    """The companion is baked onto the crown of the head layer and grips both shoulders."""
    base, dark, lite = pal.base, pal.shadowed(pal.base, 0.6), pal.lightened(pal.base, 0.26)
    accent, glow = pal.accent, pal.accent_light
    belly = sk.mix_rgb(lite, (250, 246, 236), 0.45)
    patch.face("hat", "top", base)
    patch.cell("hat", "top", 2, 2, belly, cw=4, ch=3)             # belly facing up-front
    patch.cell("hat", "top", 1, 1, dark, cw=2, ch=2)              # ear nubs
    patch.cell("hat", "top", 5, 1, dark, cw=2, ch=2)
    patch.cell("hat", "top", 2, 6, accent, cw=4, ch=1)            # tail line
    patch.cell("hat", "top", 3, 4, glow, cw=2, ch=1)              # eye shine
    for row in range(0, 2):                                        # a ridge along the crown
        patch.cell("hat", "top", 3, row, sk.mix_rgb(base, lite, 0.5), cw=2, ch=1)
    # The creature peeks over the forehead: two eyes and a snout on the front rows.
    for x, colour in ((1, glow), (2, dark), (5, dark), (6, glow)):
        patch.cell("hat", "front", x, 1, colour)
    patch.cell("hat", "front", 3, 2, accent, cw=2, ch=1)
    patch.band("hat", 0, sk.mix_rgb(base, dark, 0.4))
    for part in ("right_arm_hat", "left_arm_hat"):                 # paws gripping the shoulders
        u, v, w, h = patch.face_rect(part, "top")
        patch.rect(u, v, w, h, sk.mix_rgb(base, dark, 0.35))
        for name in sk.SIDE_FACES:
            pu, pv, pw, ph = patch.face_rect(part, name)
            patch.rect(pu, pv, pw, 2, base)
            patch.rect(pu, pv + 2, pw, 1, dark)


def pet_model(family: str, pal: Palette, variant: int) -> list[Attachment]:
    """A companion perched on the shoulder, authored in jacket-local space (y = 0 is the shoulder)."""
    out: list[Attachment] = []
    side = 1 if variant % 2 == 0 else -1
    body, lite, accent, dark = pal.base, pal.lightened(pal.base, 0.3), pal.accent, pal.shadowed(pal.base, 0.35)

    def add(x: float, y: float, z: float, size: Sequence[float], color: RGB, *,
            rotation: tuple[float, float, float] = (0.0, 0.0, 0.0), alpha: int = 255,
            glow: float = 0.0) -> None:
        out.append(Attachment(
            box=box((x, y, z), size, color=color, alpha=alpha, glow=glow),
            layer="jacket",
            translate=(side * 4.6, 0.8, 0.0),
            rotation=rotation,
        ))

    if family == "wisp":
        add(-1.6, 1.2, -1.6, (3.2, 3.2, 3.2), body, rotation=(0.0, 18.0, 0.0), alpha=225, glow=0.5)
        add(-1.0, 1.8, -2.4, (2.0, 2.0, 0.8), accent, alpha=235, glow=0.9)
        add(-1.9, 0.0, -1.1, (1.2, 1.4, 1.2), lite, alpha=190, glow=0.4)
    elif family == "blob":
        add(-2.0, 0.0, -2.0, (4.0, 2.8, 4.0), body)
        add(-1.6, 2.8, -1.6, (3.2, 1.4, 3.2), lite)
        add(-1.2, 1.0, -2.9, (0.9, 0.9, 0.6), accent)
        add(0.3, 1.0, -2.9, (0.9, 0.9, 0.6), accent)
    elif family == "moth":
        add(-1.4, 0.9, -1.4, (2.8, 2.2, 3.4), body)
        add(-2.4, 1.2, -0.6, (2.0, 0.5, 3.0), lite, rotation=(0.0, 26.0, -12.0))
        add(0.4, 1.2, -0.6, (2.0, 0.5, 3.0), lite, rotation=(0.0, -26.0, 12.0))
        add(-0.9, 2.6, -2.0, (1.8, 1.6, 1.8), dark)
    elif family == "crab":
        add(-2.0, 0.0, -2.0, (4.0, 2.0, 4.0), body)
        add(-1.6, 2.0, -1.6, (3.2, 1.0, 3.2), lite)
        add(-2.8, 1.0, -3.2, (1.2, 1.2, 1.2), accent, rotation=(0.0, 22.0, 0.0))
        add(1.6, 1.0, -3.2, (1.2, 1.2, 1.2), accent, rotation=(0.0, -22.0, 0.0))
        add(-3.0, 0.4, -2.4, (1.4, 1.0, 1.0), dark)
    elif family == "dragon":
        add(-1.8, 1.0, -2.0, (3.6, 2.6, 4.4), body)
        add(-1.2, 2.6, -3.0, (2.4, 2.0, 2.0), lite)
        add(-0.9, 4.6, -2.6, (0.7, 1.4, 0.7), accent, rotation=(0.0, 0.0, -18.0))
        add(0.2, 4.6, -2.6, (0.7, 1.4, 0.7), accent, rotation=(0.0, 0.0, 18.0))
        add(1.8, 1.6, 0.4, (2.6, 0.5, 2.2), dark, rotation=(0.0, 0.0, 24.0))
    elif family == "turtle":
        add(-2.2, 0.0, -2.2, (4.4, 2.2, 4.4), body)
        add(-1.8, 2.2, -1.8, (3.6, 1.4, 3.6), lite)
        add(-1.4, 0.6, -3.4, (1.6, 1.4, 1.2), accent)
        add(1.2, 0.4, 1.4, (1.2, 1.0, 1.2), dark)
    elif family == "bot":
        add(-1.6, 1.0, -1.6, (3.2, 3.0, 3.2), body)
        add(-1.2, 2.2, -2.4, (2.4, 1.2, 0.6), accent, glow=0.8)
        add(-0.4, 4.0, -0.4, (0.8, 1.8, 0.8), lite, rotation=(0.0, 0.0, -14.0))
    elif family == "fox":
        add(-2.0, 0.6, -1.8, (4.0, 2.0, 3.6), body)
        add(-1.2, 1.4, -3.0, (2.4, 1.8, 1.6), lite)
        add(-1.6, 3.2, -2.6, (0.9, 1.2, 0.6), dark, rotation=(0.0, 0.0, -20.0))
        add(0.5, 3.2, -2.6, (0.9, 1.2, 0.6), dark, rotation=(0.0, 0.0, 20.0))
        add(1.6, 1.0, 1.2, (2.4, 1.2, 2.4), accent, rotation=(0.0, 0.0, 26.0))
    else:
        add(-1.6, 0.6, -1.6, (3.2, 3.0, 3.2), body)
    # A soft perch so the pet does not look like it is floating.
    out.insert(0, Attachment(box=box((-2.6, -0.6, -2.6), (5.2, 0.8, 5.2),
                                     color=pal.shadowed(body, 0.55), alpha=115),
                             layer="jacket", translate=(side * 4.6, 0.2, 0.0)))
    out.append(Attachment(box=box((-1.2, 0.0, -1.2), (2.4, 0.6, 2.4), color=accent, alpha=175, glow=0.6),
                          layer="jacket", translate=(side * 4.6, 0.0, 0.0)))
    return out


EMOTE_POSES: dict[str, dict[str, tuple[float, float, float]]] = {
    "greeting": {"right_arm": (-118.0, 0.0, 16.0), "right_arm_hat": (-118.0, 0.0, 16.0),
                 "left_arm": (14.0, 0.0, -12.0), "left_arm_hat": (14.0, 0.0, -12.0),
                 "head": (0.0, -14.0, 0.0), "hat": (0.0, -14.0, 0.0)},
    "cheer": {"right_arm": (-152.0, 0.0, 10.0), "right_arm_hat": (-152.0, 0.0, 10.0),
              "left_arm": (-150.0, 0.0, -10.0), "left_arm_hat": (-150.0, 0.0, -10.0),
              "head": (-8.0, -8.0, 0.0), "hat": (-8.0, -8.0, 0.0)},
    "bow": {"body": (26.0, 0.0, 0.0), "jacket": (26.0, 0.0, 0.0), "head": (-18.0, 0.0, 0.0),
            "hat": (-18.0, 0.0, 0.0), "right_arm": (30.0, 0.0, 12.0), "right_arm_hat": (30.0, 0.0, 12.0),
            "left_arm": (30.0, 0.0, -12.0), "left_arm_hat": (30.0, 0.0, -12.0),
            "cape": (-16.0, 0.0, 0.0)},
    "drum": {"right_arm": (-74.0, 0.0, 22.0), "right_arm_hat": (-74.0, 0.0, 22.0),
             "left_arm": (-70.0, 0.0, -20.0), "left_arm_hat": (-70.0, 0.0, -20.0),
             "head": (6.0, -8.0, 0.0), "hat": (6.0, -8.0, 0.0)},
    "dance": {"right_arm": (-104.0, 0.0, 26.0), "right_arm_hat": (-104.0, 0.0, 26.0),
              "left_arm": (26.0, 0.0, -30.0), "left_arm_hat": (26.0, 0.0, -30.0),
              "head": (0.0, 16.0, 0.0), "hat": (0.0, 16.0, 0.0),
              "left_leg": (0.0, 0.0, -10.0), "left_leg_hat": (0.0, 0.0, -10.0)},
    "salute": {"right_arm": (-134.0, 0.0, 34.0), "right_arm_hat": (-134.0, 0.0, 34.0),
               "left_arm": (6.0, 0.0, -8.0), "left_arm_hat": (6.0, 0.0, -8.0),
               "head": (-6.0, -10.0, 0.0), "hat": (-6.0, -10.0, 0.0)},
    "clap": {"right_arm": (-88.0, 0.0, 8.0), "right_arm_hat": (-88.0, 0.0, 8.0),
             "left_arm": (-86.0, 0.0, -8.0), "left_arm_hat": (-86.0, 0.0, -8.0),
             "head": (4.0, 0.0, 0.0), "hat": (4.0, 0.0, 0.0)},
    "victory": {"right_arm": (-166.0, 0.0, 6.0), "right_arm_hat": (-166.0, 0.0, 6.0),
                "left_arm": (22.0, 0.0, -16.0), "left_arm_hat": (22.0, 0.0, -16.0),
                "head": (-12.0, -6.0, 0.0), "hat": (-12.0, -6.0, 0.0)},
    "twirl": {"right_arm": (-64.0, 0.0, 44.0), "right_arm_hat": (-64.0, 0.0, 44.0),
              "left_arm": (-58.0, 0.0, -46.0), "left_arm_hat": (-58.0, 0.0, -46.0),
              "body": (0.0, 22.0, 0.0), "jacket": (0.0, 22.0, 0.0),
              "head": (0.0, 16.0, 0.0), "hat": (0.0, 16.0, 0.0)},
    # vertical-slice originals that do not map onto a generated family
    "bouquet": {"right_arm": (-56.0, 0.0, 26.0), "right_arm_hat": (-56.0, 0.0, 26.0),
                "left_arm": (10.0, 0.0, -10.0), "left_arm_hat": (10.0, 0.0, -10.0),
                "head": (-6.0, -8.0, 0.0), "hat": (-6.0, -8.0, 0.0)},
    "ribbon": {"right_arm": (-96.0, 0.0, 30.0), "right_arm_hat": (-96.0, 0.0, 30.0),
               "left_arm": (-92.0, 0.0, -28.0), "left_arm_hat": (-92.0, 0.0, -28.0),
               "body": (0.0, 14.0, 0.0), "jacket": (0.0, 14.0, 0.0)},
}


def paint_emote(patch: Patch, pal: Palette, family: str, variant: int) -> None:
    """Wrist wraps and a chest mark: the in-game signature of an emote on a plain skin."""
    base, dark, lite = pal.base, pal.shadowed(pal.base, 0.6), pal.lightened(pal.base, 0.26)
    accent, glow = pal.accent, pal.accent_light
    for part in ("right_arm", "left_arm"):
        for name in sk.SIDE_FACES:
            u, v, w, h = patch.face_rect(part, name)
            patch.rect(u, v + h - 3, w, 2, accent)
            patch.rect(u, v + h - 4, w, 1, sk.mix_rgb(accent, dark, 0.5))
        for name in sk.SIDE_FACES:
            u, v, w, h = patch.face_rect(part + "_hat", name)
            patch.rect(u, v + h - 3, w, 3, sk.mix_rgb(glow, accent, 0.3))
            patch.rect(u, v + h - 4, w, 1, lite)
    for name in ("front", "back"):
        u, v, w, h = patch.face_rect("jacket", name)
        patch.rect(u + w // 2 - 2, v + 2, 4, 3, base)
        patch.rect(u + w // 2 - 1, v + 3, 2, 1, glow)
    if family in ("cheer", "victory", "twirl"):
        for name in sk.SIDE_FACES:
            u, v, w, h = patch.face_rect("body", name)
            patch.rect(u, v + 3, w, 1, sk.mix_rgb(base, lite, 0.5))


def emote_prop(family: str, pal: Palette, variant: int) -> tuple[list[Attachment], dict[str, tuple[float, float, float]]]:
    """The prop the character actually holds, posed in the raised right hand."""
    base, dark, lite = pal.base, pal.shadowed(pal.base, 0.55), pal.lightened(pal.base, 0.24)
    accent, glow, trim = pal.accent, pal.accent_light, pal.trim
    pose = dict(EMOTE_POSES.get(family, EMOTE_POSES["greeting"]))
    out: list[Attachment] = []
    # Geometry is authored in right-arm space; the hand sits at y = 0 of that part (arm hangs down).
    if family in ("greeting", "victory"):
        out.append(Attachment(box=box((-0.4, -1.0, -0.4), (0.8, 6.4, 0.8), color=trim),
                              layer="right_arm", translate=(0.6, -0.4, 1.6), rotation=(18.0, 0.0, 0.0)))
        out.append(Attachment(box=box((-0.2, 4.6, -2.4), (0.5, 3.4, 4.6), color=accent, alpha=235),
                              layer="right_arm", translate=(0.6, -0.4, 1.6), rotation=(0.0, 0.0, 6.0)))
        out.append(Attachment(box=box((-0.2, 3.0, -3.0), (0.5, 2.0, 2.0), color=lite, alpha=225),
                              layer="right_arm", translate=(0.6, -0.4, 1.6)))
    elif family == "cheer":
        out.append(Attachment(box=box((-0.5, -1.4, -0.5), (1.0, 7.2, 1.0), color=dark),
                              layer="right_arm", translate=(0.4, 0.2, 1.2)))
        for angle in range(0, 360, 72):
            radians = math.radians(angle)
            out.append(Attachment(
                box=box((-0.8, 0.0, -0.8), (1.6, 1.6, 1.6), color=glow if angle % 144 == 0 else accent),
                layer="right_arm", translate=(0.4 + 1.9 * math.cos(radians), 6.4 + 1.9 * math.sin(radians), 1.2),
            ))
        out.append(Attachment(box=box((-1.4, 5.2, -1.4), (2.8, 2.8, 2.8), color=lite),
                              layer="right_arm", translate=(0.4, 0.2, 1.2)))
    elif family in ("bow", "bouquet"):
        out.append(Attachment(box=box((-0.4, -1.6, -0.4), (0.8, 4.2, 0.8), color=sk.mix_rgb(base, (60, 90, 50), 0.6)),
                              layer="right_arm", translate=(2.4, 1.2, 2.2), rotation=(0.0, 0.0, -26.0)))
        for index in range(5):
            angle = math.radians(-40 + index * 22)
            out.append(Attachment(box=box((-0.9, 0.0, -0.9), (1.8, 1.0, 1.8),
                                          color=accent if index % 2 == 0 else glow),
                                  layer="right_arm",
                                  translate=(2.4 + 1.9 * math.cos(angle), 3.6 + 1.5 * math.sin(angle), 2.2)))
        out.append(Attachment(box=box((-0.9, 2.8, -0.9), (1.8, 1.4, 1.8), color=sk.mix_rgb(lite, (255, 255, 240), 0.4)),
                              layer="right_arm", translate=(2.4, 1.2, 2.2), rotation=(0.0, 0.0, -26.0)))
    elif family == "drum":
        out.append(Attachment(box=box((-2.8, -2.4, -2.8), (5.6, 3.4, 5.6), color=sk.mix_rgb(base, dark, 0.3)),
                              layer="body", translate=(0.0, 4.4, 3.4), rotation=(78.0, 0.0, 0.0)))
        out.append(Attachment(box=box((-2.4, -0.6, -2.4), (4.8, 0.8, 4.8), color=lite),
                              layer="body", translate=(0.0, 4.4, 3.4), rotation=(78.0, 0.0, 0.0)))
        for side in (1, -1):
            out.append(Attachment(box=box((-0.3, 0.0, -0.3), (0.6, 3.4, 0.6), color=accent),
                                  layer="right_arm" if side > 0 else "left_arm",
                                  translate=(side * 1.4, 0.4, 1.2), rotation=(0.0, 0.0, side * 24)))
    elif family in ("salute", "clap"):
        for side in (1, -1):
            out.append(Attachment(box=box((-1.2, -0.4, -1.6), (2.4, 1.0, 3.2), color=accent),
                                  layer="right_arm" if side > 0 else "left_arm",
                                  translate=(side * 0.6, -0.2, 1.4), rotation=(0.0, side * 12, 0.0)))
        if family == "clap":
            for index in range(4):
                angle = math.radians(45 + index * 90)
                out.append(Attachment(box=box((-0.5, -0.5, -0.5), (1.0, 1.0, 1.0), color=glow),
                                      layer="body",
                                      translate=(3.0 * math.cos(angle), 12.0 + 2.2 * math.sin(angle), 4.4),
                                      rotation=(0.0, 0.0, 45.0)))
    elif family == "dance":
        for index in range(4):
            out.append(Attachment(box=box((-1.6, 0.0, -0.4), (3.2, 1.0, 0.8),
                                          color=accent if index % 2 == 0 else lite, alpha=225),
                                  layer="right_arm", translate=(0.0, -1.0 + index * 0.2, 2.0),
                                  rotation=(0.0, 0.0, 20.0 + index * 18.0)))
    elif family == "ribbon":
        for index in range(3):
            out.append(Attachment(box=box((-0.6, 0.0, -0.5), (1.2, 2.6 + index, 1.0), color=glow, alpha=215),
                                  layer="right_arm", translate=(-2.0 + index * 2.0, 2.0, 1.8)))
            out.append(Attachment(box=box((-0.6, 0.0, -0.5), (1.2, 2.2 + index, 1.0), color=accent, alpha=215),
                                  layer="left_arm", translate=(-2.0 + index * 2.0, 2.0, 1.8)))
    elif family == "twirl":
        for index in range(6):
            angle = math.radians(index * 60)
            out.append(Attachment(box=box((-0.7, -0.7, -0.7), (1.4, 1.4, 1.4),
                                          color=glow if index % 2 else accent, alpha=205),
                                  layer="body",
                                  translate=(5.2 * math.cos(angle), 14.0 + 1.6 * math.sin(angle),
                                             5.2 * math.sin(angle))))
    return out, pose


# ---------------------------------------------------------------------------
# Auras
# ---------------------------------------------------------------------------
def paint_aura(patch: Patch, pal: Palette, family: str, variant: int) -> None:
    """Emissive edging on the outer layers: what the aura actually bakes into the player skin."""
    accent, glow = pal.accent, pal.accent_light
    soft = sk.mix_rgb(accent, glow, 0.45)
    faint = sk.mix_rgb(soft, (12, 14, 18), 0.35)
    overlay = ("hat", "right_arm_hat", "left_arm_hat", "right_leg_hat", "left_leg_hat")
    for part in overlay:
        for name in sk.SIDE_FACES:
            u, v, w, h = patch.face_rect(part, name)
            patch.rect(u, v + h - 2, w, 1, soft)                   # lit hem on the outer layer
            patch.rect(u, v + h - 1, w, 1, faint)                  # grounding shadow line
        patch.face(part, "top", sk.mix_rgb(soft, pal.base, 0.45))
    # The tunic keeps a single glowing trim at the waist, not one on every face.
    for name in sk.SIDE_FACES:
        u, v, w, h = patch.face_rect("jacket", name)
        patch.rect(u, v + h - 2, w, 1, soft)
        patch.rect(u, v + h - 1, w, 1, faint)
    # A few motes sit on the fabric itself so the colour survives at 1x zoom.
    spots = {"monsoon": ((0, 3), (2, 5), (5, 2)), "aurora": ((1, 4), (3, 6), (6, 3)),
             "ember": ((0, 2), (2, 6), (5, 4), (6, 1)), "rune": ((1, 1), (4, 5), (6, 2)),
             "star": ((0, 5), (3, 2), (5, 6)), "snow": ((1, 2), (2, 5), (6, 4)),
             "leaf": ((0, 4), (4, 3), (6, 6)), "vortex": ((2, 2), (3, 5), (5, 1), (6, 4)),
             "spectrum": ((0, 1), (2, 3), (4, 5), (6, 2))}.get(family, ((1, 3), (4, 6)))
    for part in overlay:
        block = sk.SKIN_PARTS[part]
        for name in sk.SIDE_FACES:
            u, v, w, h = block.face_rect(name)
            for index, (sx, sy) in enumerate(spots):
                x = u + (sx + index) % max(1, w)
                y = v + (sy + (index * 2)) % max(1, h)
                colour = glow if (index + variant) % 2 == 0 else accent
                patch.rect(x, y, 1, 1, colour)
    if family in ("aurora", "spectrum", "vortex"):
        # Veil arcs across the back: a soft gradient rather than hard bands.
        u, v, w, h = patch.face_rect("jacket", "back")
        for step in range(3):
            patch.rect(u, v + 2 + step, w, 1, sk.mix_rgb(glow, soft, step / 3.0))


def aura_motes(family: str, pal: Palette, variant: int) -> list[Attachment]:
    """The floating ring of motes drawn around the character in the card art."""
    accent, glow, base = pal.accent, pal.accent_light, pal.base
    count = {"monsoon": 8, "aurora": 3, "ember": 9, "rune": 4, "star": 7, "snow": 10,
             "leaf": 6, "vortex": 12, "spectrum": 6}.get(family, 8)
    radius = {"monsoon": 8.5, "aurora": 11.0, "ember": 7.5, "rune": 9.0, "star": 10.0,
              "snow": 9.5, "leaf": 8.0, "vortex": 7.0, "spectrum": 10.5}.get(family, 9.0)
    height = {"rune": 6.0, "snow": 22.0, "monsoon": 14.0, "ember": 12.0, "vortex": 16.0}.get(family, 12.0)
    twist = (variant % 5) * 11.0
    out: list[Attachment] = []
    for index in range(count):
        angle = math.radians(index * (360.0 / count) + twist)
        rise = 0.0
        if family == "ember":
            rise = (index % 3) * 2.2
        if family == "snow":
            rise = -(index % 3) * 2.6
        if family in ("aurora", "spectrum"):
            span = 1.0 + (index % 3) * 0.4
            out.append(Attachment(
                box=box((-0.6, 0.0, -0.6), (1.2, 7.5 * span, 1.2),
                        color=sk.mix_rgb(glow, accent, index / max(1, count - 1)), alpha=120),
                layer="head", translate=(radius * math.cos(angle), -height + 10.0, radius * math.sin(angle)),
                rotation=(0.0, -math.degrees(angle), (index % 2 - 0.5) * 10.0)))
            continue
        size = 1.5 + (index % 3) * 0.35
        box_size = (1.0, 3.0, 1.0) if family == "rune" else (size, size, size)
        color = accent if index % 2 == 0 else glow
        if family in ("monsoon", "snow"):
            color = sk.mix_rgb(color, (235, 250, 255), 0.35)
        out.append(Attachment(box=box((-box_size[0] / 2.0, 0.0, -box_size[2] / 2.0), box_size,
                                      color=color, alpha=215),
                              layer="head",
                              translate=(radius * math.cos(angle), -height + rise, radius * math.sin(angle)),
                              rotation=(0.0, math.degrees(angle) + 90.0,
                                         24.0 if family == "vortex" else 0.0)))
    # A wide, faint ring on the ground sells the "aura" reading at card size.
    out.append(Attachment(box=box((-radius, -14.6, -radius), (radius * 2.0, 0.5, radius * 2.0),
                                  color=sk.mix_rgb(accent, base, 0.5), alpha=64),
                          layer="body", translate=(0.0, 14.0, 0.0)))
    return out


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------
PAINTERS: dict[str, Callable[[Patch, Palette, str, int], None]] = {
    "hat": paint_hat,
    "suit": paint_suit,
    "back": paint_back,
    "pet": paint_pet,
    "emote": paint_emote,
    "aura": paint_aura,
}

ATTACH_BUILDERS: dict[str, Callable[[str, Palette, int], Any]] = {
    "hat": lambda family, pal, variant: hat_attachments(family, pal, variant),
    "suit": lambda family, pal, variant: suit_attachments(family, pal, variant),
    "back": lambda family, pal, variant: back_attachments(family, pal, variant),
    "pet": lambda family, pal, variant: pet_model(family, pal, variant),
    "aura": aura_motes,
}


def build_art(category: str, family: str, palette: Sequence[str], variant: int,
              emissive: bool = False, seed: int = 0) -> ItemArt:
    """Author the in-game garment patch plus the card geometry for one item."""
    pal = Palette.from_hex(palette, emissive=emissive)
    skin_patch = Patch.skin()
    cape_patch = Patch.cape() if category == "back" else None
    if category == "back":
        paint_back(skin_patch, cape_patch, pal, family, variant)
    else:
        PAINTERS[category](skin_patch, pal, family, variant)
    pose: dict[str, tuple[float, float, float]] = {}
    if category == "emote":
        attachments, pose = emote_prop(family, pal, variant)
    elif category == "aura":
        attachments = aura_motes(family, pal, variant)
    elif category == "hat":
        attachments = hat_attachments(family, pal, variant)
    elif category == "suit":
        attachments = suit_attachments(family, pal, variant)
    elif category == "back":
        attachments = back_attachments(family, pal, variant)
    elif category == "pet":
        attachments = pet_model(family, pal, variant)
    else:
        attachments = []
    rng = np.random.default_rng(seed or 1)
    skin_patch.grain(rng, strength=2)
    if cape_patch is not None:
        cape_patch.grain(rng, strength=2)
    return ItemArt(patch=skin_patch.array, cape=None if cape_patch is None else cape_patch.array,
                   attachments=attachments, pose=pose)


FAMILY_FOR_SHAPE: dict[str, str] = {
    # The original vertical-slice shapes, mapped onto the family art they now share.
    "aviator_cap": "aviator", "tide_circlet": "circlet", "mushroom_hood": "hood",
    "lantern_halo": "halo", "basalt_helm": "antlers",
    "diver_suit": "diver", "willow_suit": "wayfinder", "prism_suit": "cartographer",
    "stargazer_suit": "stargazer", "mechanist_suit": "mechanist",
    "cicada_wings": "wings", "windup_pack": "satchel", "kite_banner": "banner",
    "emberleaf_glider": "glider", "bell_drone": "drone",
    "wisp_pet": "wisp", "soot_pet": "blob", "moth_pet": "moth", "comet_pet": "dragon",
    "pebble_crab": "crab",
    "origami_prop": "greeting", "spark_wand": "cheer", "marigold_bouquet": "bouquet",
    "pocket_drum": "drum", "moon_ribbons": "ribbon",
    "monsoon_ring": "monsoon", "aurora_arcs": "aurora", "marigold_aura": "ember",
    "rune_ring": "rune", "star_silt": "star",
    # Full-catalog families resolve to themselves.
    "spire": "spire", "hood": "hood", "halo": "halo", "circlet": "circlet", "visor": "visor",
    "antlers": "antlers", "wreath": "wreath", "mask": "mask",
    "wayfinder": "wayfinder", "cartographer": "cartographer", "samurai": "samurai",
    "ranger": "ranger", "alchemist": "alchemist", "sentinel": "sentinel",
    "satchel": "satchel", "banner": "banner", "glider": "glider", "drone": "drone",
    "shield": "shield", "tail": "tail", "lantern": "lantern", "wingpack": "wingpack",
    "blob": "blob", "moth": "moth", "crab": "crab", "bird": "bird", "dragon": "dragon",
    "turtle": "turtle", "bot": "bot", "fox": "fox",
    "greeting": "greeting", "cheer": "cheer", "bow": "bow", "drum": "drum", "dance": "dance",
    "salute": "salute", "clap": "clap", "victory": "victory", "twirl": "twirl",
    "monsoon": "monsoon", "aurora": "aurora", "ember": "ember", "rune": "rune", "star": "star",
    "snow": "snow", "leaf": "leaf", "vortex": "vortex", "spectrum": "spectrum",
}


def family_for_shape(category: str, shape: str) -> str:
    """Resolve a catalog `shape` name to the family whose art it uses."""
    if shape in FAMILY_FOR_SHAPE:
        return FAMILY_FOR_SHAPE[shape]
    if shape.startswith("full_"):
        middle = shape[len("full_"):].split("_")
        if len(middle) >= 2 and middle[0] == category:
            return "_".join(middle[1:-1]) if middle[-1].isdigit() else "_".join(middle[1:])
    return "circlet" if category == "hat" else ""
