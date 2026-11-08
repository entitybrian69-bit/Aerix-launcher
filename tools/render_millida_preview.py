#!/usr/bin/env python3
"""Renders a static PNG of the Millida-style shell for design review.

The live, interactive preview (real WebGL character + working emotes) is preview/millida.html;
this is the still frame that can be attached to a PR or opened without a browser.

Run:  PYTHONPATH=/tmp/pillowlib python3 tools/render_millida_preview.py
"""

from pathlib import Path
import math

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app_pojavlauncher/src/main/res"
OUT = ROOT / "preview/millida-preview.png"

W, H = 1440, 900
BG = (20, 20, 22)
BAR = (16, 16, 18)
PANEL = (30, 30, 33)
SOFT = (41, 41, 45)
CHIP = (51, 51, 55)
INK = (8, 8, 10)
SHADOW = (7, 7, 8)
TEXT = (244, 244, 246)
MUTED = (166, 166, 174)
GREEN = (92, 214, 92)
GREEN_DARK = (47, 138, 52)
BLUE = (29, 78, 168)
BLUE_DARK = (16, 47, 108)
ORANGE = (184, 121, 26)
ORANGE_DARK = (122, 76, 16)
PINK = (179, 32, 75)
PINK_DARK = (116, 20, 49)
PURPLE = (91, 61, 158)
TEAL = (23, 122, 122)
TEAL_DARK = (14, 79, 79)
YELLOW = (232, 200, 74)
SCENE = (16, 58, 24)
SCENE_GLOW = (44, 122, 50)
GRASS = (60, 156, 62)
GRASS_DARK = (29, 90, 34)


def font(size: int):
    return ImageFont.truetype(str(RES / "font/minecraft_ten.ttf"), size)


def micon(name: str, size: int) -> Image.Image:
    im = Image.open(RES / f"drawable-nodpi/aerix_millida_ic_{name}.png").convert("RGBA")
    return im.resize((size, size), Image.Resampling.NEAREST)


def notch_points(box, step=8):
    x0, y0, x1, y1 = box
    return [
        (x0 + step, y0), (x1 - step, y0),
        (x1 - step, y0 + step), (x1, y0 + step),
        (x1, y1 - step), (x1 - step, y1 - step),
        (x1 - step, y1), (x0 + step, y1),
        (x0 + step, y1 - step), (x0, y1 - step),
        (x0, y0 + step), (x0 + step, y0 + step),
    ]


def notched(d, box, fill, step=8, shadow=6, outline=INK, width=2):
    pts = notch_points(box, step)
    if shadow:
        d.polygon([(x + shadow, y + shadow) for x, y in pts], fill=SHADOW)
    d.polygon(pts, fill=fill)
    if outline and width:
        d.line(pts + [pts[0]], fill=outline, width=width, joint="curve")


def rays(d, box, a, b, count=24, cx=None, cy=None):
    x0, y0, x1, y1 = box
    cx = (x0 + x1) / 2 if cx is None else cx
    cy = (y0 + y1) * 0.54 if cy is None else cy
    r = max(x1 - x0, y1 - y0) * 1.25
    d.rectangle(box, fill=b)
    for i in range(count):
        t0 = i * 2 * math.pi / count
        t1 = (i + 0.55) * 2 * math.pi / count
        d.polygon(
            [(cx, cy), (cx + r * math.cos(t0), cy + r * math.sin(t0)),
             (cx + r * math.cos(t1), cy + r * math.sin(t1))],
            fill=a if i % 2 == 0 else b,
        )


def paste(d, im, x, y):
    d._image.paste(im, (int(x), int(y)), im)


def label(d, xy, text, size, color=TEXT, anchor=None):
    d.text(xy, text, font=font(size), fill=color, anchor=anchor)


def character(d, cx, cy, s=1.0):
    """A pseudo-3D Minecraft character: front faces plus shaded top/right faces."""
    def rect(box, c):
        x0, y0, x1, y1 = [int(v) for v in box]
        d.rectangle([x0, y0, x1, y1], fill=c)

    def quad(points, c):
        d.polygon([(int(x), int(y)) for x, y in points], fill=c)

    u = 16 * s  # one pixel unit
    # Head: front, right side, top.
    hx, hy = cx - 4 * u, cy - 15 * u
    hw, hh, hd = 8 * u, 8 * u, 1.6 * u
    rect((hx, hy, hx + hw, hy + hh), (196, 146, 104))
    quad([(hx + hw, hy), (hx + hw + hd, hy - hd), (hx + hw + hd, hy + hh - hd),
          (hx + hw, hy + hh)], (150, 104, 76))
    quad([(hx, hy), (hx + hd, hy - hd), (hx + hw + hd, hy - hd), (hx + hw, hy)], (92, 58, 38))
    rect((hx, hy, hx + hw, hy + 2.2 * u), (78, 50, 32))
    # Eyes, mouth.
    rect((hx + 1.6 * u, hy + 3.6 * u, hx + 2.7 * u, hy + 4.7 * u), (36, 62, 140))
    rect((hx + 5.3 * u, hy + 3.6 * u, hx + 6.4 * u, hy + 4.7 * u), (36, 62, 140))
    rect((hx + 3.1 * u, hy + 5.8 * u, hx + 4.9 * u, hy + 6.3 * u), (140, 84, 66))
    # Torso.
    bx, by = cx - 4 * u, cy - 7 * u
    bw, bh = 8 * u, 12 * u
    rect((bx, by, bx + bw, by + bh), (40, 176, 196))
    quad([(bx + bw, by), (bx + bw + hd, by - hd), (bx + bw + hd, by + bh - hd),
          (bx + bw, by + bh)], (30, 128, 150))
    quad([(bx, by), (bx + hd, by - hd), (bx + bw + hd, by - hd), (bx + bw, by)], (72, 208, 220))
    # Arms, hanging slightly out.
    for side in (-1, 1):
        ax = bx - 4 * u if side < 0 else bx + bw
        rect((ax, by + 0.4 * u, ax + 4 * u, by + 11 * u),
             (40, 176, 196) if side < 0 else (34, 150, 174))
        rect((ax, by + 8.5 * u, ax + 4 * u, by + 12 * u), (196, 146, 104))
    # Legs.
    ly = by + bh
    for side in (-1, 1):
        lx = bx if side < 0 else cx
        rect((lx, ly, lx + 4 * u, ly + 12 * u), (60, 60, 140) if side < 0 else (48, 48, 120))
        rect((lx, ly + 11 * u, lx + 4 * u, ly + 12 * u), (70, 72, 78))
    # Hard shadow on the grass.
    d.ellipse([cx - 6 * u, cy + 16.6 * u, cx + 6 * u, cy + 18.2 * u], fill=(12, 48, 18))


def build_tile(d, box, title, sub, icon, fill, cap, plus=False):
    notched(d, box, fill, step=7, shadow=6)
    x0, y0, x1, y1 = box
    ic = micon(icon, 56 if not plus else 62)
    paste(d, ic, (x0 + x1) / 2 - ic.width / 2, y0 + (y1 - y0) * 0.28)
    if cap:
        cap_y = y1 - 44
        d.rectangle([x0 + 6, cap_y, x1 - 6, y1 - 6], fill=cap)
        label(d, (x0 + 14, cap_y + 5), title, 12)
        label(d, (x0 + 14, cap_y + 24), sub, 10, (226, 226, 232))
    else:
        label(d, ((x0 + x1) / 2, y1 - 34), title, 12, anchor="mm")


def promo(d, box, title, sub, icon, fill, tone):
    notched(d, box, fill, step=7, shadow=6)
    x0, y0, x1, y1 = box
    # Sunburst clipped to the art area via a temporary image.
    art = Image.new("RGB", (int(x1 - x0 - 12), int((y1 - y0) * 0.62)), fill)
    rays(ImageDraw.Draw(art), (0, 0, art.width, art.height), tone, fill, count=20,
         cx=art.width / 2, cy=art.height * 0.6)
    d._image.paste(art, (int(x0 + 6), int(y0 + 6)))
    d.line(notch_points(box, 7) + [notch_points(box, 7)[0]], fill=INK, width=2)
    ic = micon(icon, 50)
    paste(d, ic, (x0 + x1) / 2 - ic.width / 2, y0 + (y1 - y0) * 0.27)
    cap_y = y1 - 52
    d.rectangle([x0 + 5, cap_y, x1 - 5, y1 - 5], fill=tone)
    label(d, (x0 + 14, cap_y + 6), title, 13)
    label(d, (x0 + 14, cap_y + 28), sub, 10, (236, 236, 240))


def main() -> None:
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    # ---- Top bar ---------------------------------------------------------------
    d.rectangle([0, 0, W, 68], fill=BAR)
    notched(d, (24, 10, 310, 60), PANEL, step=6, shadow=5)
    paste(d, micon("player", 36), 34, 16)
    label(d, (80, 19), "Player2773", 15)
    label(d, (80, 39), "Ready to play", 10, MUTED)
    for i, name in enumerate(("friends", "settings")):
        x = 1296 + i * 62
        notched(d, (x, 11, x + 52, 59), SOFT, step=5, shadow=4)
        paste(d, micon(name, 26), x + 13, 24)

    # ---- Left promo column -----------------------------------------------------
    promo(d, (24, 86, 254, 300), "Shop", "Capes & cosmetics", "gift", PINK_DARK, PINK)
    promo(d, (24, 312, 254, 526), "My skin", "Upload your own", "skin", BLUE_DARK, BLUE)
    promo(d, (24, 538, 254, 752), "Play with friends", "Free · parties", "friends", GREEN_DARK, GREEN)

    # ---- Centre: green grass character stage ----------------------------------
    stage = (270, 86, 1140, 686)
    scene = Image.new("RGB", (stage[2] - stage[0], stage[3] - stage[1]), SCENE)
    sd = ImageDraw.Draw(scene)
    rays(sd, (0, 0, scene.width, scene.height), SCENE_GLOW, SCENE, count=24,
         cx=scene.width / 2, cy=scene.height * 0.54)
    for x, y in ((.12, .18), (.27, .34), (.74, .14), (.86, .31), (.18, .63),
                 (.81, .68), (.41, .09), (.63, .79), (.07, .44), (.94, .52)):
        sx, sy = x * scene.width, y * scene.height
        sd.rectangle([sx, sy, sx + 6, sy + 6], fill=(48, 118, 58))
    d._image.paste(scene, (stage[0], stage[1]))
    character(d, (270 + 1140) / 2, 86 + 600 * 0.52, s=1.0)
    # Grass ledge.
    d.rectangle([270, 646, 1140, 686], fill=GRASS_DARK)
    d.rectangle([270, 646, 1140, 658], fill=GRASS)
    for i, x in enumerate(range(274, 1140, 26)):
        if i % 3 == 0:
            d.rectangle([x, 666, x + 7, 673], fill=GRASS_DARK)
    # Pose chips + status + party chips.
    for i, (txt, active) in enumerate((("STAND", True), ("WAVE", False), ("DANCE", False))):
        box = (1016, 100 + i * 40, 1126, 132 + i * 40)
        notched(d, box, GREEN if active else CHIP, step=4, shadow=3)
        label(d, (box[0] + 12, box[1] + 8), txt, 11, INK if active else TEXT)
    for i, txt in enumerate(("AUTO", "WAVING")):
        box = (284, 100 + i * 40, 396, 132 + i * 40)
        notched(d, box, GREEN if i == 0 else CHIP, step=4, shadow=3)
        label(d, (box[0] + 12, box[1] + 8), txt, 11, INK if i == 0 else TEXT)
    for i, (txt, ic) in enumerate((("FIND A PARTY", "friends"), ("INVITE A FRIEND", "gift"))):
        x = 540 + i * 236
        notched(d, (x, 598, x + 226, 636), SOFT, step=4, shadow=3)
        paste(d, micon(ic, 18), x + 9, 607)
        label(d, (x + 34, 610), txt, 10)

    # ---- Centre dock: blue START HERE + green PLAY ----------------------------
    notched(d, (270, 700, 866, 806), BLUE, step=8, shadow=7)
    notched(d, (284, 714, 356, 792), BLUE_DARK, step=5, shadow=3)
    paste(d, micon("chest", 48), 292, 726)
    label(d, (372, 718), "START HERE", 10, YELLOW)
    label(d, (372, 736), "Aerix Client 1.21.4", 17)
    label(d, (372, 764), "Fabric · 1.21.4", 10, (216, 226, 248))
    notched(d, (880, 700, 1140, 806), GREEN, step=8, shadow=7)
    paste(d, micon("arrow", 30), 946, 738)
    label(d, (986, 734), "PLAY", 26, INK)
    label(d, (986, 770), "0h played", 10, (24, 60, 24))
    notched(d, (270, 814, 400, 856), SOFT, step=5, shadow=4)
    label(d, (300, 827), "BUILD", 12)

    # ---- Right: MY BUILDS ------------------------------------------------------
    label(d, (1156, 96), "MY BUILDS", 17)
    label(d, (1392, 100), "ALL >", 11, GREEN, anchor="ra")
    build_tile(d, (1156, 130, 1416, 300), "NEW BUILD", "", "plus", PANEL, None, plus=True)
    build_tile(d, (1156, 312, 1416, 482), "Aerix Client 1.21.4", "Fabric · 1.21.4",
               "chest", ORANGE, ORANGE_DARK)
    build_tile(d, (1156, 494, 1416, 664), "Companion mod", "Client & HUD",
               "controller", TEAL, TEAL_DARK)

    # ---- Bottom bar ------------------------------------------------------------
    d.rectangle([0, 826, W, H], fill=BAR)
    tabs = [("grass", "Play", True), ("book", "Catalog", False),
            ("player", "Character", False), ("emerald", "Shop", False),
            ("dots", "More", False)]
    tab_w = W / 5
    for i, (ic, name, active) in enumerate(tabs):
        cx = tab_w * i + tab_w / 2
        if active:
            notched(d, (cx - 94, 832, cx + 94, 878), GREEN, step=6, shadow=5)
        paste(d, micon(ic, 28), cx - 14, 840 if active else 838)
        label(d, (cx, 884), name, 11, GREEN if active else MUTED, anchor="mm")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT, optimize=True)
    print(f"wrote {OUT} ({img.width}x{img.height}, {OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
