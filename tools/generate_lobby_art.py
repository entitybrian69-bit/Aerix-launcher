#!/usr/bin/env python3
"""Generate the Aerix Lobby pixel-art assets from the bundled Minecraft skin texture.

The launcher ships ``assets/steve.png`` (a classic 64x64 skin). This script cuts the
front-facing head/body/arms/legs out of it, re-colours a few variants and places them on
Minecraft-style sunburst card art. It also draws the small blocky chrome icons.

Everything is drawn on a coarse pixel grid and upscaled with NEAREST, so the result stays
crisp at any density. Output goes to ``res/drawable-nodpi`` and ``assets/wallpapers``.

Requires Pillow:  python3 -m pip install pillow
                 python3 tools/generate_lobby_art.py
"""
from pathlib import Path
import colorsys
import math

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app_pojavlauncher" / "src" / "main"
DRAWABLE = APP / "res" / "drawable-nodpi"
SKIN = APP / "assets" / "steve.png"

SCALE = 10  # one skin pixel -> 10 art pixels for a character


def _skin_rgba():
    return Image.open(SKIN).convert("RGBA")


def _paste(dst, src, box, alpha=True):
    if alpha:
        dst.alpha_composite(src, box)
    else:
        dst.paste(src, box)


def character(shirt_hue=None, pants_hue=None, hair_hue=None, hat=None, cape=None):
    """Build a 16x32 front-view Minecraft character, then upscale by SCALE."""
    skin = _skin_rgba()
    body = Image.new("RGBA", (16, 32), (0, 0, 0, 0))

    # Classic 64x64 skin layout (front faces).
    regions = [
        ((8, 8, 16, 16), (4, 0)),     # head
        ((20, 20, 28, 32), (4, 8)),   # torso
        ((44, 20, 48, 32), (0, 8)),   # right arm
        ((36, 52, 40, 64), (12, 8)),  # left arm
        ((4, 20, 8, 32), (4, 20)),    # right leg
        ((20, 52, 24, 64), (8, 20)),  # left leg
    ]
    for crop, at in regions:
        part = skin.crop(crop)
        body.alpha_composite(part, at)

    # Optional second layer: hats are drawn on top of the head's upper rows.
    if hat == "crown":
        d = ImageDraw.Draw(body)
        crown = (255, 206, 62, 255)
        dark = (176, 126, 20, 255)
        for x in range(4, 12):
            d.point((x, 0), fill=crown)
        for x in (4, 6, 8, 10, 11):
            d.point((x, 1), fill=crown if x % 2 == 0 else dark)
        d.rectangle((4, 2, 11, 2), fill=dark)
        d.point((5, 1), fill=(220, 60, 70, 255))
        d.point((8, 1), fill=(70, 150, 240, 255))
    elif hat == "straw":
        d = ImageDraw.Draw(body)
        straw = (238, 214, 122, 255)
        shade = (186, 152, 66, 255)
        d.rectangle((2, 1, 13, 2), fill=straw)
        d.rectangle((4, 0, 11, 1), fill=straw)
        d.rectangle((3, 2, 12, 2), fill=shade)
    elif hat == "cap":
        d = ImageDraw.Draw(body)
        red = (206, 52, 58, 255)
        d.rectangle((4, 0, 11, 2), fill=red)
        d.rectangle((2, 2, 13, 2), fill=(150, 34, 40, 255))

    # Re-colour by hue while keeping the original shading (saturation/value).
    if shirt_hue is not None or pants_hue is not None or hair_hue is not None:
        px = body.load()
        for y in range(32):
            for x in range(16):
                r, g, b, a = px[x, y]
                if a == 0:
                    continue
                h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                deg = h * 360
                new_h = None
                if s > 0.35:
                    if 165 <= deg <= 200 and shirt_hue is not None:      # cyan shirt
                        new_h = shirt_hue
                    elif 215 <= deg <= 260 and pants_hue is not None:     # blue trousers
                        new_h = pants_hue
                    elif 10 <= deg <= 45 and v < 0.55 and hair_hue is not None:  # dark hair
                        new_h = hair_hue
                if new_h is not None:
                    rr, gg, bb = colorsys.hsv_to_rgb(new_h / 360, s, v)
                    px[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), a)
                # The top of the head is Steve's flat dark-brown hair. Re-tint that
                # separately so the blue/green variants read as different characters.
                if hair_hue is not None and hat is None and 4 <= x <= 11 and 0 <= y <= 3:
                    if v < 0.58 and 0.15 < s < 0.8:
                        rr, gg, bb = colorsys.hsv_to_rgb(
                            hair_hue / 360, max(s, 0.42), min(v * 1.25, 0.85)
                        )
                        px[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), a)

    if cape:
        # A simple dark cape peeking out behind the shoulders, drawn on a wider canvas.
        wide = Image.new("RGBA", (20, 32), (0, 0, 0, 0))
        cape_draw = ImageDraw.Draw(wide)
        cape_draw.rectangle((2, 8, 17, 27), fill=cape)
        wide.alpha_composite(body, (2, 0))
        body = wide

    return body.resize((body.width * SCALE, body.height * SCALE), Image.NEAREST)


def sunburst(width, height, base, light, dark, cx=None, cy=None, rays=24):
    """Alternating flat rays radiating from a point, like the launcher-card artwork."""
    img = Image.new("RGB", (width, height), base)
    d = ImageDraw.Draw(img)
    cx = width * 0.5 if cx is None else cx
    cy = height * 0.42 if cy is None else cy
    radius = math.hypot(width, height) * 1.2
    step = 360 / rays
    for i in range(rays):
        a0 = math.radians(i * step)
        a1 = math.radians((i + 1) * step)
        colour = light if i % 2 == 0 else dark
        d.polygon(
            [
                (cx, cy),
                (cx + radius * math.cos(a0), cy + radius * math.sin(a0)),
                (cx + radius * math.cos(a1), cy + radius * math.sin(a1)),
            ],
            fill=colour,
        )
    return img.convert("RGBA")


def card_art(name, base, light, dark, characters):
    w, h = 480, 300
    img = sunburst(w, h, base, light, dark)
    for item in characters:
        ch, cx, top, max_h = item
        ratio = min(1.0, max_h / ch.height)
        resized = ch.resize((int(ch.width * ratio), int(ch.height * ratio)), Image.NEAREST)
        x = int(cx - resized.width / 2)
        y = int(top)
        img.alpha_composite(resized, (x, y))
    # A subtle dark floor so the character's feet do not float on the rays.
    d = ImageDraw.Draw(img)
    d.rectangle((0, h - 18, w, h), fill=(0, 0, 0, 55))
    img.convert("RGB").save(DRAWABLE / name, quality=95)
    print("wrote", name)


def pixel_icon(name, rows, palette, scale=8):
    """Draw a tiny blocky icon from a list of equal-length character rows."""
    h = len(rows)
    w = len(rows[0])
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            colour = palette.get(ch)
            if colour:
                d.point((x, y), fill=colour)
    img.resize((w * scale, h * scale), Image.NEAREST).save(DRAWABLE / name)
    print("wrote", name)


def main():
    DRAWABLE.mkdir(parents=True, exist_ok=True)

    shop_char = character(hat="crown", cape=(92, 18, 48, 235), hair_hue=28)
    skin_char = character(shirt_hue=205, pants_hue=225, hair_hue=215)
    buddy_a = character(hat="straw", shirt_hue=95, pants_hue=35)
    buddy_b = character(hat="cap", shirt_hue=205, pants_hue=230, hair_hue=18)

    card_art(
        "aerix_lobby_card_shop.png",
        (168, 22, 66), (214, 52, 96), (140, 14, 52),
        [(shop_char, 250, 6, 292)],
    )
    card_art(
        "aerix_lobby_card_skin.png",
        (26, 94, 198), (58, 148, 238), (18, 70, 160),
        [(skin_char, 240, 6, 292)],
    )
    card_art(
        "aerix_lobby_card_friends.png",
        (40, 132, 46), (76, 178, 74), (26, 100, 36),
        [(buddy_a, 168, 34, 250), (buddy_b, 316, 26, 262)],
    )

    # A large standalone player for the static lobby preview. The app itself shows the real
    # rotatable 3D skin from the skinview WebView; this is only the HTML mock's stand-in.
    hero = character(hat="crown", cape=(52, 78, 148, 235), shirt_hue=205, pants_hue=225)
    # Transparent: the HTML preview adds a shape-following drop-shadow in CSS, and the static
    # PNG renderer adds a blurred silhouette. No baked-in rectangle outline.
    hero = hero.resize((hero.width * 2, hero.height * 2), Image.NEAREST)
    hero.save(DRAWABLE / "aerix_lobby_player.png")
    print("wrote", "aerix_lobby_player.png")

    white = (255, 255, 255, 255)
    grey = (196, 203, 210, 255)
    black = (20, 22, 26, 255)
    gold = (255, 205, 72, 255)
    brown = (146, 92, 46, 255)
    brown_dark = (98, 58, 28, 255)

    pixel_icon("aerix_lobby_ic_gift.png", [
        "............",
        "...WW..WW...",
        "..WWWWWWWW..",
        ".WWWWWWWWWW.",
        ".WWWGGGGWWW.",
        "WWWWGGGGWWWW",
        "WWWWGGGGWWWW",
        "WWWWGGGGWWWW",
        "WWWWGGGGWWWW",
        "WWWWGGGGWWWW",
        ".WWWWWWWWWW.",
        "............",
    ], {"W": white, "G": gold})

    pixel_icon("aerix_lobby_ic_chest.png", [
        "............",
        ".BBBBBBBBBB.",
        "BDDDDDDDDDDB",
        "BDDDDDDDDDDB",
        "BKKKKKKKKKKB",
        "BDDDDDGGDDDB",
        "BDDDDDGGDDDB",
        "BDDDDDDDDDDB",
        "BDDDDDDDDDDB",
        "BKKKKKKKKKKB",
        ".BBBBBBBBBB.",
        "............",
    ], {"B": brown_dark, "D": brown, "K": (110, 66, 32, 255), "G": gold})

    pixel_icon("aerix_lobby_ic_headset.png", [
        "............",
        "....WWWW....",
        "..WW....WW..",
        ".WW......WW.",
        ".WW......WW.",
        "WWW......WWW",
        "WWW......WWW",
        "WWW......WWW",
        ".WW......WW.",
        "..WWWWWWWW..",
        "....WWWW....",
        "............",
    ], {"W": white})

    pixel_icon("aerix_lobby_ic_cursor.png", [
        "K...........",
        "KW..........",
        "KWW.........",
        "KWWW........",
        "KWWWW.......",
        "KWWWWW......",
        "KWWWWWW.....",
        "KWWWWWWW....",
        "KWWWWWWWW...",
        "KWWWWKWWW...",
        "KWWK.KWWW...",
        "KK...KWWW...",
    ], {"W": white, "K": black})

    pixel_icon("aerix_lobby_ic_grid.png", [
        "............",
        ".WWWW..WWWW.",
        ".WWWW..WWWW.",
        ".WWWW..WWWW.",
        ".WWWW..WWWW.",
        "............",
        ".WWWW..WWWW.",
        ".WWWW..WWWW.",
        ".WWWW..WWWW.",
        ".WWWW..WWWW.",
        "............",
        "............",
    ], {"W": white})

    pixel_icon("aerix_lobby_ic_excl.png", [
        "........",
        "........",
        "...WW...",
        "...WW...",
        "...WW...",
        "...WW...",
        "........",
        "...WW...",
    ], {"W": black})

    pixel_icon("aerix_lobby_ic_close.png", [
        "............",
        "WW........WW",
        ".WW......WW.",
        "..WW....WW..",
        "...WW..WW...",
        "....WWWW....",
        "....WWWW....",
        "...WW..WW...",
        "..WW....WW..",
        ".WW......WW.",
        "WW........WW",
        "............",
    ], {"W": white})

    pixel_icon("aerix_lobby_ic_person_add.png", [
        "............",
        "...WWWW.....",
        "..WWWWWW....",
        "..WWWWWW..W.",
        "...WWWW...W.",
        "..WWWWWW.WWW",
        ".WWWWWWWW.W.",
        ".WWWWWWWW...",
        ".WWWWWWWW...",
        ".WW....WW...",
        "............",
        "............",
    ], {"W": white})

    pixel_icon("aerix_lobby_ic_search.png", [
        "............",
        "....WWWW....",
        "...WWWWWW...",
        "..WW....WW..",
        "..WW....WW..",
        "..WW....WW..",
        "...WWWWWW...",
        "....WWWW....",
        "......WWWW..",
        "........WWWW",
        "............",
        "............",
    ], {"W": white})

    # Steve's 8x8 head face, straight from the bundled skin, for the account chrome button.
    head = _skin_rgba().crop((8, 8, 16, 16)).resize((96, 96), Image.NEAREST)
    head.save(DRAWABLE / "aerix_lobby_ic_head.png")
    print("wrote", "aerix_lobby_ic_head.png")

    pixel_icon("aerix_lobby_ic_settings.png", [
        "....WWWW....",
        "..WWWWWWWW..",
        "..WWWWWWWW..",
        "WWWWWWWWWWWW",
        "WWWW....WWWW",
        "WWWW....WWWW",
        "WWWW....WWWW",
        "WWWW....WWWW",
        "WWWWWWWWWWWW",
        "..WWWWWWWW..",
        "..WWWWWWWW..",
        "....WWWW....",
    ], {"W": white})


if __name__ == "__main__":
    main()
