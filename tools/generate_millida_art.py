#!/usr/bin/env python3
"""Regenerates the Millida-style pixel icons used by the Aerix Millida shell.

Millida's chrome is near-black with small, fully-coloured 16x16 pixel icons that carry a hard,
one-pixel dark outline. Everything is drawn on a 16x16 grid and scaled with NEAREST, so edges stay
square steps (no antialiasing, no blur).

Outputs: app_pojavlauncher/src/main/res/drawable-nodpi/aerix_millida_ic_*.png

Run:  PYTHONPATH=/tmp/pillowlib python3 tools/generate_millida_art.py
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app_pojavlauncher/src/main/res/drawable-nodpi"

INK = (10, 10, 12, 255)
CLEAR = (0, 0, 0, 0)
GREEN = (92, 214, 92, 255)
GREEN_DARK = (46, 125, 46, 255)
GRASS = (92, 178, 72, 255)
DIRT = (136, 92, 58, 255)
BROWN = (178, 118, 62, 255)
BROWN_DARK = (116, 72, 36, 255)
ORANGE = (232, 160, 48, 255)
BLUE = (56, 112, 208, 255)
BLUE_DARK = (28, 62, 140, 255)
CYAN = (64, 196, 214, 255)
RED = (206, 54, 74, 255)
PINK = (224, 76, 126, 255)
WHITE = (240, 242, 246, 255)
GREY = (118, 122, 130, 255)
GREY_DARK = (66, 70, 78, 255)
GOLD = (246, 200, 72, 255)


def grass(d):
    d.rectangle([1, 1, 14, 7], fill=GRASS)
    d.rectangle([1, 1, 14, 2], fill=(126, 208, 96, 255))
    d.rectangle([1, 8, 14, 14], fill=DIRT)
    for x, y in ((3, 10), (8, 12), (12, 9), (5, 13)):
        d.rectangle([x, y, x + 1, y + 1], fill=BROWN_DARK)
    d.rectangle([1, 1, 14, 14], outline=INK, width=1)


def book(d):
    d.rectangle([2, 2, 12, 13], fill=ORANGE)
    d.rectangle([4, 2, 12, 13], fill=(206, 132, 36, 255))
    d.rectangle([2, 2, 12, 3], fill=(250, 196, 92, 255))
    d.rectangle([5, 5, 10, 6], fill=WHITE)
    d.rectangle([5, 8, 9, 9], fill=WHITE)
    d.rectangle([2, 2, 12, 13], outline=INK, width=1)


def player(d):
    d.rectangle([4, 1, 11, 3], fill=(78, 50, 32, 255))
    d.rectangle([4, 4, 11, 10], fill=(196, 146, 104, 255))
    d.rectangle([5, 6, 6, 7], fill=BLUE)
    d.rectangle([9, 6, 10, 7], fill=BLUE)
    d.rectangle([6, 9, 9, 9], fill=(140, 84, 66, 255))
    d.rectangle([3, 11, 12, 14], fill=CYAN)
    d.rectangle([3, 11, 5, 14], fill=(36, 142, 166, 255))
    d.rectangle([4, 1, 11, 10], outline=INK, width=1)
    d.rectangle([3, 11, 12, 14], outline=INK, width=1)


def emerald(d):
    d.polygon([(7, 1), (12, 4), (14, 9), (7, 15), (1, 9), (3, 4)], fill=GREEN_DARK)
    d.polygon([(7, 2), (11, 5), (12, 9), (7, 13), (3, 9), (4, 5)], fill=GREEN)
    d.polygon([(7, 2), (11, 5), (7, 7), (4, 5)], fill=(158, 240, 144, 255))
    d.polygon([(1, 9), (7, 15), (14, 9), (12, 9), (7, 13), (3, 9)], outline=INK)


def dots(d):
    for x in (2, 7, 12):
        d.rectangle([x, 6, x + 2, 9], fill=GREY)
        d.rectangle([x, 6, x + 2, 9], outline=INK, width=1)


def chest(d):
    d.rectangle([2, 5, 13, 14], fill=BROWN)
    d.rectangle([2, 3, 13, 6], fill=BROWN_DARK)
    d.rectangle([3, 7, 12, 8], fill=(92, 56, 28, 255))
    d.rectangle([7, 7, 9, 11], fill=GOLD)
    d.rectangle([2, 5, 13, 14], outline=INK, width=1)
    d.rectangle([2, 3, 13, 6], outline=INK, width=1)


def friends(d):
    d.rectangle([1, 3, 7, 9], fill=(216, 168, 120, 255))
    d.rectangle([9, 3, 15, 9], fill=(156, 196, 220, 255))
    d.rectangle([0, 10, 8, 14], fill=BLUE)
    d.rectangle([8, 10, 15, 14], fill=CYAN)
    d.rectangle([2, 5, 3, 6], fill=INK)
    d.rectangle([5, 5, 6, 6], fill=INK)
    d.rectangle([10, 5, 11, 6], fill=INK)
    d.rectangle([13, 5, 14, 6], fill=INK)


def settings(d):
    d.rectangle([5, 1, 10, 14], fill=GREY)
    d.rectangle([1, 5, 14, 10], fill=GREY)
    d.rectangle([3, 3, 12, 12], fill=GREY_DARK)
    d.rectangle([6, 6, 9, 9], fill=(30, 32, 36, 255))
    d.rectangle([5, 1, 10, 14], outline=INK, width=1)
    d.rectangle([1, 5, 14, 10], outline=INK, width=1)


def plus(d):
    d.rectangle([6, 2, 9, 13], fill=GREEN)
    d.rectangle([2, 6, 13, 9], fill=GREEN)
    d.rectangle([6, 2, 9, 13], outline=GREEN_DARK, width=1)
    d.rectangle([2, 6, 13, 9], outline=GREEN_DARK, width=1)


def arrow(d):
    d.polygon([(5, 2), (5, 13), (13, 8)], fill=GREEN)
    d.polygon([(5, 2), (5, 13), (13, 8)], outline=GREEN_DARK)


def sparkle(d):
    d.rectangle([7, 1, 8, 14], fill=GOLD)
    d.rectangle([1, 7, 14, 8], fill=GOLD)
    d.rectangle([5, 5, 10, 10], fill=(255, 232, 150, 255))


def discord(d):
    d.rectangle([2, 4, 13, 11], fill=(88, 101, 242, 255))
    d.rectangle([4, 6, 6, 8], fill=WHITE)
    d.rectangle([9, 6, 11, 8], fill=WHITE)
    d.polygon([(2, 4), (5, 2), (11, 2), (13, 4)], fill=(110, 124, 250, 255))


def globe(d):
    d.rectangle([4, 1, 11, 3], fill=BLUE)
    d.rectangle([2, 4, 13, 11], fill=BLUE)
    d.rectangle([4, 12, 11, 14], fill=BLUE)
    d.rectangle([7, 3, 8, 12], fill=(140, 200, 240, 255))
    d.rectangle([3, 6, 12, 7], fill=(140, 200, 240, 255))
    d.rectangle([2, 4, 13, 11], outline=INK, width=1)


def gift(d):
    d.rectangle([2, 6, 13, 14], fill=RED)
    d.rectangle([2, 4, 13, 7], fill=(240, 96, 110, 255))
    d.rectangle([7, 4, 8, 14], fill=GOLD)
    d.rectangle([2, 6, 13, 14], outline=INK, width=1)


def skin(d):
    d.rectangle([4, 2, 11, 9], fill=(196, 146, 104, 255))
    d.rectangle([4, 1, 11, 3], fill=(78, 50, 32, 255))
    d.rectangle([3, 9, 12, 14], fill=CYAN)
    d.rectangle([5, 5, 6, 6], fill=INK)
    d.rectangle([9, 5, 10, 6], fill=INK)
    d.rectangle([4, 2, 11, 9], outline=INK, width=1)


def crown(d):
    d.polygon([(1, 11), (2, 3), (5, 7), (8, 2), (11, 7), (14, 3), (15, 11)], fill=GOLD)
    d.rectangle([1, 11, 14, 13], fill=(206, 152, 40, 255))
    d.polygon([(1, 11), (2, 3), (5, 7), (8, 2), (11, 7), (14, 3), (15, 11)], outline=INK)


def controller(d):
    d.rectangle([2, 5, 13, 11], fill=GREY_DARK)
    d.rectangle([1, 7, 4, 12], fill=GREY_DARK)
    d.rectangle([11, 7, 14, 12], fill=GREY_DARK)
    d.rectangle([4, 7, 5, 9], fill=WHITE)
    d.rectangle([4, 7, 7, 8], fill=WHITE)
    d.rectangle([10, 6, 11, 8], fill=RED)
    d.rectangle([2, 5, 13, 11], outline=INK, width=1)


def wrench(d):
    d.polygon([(2, 12), (9, 5), (11, 2), (14, 5), (11, 7), (4, 14)], fill=GREY)
    d.rectangle([9, 1, 14, 5], outline=INK)
    d.polygon([(2, 12), (9, 5), (11, 7), (4, 14)], outline=INK)


def skull(d):
    d.rectangle([3, 2, 12, 11], fill=WHITE)
    d.rectangle([4, 6, 6, 8], fill=INK)
    d.rectangle([9, 6, 11, 8], fill=INK)
    d.rectangle([6, 11, 9, 14], fill=WHITE)
    d.rectangle([3, 2, 12, 11], outline=INK, width=1)


GLYPHS = {
    "grass": grass, "book": book, "player": player, "emerald": emerald,
    "dots": dots, "chest": chest, "friends": friends, "settings": settings,
    "plus": plus, "arrow": arrow, "sparkle": sparkle, "discord": discord,
    "globe": globe, "gift": gift, "skin": skin, "crown": crown,
    "controller": controller, "wrench": wrench, "skull": skull,
}


def build(fn) -> Image.Image:
    base = Image.new("RGBA", (16, 16), CLEAR)
    fn(ImageDraw.Draw(base))
    # Hard one-pixel dark silhouette behind the icon: Millida icons never have soft shadows.
    shifted = Image.new("RGBA", (16, 16), CLEAR)
    shifted.paste(base, (1, 1))
    ink = Image.new("RGBA", (16, 16), INK)
    shadow = Image.composite(ink, shifted, shifted.getchannel("A"))
    out = Image.alpha_composite(shadow, base)
    return out.resize((96, 96), Image.Resampling.NEAREST)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in GLYPHS.items():
        build(fn).save(OUT / f"aerix_millida_ic_{name}.png")
    print(f"wrote {len(GLYPHS)} Millida icons")


if __name__ == "__main__":
    main()
