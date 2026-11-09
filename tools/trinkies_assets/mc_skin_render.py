#!/usr/bin/env python3
"""Minecraft skin-layout primitives, an original procedural base skin, and a small textured
software renderer for wardrobe previews.

Everything here is original: the layout constants describe the public 64x64 player-texture format
(which face of which body part lives at which texel rectangle), the base skin is generated pixel
art, and the renderer is a plain z-buffered quad rasterizer. No game or third-party asset is read.

This module is the single source of truth for two things that must never drift apart:

* ``preview.png`` on the catalog card, which shows a character actually wearing the item, and
* the in-game result, because the very same 64x64 patch is composited onto the player's skin
  texture by ``TrinkiesGarmentComposite`` on the launcher side and inside the client mod.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Iterable, Sequence

import numpy as np

SKIN_SIZE = 64
CAPE_WIDTH = 64
CAPE_HEIGHT = 32
FACES = ("top", "bottom", "right", "front", "left", "back")
SIDE_FACES = ("right", "front", "left", "back")


# ---------------------------------------------------------------------------
# Skin layout
# ---------------------------------------------------------------------------
# Each body part owns a 32x16 UV block in the 64x64 skin, unfolded the way the game reads it:
#
#                     +--------+--------+
#                     |   top  | bottom |      rows v .. v+depth
#   +-------+---------+--------+--------+-------+
#   | right |  front  |  left  |  back  |        rows v+depth .. v+depth+height
#   +-------+---------+--------+--------+-------+
@dataclass(frozen=True)
class PartBlock:
    name: str
    u: int
    v: int
    width: int
    height: int
    depth: int

    def face_rect(self, face: str) -> tuple[int, int, int, int]:
        u, v, w, h, d = self.u, self.v, self.width, self.height, self.depth
        if face == "top":
            return (u + d, v, w, d)
        if face == "bottom":
            return (u + d + w, v, w, d)
        band = u
        if face == "right":
            return (band, v + d, d, h)
        if face == "front":
            return (band + d, v + d, w, h)
        if face == "left":
            return (band + d + w, v + d, d, h)
        if face == "back":
            return (band + d + w + d, v + d, w, h)
        raise KeyError(face)

    def block_rect(self) -> tuple[int, int, int, int]:
        """The whole footprint the game reads for this part: four side faces in a row, with the
        top and bottom faces stacked in a single `depth`-tall band above them."""
        return (self.u, self.v, 2 * self.width + 2 * self.depth, self.height + self.depth)

    def covers(self, x: int, y: int) -> bool:
        for face in FACES:
            u, v, w, h = self.face_rect(face)
            if u <= x < u + w and v <= y < v + h:
                return True
        return False


SKIN_PARTS: dict[str, PartBlock] = {
    "head": PartBlock("head", 0, 0, 8, 8, 8),
    "body": PartBlock("body", 16, 16, 8, 12, 4),
    "right_arm": PartBlock("right_arm", 40, 16, 4, 12, 4),
    "right_leg": PartBlock("right_leg", 0, 16, 4, 12, 4),
    "left_arm": PartBlock("left_arm", 32, 48, 4, 12, 4),
    "left_leg": PartBlock("left_leg", 16, 48, 4, 12, 4),
    # Overlay layers: same UV block size, drawn half a pixel larger by the game.
    "hat": PartBlock("hat", 32, 0, 8, 8, 8),
    "jacket": PartBlock("jacket", 16, 32, 8, 12, 4),
    "right_arm_hat": PartBlock("right_arm_hat", 40, 32, 4, 12, 4),
    "right_leg_hat": PartBlock("right_leg_hat", 0, 32, 4, 12, 4),
    "left_arm_hat": PartBlock("left_arm_hat", 48, 48, 4, 12, 4),
    "left_leg_hat": PartBlock("left_leg_hat", 0, 48, 4, 12, 4),
}

CAPE_PARTS: dict[str, PartBlock] = {
    # The cape is a separate 64x32 texture using the same unfolding rule for a 10x16x1 box.
    "cape": PartBlock("cape", 0, 0, 10, 16, 1),
}

# Which skin blocks a category may paint. Compositing is plain source-over, so this table exists to
# keep generated art honest: a hat that painted the legs would show up as a stray blob on the
# character. `validate_catalog.py` rejects patches that leak outside these parts.
CATEGORY_PATCH_PARTS: dict[str, tuple[str, ...]] = {
    # Hats may also touch the jacket: a hood or cloak drapes onto the shoulders, which is exactly
    # what a hand-authored skin does. Anything below the torso is still a bug.
    "hat": ("head", "hat", "jacket"),
    "suit": ("body", "jacket", "right_arm", "right_arm_hat", "left_arm", "left_arm_hat",
             "right_leg", "right_leg_hat", "left_leg", "left_leg_hat", "hat"),
    "back": ("jacket", "hat"),
    # A pet sits on the shoulder, so it may also paint the outer torso/arm layers as a harness.
    "pet": ("head", "hat", "jacket", "right_arm_hat", "left_arm_hat"),
    "emote": ("right_arm", "right_arm_hat", "left_arm", "left_arm_hat", "body", "jacket"),
    "aura": ("head", "hat", "body", "jacket", "right_arm", "right_arm_hat", "left_arm",
             "left_arm_hat", "right_leg", "right_leg_hat", "left_leg", "left_leg_hat"),
}

# ---------------------------------------------------------------------------
# Player model (units are skin pixels; y up, +z toward the viewer, feet at y = 0)
# ---------------------------------------------------------------------------
# A vanilla player is 32 px tall: legs 12, torso 12, head 8.
@dataclass(frozen=True)
class Layer:
    name: str
    part: str                                # UV block that textures this layer
    origin: tuple[float, float, float]       # min corner of the box
    size: tuple[float, float, float]
    pivot: tuple[float, float, float]
    inflate: float = 0.0


PLAYER_LAYERS: tuple[Layer, ...] = (
    Layer("cape", "cape", (-5.0, 8.0, -3.5), (10.0, 16.0, 1.0), (0.0, 24.0, -2.0)),
    Layer("left_leg", "left_leg", (0.0, 0.0, -2.0), (4.0, 12.0, 4.0), (2.0, 12.0, 0.0)),
    Layer("left_leg_hat", "left_leg_hat", (0.0, 0.0, -2.0), (4.0, 12.0, 4.0), (2.0, 12.0, 0.0), 0.5),
    Layer("right_leg", "right_leg", (-4.0, 0.0, -2.0), (4.0, 12.0, 4.0), (-2.0, 12.0, 0.0)),
    Layer("right_leg_hat", "right_leg_hat", (-4.0, 0.0, -2.0), (4.0, 12.0, 4.0), (-2.0, 12.0, 0.0), 0.5),
    Layer("left_arm", "left_arm", (0.0, 12.0, -2.0), (4.0, 12.0, 4.0), (4.0, 24.0, 0.0)),
    Layer("left_arm_hat", "left_arm_hat", (0.0, 12.0, -2.0), (4.0, 12.0, 4.0), (4.0, 24.0, 0.0), 0.5),
    Layer("right_arm", "right_arm", (-4.0, 12.0, -2.0), (4.0, 12.0, 4.0), (-4.0, 24.0, 0.0)),
    Layer("right_arm_hat", "right_arm_hat", (-4.0, 12.0, -2.0), (4.0, 12.0, 4.0), (-4.0, 24.0, 0.0), 0.5),
    Layer("body", "body", (-4.0, 12.0, -2.0), (8.0, 12.0, 4.0), (0.0, 24.0, 0.0)),
    Layer("jacket", "jacket", (-4.0, 12.0, -2.0), (8.0, 12.0, 4.0), (0.0, 24.0, 0.0), 0.5),
    Layer("head", "head", (-4.0, 24.0, -4.0), (8.0, 8.0, 8.0), (0.0, 24.0, 0.0)),
    Layer("hat", "hat", (-4.0, 24.0, -4.0), (8.0, 8.0, 8.0), (0.0, 24.0, 0.0), 0.5),
)

LAYER_BY_NAME: dict[str, Layer] = {layer.name: layer for layer in PLAYER_LAYERS}

# A relaxed standing pose: a small counter-swing plus a turned head keeps the silhouette from
# reading as a flat cut-out.
DEFAULT_POSE: dict[str, tuple[float, float, float]] = {
    "left_arm": (7.0, 0.0, -9.0),
    "left_arm_hat": (7.0, 0.0, -9.0),
    "right_arm": (-5.0, 0.0, 7.0),
    "right_arm_hat": (-5.0, 0.0, 7.0),
    "head": (0.0, -9.0, 0.0),
    "hat": (0.0, -9.0, 0.0),
    "cape": (-9.0, 0.0, 0.0),
}

# Brightness per face for a light from the camera's upper front-left.
FACE_SHADE: dict[str, float] = {
    "top": 1.18, "front": 1.0, "left": 0.9, "right": 0.76, "back": 0.62, "bottom": 0.48,
}


def hex_rgb(value: str) -> tuple[int, int, int]:
    return tuple(int(value[index:index + 2], 16) for index in (1, 3, 5))  # type: ignore[return-value]


def scale_rgb(color: Sequence[int], factor: float) -> tuple[int, int, int]:
    return tuple(max(0, min(255, round(channel * factor))) for channel in color[:3])  # type: ignore[return-value]


def mix_rgb(left: Sequence[int], right: Sequence[int], amount: float) -> tuple[int, int, int]:
    return tuple(max(0, min(255, round(a + (b - a) * amount)))
                 for a, b in zip(left[:3], right[:3]))  # type: ignore[return-value]


# ---------------------------------------------------------------------------
# Original procedural base skin: the plain mannequin garments are baked onto
# ---------------------------------------------------------------------------
def make_base_skin(seed: int = 7) -> np.ndarray:
    sheet = np.zeros((SKIN_SIZE, SKIN_SIZE, 4), dtype=np.uint8)
    skin = (198, 150, 114)
    skin_mid = (176, 128, 96)
    skin_dark = (150, 106, 80)
    hair = (78, 52, 37)
    hair_dark = (52, 34, 25)
    shirt = (60, 70, 84)
    shirt_dark = (44, 52, 64)
    trousers = (48, 56, 76)
    trousers_dark = (36, 42, 56)
    shoes = (31, 34, 40)

    def face(part: str, name: str, color: Sequence[int]) -> None:
        u, v, w, h = SKIN_PARTS[part].face_rect(name)
        sheet[v:v + h, u:u + w, :3] = color
        sheet[v:v + h, u:u + w, 3] = 255

    def paint(part: str, color: Sequence[int]) -> None:
        for name in FACES:
            face(part, name, color)

    paint("head", skin)
    for name in ("top", "back", "right", "left"):
        face("head", name, hair)
    for name in SIDE_FACES:                      # hair fringe across the top row of each side
        u, v, w, h = SKIN_PARTS["head"].face_rect(name)
        sheet[v:v + 2, u:u + w, :3] = hair
        sheet[v:v + 2, u:u + w, 3] = 255
    fu, fv, fw, fh = SKIN_PARTS["head"].face_rect("front")
    sheet[fv + 2, fu:fu + fw, :3] = skin_mid      # brow line
    for eye in (1, 5):
        sheet[fv + 3, fu + eye, :3] = (236, 236, 234)
        sheet[fv + 3, fu + eye + 1, :3] = hair_dark
        sheet[fv + 2, fu + eye:fu + eye + 2, :3] = hair
    sheet[fv + 5, fu + 3:fu + 5, :3] = skin_dark  # mouth
    sheet[fv + 6, fu + 2:fu + 6, :3] = skin_dark
    sheet[fv + 7, fu:fu + fw, :3] = skin_mid      # jaw shade

    for part in ("right_arm", "left_arm"):
        paint(part, skin)
        for name in FACES:
            u, v, w, h = SKIN_PARTS[part].face_rect(name)
            sheet[v:v + 6, u:u + w, :3] = shirt            # sleeve
            sheet[v + 6:v + 7, u:u + w, :3] = shirt_dark   # cuff
            sheet[v:v + 7, u:u + w, 3] = 255
        u, v, w, h = SKIN_PARTS[part].face_rect("top")
        sheet[v:v + h, u:u + w, :3] = shirt
        sheet[v:v + h, u:u + w, 3] = 255

    paint("body", shirt)
    for name in SIDE_FACES:
        u, v, w, h = SKIN_PARTS["body"].face_rect(name)
        sheet[v:v + 1, u:u + w, :3] = shirt_dark
        sheet[v + 2:v + 4, u + w // 2 - 1:u + w // 2 + 1, :3] = shirt_dark
        sheet[v + h - 4:v + h - 3, u:u + w, :3] = trousers_dark
        sheet[v + h - 3:v + h, u:u + w, :3] = trousers
    u, v, w, h = SKIN_PARTS["body"].face_rect("bottom")
    sheet[v:v + h, u:u + w, :3] = trousers
    sheet[v:v + h, u:u + w, 3] = 255

    for part in ("right_leg", "left_leg"):
        paint(part, trousers)
        for name in FACES:
            u, v, w, h = SKIN_PARTS[part].face_rect(name)
            sheet[v + h - 5:v + h, u:u + w, :3] = shoes
            sheet[v + h - 6:v + h - 5, u:u + w, :3] = trousers_dark
        u, v, w, h = SKIN_PARTS[part].face_rect("bottom")
        sheet[v:v + h, u:u + w, :3] = shoes
        sheet[v:v + h, u:u + w, 3] = 255

    for part in ("hat", "jacket", "right_arm_hat", "left_arm_hat", "right_leg_hat", "left_leg_hat"):
        for name in FACES:
            u, v, w, h = SKIN_PARTS[part].face_rect(name)
            sheet[v:v + h, u:u + w] = 0        # overlay layers start empty for garments to fill

    rng = np.random.default_rng(seed)
    grain = rng.integers(0, 6, size=(SKIN_SIZE, SKIN_SIZE, 1)).astype(np.int16) - 2
    visible = sheet[..., 3:4] > 0
    shaded = np.clip(sheet[..., :3].astype(np.int16) + grain, 0, 255).astype(np.uint8)
    sheet[..., :3] = np.where(visible, shaded, sheet[..., :3])
    return sheet


# ---------------------------------------------------------------------------
# Garment patches
# ---------------------------------------------------------------------------
@dataclass
class Patch:
    """A drawable RGBA image laid out in the skin (or cape) UV format."""

    array: np.ndarray
    parts: dict[str, PartBlock] = field(default_factory=lambda: dict(SKIN_PARTS))

    @classmethod
    def skin(cls) -> "Patch":
        return cls(np.zeros((SKIN_SIZE, SKIN_SIZE, 4), dtype=np.uint8), dict(SKIN_PARTS))

    @classmethod
    def cape(cls) -> "Patch":
        return cls(np.zeros((CAPE_HEIGHT, CAPE_WIDTH, 4), dtype=np.uint8), dict(CAPE_PARTS))

    # -- drawing primitives ------------------------------------------------
    def rect(self, x: int, y: int, w: int, h: int, color: Sequence[int]) -> None:
        height, width = self.array.shape[:2]
        x0, y0 = max(0, int(x)), max(0, int(y))
        x1, y1 = min(width, x0 + int(w)), min(height, y0 + int(h))
        if x1 <= x0 or y1 <= y0:
            return
        self.array[y0:y1, x0:x1, :3] = color[:3]
        self.array[y0:y1, x0:x1, 3] = color[3] if len(color) > 3 else 255

    def face_rect(self, part: str, name: str) -> tuple[int, int, int, int]:
        return self.parts[part].face_rect(name)

    def face(self, part: str, name: str, color: Sequence[int]) -> None:
        u, v, w, h = self.face_rect(part, name)
        self.rect(u, v, w, h, color)

    def part(self, part: str, color: Sequence[int], faces: Iterable[str] = FACES) -> None:
        for name in faces:
            self.face(part, name, color)

    def cell(self, part: str, name: str, cx: int, cy: int, color: Sequence[int],
              cw: int = 1, ch: int = 1) -> None:
        """Paint texels of one face, counted from that face's own top-left corner."""
        u, v, w, h = self.face_rect(part, name)
        if cx < 0 or cy < 0 or cx >= w or cy >= h:
            return
        self.rect(u + cx, v + cy, min(cw, w - cx), min(ch, h - cy), color)

    def band(self, part: str, row: int, color: Sequence[int], height: int = 1) -> None:
        """Paint a wraparound row of the four side faces; a negative row counts from the bottom."""
        for name in SIDE_FACES:
            u, v, w, h = self.face_rect(part, name)
            y = v + (row if row >= 0 else h + row)
            self.rect(u, y, w, height, color)

    def sides(self, part: str) -> tuple[str, ...]:
        return SIDE_FACES

    def outline(self, part: str, name: str, color: Sequence[int]) -> None:
        u, v, w, h = self.face_rect(part, name)
        self.rect(u, v, w, 1, color)
        self.rect(u, v + h - 1, w, 1, color)
        self.rect(u, v, 1, h, color)
        self.rect(u + w - 1, v, 1, h, color)

    def darken_edges(self, part: str, name: str, amount: float = 0.72) -> None:
        u, v, w, h = self.face_rect(part, name)
        region = self.array[v:v + h, u:u + w]
        painted = region[..., 3] > 0
        border = np.zeros(region.shape[:2], dtype=bool)
        border[0, :] = True
        border[-1, :] = True
        border[:, 0] = True
        border[:, -1] = True
        border &= painted
        if border.any():
            region[border, :3] = np.clip(region[border, :3].astype(np.float32) * amount, 0, 255).astype(np.uint8)

    def grain(self, rng: np.random.Generator, strength: int = 7) -> None:
        delta = rng.integers(-strength, strength + 1, size=self.array.shape[:2]).astype(np.int16)
        painted = self.array[..., 3] > 0
        rgb = np.clip(self.array[..., :3].astype(np.int16) + delta[..., None], 0, 255).astype(np.uint8)
        self.array[..., :3] = np.where(painted[..., None], rgb, self.array[..., :3])

    # -- compositing --------------------------------------------------------
    def alpha_composite(self, other: np.ndarray) -> None:
        """Source-over blend; transparent texels in `other` leave this image untouched."""
        if other.shape != self.array.shape:
            raise ValueError(f"patch size mismatch: {other.shape} vs {self.array.shape}")
        src = other.astype(np.float32)
        sa = src[..., 3:4] / 255.0
        if float(sa.max()) <= 0.0:
            return
        dst = self.array.astype(np.float32)
        da = dst[..., 3:4] / 255.0
        out_a = sa + da * (1.0 - sa)
        safe = np.where(out_a > 1e-6, out_a, 1.0)
        out_rgb = (src[..., :3] * sa + dst[..., :3] * da * (1.0 - sa)) / safe
        self.array[..., :3] = np.where(out_a > 1e-6, np.clip(out_rgb, 0, 255), dst[..., :3]).astype(np.uint8)
        self.array[..., 3:4] = np.clip(out_a * 255.0, 0, 255).astype(np.uint8)

    def glow_composite(self, other: np.ndarray, strength: float = 1.0) -> None:
        """Additive pass used by auras: brighten the layer below instead of hiding it."""
        if other.shape != self.array.shape:
            raise ValueError(f"patch size mismatch: {other.shape} vs {self.array.shape}")
        src = other.astype(np.float32)
        sa = src[..., 3:4] / 255.0 * float(strength)
        if float(sa.max()) <= 0.0:
            return
        dst = self.array.astype(np.float32)
        lit = dst[..., :3] * (1.0 - sa * 0.3) + src[..., :3] * sa + 22.0 * sa
        self.array[..., :3] = np.clip(lit, 0, 255).astype(np.uint8)
        self.array[..., 3:4] = np.clip(np.maximum(dst[..., 3:4], sa * 255.0), 0, 255).astype(np.uint8)

    def leaks_outside(self, allowed: Iterable[str]) -> list[tuple[int, int]]:
        mask = np.zeros(self.array.shape[:2], dtype=bool)
        for part in allowed:
            block = self.parts.get(part)
            if block is None:
                continue
            for name in FACES:
                u, v, w, h = block.face_rect(name)
                mask[v:v + h, u:u + w] = True
        painted = self.array[..., 3] > 0
        return [tuple(int(c) for c in (x, y)) for y, x in zip(*np.nonzero(painted & ~mask))]


def make_base_cape() -> np.ndarray:
    """The mannequin's plain cape cloth, laid out in the 64×32 cape UV a client expects."""
    patch = Patch.cape()
    cloth = (52, 61, 74)
    dark = (38, 45, 56)
    trim = (146, 168, 176)
    for name in FACES:
        patch.face("cape", name, cloth if name in ("front", "back") else dark)
        u, v, w, h = CAPE_PARTS["cape"].face_rect(name)
        patch.rect(u, v + h - 2, w, 1, trim)
        patch.rect(u, v + h - 1, w, 1, dark)
    return patch.array


def composite_over(base: np.ndarray, patch: np.ndarray) -> np.ndarray:
    result = Patch(base.copy())
    result.alpha_composite(patch)
    return result.array


def composite_glow(base: np.ndarray, patch: np.ndarray, strength: float = 1.0) -> np.ndarray:
    result = Patch(base.copy())
    result.glow_composite(patch, strength)
    return result.array


# ---------------------------------------------------------------------------
# Boxes and faces
# ---------------------------------------------------------------------------
def face_corners(x0: float, y0: float, z0: float, x1: float, y1: float, z1: float,
                 name: str) -> tuple[tuple[float, float, float], ...]:
    """The four corners of a face, in the order the skin layout reads the face rectangle."""
    table = {
        "front": ((x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)),
        "back": ((x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)),
        "left": ((x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)),
        "right": ((x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)),
        "top": ((x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)),
        "bottom": ((x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)),
    }
    return table[name]


@dataclass(frozen=True)
class Box:
    """A box in a part's local space.

    ``uv_part`` faces sample the composited skin; a box without one is a flat-coloured solid
    (used for wings, props, pets and aura motes).
    """

    origin: tuple[float, float, float]
    size: tuple[float, float, float]
    uv_part: str | None = None
    color: tuple[int, int, int] | None = None
    inflate: float = 0.0
    alpha: int = 255
    uv_faces: dict[str, tuple[int, int, int, int]] | None = None
    parts: dict[str, PartBlock] | None = None
    texture: "np.ndarray | None" = None      # small RGBA sheet for solid attachment art

    def bounds(self) -> tuple[float, float, float, float, float, float]:
        x0, y0, z0 = self.origin
        w, h, d = self.size
        grow = self.inflate
        return (x0 - grow, y0 - grow, z0 - grow, x0 + w + grow, y0 + h + grow, z0 + d + grow)


@dataclass
class Face:
    points: np.ndarray          # (4, 3) world space
    uv: np.ndarray              # (4, 2) texel coordinates of the face rectangle's corners
    texture: np.ndarray | None   # the sheet to sample, or None for a flat colour
    color: tuple[int, int, int]
    alpha: int
    shade: float


def _rotation_matrix(rx: float, ry: float, rz: float) -> np.ndarray:
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    rot_x = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]], dtype=float)
    rot_y = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], dtype=float)
    rot_z = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]], dtype=float)
    return rot_z @ rot_y @ rot_x


def transform(points: np.ndarray, pivot: Sequence[float], rotation_deg: Sequence[float],
              translate: Sequence[float], scale: float = 1.0) -> np.ndarray:
    """Rotate about ``pivot``, then scale about the origin and translate. Order matches the model."""
    moved = points - np.array(pivot, dtype=float)
    if any(abs(float(value)) > 1e-9 for value in rotation_deg):
        matrix = _rotation_matrix(*(math.radians(float(value)) for value in rotation_deg))
        moved = moved @ matrix.T
    return moved * float(scale) + np.array(pivot, dtype=float) + np.array(translate, dtype=float)


def box_faces(box: Box, skin_sheet: np.ndarray | None = None,
              alt_sheet: np.ndarray | None = None) -> list[Face]:
    """Return the six faces of a box, textured from the skin sheet or the box's own art."""
    x0, y0, z0, x1, y1, z1 = box.bounds()
    parts = box.parts or SKIN_PARTS
    faces: list[Face] = []
    for name in FACES:
        corners = np.array(face_corners(x0, y0, z0, x1, y1, z1, name), dtype=float)
        sheet: np.ndarray | None = None
        if box.uv_part and skin_sheet is not None:
            u, v, w, h = parts[box.uv_part].face_rect(name)
            sheet = skin_sheet
        elif box.uv_faces and name in box.uv_faces:
            u, v, w, h = box.uv_faces[name]
            sheet = box.texture if box.texture is not None else alt_sheet
        elif box.texture is not None:
            height, width = box.texture.shape[:2]
            u, v, w, h = 0, 0, width, height
            sheet = box.texture
        else:
            u, v, w, h = 0, 0, 1, 1
        uv = np.array([[u, v], [u + w, v], [u + w, v + h], [u, v + h]], dtype=float)
        faces.append(Face(corners, uv, sheet, box.color or (200, 200, 200), box.alpha,
                          FACE_SHADE[name]))
    return faces


# ---------------------------------------------------------------------------
# Camera and rasterizer
# ---------------------------------------------------------------------------
@dataclass
class Camera:
    """A look-at perspective camera; yaw 0 views the character from the front (+z)."""

    eye: np.ndarray
    right: np.ndarray
    up: np.ndarray
    forward: np.ndarray
    focal: float
    size: int

    @classmethod
    def look_at(cls, center: Sequence[float], radius: float, resolution: int, yaw_deg: float = 24.0,
                pitch_deg: float = 10.0, fov_deg: float = 26.0, supersample: int = 2,
                padding: float = 1.12, target: Sequence[float] | None = None) -> "Camera":
        size = resolution * supersample
        focal = (size / 2.0) / math.tan(math.radians(fov_deg) / 2.0)
        yaw = math.radians(yaw_deg)
        pitch = math.radians(pitch_deg)
        direction = np.array([math.sin(yaw) * math.cos(pitch), math.sin(pitch),
                              math.cos(yaw) * math.cos(pitch)], dtype=float)
        distance = max(radius, 1.0) * padding / math.tan(math.radians(fov_deg) / 2.0)
        look = np.array(target, dtype=float) if target is not None else np.array(center, dtype=float)
        eye = look + direction * distance
        forward = look - eye
        forward = forward / max(float(np.linalg.norm(forward)), 1e-9)
        right = np.cross(forward, np.array([0.0, 1.0, 0.0]))
        right = right / max(float(np.linalg.norm(right)), 1e-9)
        up = np.cross(right, forward)
        return cls(eye, right, up, forward, focal, size)

    def project(self, points: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
        shifted = points - self.eye
        x = shifted @ self.right
        y = shifted @ self.up
        depth = shifted @ self.forward
        safe = np.where(np.abs(depth) < 1e-6, 1e-6, depth)
        screen = np.stack([self.size / 2.0 + x * self.focal / safe,
                           self.size / 2.0 - y * self.focal / safe], axis=1)
        return screen, depth


def render_faces(faces: Sequence[Face], camera: Camera, silhouette: tuple[int, int, int, int] | None = None,
                 background: tuple[int, int, int, int] | None = None) -> np.ndarray:
    size = camera.size
    target = np.zeros((size, size, 4), dtype=np.uint8)
    if background is not None and background[3] > 0:
        target[:, :] = background
    zbuf = np.full((size, size), np.inf, dtype=np.float32)
    drawn: list[tuple[float, np.ndarray, np.ndarray, Face]] = []
    for face in faces:
        screen, depth = camera.project(face.points)
        if float(depth.min()) <= 1.0:
            continue
        drawn.append((float(depth.mean()), screen, depth, face))
    # Painter order first (so translucent faces composite back-to-front), z-buffer for correctness.
    for _, screen, depth, face in sorted(drawn, key=lambda item: -item[0]):
        _paint_face(target, zbuf, camera.size, screen, depth, face)
    if silhouette is not None and silhouette[3] > 0:
        target = _add_silhouette(target, silhouette)
    return target


def _add_silhouette(image: np.ndarray, color: Sequence[int]) -> np.ndarray:
    mask = image[..., 3] > 12
    grown = mask.copy()
    grown[1:, :] |= mask[:-1, :]
    grown[:-1, :] |= mask[1:, :]
    grown[:, 1:] |= mask[:, :-1]
    grown[:, :-1] |= mask[:, 1:]
    ring = grown & ~mask
    if ring.any():
        image = image.copy()
        image[ring, :3] = color[:3]
        image[ring, 3] = max(int(color[3]), int(image[ring, 3].max()))
    return image


def _paint_face(target: np.ndarray, zbuf: np.ndarray, size: int, screen: np.ndarray, depth: np.ndarray,
                face: Face) -> None:
    xs, ys = screen[:, 0], screen[:, 1]
    x0, x1 = max(0, int(math.floor(xs.min()))), min(size, int(math.ceil(xs.max())) + 1)
    y0, y1 = max(0, int(math.floor(ys.min()))), min(size, int(math.ceil(ys.max())) + 1)
    if x1 - x0 <= 0 or y1 - y0 <= 0:
        return
    gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
    inside = np.zeros(gx.shape, dtype=bool)
    weights = np.zeros(gx.shape + (4,), dtype=np.float32)
    for tri in ((0, 1, 2), (0, 2, 3)):
        i0, i1, i2 = tri
        p0, p1, p2 = screen[i0], screen[i1], screen[i2]
        d0, d1, d2 = depth[i0], depth[i1], depth[i2]
        denominator = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p2[0] - p0[0]) * (p1[1] - p0[1])
        if abs(denominator) < 1e-9:
            continue
        px, py = gx - p0[0], gy - p0[1]
        b1 = (px * (p2[1] - p0[1]) - py * (p2[0] - p0[0])) / denominator
        b2 = (py * (p1[0] - p0[0]) - px * (p1[1] - p0[1])) / denominator
        b0 = 1.0 - b1 - b2
        hit = (b1 >= -1e-6) & (b2 >= -1e-6) & (b0 >= -1e-6)
        inside |= hit
        weights[..., i0] = np.where(hit, np.maximum(b0, 0.0), weights[..., i0])
        weights[..., i1] = np.where(hit, np.maximum(b1, 0.0), weights[..., i1])
        weights[..., i2] = np.where(hit, np.maximum(b2, 0.0), weights[..., i2])
    if not inside.any():
        return
    corner_uv = face.uv
    u = float(corner_uv[0, 0]) + _lerp_values(corner_uv, 0, weights)
    v = float(corner_uv[0, 1]) + _lerp_values(corner_uv, 1, weights)
    z = _lerp_scalar(depth, weights)
    tex_x = np.floor(u).astype(np.int64)
    tex_y = np.floor(v).astype(np.int64)
    if face.texture is None:
        src = np.zeros(gx.shape + (4,), dtype=np.uint8)
        src[..., :3] = face.color
        src[..., 3] = face.alpha
    else:
        height, width = face.texture.shape[:2]
        valid = (tex_x >= 0) & (tex_y >= 0) & (tex_x < width) & (tex_y < height)
        inside &= valid
        if not inside.any():
            return
        sampled = face.texture[np.clip(tex_y, 0, height - 1), np.clip(tex_x, 0, width - 1)]
        src = np.where(inside[..., None], sampled, 0)
        src = np.maximum(src, 0)
    alpha = src[..., 3].astype(np.float32) * (face.alpha / 255.0)
    use = inside & (alpha >= 8.0) & (z <= zbuf[y0:y1, x0:x1])
    if not use.any():
        return
    rows, cols = np.nonzero(use)
    px, py = y0 + rows, x0 + cols
    a = (alpha[rows, cols] / 255.0)[:, None]
    rgb = src[rows, cols, :3].astype(np.float32) * face.shade
    current = target[px, py]
    blended = rgb * a + current[:, :3].astype(np.float32) * (1.0 - a)
    target[px, py, :3] = np.clip(blended, 0, 255).astype(np.uint8)
    target[px, py, 3] = np.clip(alpha[rows, cols] + current[:, 3] * (1.0 - a[:, 0]), 0, 255).astype(np.uint8)
    zbuf[px, py] = z[rows, cols]


def _lerp_values(corner_uv: np.ndarray, axis: int, weights: np.ndarray) -> np.ndarray:
    total = np.zeros(weights.shape[:2], dtype=np.float32)
    for index in range(4):
        total = total + weights[..., index] * float(corner_uv[index, axis] - corner_uv[0, axis])
    return total


def _lerp_scalar(values: np.ndarray, weights: np.ndarray) -> np.ndarray:
    total = np.zeros(weights.shape[:2], dtype=np.float32)
    for index in range(4):
        total = total + weights[..., index] * float(values[index])
    return total


# ---------------------------------------------------------------------------
# Scene assembly
# ---------------------------------------------------------------------------
@dataclass
class Attachment:
    """Solid or self-textured geometry that rides along with a player layer (horns, wings, a pet
    on the shoulder, a prop in hand, aura motes)."""

    box: Box
    layer: str = "body"
    rotation: tuple[float, float, float] = (0.0, 0.0, 0.0)
    translate: tuple[float, float, float] = (0.0, 0.0, 0.0)
    pivot: tuple[float, float, float] = (0.0, 0.0, 0.0)


# Which parts of the model each category's card art should frame, and from which angle.
CATEGORY_FRAMING: dict[str, dict[str, Any]] = {
    "hat": {"layers": ("head", "hat", "jacket", "body", "left_arm", "left_arm_hat",
                        "right_arm", "right_arm_hat"),
            "yaw": 24.0, "pitch": 12.0, "fov": 24.0},
    "suit": {"layers": tuple(layer.name for layer in PLAYER_LAYERS),
             "yaw": 22.0, "pitch": 7.0, "fov": 26.0},
    "back": {"layers": tuple(layer.name for layer in PLAYER_LAYERS),
             "yaw": 156.0, "pitch": 10.0, "fov": 26.0},
    "pet": {"layers": tuple(layer.name for layer in PLAYER_LAYERS),
            "yaw": 20.0, "pitch": 12.0, "fov": 25.0},
    "emote": {"layers": ("head", "hat", "body", "jacket", "left_arm", "left_arm_hat",
                         "right_arm", "right_arm_hat", "left_leg", "right_leg",
                         "left_leg_hat", "right_leg_hat"),
              "yaw": 28.0, "pitch": 10.0, "fov": 25.0},
    "aura": {"layers": tuple(layer.name for layer in PLAYER_LAYERS),
             "yaw": 16.0, "pitch": 11.0, "fov": 27.0},
}


def build_character_faces(skin_sheet: np.ndarray, cape_sheet: np.ndarray | None,
                          pose: dict[str, tuple[float, float, float]],
                          layers: Sequence[str]) -> list[Face]:
    """The player model itself, textured with the composited skin (and cape) sheets."""
    faces: list[Face] = []
    for layer in PLAYER_LAYERS:
        if layer.name not in layers:
            continue
        if layer.name == "cape":
            if cape_sheet is None:
                continue
            sheet = cape_sheet
            parts = CAPE_PARTS
        else:
            sheet = skin_sheet
            parts = SKIN_PARTS
        box = Box(origin=layer.origin, size=layer.size, uv_part=layer.part, inflate=layer.inflate,
                  parts=parts)
        rotation = pose.get(layer.name, (0.0, 0.0, 0.0))
        for face in box_faces(box, skin_sheet=sheet):
            face.points = transform(face.points, layer.pivot, rotation, (0.0, 0.0, 0.0))
            faces.append(face)
    return faces


def layer_anchor(layer: Layer) -> tuple[float, float, float]:
    """Local origin for geometry riding a layer: x/z centred on the limb, y at its base."""
    x0, y0, z0 = layer.origin
    width, _height, depth = layer.size
    return (x0 + width / 2.0, y0, z0 + depth / 2.0)


def build_attachment_faces(attachments: Sequence[Attachment],
                           pose: dict[str, tuple[float, float, float]],
                           skin_sheet: np.ndarray) -> list[Face]:
    """Attachment geometry is authored in its layer's local space: x/z measured from the limb axis,
    y from the limb's base, so horns at y = 8 sit on the crown and a collar at y = 11 on the torso."""
    faces: list[Face] = []
    for attachment in attachments:
        box = attachment.box
        owner = LAYER_BY_NAME.get(attachment.layer)
        for face in box_faces(box, skin_sheet=skin_sheet if box.uv_part else None):
            points = transform(face.points, attachment.pivot, attachment.rotation, attachment.translate)
            if owner is not None:
                points = points + np.array(layer_anchor(owner), dtype=float)
                points = transform(points, owner.pivot, pose.get(owner.name, (0.0, 0.0, 0.0)),
                                   (0.0, 0.0, 0.0))
            face.points = points
            faces.append(face)
    return faces


def model_bounds(faces: Sequence[Face]) -> tuple[np.ndarray, np.ndarray]:
    if not faces:
        zero = np.zeros(3)
        return zero, zero + 1.0
    stacked = np.concatenate([face.points for face in faces], axis=0)
    return stacked.min(axis=0), stacked.max(axis=0)


# ---------------------------------------------------------------------------
# Image composition
# ---------------------------------------------------------------------------
def resize_premultiplied(image: np.ndarray, width: int, height: int) -> np.ndarray:
    """Bilinear resize that keeps transparent edges clean (no colour fringing)."""
    from PIL import Image

    source = image.astype(np.float32)
    alpha = source[..., 3:4] / 255.0
    premultiplied = np.dstack([np.clip(source[..., :3] * alpha, 0, 255), alpha * 255.0])
    resized = np.asarray(
        Image.fromarray(premultiplied.astype(np.uint8), "RGBA").resize((width, height), Image.Resampling.BILINEAR),
        dtype=np.float32)
    out_alpha = resized[..., 3:4]
    safe = np.where(out_alpha > 1.0, out_alpha / 255.0, 1.0)
    rgb = np.where(out_alpha > 1.0, resized[..., :3] / safe, 0.0)
    return np.clip(np.dstack([rgb, out_alpha]), 0, 255).astype(np.uint8)


def compose_preview_image(rendered: np.ndarray, size: int, *, padding: float = 0.06,
                          ground_shadow: bool = True, min_scale: float = 0.25,
                          max_scale: float = 4.0) -> np.ndarray:
    """Crop the render to its content, scale it into a `size` square, and ground it with a shadow."""
    mask = rendered[..., 3] > 12
    if not mask.any():
        return np.zeros((size, size, 4), dtype=np.uint8)
    rows = np.nonzero(mask.any(axis=1))[0]
    cols = np.nonzero(mask.any(axis=0))[0]
    top, bottom = int(rows[0]), int(rows[-1]) + 1
    left, right = int(cols[0]), int(cols[-1]) + 1
    crop = rendered[top:bottom, left:right]
    crop_h = max(1, bottom - top)
    crop_w = max(1, right - left)
    inner = max(8, int(round(size * (1.0 - 2.0 * padding))))
    scale = min(inner / crop_w, inner / crop_h)
    scale = max(min_scale, min(max_scale, scale))
    target_w = max(1, int(round(crop_w * scale)))
    target_h = max(1, int(round(crop_h * scale)))
    placed = resize_premultiplied(crop, target_w, target_h)

    canvas = np.zeros((size, size, 4), dtype=np.uint8)
    if ground_shadow:
        centre_x = size // 2
        centre_y = size // 2 + target_h // 2 - 1
        radius_x = max(4, int(target_w * 0.40))
        radius_y = max(2, int(radius_x * 0.22))
        yy, xx = np.mgrid[0:size, 0:size]
        distance = np.sqrt(((xx - centre_x) / radius_x) ** 2 + ((yy - centre_y) / radius_y) ** 2)
        falloff = np.clip(1.0 - distance, 0.0, 1.0)
        canvas[..., 3] = np.maximum(canvas[..., 3], (falloff * 96.0).astype(np.uint8))
        canvas[..., :3] = 12
    x0 = (size - target_w) // 2
    y0 = (size - target_h) // 2
    x1, y1 = x0 + target_w, y0 + target_h
    if x0 < 0 or y0 < 0:
        crop_box = (max(0, -x0), max(0, -y0), min(target_w, target_w - (size - x1)),
                    min(target_h, target_h - (size - y1)))
        placed = placed[crop_box[1]:crop_box[1] + crop_box[3], crop_box[0]:crop_box[0] + crop_box[2]]
        x0, y0 = 0, 0
        x1, y1 = x0 + placed.shape[1], y0 + placed.shape[0]
    region = canvas[y0:y1, x0:x1]
    src = placed.astype(np.float32)
    a = src[..., 3:4] / 255.0
    out_rgb = src[..., :3] * a + region[..., :3].astype(np.float32) * (1.0 - a)
    region[..., :3] = np.clip(out_rgb, 0, 255).astype(np.uint8)
    region[..., 3] = np.clip(src[..., 3] + region[..., 3] * (1.0 - a[..., 0]), 0, 255).astype(np.uint8)
    return canvas


def render_character_preview(size: int, skin_sheet: np.ndarray,
                             attachments: Sequence[Attachment], *, category: str = "suit",
                             cape_sheet: np.ndarray | None = None,
                             pose: dict[str, tuple[float, float, float]] | None = None,
                             supersample: int = 3,
                             silhouette: tuple[int, int, int, int] | None = (10, 12, 16, 210),
                             extra_faces: Sequence[Face] | None = None) -> np.ndarray:
    """Render a character wearing the composited skin, framed for the item's category."""
    framing = CATEGORY_FRAMING.get(category, CATEGORY_FRAMING["suit"])
    active_pose = dict(DEFAULT_POSE)
    if pose is not None:
        active_pose.update(pose)
    layers = tuple(name for name in (layer.name for layer in PLAYER_LAYERS) if name in framing["layers"])
    faces = build_character_faces(skin_sheet, cape_sheet, active_pose, layers)
    faces.extend(build_attachment_faces(attachments, active_pose, skin_sheet))
    if extra_faces:
        faces.extend(extra_faces)
    low, high = model_bounds(faces)
    centre = (low + high) / 2.0
    radius = float(np.max(high - low)) / 2.0
    camera = Camera.look_at(centre, radius, size, yaw_deg=float(framing["yaw"]),
                            pitch_deg=float(framing["pitch"]), fov_deg=float(framing["fov"]),
                            supersample=supersample, padding=1.18)
    rendered = render_faces(faces, camera, silhouette=silhouette)
    return compose_preview_image(rendered, size)
