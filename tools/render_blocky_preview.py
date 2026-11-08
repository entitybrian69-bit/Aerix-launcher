#!/usr/bin/env python3
"""Renders a static PNG of the Blocky Shell for design review.

The live, interactive preview (real WebGL character + working emotes) is preview/index.html;
this is the still frame that can be attached to a PR or opened without a browser.

Run:  PYTHONPATH=/tmp/pillowlib python3 tools/render_blocky_preview.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app_pojavlauncher/src/main/res"
ASSETS = ROOT / "app_pojavlauncher/src/main/assets"
OUT = ROOT / "preview/blocky-preview.png"

W, H = 1440, 900
INK = (7, 9, 13)
SHADOW = (5, 7, 11)
PANEL = (17, 24, 36)
WHITE = (247, 250, 255)

OPTIONS = [
    ("PLAY", "Launch your instance", "play", (78, 168, 60), "CHECKER", "cheer"),
    ("LIBRARY", "Instances & versions", "library", (46, 122, 212), "STEPS", "action"),
    ("WARDROBE", "Skins, capes & hats", "wardrobe", (139, 92, 246), "CHECKER", "wave"),
    ("COMPANION MOD", "Client & cosmetics", "mod", (240, 123, 47), "STEPS", "action"),
    ("FRIENDS", "Party & messages", "friends", (34, 184, 214), "BOLTS", "wave"),
    ("DISCOVER", "Mods & modpacks", "discover", (238, 111, 168), "DOTS", "wave"),
    ("SERVERS", "Play together", "globe", (90, 100, 216), "STRIPES", "jump"),
    ("ACCOUNTS", "Sign in & profiles", "accounts", (199, 68, 184), "BOLTS", "wave"),
    ("SETTINGS", "Launcher options", "settings", (84, 99, 122), "BRICKS", "action"),
    ("CONTENT", "Mods, packs & saves", "folder", (24, 169, 141), "GRID", "action"),
    ("WALLPAPERS", "Themes & backdrops", "sun", (242, 185, 51), "STRIPES", "jump"),
    ("CHEST", "Unlock a wallpaper", "gift", (162, 106, 70), "CHECKER", "dance"),
]
EMOTES = [
    ("WAVING", "wave", (34, 184, 214)),
    ("DANCING", "dance", (238, 111, 168)),
    ("JUMPING", "jump", (140, 198, 63)),
    ("ACTION", "action", (240, 123, 47)),
]


def font(size: int):
    return ImageFont.truetype(str(RES / "font/minecraft_ten.ttf"), size)


def icon(name: str) -> Image.Image:
    return Image.open(RES / f"drawable-nodpi/aerix_blocky_ic_{name}.png").convert("RGBA")


def shade(color, factor=0.55):
    return tuple(max(0, min(255, int(c * factor))) for c in color[:3])


def pattern(d, box, kind, color, unit=16):
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    if kind == "CHECKER":
        for row, y in enumerate(range(y0, y1, unit)):
            for col, x in enumerate(range(x0, x1, unit)):
                if (row + col) % 2 == 0:
                    d.rectangle([x, y, min(x + unit, x1), min(y + unit, y1)], fill=color)
    elif kind == "STRIPES":
        for x in range(x0, x1, unit):
            d.rectangle([x, y0, min(x + unit // 2, x1), y1], fill=color)
    elif kind == "DOTS":
        for y in range(y0 + 8, y1, unit):
            for x in range(x0 + 8, x1, unit):
                d.rectangle([x - 4, y - 4, x + 4, y + 4], fill=color)
    elif kind == "STEPS":
        for i, x in enumerate(range(x0, x1, unit)):
            top = y1 - ((i % 4) + 1) * unit // 2
            d.rectangle([x, top, min(x + unit, x1), y1], fill=color)
    elif kind == "GRID":
        for x in range(x0, x1, unit):
            d.rectangle([x, y0, x + 2, y1], fill=color)
        for y in range(y0, y1, unit):
            d.rectangle([x0, y, x1, y + 2], fill=color)
    elif kind == "BRICKS":
        for row, y in enumerate(range(y0, y1, unit)):
            for x in range(x0, x1, unit):
                d.rectangle([x, y, min(x + unit - 3, x1), min(y + unit - 3, y1)], fill=color)
    elif kind == "BOLTS":
        for i, x in enumerate(range(x0, x1, unit)):
            y = y0 + (i % 3) * unit
            d.rectangle([x, y, x + unit // 2, y + unit // 2], fill=color)
            d.rectangle([x + unit // 2, y + unit // 2, x + unit, y + unit], fill=color)


def block(d, box, fill, outline=4, shadow=7, kind=None, tint=None):
    x0, y0, x1, y1 = box
    d.rectangle([x0 + shadow, y0 + shadow, x1 + shadow, y1 + shadow], fill=SHADOW)
    d.rectangle([x0, y0, x1, y1], fill=fill)
    if kind:
        pattern(d, (x0 + outline, y0 + outline, x1 - outline, y1 - outline), kind,
                tint or shade(fill))
    d.rectangle([x0, y0, x1, y1], outline=INK, width=outline)


def label(d, xy, text, size, color=WHITE):
    d.text(xy, text, font=font(size), fill=color)


def main() -> None:
    bg = Image.open(ASSETS / "wallpapers/wp_27_blocky_plaza.jpg").convert("RGBA")
    scale = max(W / bg.width, H / bg.height)
    bg = bg.resize((int(bg.width * scale), int(bg.height * scale)), Image.Resampling.NEAREST)
    left = (bg.width - W) // 2
    top = (bg.height - H) // 2
    img = bg.crop((left, top, left + W, top + H)).convert("RGBA")
    scrim = Image.new("RGBA", (W, H), (7, 12, 20, 90))
    img = Image.alpha_composite(img, scrim)
    d = ImageDraw.Draw(img)
    d.rectangle([0, H - 260, W, H], fill=(7, 12, 20, 90))

    # Stepped sunburst across the top, from the sketch.
    sun = Image.open(RES / "drawable-nodpi/aerix_blocky_sun.png").convert("RGBA")
    sun = sun.resize((256, 14), Image.Resampling.NEAREST)
    for x in range(0, W, 256):
        img.paste(sun, (x, 8), sun)

    # Header.
    block(d, (14, 32, 72, 90), (90, 100, 216), shadow=4)
    img.paste(icon("sun").resize((36, 36), Image.Resampling.NEAREST), (32, 45),
              icon("sun").resize((36, 36), Image.Resampling.NEAREST))
    label(d, (86, 38), "AERIX", 24)
    label(d, (86, 70), "BLOCKY LAUNCHER - PICK A BLOCK", 11)
    chrome = [("grid", (220, 67, 81)), ("accounts", (46, 122, 212)),
              ("settings", (84, 99, 122)), ("sound", (242, 185, 51)),
              ("help", (24, 169, 141))]
    for i, (name, color) in enumerate(chrome):
        x = W - 14 - (len(chrome) - i) * 52
        block(d, (x, 39, x + 44, 83), color, shadow=4)
        ic = icon(name).resize((24, 24), Image.Resampling.NEAREST)
        img.paste(ic, (x + 10, 49), ic)

    main_top, main_bottom = 102, 770
    stage = (14, main_top, 620, main_bottom)
    options = (636, main_top, W - 14, main_bottom)
    block(d, stage, PANEL, shadow=8, kind="STRIPES", tint=(27, 39, 53))
    block(d, options, PANEL, shadow=8)

    # Character stage: smooth 3D stand-in, hard divider, emote deck.
    player = Image.open(ROOT / "preview/blocky-player.png").convert("RGBA")
    player = player.resize((260, 428), Image.Resampling.LANCZOS)
    img.alpha_composite(player, (190, 150))
    label(d, (32, 116), "STEVE", 13)
    label(d, (548, 116), "3D", 13)
    d.rectangle([18, 616, 616, 622], fill=INK)
    label(d, (32, 636), "WAVING", 13)
    label(d, (132, 636), "AUTO", 13)
    for i, (text, name, color) in enumerate(EMOTES):
        x = 26 + i * 148
        block(d, (x, 666, x + 138, 716), color, shadow=5, kind="DOTS")
        ic = icon(name).resize((22, 22), Image.Resampling.NEAREST)
        img.paste(ic, (x + 10, 686), ic)
        label(d, (x + 40, 690), text, 10)
    label(d, (32, 728), "DRAG TO ROTATE - TAP A BLOCK", 10)

    # Twelve differently coloured, black-outlined option cards.
    gx0, gy0, gx1, gy1 = options[0] + 12, options[1] + 12, options[2] - 12, options[3] - 12
    cw, ch = (gx1 - gx0 - 12) // 2, (gy1 - gy0 - 5 * 10) // 6
    for i, (title, sub, ic_name, fill, kind, _) in enumerate(OPTIONS):
        col, row = i % 2, i // 2
        x = gx0 + col * (cw + 12)
        y = gy0 + row * (ch + 10)
        block(d, (x, y, x + cw, y + ch), fill, shadow=6, kind=kind,
              tint=shade(fill, 0.72))
        ic = icon(ic_name).resize((30, 30), Image.Resampling.NEAREST)
        img.paste(ic, (x + 14, y + (ch - 30) // 2), ic)
        label(d, (x + 56, y + ch // 2 - 16), title, 14)
        label(d, (x + 56, y + ch // 2 + 6), sub, 10)
        if i == 11:
            d.rectangle([x + cw - 36, y + ch // 2 - 12, x + cw - 12, y + ch // 2 + 12],
                        fill=(244, 246, 250), outline=INK, width=2)
            label(d, (x + cw - 27, y + ch // 2 - 7), "!", 14, (220, 67, 81))

    # Grass spikes.
    for x in range(0, W, 16):
        h = 8 + (x // 16 % 4) * 3
        d.rectangle([x, 786 - h, x + 12, 786], fill=(63, 154, 58))

    # Bottom dock.
    block(d, (14, 794, W - 14, 884), PANEL, shadow=6)
    block(d, (26, 802, 430, 876), (46, 122, 212), shadow=5, kind="STEPS")
    ic = icon("mod").resize((30, 30), Image.Resampling.NEAREST)
    img.paste(ic, (38, 822), ic)
    label(d, (82, 812), "START HERE", 10)
    label(d, (82, 830), "Aerix Client 1.16.5", 16)
    label(d, (82, 852), "1.16.5 - Fabric", 10)
    block(d, (442, 802, 720, 876), (78, 168, 60), shadow=5, kind="CHECKER")
    ic = icon("play").resize((26, 26), Image.Resampling.NEAREST)
    img.paste(ic, (456, 824), ic)
    label(d, (492, 828), "PLAY", 20)
    for i in range(10):
        bx = 492 + i * 21
        d.rectangle([bx, 858, bx + 18, 868],
                    fill=(242, 185, 51) if i < 7 else (27, 39, 53), outline=INK, width=1)
    block(d, (732, 802, 792, 876), (242, 185, 51), shadow=5, kind="STRIPES")
    block(d, (804, 802, 864, 876), (24, 169, 141), shadow=5, kind="GRID")
    for x, name in ((744, "gift"), (816, "help")):
        ic = icon(name).resize((36, 36), Image.Resampling.NEAREST)
        img.paste(ic, (x, 820), ic)
    label(d, (882, 826), "TWELVE BLOCKS - TWELVE COLOURS - ONE BLACK OUTLINE", 12)
    label(d, (882, 848), "3D SKIN + AUTO EMOTES + BLOCKY SOUND", 12)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").save(OUT, quality=95)
    print(f"wrote {OUT} ({img.width}x{img.height})")


if __name__ == "__main__":
    main()
