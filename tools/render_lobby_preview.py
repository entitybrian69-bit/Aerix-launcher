#!/usr/bin/env python3
"""Render a static PNG mock of the Aerix Lobby for quick visual review.

Mirrors preview/index.html at 1280x720 using the exact same app assets and pixel font.
Requires Pillow:  python3 -m pip install pillow
                  python3 tools/render_lobby_preview.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app_pojavlauncher/src/main"
DRAWABLE = APP / "res/drawable-nodpi"
FONT = APP / "res/font/minecraft_ten.ttf"
WALL = APP / "assets/wallpapers/wp_26_lobby_village.jpg"
OUT = ROOT / "preview/lobby-preview.png"

W, H = 1280, 720
INK = (10, 14, 20)
SHADOW = (6, 9, 14)
PANEL = (19, 27, 38)
LINE = (51, 64, 78)
BLUE = (46, 122, 212)
BLUE_DARK = (23, 69, 127)
GREEN = (85, 175, 57)
GREEN_DARK = (45, 107, 34)
YELLOW = (244, 197, 62)
YELLOW_DARK = (154, 116, 21)
RED = (217, 59, 78)
WHITE = (245, 247, 250)
MUTED = (175, 186, 198)


def font(size):
    return ImageFont.truetype(str(FONT), size)


def asset(name):
    return Image.open(DRAWABLE / name).convert("RGBA")


def cover(img, size):
    iw, ih = img.size
    w, h = size
    scale = max(w / iw, h / ih)
    return img.resize((int(iw * scale), int(ih * scale)), Image.LANCZOS).crop(
        ((int(iw * scale) - w) // 2, (int(ih * scale) - h) // 2,
         (int(iw * scale) - w) // 2 + w, (int(ih * scale) - h) // 2 + h)
    )


def shadow_text(d, xy, text, fnt, fill=WHITE, offset=(3, 4), shadow=(7, 10, 16)):
    x, y = xy
    d.text((x + offset[0], y + offset[1]), text, font=fnt, fill=shadow)
    d.text((x, y), text, font=fnt, fill=fill)


def blocky(d, box, fill, outline=INK, shadow=SHADOW, step=6, width=3):
    x0, y0, x1, y1 = box
    d.rectangle((x0 + step, y0 + step, x1 + step, y1 + step), fill=shadow)
    d.rectangle((x0, y0, x1, y1), fill=fill, outline=outline, width=width)


def paste_alpha(base, img, xy):
    base.paste(img, xy, img)


def main():
    bg = cover(Image.open(WALL).convert("RGB"), (W, H)).filter(ImageFilter.GaussianBlur(2))
    # Vertical + horizontal scrims to keep the cards legible.
    veil = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    v = ImageDraw.Draw(veil)
    for y in range(H):
        t = y / H
        a = int(90 * max(0.0, 1 - t / 0.46) + 150 * max(0.0, (t - 0.55) / 0.45))
        v.line((0, y, W, y), fill=(6, 14, 22, min(a, 165)))
    for x in range(W):
        t = abs(x / W - 0.5) * 2
        v.line((x, 0, x, H), fill=(6, 13, 20, int(60 * max(0.0, (t - 0.55) / 0.45))))
    img = Image.alpha_composite(bg.convert("RGBA"), veil).convert("RGB")
    d = ImageDraw.Draw(img)

    # Centre: the big player (2D stand-in for the live 3D skin).
    hero = asset("aerix_lobby_player.png")
    hero_h = int(H * 0.68)
    hero = hero.resize((int(hero.width * hero_h / hero.height), hero_h), Image.NEAREST)
    hx0, hy0 = (W - hero.width) // 2, int(H * 0.10)
    # A soft, shape-following drop shadow instead of a baked-in rectangle.
    silhouette = Image.new("RGBA", hero.size, (5, 9, 15, 0))
    silhouette.putalpha(hero.getchannel("A").filter(ImageFilter.GaussianBlur(12)))
    shadow_layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    shadow_layer.alpha_composite(silhouette, (hx0 + 12, hy0 + 22))
    img = Image.alpha_composite(img.convert("RGBA"), shadow_layer).convert("RGB")
    paste_alpha(img, hero, (hx0, hy0))
    d = ImageDraw.Draw(img)

    # Left stack: sunburst art cards with a hard stepped shadow, label band, note and badge.
    cards = [
        ("aerix_lobby_card_shop.png", "SHOP", "Bonus waiting", "excl", 0.045),
        ("aerix_lobby_card_skin.png", "MY SKIN", "Change your look", None, 0.335),
        ("aerix_lobby_card_friends.png", "PLAY WITH FRIENDS", "Party up", "dot", 0.625),
    ]
    card_w, card_h = int(W * 0.17), int(H * 0.225)
    for art, title, note, badge, ty in cards:
        x = int(W * (0.028 if badge != "dot" else 0.028))
        y = int(H * ty)
        box = (x, y, x + card_w, y + card_h)
        blocky(d, box, (29, 40, 53), step=6)
        art_img = asset(art).convert("RGB").resize((card_w - 6, card_h - 6), Image.LANCZOS)
        img.paste(art_img, (x + 3, y + 3))
        d = ImageDraw.Draw(img)
        d.rectangle((x + 3, y + card_h - 33, x + card_w - 3, y + card_h - 3), fill=(9, 14, 20))
        f = font(14)
        tw = d.textlength(title, font=f)
        d.text((x + (card_w - tw) / 2, y + card_h - 30), title, font=f, fill=WHITE)
        nf = font(12)
        nw = d.textlength(note, font=nf)
        shadow_text(d, (x + (card_w - nw) / 2, y + card_h + 8), note, nf)
        if badge == "excl":
            bx, by = x - 9, y - 9
            d.rectangle((bx, by, bx + 27, by + 27), fill=(255, 255, 255), outline=INK, width=2)
            f2 = font(20)
            d.text((bx + 9, by + 1), "!", font=f2, fill=INK)
        elif badge == "dot":
            dx, dy = x + card_w - 7, y - 7
            d.ellipse((dx, dy, dx + 15, dy + 15), fill=RED, outline=INK, width=2)

    # Top-right chrome buttons.
    icons = ["aerix_lobby_ic_grid.png", "aerix_lobby_ic_head.png",
             "aerix_lobby_ic_gift.png", "aerix_lobby_ic_settings.png"]
    bs, gap = 44, 8
    right = W - int(W * 0.014)
    top = int(H * 0.05)
    for i, name in enumerate(icons):
        x = right - (4 - i) * (bs + gap)
        blocky(d, (x, top, x + bs, top + bs), PANEL, outline=(7, 10, 15), step=4)
        ic = asset(name).resize((24, 24), Image.NEAREST)
        paste_alpha(img, ic, (x + 10, top + 10))
    d = ImageDraw.Draw(img)

    # Right promo card + carousel dash.
    pw, ph = int(W * 0.195), int(H * 0.34)
    px = W - int(W * 0.018) - pw
    py = int(H * 0.5 - ph / 2 - H * 0.09)
    blocky(d, (px, py, px + pw, py + ph), (11, 14, 20), outline=(44, 51, 61), step=6)
    d.text((px + 13, py + 12), "+1 REWARD FOR EVERY FRIEND", font=font(10), fill=MUTED)
    shadow_text(d, (px + 13, py + ph // 2 - 20), "PLAY TOGETHER", font(21))
    shadow_text(d, (px + 13, py + ph // 2 + 8), "INVITE A FRIEND", font(15), fill=YELLOW)
    d.rectangle((px + pw - 74, py + ph - 34, px + pw - 13, py + ph - 12),
                fill=(123, 69, 201), outline=(75, 42, 126), width=2)
    d.text((px + pw - 68, py + ph - 31), "AERIX", font=font(11), fill=WHITE)
    d.rectangle((px + pw - 28, py + ph + 9, px + pw, py + ph + 13), fill=WHITE)

    # Find a party / Invite a friend.
    sw, sh = int(W * 0.155), int(H * 0.085)
    sy = H - int(H * 0.295) - sh
    for i, (ic, label) in enumerate([
        ("aerix_lobby_ic_search.png", "Find a party"),
        ("aerix_lobby_ic_person_add.png", "Invite a friend"),
    ]):
        sx = int(W / 2 - sw - 5) + i * (sw + 10)
        blocky(d, (sx, sy, sx + sw, sy + sh), (18, 24, 34), outline=(57, 66, 77), step=5)
        icon = asset(ic).resize((20, 20), Image.NEAREST)
        paste_alpha(img, icon, (sx + 10, sy + sh // 2 - 10))
        d = ImageDraw.Draw(img)
        d.text((sx + 38, sy + sh // 2 - 7), label, font=font(12), fill=WHITE)

    # Bottom dock.
    dw, dh = int(W * 0.58), int(H * 0.215)
    dx = int((W - dw) / 2)
    dy = H - int(H * 0.025) - dh
    blocky(d, (dx, dy, dx + dw, dy + dh), PANEL, outline=LINE, step=7)
    d.text((dx + 9, dy - 20), "START HERE", font=font(11), fill=YELLOW)
    inner_x, inner_y = dx + 9, dy + 9
    inner_w, inner_h = dw - 18, dh - 18
    gap = 9
    xw = 30
    avail = inner_w - 3 * gap - xw
    blue_w = int(avail * 1.25 / (1.25 + 0.85 + 0.62))
    green_w = int(avail * 0.85 / (1.25 + 0.85 + 0.62))
    yellow_w = avail - blue_w - green_w
    by = inner_y
    bh = inner_h
    # Blue instance card
    blocky(d, (inner_x, by, inner_x + blue_w, by + bh), BLUE, outline=BLUE_DARK, step=5)
    d.text((inner_x + 12, by + 10), "START HERE", font=font(10), fill=YELLOW)
    shadow_text(d, (inner_x + 12, by + 28), "Fabric 1.20.1", font(16))
    d.text((inner_x + 12, by + 51), "Fabric 1.20.1", font=ImageFont.load_default(), fill=(220, 235, 255))
    chest = asset("aerix_lobby_ic_chest.png").resize((34, 34), Image.NEAREST)
    paste_alpha(img, chest, (inner_x + blue_w - 44, by + bh // 2 - 17))
    # Green Play
    gx = inner_x + blue_w + gap
    blocky(d, (gx, by, gx + green_w, by + bh), GREEN, outline=GREEN_DARK, step=5)
    shadow_text(d, (gx + 12, by + 14), "Play", font(22))
    d.text((gx + 12, by + 44), "Ready", font=font(11), fill=(232, 255, 224))
    cursor = asset("aerix_lobby_ic_cursor.png").resize((30, 30), Image.NEAREST)
    paste_alpha(img, cursor, (gx + green_w - 40, by + bh // 2 - 15))
    # Yellow chest
    yx = gx + green_w + gap
    blocky(d, (yx, by, yx + yellow_w, by + bh), YELLOW, outline=YELLOW_DARK, step=5)
    chest2 = asset("aerix_lobby_ic_chest.png").resize((32, 32), Image.NEAREST)
    paste_alpha(img, chest2, (yx + yellow_w // 2 - 16, by + 14))
    d = ImageDraw.Draw(img)
    d.text((yx + 10, by + 54), "Open chest", font=font(11), fill=INK)
    # X
    xx = yx + yellow_w + gap
    blocky(d, (xx, by + bh // 2 - 15, xx + 30, by + bh // 2 + 15), (42, 52, 64),
           outline=(10, 14, 19), step=4)
    close = asset("aerix_lobby_ic_close.png").resize((16, 16), Image.NEAREST)
    paste_alpha(img, close, (xx + 7, by + bh // 2 - 8))
    d = ImageDraw.Draw(img)

    # Help button, bottom right.
    hw, hh = int(W * 0.095), int(H * 0.085)
    hx, hy = W - int(W * 0.012) - hw, H - int(H * 0.035) - hh
    blocky(d, (hx, hy, hx + hw, hy + hh), (18, 24, 34), outline=(57, 66, 77), step=5)
    hs = asset("aerix_lobby_ic_headset.png").resize((20, 20), Image.NEAREST)
    paste_alpha(img, hs, (hx + 10, hy + hh // 2 - 10))
    d = ImageDraw.Draw(img)
    d.text((hx + 36, hy + hh // 2 - 7), "Help", font=font(12), fill=WHITE)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT, quality=92)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
