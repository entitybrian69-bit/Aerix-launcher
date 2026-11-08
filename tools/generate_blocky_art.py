#!/usr/bin/env python3
"""Regenerates the Blocky Shell artwork.

Everything is drawn on a tiny pixel grid and scaled with NEAREST so every edge stays a hard
square step: no gradients, no antialiasing, no blur. That is the "blocky look" rule.

Outputs:
  app_pojavlauncher/src/main/assets/wallpapers/wp_27_blocky_plaza.jpg  (crisp voxel plaza)
  app_pojavlauncher/src/main/res/drawable-nodpi/aerix_blocky_ic_*.png   (white pixel icons)
  app_pojavlauncher/src/main/res/drawable-nodpi/aerix_blocky_sun.png     (stepped sunburst)
  preview/blocky-player.png                                              (static preview stand-in)

Run:  PYTHONPATH=/tmp/pillowlib python3 tools/generate_blocky_art.py
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
WALLPAPERS = ROOT / "app_pojavlauncher/src/main/assets/wallpapers"
DRAWABLE = ROOT / "app_pojavlauncher/src/main/res/drawable-nodpi"
PREVIEW = ROOT / "preview"

WHITE = (255, 255, 255, 255)
INK = (8, 9, 13, 255)
CLEAR = (0, 0, 0, 0)


# --------------------------------------------------------------------------------------
# Crisp voxel plaza: 160x90 source pixels, x8 NEAREST -> 1280x720.
# --------------------------------------------------------------------------------------
def build_plaza() -> Image.Image:
    w, h = 160, 90
    img = Image.new("RGB", (w, h), (108, 190, 240))
    d = ImageDraw.Draw(img)

    sky = [(30, 126, 214), (48, 148, 226), (74, 170, 234), (108, 194, 242), (146, 214, 246)]
    for i, color in enumerate(sky):
        y0 = i * 11
        d.rectangle([0, y0, w, y0 + 10], fill=color)

    # Stepped sun.
    d.rectangle([118, 8, 138, 28], fill=(255, 226, 120))
    d.rectangle([122, 4, 134, 8], fill=(255, 238, 168))
    d.rectangle([122, 28, 134, 32], fill=(255, 238, 168))
    d.rectangle([114, 12, 118, 24], fill=(255, 238, 168))
    d.rectangle([138, 12, 142, 24], fill=(255, 238, 168))

    # Blocky clouds: pure rectangles, hard edges.
    for x, y, cw, ch in [(8, 12, 26, 5), (14, 7, 14, 5), (54, 20, 30, 5),
                         (62, 15, 16, 5), (98, 36, 24, 4), (4, 34, 20, 4)]:
        d.rectangle([x, y, x + cw, y + ch], fill=(236, 246, 252))
        d.rectangle([x + 3, y + ch, x + cw - 3, y + ch + 2], fill=(214, 233, 246))

    # Distant skyline: stepped voxel towers.
    skyline = [
        (0, 44, 14, (64, 116, 158)), (14, 38, 10, (76, 130, 172)),
        (24, 47, 12, (58, 108, 150)), (36, 34, 14, (84, 140, 184)),
        (50, 42, 10, (66, 120, 162)), (60, 30, 12, (92, 150, 192)),
        (72, 40, 14, (70, 126, 168)), (86, 36, 10, (86, 144, 188)),
        (96, 46, 14, (62, 112, 154)), (110, 40, 12, (80, 136, 180)),
        (122, 48, 16, (58, 106, 148)), (138, 38, 22, (88, 146, 190)),
    ]
    for x, y, cw, color in skyline:
        d.rectangle([x, y, x + cw, 62], fill=color)
        d.rectangle([x, y - 2, x + cw, y], fill=tuple(min(255, c + 22) for c in color))
        for wy in range(y + 5, 60, 7):
            for wx in range(x + 2, x + cw - 2, 5):
                d.rectangle([wx, wy, wx + 2, wy + 3], fill=(252, 226, 150))

    # Mid-ground blocky buildings with stepped roofs.
    buildings = [
        (4, 46, 30, (198, 146, 96), (150, 96, 62)),
        (38, 52, 26, (214, 172, 118), (166, 112, 72)),
        (68, 40, 30, (188, 132, 92), (138, 88, 58)),
        (102, 50, 28, (222, 182, 128), (172, 120, 78)),
        (132, 44, 26, (202, 152, 102), (152, 100, 66)),
    ]
    for x, y, bw, face, roof in buildings:
        d.rectangle([x, y, x + bw, 64], fill=face)
        d.rectangle([x - 2, y - 4, x + bw + 2, y], fill=roof)
        d.rectangle([x + 3, y + 4, x + 9, y + 12], fill=(96, 176, 204))
        d.rectangle([x + bw - 10, y + 4, x + bw - 4, y + 12], fill=(96, 176, 204))
        door_top = min(y + 18, 62)
        d.rectangle([x + bw // 2 - 4, door_top, x + bw // 2 + 4, 64], fill=(112, 76, 52))
        d.rectangle([x, 62, x + bw, 64], fill=roof)

    # Ground: grass band, dirt blocks, path, and the stepped spikes from the sketch.
    d.rectangle([0, 64, w, 69], fill=(104, 186, 74))
    d.rectangle([0, 69, w, 90], fill=(126, 92, 60))
    for y in range(70, 90, 6):
        for x in range(0, w, 12):
            off = 6 if (y // 6) % 2 else 0
            d.rectangle([x + off, y, x + off + 10, y + 4], fill=(150, 110, 72))
            d.rectangle([x + off + 1, y + 1, x + off + 4, y + 2], fill=(170, 128, 84))

    # Voxel grass spikes along the bottom edge (blocky triangles = stacks of squares).
    for x in range(0, w, 10):
        peak = 12 + (x * 7) % 10
        for step in range(0, peak, 3):
            half = min(4, max(0, (peak - step) // 2))
            d.rectangle([x + half, 64 - step, x + 10 - half, 64 - step + 2],
                        fill=(72, 156, 62))
        d.rectangle([x, 64, x + 9, 66], fill=(88, 172, 70))

    # Path of square slabs in front.
    for x in range(0, w, 16):
        d.rectangle([x, 78, x + 14, 84], fill=(196, 186, 164))
        d.rectangle([x + 1, 78, x + 13, 79], fill=(220, 212, 192))

    # A couple of square bushes / cubes for the "voxel build" feel.
    for x, y, s in [(20, 58, 7), (94, 56, 9), (146, 58, 6)]:
        d.rectangle([x, y, x + s, y + s], fill=(76, 156, 66))
        d.rectangle([x, y, x + s, y + 2], fill=(104, 190, 82))
        d.rectangle([x, y, x + 2, y + s], fill=(58, 126, 52))

    return img.resize((1280, 720), Image.Resampling.NEAREST)


# --------------------------------------------------------------------------------------
# White pixel icons on transparent background, with a hard dark offset shadow.
# --------------------------------------------------------------------------------------
def r(d, x1, y1, x2, y2, color=WHITE):
    d.rectangle([x1, y1, x2, y2], fill=color)


def p(d, points, color=WHITE):
    d.polygon(points, fill=color)


GLYPHS = {
    "play": lambda d: p(d, [(3, 2), (3, 14), (14, 8)]),
    "wardrobe": lambda d: (
        r(d, 6, 2, 9, 3), r(d, 7, 4, 8, 5),
        p(d, [(2, 6), (7, 5), (13, 6), (13, 9), (11, 9), (11, 7), (5, 7), (5, 9), (2, 9)]),
        r(d, 4, 10, 11, 14), r(d, 6, 11, 9, 14, CLEAR)),
    "friends": lambda d: (
        r(d, 2, 3, 6, 7), r(d, 1, 8, 7, 13),
        r(d, 10, 3, 14, 7), r(d, 9, 8, 15, 13)),
    "mod": lambda d: (
        r(d, 3, 4, 12, 13), r(d, 3, 4, 12, 5), r(d, 3, 4, 4, 13),
        r(d, 6, 7, 9, 10, CLEAR), r(d, 11, 12, 13, 14)),
    "chest": lambda d: (
        r(d, 2, 5, 13, 14), r(d, 2, 5, 13, 7), r(d, 2, 8, 13, 9, INK),
        r(d, 7, 8, 9, 11), r(d, 3, 3, 12, 4)),
    "help": lambda d: (
        r(d, 4, 3, 11, 4), r(d, 10, 4, 12, 8), r(d, 7, 7, 10, 9),
        r(d, 7, 9, 9, 11), r(d, 7, 12, 9, 14)),
    "settings": lambda d: (
        r(d, 6, 1, 9, 14), r(d, 1, 6, 14, 9), r(d, 3, 3, 12, 12),
        r(d, 5, 5, 10, 10, CLEAR), r(d, 6, 6, 9, 9)),
    "library": lambda d: (
        r(d, 2, 3, 5, 13), r(d, 6, 4, 9, 13), r(d, 10, 2, 13, 13),
        r(d, 2, 2, 5, 3), r(d, 6, 3, 9, 4), r(d, 10, 1, 13, 2)),
    "discover": lambda d: (
        p(d, [(7, 1), (13, 7), (7, 13), (1, 7)]),
        p(d, [(7, 4), (10, 7), (7, 10), (4, 7)], CLEAR),
        r(d, 7, 6, 8, 8)),
    "accounts": lambda d: (r(d, 5, 2, 11, 8), r(d, 2, 9, 14, 14), r(d, 6, 10, 10, 14, CLEAR)),
    "grid": lambda d: [r(d, x, y, x + 3, y + 3) for x in (1, 6, 11) for y in (1, 6, 11)],
    "close": lambda d: (p(d, [(2, 1), (4, 1), (8, 6), (12, 1), (14, 1), (14, 3), (10, 8),
                              (14, 13), (14, 15), (12, 15), (8, 10), (4, 15), (2, 15),
                              (2, 13), (6, 8), (2, 3)])),
    "sound": lambda d: (r(d, 2, 6, 5, 10), p(d, [(5, 6), (8, 3), (8, 13), (5, 10)]),
                        r(d, 10, 5, 11, 11), r(d, 12, 3, 13, 13)),
    "dance": lambda d: (r(d, 6, 1, 9, 5), r(d, 2, 2, 4, 7), r(d, 11, 2, 13, 7),
                        r(d, 5, 6, 10, 10), r(d, 5, 10, 7, 14), r(d, 8, 10, 11, 13)),
    "jump": lambda d: (p(d, [(7, 0), (13, 6), (10, 6), (10, 10), (5, 10), (5, 6), (2, 6)]),
                       r(d, 3, 12, 12, 14)),
    "wave": lambda d: (r(d, 6, 6, 9, 14), r(d, 3, 2, 5, 8), r(d, 7, 1, 9, 5),
                       r(d, 10, 2, 12, 7), r(d, 12, 5, 14, 9)),
    "action": lambda d: (p(d, [(2, 3), (13, 1), (14, 4), (5, 7)]),
                         r(d, 7, 6, 9, 14), r(d, 6, 13, 10, 15)),
    "invite": lambda d: (r(d, 4, 2, 9, 7), r(d, 2, 8, 11, 13),
                         r(d, 12, 3, 14, 8), r(d, 11, 5, 15, 7)),
    "guide": lambda d: (r(d, 7, 1, 9, 15), r(d, 1, 7, 15, 9),
                        r(d, 4, 4, 12, 12), r(d, 7, 1, 9, 15), r(d, 1, 7, 15, 9)),
    "classic": lambda d: (p(d, [(7, 1), (7, 13), (2, 8)]), r(d, 7, 6, 14, 9)),
    "search": lambda d: (r(d, 2, 2, 10, 11), r(d, 4, 4, 8, 9, CLEAR),
                         r(d, 10, 10, 14, 14)),
    "gift": lambda d: (r(d, 2, 6, 14, 14), r(d, 7, 6, 9, 14),
                       r(d, 2, 4, 14, 6), r(d, 5, 2, 7, 4), r(d, 9, 2, 11, 4)),
    "excl": lambda d: (r(d, 6, 2, 10, 10), r(d, 6, 12, 10, 14)),
    "headset": lambda d: (r(d, 5, 3, 11, 10), r(d, 2, 5, 4, 11),
                          r(d, 12, 5, 14, 11), r(d, 2, 3, 14, 5), r(d, 12, 10, 14, 14)),
    "cursor": lambda d: p(d, [(2, 1), (2, 14), (6, 10), (9, 15), (11, 13), (8, 8), (13, 8)]),
    "person_add": lambda d: (r(d, 3, 2, 8, 7), r(d, 1, 8, 10, 13),
                             r(d, 11, 4, 13, 10), r(d, 10, 6, 14, 8)),
    "sun": lambda d: (r(d, 5, 5, 10, 10), r(d, 7, 1, 8, 4), r(d, 7, 11, 8, 14),
                      r(d, 1, 7, 4, 8), r(d, 11, 7, 14, 8),
                      r(d, 2, 2, 4, 4), r(d, 11, 11, 13, 13)),
    "folder": lambda d: (r(d, 1, 4, 14, 14), r(d, 1, 2, 7, 4), r(d, 3, 7, 12, 9)),
    "globe": lambda d: (r(d, 4, 2, 11, 4), r(d, 2, 5, 13, 10), r(d, 4, 11, 11, 13),
                        r(d, 7, 4, 8, 12), r(d, 3, 7, 12, 8)),
}


def build_icon(fn) -> Image.Image:
    base = Image.new("RGBA", (16, 16), CLEAR)
    d = ImageDraw.Draw(base)
    fn(d)
    shadow = Image.new("RGBA", (16, 16), CLEAR)
    sd = ImageDraw.Draw(shadow)
    sd.rectangle([0, 0, 15, 15], fill=CLEAR)
    # Hard one-pixel offset silhouette: blocky, never blurred.
    alpha = base.getchannel("A")
    shifted = Image.new("RGBA", (16, 16), CLEAR)
    shifted.paste(base, (1, 1))
    mask = shifted.getchannel("A")
    ink = Image.new("RGBA", (16, 16), INK)
    shadow = Image.composite(ink, shadow, mask)
    out = Image.alpha_composite(shadow, base)
    del alpha, sd
    return out.resize((96, 96), Image.Resampling.NEAREST)


def build_sunburst() -> Image.Image:
    """Stepped hanging rays, matching the top of the sketch."""
    img = Image.new("RGBA", (256, 96), CLEAR)
    d = ImageDraw.Draw(img)
    for i in range(8):
        x = i * 32
        depth = 44 + (i % 3) * 18
        d.rectangle([x + 4, 0, x + 26, depth - 12], fill=(255, 214, 92, 255))
        d.rectangle([x + 9, depth - 12, x + 21, depth], fill=(255, 190, 64, 255))
        d.rectangle([x + 4, 0, x + 26, 6], fill=(255, 238, 160, 255))
    return img


def build_preview_player() -> Image.Image:
    """A shaded blocky stand-in for the static PNG preview (the app uses a real 3D model)."""
    img = Image.new("RGBA", (280, 460), CLEAR)
    d = ImageDraw.Draw(img)
    # Soft dark ground shadow, drawn as hard steps.
    d.rectangle([54, 420, 226, 434], fill=(10, 14, 22, 90))
    # Cape sits BEHIND the player, so the model still reads as a solid 3D figure.
    d.polygon([(78, 140), (202, 140), (216, 326), (64, 326)], fill=(150, 34, 48, 235))
    d.rectangle([78, 140, 202, 152], fill=(196, 54, 70, 235))
    d.polygon([(202, 140), (216, 326), (196, 326), (190, 140)], fill=(108, 24, 36, 235))
    # Head
    d.rectangle([86, 30, 194, 138], fill=(214, 162, 116))
    d.rectangle([86, 30, 194, 48], fill=(96, 62, 42))
    d.rectangle([86, 118, 194, 138], fill=(176, 124, 84))
    d.rectangle([110, 76, 126, 92], fill=(38, 46, 66))
    d.rectangle([154, 76, 170, 92], fill=(38, 46, 66))
    d.rectangle([126, 108, 154, 116], fill=(140, 86, 66))
    # Body
    d.rectangle([86, 138, 194, 278], fill=(56, 132, 196))
    d.rectangle([86, 138, 194, 156], fill=(76, 158, 220))
    d.rectangle([126, 156, 154, 278], fill=(38, 96, 152))
    # Arms
    d.rectangle([46, 140, 84, 278], fill=(48, 116, 176))
    d.rectangle([196, 140, 234, 278], fill=(48, 116, 176))
    d.rectangle([46, 140, 84, 158], fill=(72, 148, 206))
    d.rectangle([196, 140, 234, 158], fill=(72, 148, 206))
    d.rectangle([46, 262, 84, 278], fill=(206, 152, 108))
    d.rectangle([196, 262, 234, 278], fill=(206, 152, 108))
    # Legs
    d.rectangle([86, 278, 136, 420], fill=(52, 68, 108))
    d.rectangle([144, 278, 194, 420], fill=(52, 68, 108))
    d.rectangle([86, 404, 136, 420], fill=(38, 42, 52))
    d.rectangle([144, 404, 194, 420], fill=(38, 42, 52))
    # Hard side shading keeps the figure reading as a volume, not a flat sprite.
    d.rectangle([188, 30, 194, 138], fill=(164, 118, 82))
    d.rectangle([188, 138, 194, 278], fill=(38, 96, 142))
    d.rectangle([228, 140, 234, 278], fill=(36, 88, 138))
    d.rectangle([130, 278, 136, 420], fill=(38, 50, 82))
    d.rectangle([188, 278, 194, 420], fill=(38, 50, 82))
    return img


def main() -> None:
    WALLPAPERS.mkdir(parents=True, exist_ok=True)
    DRAWABLE.mkdir(parents=True, exist_ok=True)
    PREVIEW.mkdir(parents=True, exist_ok=True)

    build_plaza().save(WALLPAPERS / "wp_27_blocky_plaza.jpg", quality=92, subsampling=0)
    for name, fn in GLYPHS.items():
        build_icon(fn).save(DRAWABLE / f"aerix_blocky_ic_{name}.png")
    build_sunburst().save(DRAWABLE / "aerix_blocky_sun.png")
    build_preview_player().save(PREVIEW / "blocky-player.png")
    print(f"wrote plaza + {len(GLYPHS)} icons + sunburst + preview player")


if __name__ == "__main__":
    main()
