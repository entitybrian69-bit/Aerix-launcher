"""Aerix - Liquid Crystal design kit.

A small Pillow based design system used to render the Aerix launcher mockups.
Everything is drawn procedurally so the ten screen mockups share one theme:

    * deep abyss backdrop with blurred aurora blooms and caustics
    * frosted "liquid crystal" panels: real backdrop blur, specular sheen,
      chromatic refraction rim and a soft drop shadow
    * Inter typography, Lucide iconography

All coordinates are CSS pixels on a 1080x2400 phone canvas; the kit renders at
2x and downsamples for crisp output.
"""

import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

# ----------------------------------------------------------------------------
# canvas
# ----------------------------------------------------------------------------
S = 2                      # supersample factor
W, H = 1080, 2400
DW, DH = W * S, H * S

FONT_DIR = os.environ.get("AERIX_FONT_DIR", "/home/user/.fonts_build")
DEJAVU = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
DEJAVU_B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

INTER = {
    400: f"{FONT_DIR}/Inter-Regular.ttf",
    500: f"{FONT_DIR}/Inter-Medium.ttf",
    600: f"{FONT_DIR}/Inter-SemiBold.ttf",
    700: f"{FONT_DIR}/Inter-Bold.ttf",
    800: f"{FONT_DIR}/Inter-ExtraBold.ttf",
}
_WEIGHTS = sorted(INTER)
LUCIDE = f"{FONT_DIR}/lucide.ttf"
LUCIDE_CP = f"{FONT_DIR}/lucide_codepoints.json"

# ----------------------------------------------------------------------------
# palette
# ----------------------------------------------------------------------------
INK = (236, 247, 255)
INK_2 = (188, 210, 227)
INK_DIM = (146, 174, 198)
INK_FAINT = (108, 136, 161)
CYAN = (110, 240, 255)
MINT = (124, 255, 203)
VIOLET = (167, 139, 250)
AZURE = (96, 165, 250)
ROSE = (255, 138, 176)
AMBER = (255, 199, 122)
LIME = (186, 255, 141)
WHITE = (255, 255, 255)

# ----------------------------------------------------------------------------
# fonts / icons
# ----------------------------------------------------------------------------
_font_cache = {}
_icon_font_cache = {}
_CP = None
_METRICS = {}


def codepoints():
    global _CP
    if _CP is None:
        with open(LUCIDE_CP) as fh:
            _CP = json.load(fh)
    return _CP


def _pick(weight):
    return min(_WEIGHTS, key=lambda w: abs(w - weight))


def font(size, weight=500):
    w = _pick(weight)
    key = (round(size * 4), w)
    if key not in _font_cache:
        path = INTER[w]
        if not os.path.exists(path):
            path = DEJAVU_B if w >= 600 else DEJAVU
        _font_cache[key] = ImageFont.truetype(path, int(round(size * S)))
    return _font_cache[key]


def metrics(size, weight=500):
    """(cap_height, descender) in device px for a css px `size`."""
    w = _pick(weight)
    if w not in _METRICS:
        f = font(100, w)
        probe = Image.new("RGBA", (600, 500), (0, 0, 0, 0))
        ImageDraw.Draw(probe).text((200, 300), "H", font=f, fill=(255, 255, 255, 255),
                                   anchor="ls")
        l, t, r, b = probe.split()[3].getbbox()
        cap = (b - t) / (100 * S)
        ImageDraw.Draw(probe).text((200, 300), "g", font=f, fill=(255, 255, 255, 255),
                                   anchor="ls")
        l2, t2, r2, b2 = probe.split()[3].getbbox()
        _METRICS[w] = (cap, (b2 - 300 * 1) / (100 * S) - 0.0)
        _METRICS[w] = (cap, (b2 - 300) / (100 * S))
    cap, desc = _METRICS[w]
    return cap * size * S, desc * size * S


def icon_font(size):
    key = round(size * 4)
    if key not in _icon_font_cache:
        _icon_font_cache[key] = ImageFont.truetype(LUCIDE, int(round(size * S)))
    return _icon_font_cache[key]


def textw(s, size, weight=500, tracking=0.0):
    f = font(size, weight)
    w = f.getlength(s)
    if tracking:
        w += tracking * S * max(0, len(str(s)) - 1)
    return w


def wrap(s, size, weight, maxw):
    if not s:
        return []
    words, lines, cur = str(s).split(" "), [], ""
    for word in words:
        trial = word if not cur else cur + " " + word
        if textw(trial, size, weight) <= maxw:
            cur = trial
        else:
            if cur:
                lines.append(cur)
            cur = word
    lines.append(cur)
    return lines


# ----------------------------------------------------------------------------
# primitives
# ----------------------------------------------------------------------------
def _rgba(size, color):
    a = int(round(255 * color[3])) if len(color) > 3 else 255
    return Image.new("RGBA", size, (int(color[0]), int(color[1]), int(color[2]), a))


def gradient(size, stops, mode="v"):
    """stops: [(pos, (r,g,b,a))] a in 0..1 ; mode v|h|d|du"""
    w, h = size
    if mode == "v":
        t = np.linspace(0.0, 1.0, h)[:, None].repeat(w, 1)
    elif mode == "h":
        t = np.linspace(0.0, 1.0, w)[None, :].repeat(h, 0)
    elif mode == "d":
        t = (np.linspace(0.0, 1.0, w)[None, :] + np.linspace(0.0, 1.0, h)[:, None]) / 2
    else:
        t = (np.linspace(0.0, 1.0, w)[None, :] + np.linspace(1.0, 0.0, h)[:, None]) / 2
    ts = [s[0] for s in stops]
    vals = np.array([s[1] for s in stops], dtype=np.float64)
    out = np.stack([np.interp(t, ts, vals[:, k]) for k in range(4)], axis=-1)
    out[..., 3] *= 255.0
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")


def rr_mask(size, radius):
    m = Image.new("L", (max(1, int(size[0])), max(1, int(size[1]))), 0)
    ImageDraw.Draw(m).rounded_rectangle(
        [0, 0, m.size[0] - 1, m.size[1] - 1], radius=max(0, int(round(radius))), fill=255
    )
    return m


def ring_mask(size, radius, width):
    outer = rr_mask(size, radius)
    inner = rr_mask((max(1, size[0] - 2 * width), max(1, size[1] - 2 * width)),
                    max(0, int(round(radius)) - width))
    canvas = Image.new("L", size, 0)
    canvas.paste(inner, (width, width))
    return ImageChops.subtract(outer, canvas)


def circle_mask(size):
    return rr_mask(size, max(size) / 2)


def stamp(im, layer, box, mask=None):
    """alpha-composite `layer` (RGBA) onto im at css box (x0, y0)."""
    if mask is not None:
        layer = layer.copy()
        layer.putalpha(ImageChops.multiply(layer.split()[3], mask))
    im.alpha_composite(layer, (int(round(box[0] * S)), int(round(box[1] * S))))


def blur_behind(im, box, radius_px):
    x0, y0, x1, y1 = [int(round(v * S)) for v in box]
    bw, bh = x1 - x0, y1 - y0
    pad = int(radius_px * S * 0.8)
    cx0, cy0 = max(0, x0 - pad), max(0, y0 - pad)
    cx1, cy1 = min(DW, x1 + pad), min(DH, y1 + pad)
    region = im.crop((cx0, cy0, cx1, cy1))
    rw, rh = region.size
    f = max(2, int(radius_px * S / 10))
    small = region.resize((max(2, rw // f), max(2, rh // f)), Image.BILINEAR)
    small = small.filter(ImageFilter.GaussianBlur(max(1.0, radius_px * S / f / 1.7)))
    big = small.resize((rw, rh), Image.BILINEAR)
    ox, oy = x0 - cx0, y0 - cy0
    return big.crop((ox, oy, ox + bw, oy + bh))


def glow(im, box, color, alpha=0.5, radius=40, spread=1.0, shape="rect"):
    x0, y0, x1, y1 = box
    bw, bh = (x1 - x0) * spread, (y1 - y0) * spread
    pad = int(radius * 2.4) + 4
    size = (int(bw * S) + pad * 2, int(bh * S) + pad * 2)
    solid = _rgba((int(bw * S), int(bh * S)), (*color, alpha))
    m = circle_mask(solid.size) if shape == "circle" else rr_mask(solid.size, min(solid.size) * 0.42)
    solid.putalpha(ImageChops.multiply(solid.split()[3], m))
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    layer.alpha_composite(solid, (pad, pad))
    layer = layer.filter(ImageFilter.GaussianBlur(radius * S))
    im.alpha_composite(layer, (int(round(x0 * S)) - pad, int(round(y0 * S)) - pad))


# ----------------------------------------------------------------------------
# the glass material
# ----------------------------------------------------------------------------
def glass(im, box, radius=34, alpha=0.085, tint=WHITE, blur=26, border=0.34,
          sheen=0.12, foot=0.10, shadow=True, glowc=None, glow_a=0.35,
          glow_r=42, chromatic=True, shape="rect"):
    x0, y0, x1, y1 = box
    bw, bh = int(round((x1 - x0) * S)), int(round((y1 - y0) * S))
    if bw <= 2 or bh <= 2:
        return
    r = int(round(radius * S))

    if glowc is not None and glow_a > 0:
        glow(im, box, glowc, glow_a, glow_r, shape=shape)
    if RECORD:
        REC.append(("glass", (x0, y0, x1, y1), "", radius, 0))

    if shadow:
        m = int(26 * S)
        layer = Image.new("RGBA", (bw + 2 * m, bh + 2 * m), (0, 0, 0, 0))
        solid = Image.new("RGBA", (bw, bh), (0, 0, 0, 88))
        solid.putalpha(ImageChops.multiply(
            solid.split()[3],
            circle_mask((bw, bh)) if shape == "circle" else rr_mask((bw, bh), r)))
        layer.alpha_composite(solid, (m, m + int(7 * S)))
        layer = layer.filter(ImageFilter.GaussianBlur(13 * S))
        im.alpha_composite(layer, (int(round(x0 * S)) - m, int(round(y0 * S)) - m))

    panel = Image.new("RGBA", (bw, bh))
    panel.paste(blur_behind(im, box, blur), (0, 0))

    panel = Image.alpha_composite(panel, _rgba((bw, bh), (*tint, alpha)))

    sh = gradient((bw, bh), [(0.0, (255, 255, 255, sheen)),
                             (0.34, (255, 255, 255, sheen * 0.22)),
                             (0.62, (255, 255, 255, 0.0)),
                             (1.0, (255, 255, 255, 0.0))], "v")
    panel = Image.alpha_composite(panel, sh)

    if foot:
        ft = gradient((bw, bh), [(0.0, (0, 0, 0, 0.0)),
                                 (0.55, (0, 0, 0, 0.0)),
                                 (1.0, (0, 0, 0, foot))], "v")
        panel = Image.alpha_composite(panel, ft)

    stamp(im, panel, (x0, y0),
          circle_mask((bw, bh)) if shape == "circle" else rr_mask((bw, bh), r))

    if border:
        wpx = max(1, int(round(1.6 * S)))
        ring = ring_mask((bw, bh), r, wpx)
        if chromatic:
            g = gradient((bw, bh), [(0.0, (255, 255, 255, border * 1.45)),
                                    (0.30, (176, 240, 255, border * 1.05)),
                                    (0.62, (162, 168, 255, border * 0.85)),
                                    (1.0, (255, 255, 255, border * 0.35))], "d")
        else:
            g = gradient((bw, bh), [(0.0, (255, 255, 255, border * 1.2)),
                                    (1.0, (255, 255, 255, border * 0.35))], "v")
        arr = np.array(g)
        arr[..., 3] = (arr[..., 3].astype(np.float32) *
                       (np.array(ring, dtype=np.float32) / 255.0)).astype(np.uint8)
        stamp(im, Image.fromarray(arr, "RGBA"), (x0, y0))


# ----------------------------------------------------------------------------
# text / icons
# ----------------------------------------------------------------------------
WARN = []
RECORD = bool(int(os.environ.get("AERIX_RECORD", "0")))
REC = []


def txt(im, s, x, y, size=24, weight=500, color=INK, halign="left",
        valign="top", tracking=0.0, alpha=1.0, maxw=None):
    if s is None or s == "":
        return
    s = str(s)
    f = font(size, weight)
    cap, desc = metrics(size, weight)
    w = textw(s, size, weight, tracking)
    if maxw and w > maxw * S:
        WARN.append(f"text overflow: {s[:44]!r} {w/S:.0f} > {maxw:.0f} css px")
    if valign == "middle":
        base = y * S + cap / 2
    elif valign == "bottom":
        base = y * S - desc
    else:
        base = y * S + cap
    if halign == "center":
        x -= w / S / 2
    elif halign == "right":
        x -= w / S
    col = (int(color[0]), int(color[1]), int(color[2]), int(round(255 * alpha)))
    if RECORD:
        REC.append(("text", (x * S / S, (base - cap) / S, (x * S + w) / S,
                             (base + desc * 0.35) / S), s, size, weight))
    if tracking == 0 and alpha >= 1.0:
        ImageDraw.Draw(im).text((round(x * S), round(base)), s, font=f,
                                fill=col, anchor="ls")
        return
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx = round(x * S)
    for ch in s:
        d.text((cx, round(base)), ch, font=f, fill=col, anchor="ls")
        cx += f.getlength(ch) + tracking * S
    im.alpha_composite(layer)


def icon(im, name, cx, cy, size=28, color=INK, alpha=1.0):
    cp = codepoints().get(name)
    if cp is None:
        WARN.append(f"missing icon: {name}")
        return
    if RECORD:
        REC.append(("icon", (cx - size * 0.55, cy - size * 0.55,
                             cx + size * 0.55, cy + size * 0.55), name, size, 0))
    f = icon_font(size)
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    col = (int(color[0]), int(color[1]), int(color[2]), int(round(255 * alpha)))
    ImageDraw.Draw(layer).text((round(cx * S), round(cy * S)),
                               chr(int(cp, 16) if isinstance(cp, str) else int(cp)),
                               font=f, fill=col, anchor="mm")
    im.alpha_composite(layer)


def hairline(im, x0, y0, x1, y1, color=WHITE, alpha=0.10, width=1.2):
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).line([x0 * S, y0 * S, x1 * S, y1 * S],
                               fill=(*color, int(255 * alpha)),
                               width=max(1, int(width * S)))
    im.alpha_composite(layer)


# ----------------------------------------------------------------------------
# backdrop
# ----------------------------------------------------------------------------
BLOBS = [
    (0.18, 0.13, 0.42, CYAN, 0.13),
    (0.86, 0.22, 0.38, VIOLET, 0.11),
    (0.62, 0.52, 0.46, AZURE, 0.10),
    (0.10, 0.74, 0.40, MINT, 0.08),
    (0.92, 0.90, 0.44, CYAN, 0.10),
    (0.42, 0.96, 0.38, ROSE, 0.05),
]


def backdrop(seed=0, shift=0.0, blobs=None, caustic=0.16):
    rng = np.random.default_rng(seed)
    w, h = 540, 1200
    yy = np.linspace(0.0, 1.0, h)[:, None]
    xx = np.linspace(0.0, 1.0, w)[None, :]
    top = np.array([6, 10, 20], dtype=np.float64)
    mid = np.array([8, 20, 38], dtype=np.float64)
    bot = np.array([3, 8, 19], dtype=np.float64)
    t = yy.repeat(w, 1)
    img = np.zeros((h, w, 3), dtype=np.float64)
    seg1 = np.clip(t / 0.55, 0, 1)[..., None]
    seg2 = np.clip((t - 0.55) / 0.45, 0, 1)[..., None]
    img += top * (1 - seg1) + mid * seg1
    img = img * (1 - seg2) + (mid * (1 - seg2) + bot * seg2) * seg2

    for (cx, cy, rad, col, inten) in (blobs or BLOBS):
        cy = (cy + shift) % 1.0
        dx = (xx - cx) * 1.05
        dy = (yy - cy) * 0.62
        d2 = dx * dx + dy * dy
        g = np.exp(-d2 / (2 * (rad * 0.42) ** 2))[..., None]
        img += g * np.array(col, dtype=np.float64) * inten

    if caustic:
        v = (np.sin((xx * 9.0 + np.sin(yy * 6.0 + seed) * 1.6) * math.pi) *
             np.cos((yy * 13.0 - xx * 3.0 + seed * 0.7) * math.pi))
        img += (v[..., None] ** 3) * np.array([9, 20, 30], dtype=np.float64) * caustic

    vx = (xx - 0.5) * 1.15
    vy = (yy - 0.5) * 1.02
    vig = np.clip(1.0 - (vx * vx + vy * vy) * 0.72, 0.32, 1.0)[..., None]
    img *= vig
    img += rng.normal(0.0, 1.1, (h, w, 3))
    img = np.clip(img, 0, 255).astype(np.uint8)
    return Image.fromarray(img, "RGB").convert("RGBA").resize((DW, DH), Image.LANCZOS)


# ----------------------------------------------------------------------------
# chrome
# ----------------------------------------------------------------------------
CONTENT_TOP = 300.0
CONTENT_BOTTOM = 2196.0


def status_bar(im, time="9:41"):
    txt(im, time, 58, 48, size=27, weight=700, color=INK, valign="middle")
    x = W - 58
    for name in ("signal", "wifi", "battery-full"):
        icon(im, name, x, 48, size=24, color=INK, alpha=0.95)
        x -= 38


def orb(im, cx, cy, size=76, name=None, tint=WHITE, alpha=0.10, icon_color=INK,
        glowc=None):
    r = size / 2
    glass(im, (cx - r, cy - r, cx + r, cy + r), radius=r, alpha=alpha, tint=tint,
          blur=22, border=0.30, sheen=0.14, glowc=glowc, glow_a=0.22 if glowc else 0.0,
          glow_r=22, shape="circle")
    if name:
        icon(im, name, cx, cy + 1, size=size * 0.42, color=icon_color)


def screen_header(im, title, subtitle=None, right=None, kicker=None, back=False):
    """right: list of dicts {icon, tint, glow, icon_color}"""
    if kicker:
        txt(im, kicker.upper(), 58, 156, size=18.5, weight=800, color=CYAN,
            tracking=3.4, alpha=0.95)
        ty = 190
    else:
        ty = 150
    txt(im, title, 58, ty, size=52, weight=800, color=INK, tracking=-1.0)
    if subtitle:
        txt(im, subtitle, 60, ty + 66, size=21.5, weight=500, color=INK_DIM)
    cx = W - 62
    if back:
        orb(im, cx, ty + 28, size=72, name="chevron-right")
        cx -= 88
    for item in (right or []):
        orb(im, cx, ty + 28, size=72, name=item.get("icon"),
            tint=item.get("tint", WHITE), glowc=item.get("glow"),
            icon_color=item.get("icon_color", INK))
        cx -= 86


DOCK_LABELS = ["LAUNCH", "INSTANCE", "MODS", "CONTROL", "SETTINGS",
               "VIDEO", "INPUT", "JAVA", "MISC", "LABS"]
DOCK_ICONS = ["rocket", "layers", "puzzle", "gamepad-2", "sliders-horizontal",
              "monitor-play", "hand", "coffee", "folder-cog", "flask-conical"]
DOCK_TINTS = [CYAN, MINT, VIOLET, AZURE, CYAN, ROSE, MINT, AMBER, CYAN, VIOLET]


def dock(im, active=0):
    h = 156.0
    y0 = H - 26 - h
    x0, x1 = 24.0, W - 24.0
    glass(im, (x0, y0, x1, y0 + h), radius=48, alpha=0.13, tint=WHITE, blur=34,
          border=0.40, sheen=0.16, foot=0.16)
    slot = (x1 - x0) / len(DOCK_LABELS)
    for i, (label, name, tint) in enumerate(zip(DOCK_LABELS, DOCK_ICONS, DOCK_TINTS)):
        cx = x0 + slot * (i + 0.5)
        if i == active:
            pw, ph = slot - 14, 112.0
            glass(im, (cx - pw / 2, y0 + 20, cx + pw / 2, y0 + 20 + ph),
                  radius=32, alpha=0.22, tint=tint, blur=18, border=0.62,
                  sheen=0.20, foot=0.0, shadow=False,
                  glowc=tint, glow_a=0.32, glow_r=20)
            icon(im, name, cx, y0 + 55, size=32, color=tint)
            txt(im, label, cx, y0 + 102, size=15, weight=800, color=tint,
                halign="center", valign="top", tracking=0.2, maxw=slot - 12)
        else:
            icon(im, name, cx, y0 + 55, size=31, color=INK_2, alpha=0.70)
            txt(im, label, cx, y0 + 102, size=14.5, weight=700, color=INK_FAINT,
                halign="center", valign="top", tracking=0.2, maxw=slot - 12)
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle(
        [(W / 2 - 108) * S, (H - 14) * S, (W / 2 + 108) * S, (H - 6) * S],
        radius=8 * S, fill=(255, 255, 255, 90))
    im.alpha_composite(layer)


# ----------------------------------------------------------------------------
# controls
# ----------------------------------------------------------------------------
def toggle(im, cx, cy, on=True, scale=1.0):
    w, h = 80.0 * scale, 44.0 * scale
    x0, y0 = cx - w / 2, cy - h / 2
    if on:
        glow(im, (x0, y0, x0 + w, y0 + h), CYAN, 0.30, 16)
        body = gradient((int(round(w * S)), int(round(h * S))),
                        [(0.0, (110, 240, 255, 0.95)),
                         (0.55, (124, 255, 203, 0.92)),
                         (1.0, (167, 139, 250, 0.95))], "h")
        stamp(im, body, (x0, y0), rr_mask(body.size, body.size[1] / 2))
        stamp(im, _rgba(body.size, (255, 255, 255, 0.42)), (x0, y0),
              ring_mask(body.size, body.size[1] / 2, max(1, int(1.4 * S))))
        kx = x0 + w - h / 2
        knob = (252, 255, 255)
    else:
        glass(im, (x0, y0, x0 + w, y0 + h), radius=h / 2, alpha=0.12, blur=16,
              border=0.30, sheen=0.10, foot=0.0, shadow=False)
        kx = x0 + h / 2
        knob = (178, 203, 223)
    r = (h / 2 - 5) * scale
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse([(kx - r) * S, (cy - r) * S,
                                   (kx + r) * S, (cy + r) * S], fill=(*knob, 255))
    im.alpha_composite(layer)


def slider(im, x, y, w, value=0.6, tint=CYAN):
    h = 12.0
    glass(im, (x, y - h / 2, x + w, y + h / 2), radius=h / 2, alpha=0.10, blur=14,
          border=0.22, sheen=0.08, foot=0.0, shadow=False)
    fw = max(0.0, w * value)
    if fw > 3:
        g = gradient((int(round(fw * S)), int(round(h * S))),
                     [(0.0, (*tint, 0.55)), (1.0, (*MINT, 0.95))], "h")
        stamp(im, g, (x, y - h / 2), rr_mask(g.size, g.size[1] / 2))
    kx = x + fw
    glow(im, (kx - 17, y - 17, kx + 17, y + 17), tint, 0.5, 12, shape="circle")
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([(kx - 15) * S, (y - 15) * S, (kx + 15) * S, (y + 15) * S],
              fill=(250, 254, 255, 255))
    d.ellipse([(kx - 15) * S, (y - 15) * S, (kx + 15) * S, (y + 15) * S],
              outline=(*tint, 190), width=int(2 * S))
    im.alpha_composite(layer)


def chip(im, cx, cy, label, tint=CYAN, size=19, padx=18, icon_name=None, alpha=0.15):
    tw = textw(label, size, 700) / S
    w = tw + padx * 2 + (30 if icon_name else 0)
    h = 46.0
    glass(im, (cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), radius=h / 2,
          alpha=alpha, tint=tint, blur=16, border=0.34, sheen=0.12, foot=0.0,
          shadow=False)
    lx = cx - w / 2 + padx
    if icon_name:
        icon(im, icon_name, lx + 10, cy, size=20, color=tint)
        lx += 30
    txt(im, label, lx, cy, size=size, weight=700, color=tint, valign="middle")
    return w


def segmented(im, box, items, active=0, tint=CYAN):
    x0, y0, x1, y1 = box
    glass(im, box, radius=(y1 - y0) / 2, alpha=0.10, blur=20, border=0.28,
          sheen=0.10, foot=0.0, shadow=False)
    n = len(items)
    sw = (x1 - x0) / n
    if active is not None:
        ax0 = x0 + sw * active + 6
        glass(im, (ax0, y0 + 6, ax0 + sw - 12, y1 - 6), radius=(y1 - y0) / 2 - 6,
              alpha=0.22, tint=tint, blur=14, border=0.55, sheen=0.16, foot=0.0,
              shadow=False, glowc=tint, glow_a=0.26, glow_r=14)
    for i, item in enumerate(items):
        cx = x0 + sw * (i + 0.5)
        col = tint if i == active else INK_2
        txt(im, item, cx, (y0 + y1) / 2, size=20, weight=800 if i == active else 600,
            color=col, halign="center", valign="middle", maxw=sw - 8)


def search_field(im, box, placeholder="Search", filter_btn=True, value=None):
    x0, y0, x1, y1 = box
    glass(im, box, radius=(y1 - y0) / 2, alpha=0.10, blur=22, border=0.30,
          sheen=0.12, foot=0.0)
    if filter_btn:
        fw = (y1 - y0) - 12
        orb(im, x1 - 8 - fw / 2, (y0 + y1) / 2, size=fw, name="sliders-horizontal",
            icon_color=INK_2, alpha=0.14)
    icon(im, "search", x0 + 34, (y0 + y1) / 2, size=24, color=INK_FAINT)
    txt(im, value if value else placeholder, x0 + 68, (y0 + y1) / 2, size=22,
        weight=500, color=INK if value else INK_FAINT, valign="middle",
        maxw=(x1 - x0) - 150)


def action_button(im, box, label, icon_name=None, accent=True, size=26,
                  text_color=None, radius=None):
    x0, y0, x1, y1 = box
    r = radius if radius is not None else (y1 - y0) / 2
    bw, bh = int(round((x1 - x0) * S)), int(round((y1 - y0) * S))
    if accent:
        glow(im, box, CYAN, 0.40, 30)
        body = gradient((bw, bh), [(0.0, (120, 244, 255, 1.0)),
                                   (0.5, (124, 255, 203, 0.98)),
                                   (1.0, (167, 139, 250, 1.0))], "du")
        stamp(im, body, (x0, y0), rr_mask((bw, bh), r * S))
        stamp(im, _rgba((bw, bh), (255, 255, 255, 0.38)), (x0, y0),
              ring_mask((bw, bh), r * S, max(1, int(1.6 * S))))
        gl = gradient((bw, int(bh * 0.55)),
                      [(0.0, (255, 255, 255, 0.32)), (1.0, (255, 255, 255, 0.0))], "v")
        gl = gl.crop((0, 0, bw, int(bh * 0.55)))
        gl.putalpha(ImageChops.multiply(
            gl.split()[3], rr_mask((bw, int(bh * 0.55)), r * S)))
        im.alpha_composite(gl, (int(round(x0 * S)), int(round(y0 * S))))
        col = text_color or (5, 16, 24)
    else:
        glass(im, box, radius=r, alpha=0.12, blur=24, border=0.34, sheen=0.14)
        col = text_color or INK
    if icon_name:
        total = textw(label, size, 800) / S + 20 + size * 1.2
        sx = (x0 + x1) / 2 - total / 2
        icon(im, icon_name, sx + size * 0.55, (y0 + y1) / 2, size=size, color=col)
        txt(im, label, sx + size * 1.2 + 20, (y0 + y1) / 2, size=size, weight=800,
            color=col, valign="middle", tracking=0.8)
    else:
        txt(im, label, (x0 + x1) / 2, (y0 + y1) / 2, size=size, weight=800,
            color=col, halign="center", valign="middle", tracking=1.2,
            maxw=(x1 - x0) - 40)


def icon_orb(im, cx, cy, size=64, name=None, tint=CYAN, strong=False):
    r = size / 2
    glass(im, (cx - r, cy - r, cx + r, cy + r), radius=r,
          alpha=0.20 if strong else 0.13, tint=tint, blur=18, border=0.40,
          sheen=0.18, foot=0.0, shadow=False, shape="circle",
          glowc=tint if strong else None, glow_a=0.30 if strong else 0.0, glow_r=18)
    if name:
        icon(im, name, cx, cy + 1, size=size * 0.46, color=tint)


# ----------------------------------------------------------------------------
# list building blocks
# ----------------------------------------------------------------------------
def section_title(im, x, y, label, color=INK_FAINT, size=18.5, tracking=2.8,
                  icon_name=None, right=None):
    if icon_name:
        icon(im, icon_name, x + 11, y + 12, size=21, color=color)
        x += 36
    txt(im, label.upper(), x, y, size=size, weight=800, color=color, tracking=tracking,
        maxw=W - (x + 60) - (140 if right else 0))
    if right:
        txt(im, right.upper(), W - 62, y + 2, size=16.5, weight=800, color=CYAN,
            halign="right", tracking=2.0, alpha=0.9)
    return y + 38


def row(im, box, name=None, title="", sub=None, control=None, tint=CYAN,
        value=None, title_size=24, strong=False, badge=None):
    x0, y0, x1, y1 = box
    glass(im, box, radius=32, alpha=0.13 if strong else 0.095, blur=26,
          border=0.5 if strong else 0.34, sheen=0.12, foot=0.08)
    cy = (y0 + y1) / 2
    lx = x0 + 28
    if name:
        icon_orb(im, lx + 32, cy, size=62, name=name, tint=tint, strong=strong)
        lx += 84
    else:
        lx += 10
    ctrl_w = {"chevron": 60, "toggle_on": 110, "toggle_off": 110,
              "value": 40, "text": 40, None: 40}.get(control, 60)
    avail = (x1 - 10) - lx - ctrl_w
    if sub:
        lines = wrap(sub, 18, 450, avail * S)
        txt(im, title, lx, y0 + 20, size=title_size, weight=650, color=INK,
            maxw=avail)
        for i, line in enumerate(lines[:2]):
            txt(im, line, lx, y0 + 54 + i * 23, size=18, weight=450, color=INK_DIM,
                maxw=avail)
    else:
        txt(im, title, lx, cy, size=title_size, weight=650, color=INK,
            valign="middle", maxw=avail)
    if badge:
        chip(im, x1 - 74, cy, badge, tint=tint, size=16.5, padx=14)
    if control == "chevron":
        icon(im, "chevron-right", x1 - 42, cy, size=28, color=INK_FAINT)
    elif control == "toggle_on":
        toggle(im, x1 - 68, cy, True)
    elif control == "toggle_off":
        toggle(im, x1 - 68, cy, False)
    elif control == "value":
        vw = textw(value or "", 20.5, 650) / S
        txt(im, value or "", x1 - 78, cy, size=20.5, weight=650, color=INK_2,
            halign="right", valign="middle", maxw=380)
        icon(im, "chevron-right", x1 - 42, cy, size=26, color=INK_FAINT)
    elif control == "text":
        txt(im, value or "", x1 - 34, cy, size=21, weight=700, color=CYAN,
            halign="right", valign="middle", maxw=380)


def tile(im, box, name=None, title="", control=None, tint=CYAN, value=None,
         sub=None):
    x0, y0, x1, y1 = box
    on = control == "toggle_on"
    glass(im, box, radius=32, alpha=0.14 if on else 0.09, blur=24,
          border=0.46 if on else 0.30, sheen=0.13, foot=0.08,
          glowc=tint if on else None, glow_a=0.20, glow_r=20)
    cy = (y0 + y1) / 2
    icon_orb(im, x0 + 40, cy, size=56, name=name, tint=tint, strong=on)
    ctrl_w = 92 if control in ("toggle_on", "toggle_off") else 40
    maxw = (x1 - x0) - 78 - ctrl_w
    if sub:
        txt(im, title, x0 + 78, y0 + 20, size=20.5, weight=650, color=INK, maxw=maxw)
        txt(im, sub, x0 + 78, y0 + 50, size=17, weight=450, color=INK_DIM, maxw=maxw)
    else:
        lines = wrap(title, 20, 650, maxw * S)
        if len(lines) > 1:
            for i, line in enumerate(lines[:2]):
                txt(im, line, x0 + 78, cy - 24 + i * 25, size=20, weight=650,
                    color=INK, maxw=maxw)
        else:
            txt(im, title, x0 + 78, cy, size=20, weight=650, color=INK,
                valign="middle", maxw=maxw)
    if control == "toggle_on":
        toggle(im, x1 - 50, cy, True, scale=0.78)
    elif control == "toggle_off":
        toggle(im, x1 - 50, cy, False, scale=0.78)
    elif control == "value":
        txt(im, value or "", x1 - 28, cy, size=18.5, weight=700, color=CYAN,
            halign="right", valign="middle", maxw=150)


def slider_row(im, box, name, title, value, value_text, tint=CYAN):
    x0, y0, x1, y1 = box
    glass(im, box, radius=32, alpha=0.095, blur=26, border=0.32, sheen=0.12, foot=0.08)
    cy = (y0 + y1) / 2
    icon_orb(im, x0 + 60, cy, size=56, name=name, tint=tint)
    txt(im, title, x0 + 100, cy - 24, size=22, weight=650, color=INK,
        maxw=(x1 - x0) - 100 - 340)
    txt(im, value_text, x0 + 100, cy + 14, size=18, weight=500, color=INK_DIM,
        maxw=(x1 - x0) - 100 - 340)
    slider(im, x1 - 320, y1 - 32, 250, value, tint)


def note(im, box, text, icon_name="circle-alert", tint=AMBER):
    x0, y0, x1, y1 = box
    glass(im, box, radius=30, alpha=0.10, tint=tint, blur=24, border=0.36,
          sheen=0.12, foot=0.06, glowc=tint, glow_a=0.16, glow_r=22)
    icon_orb(im, x0 + 44, (y0 + y1) / 2, size=52, name=icon_name, tint=tint)
    lines = wrap(text, 18.5, 550, (x1 - x0 - 104) * S)
    start = (y0 + y1) / 2 - (len(lines) - 1) * 12
    for i, line in enumerate(lines[:3]):
        txt(im, line, x0 + 82, start + i * 24, size=18.5, weight=550, color=INK_2,
            valign="middle", maxw=(x1 - x0) - 104)


def sheet(im, box, title=None, handle=True, tint=WHITE):
    x0, y0, x1, y1 = box
    glass(im, box, radius=48, alpha=0.17, tint=tint, blur=40, border=0.44,
          sheen=0.20, foot=0.14)
    if handle:
        layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
        ImageDraw.Draw(layer).rounded_rectangle(
            [(W / 2 - 46) * S, (y0 + 20) * S, (W / 2 + 46) * S, (y0 + 28) * S],
            radius=6 * S, fill=(255, 255, 255, 110))
        im.alpha_composite(layer)
    if title:
        txt(im, title, 64, y0 + 56, size=32, weight=800, color=INK, tracking=-0.4,
            maxw=(x1 - x0) - 160)


def new_screen(seed=0, shift=0.0, blobs=None):
    return backdrop(seed=seed, shift=shift, blobs=blobs)


def finish(im, path):
    out = im.resize((W, H), Image.LANCZOS) if S != 1 else im
    out.convert("RGB").save(path, "PNG", optimize=True)
    return path
