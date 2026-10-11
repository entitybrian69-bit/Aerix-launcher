"""The ten Aerix 'Liquid Crystal' screens.

Every screen keeps every feature of the launcher it replaces: nothing was
dropped, the navigation, controls and options were only re-imagined.
"""

import math

from PIL import Image, ImageDraw, ImageChops

import kit as K
from kit import (AMBER, AZURE, CYAN, INK, INK_2, INK_DIM, INK_FAINT, LIME, MINT,
                 ROSE, VIOLET, WHITE, WARN, S, W, H)

X0, X1 = 40.0, 1040.0


# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------
def arc(im, box, start, end, color, width=8, alpha=1.0):
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).arc([box[0] * S, box[1] * S, box[2] * S, box[3] * S],
                              start, end, fill=(*color, int(255 * alpha)),
                              width=int(width * S))
    im.alpha_composite(layer)


def progress(im, x, y, w, value, tint=CYAN, h=14.0):
    K.glass(im, (x, y - h / 2, x + w, y + h / 2), radius=h / 2, alpha=0.10,
            blur=14, border=0.20, sheen=0.08, foot=0.0, shadow=False)
    fw = max(0.0, w * value)
    if fw > 3:
        g = K.gradient((int(fw * S), int(h * S)),
                       [(0.0, (*tint, 0.6)), (1.0, (*K.MINT, 0.95))], "h")
        K.stamp(im, g, (x, y - h / 2), K.rr_mask(g.size, g.size[1] / 2))


def field(im, box, label, value, icon_name=None, tint=CYAN):
    x0, y0, x1, y1 = box
    K.glass(im, box, radius=28, alpha=0.09, blur=22, border=0.28, sheen=0.10, foot=0.06)
    K.txt(im, label.upper(), x0 + 26, y0 + 18, size=16, weight=800, color=INK_FAINT,
          tracking=2.0, maxw=(x1 - x0) - 52)
    lx = x0 + 26
    if icon_name:
        K.icon(im, icon_name, lx + 14, (y0 + y1) / 2 + 8, size=24, color=tint)
        lx += 40
    K.txt(im, value, lx, (y0 + y1) / 2 + 10, size=22, weight=600, color=INK_2,
          valign="middle", maxw=(x1 - x0) - (lx - x0) - 26)


def chips_row(im, x, y, items, gap=10.0, size=17):
    cx = x
    for label, tint in items:
        w = K.chip(im, 0, 0, label, tint=tint, size=size)  # measure only
        K.chip(im, cx + w / 2, y, label, tint=tint, size=size)
        cx += w + gap
    return cx


def stack_card(im, box, title=None, sub=None, icon_name=None, tint=CYAN,
               right=None, radius=36.0):
    x0, y0, x1, y1 = box
    K.glass(im, box, radius=radius, alpha=0.10, blur=30, border=0.34, sheen=0.14,
            foot=0.10)
    lx = x0 + 30
    if icon_name:
        K.icon_orb(im, lx + 34, y0 + (82 if title else (y1 - y0) / 2), size=68,
                   name=icon_name, tint=tint)
        lx += 82
    if title:
        K.txt(im, title, lx, y0 + 26, size=27, weight=750, color=INK,
              maxw=(x1 - x0) - (lx - x0) - 60)
        if sub:
            K.txt(im, sub, lx, y0 + 62, size=19, weight=500, color=INK_DIM,
                  maxw=(x1 - x0) - (lx - x0) - 60)
    if right:
        K.orb(im, x1 - 46, y0 + 46 if title else (y0 + y1) / 2, size=56,
              name=right, icon_color=INK_2, alpha=0.12)


class Stack:
    """Vertical layout cursor with overflow protection."""

    def __init__(self, im, y=K.CONTENT_TOP, x0=X0, x1=X1, bottom=K.CONTENT_BOTTOM):
        self.im, self.y, self.x0, self.x1, self.bottom = im, y, x0, x1, bottom

    def _adv(self, h):
        top = self.y
        self.y = top + h
        if self.y > self.bottom + 0.5:
            WARN.append(f"content overflow: block ends at {self.y:.0f} "
                        f"(limit {self.bottom:.0f})")
        return top

    def gap(self, h=16.0):
        self.y += h
        return self

    def at(self, y):
        self.y = y
        return self

    def section(self, label, right=None, icon_name=None, color=INK_FAINT):
        self.y = K.section_title(self.im, self.x0 + 18, self.y, label,
                                 right=right, icon_name=icon_name, color=color)
        self.y += 8
        return self

    def row(self, h=90.0, **kw):
        y = self._adv(h)
        K.row(self.im, (self.x0, y, self.x1, y + h), **kw)
        return self

    def slider(self, h=100.0, **kw):
        y = self._adv(h)
        K.slider_row(self.im, (self.x0, y, self.x1, y + h), **kw)
        return self

    def tiles(self, items, h=96.0, cols=2, gap=12.0):
        w = (self.x1 - self.x0 - gap * (cols - 1)) / cols
        rows = math.ceil(len(items) / cols)
        for r in range(rows):
            y = self._adv(h)
            for c in range(cols):
                i = r * cols + c
                if i >= len(items):
                    break
                bx = self.x0 + c * (w + gap)
                K.tile(self.im, (bx, y, bx + w, y + h), **items[i])
            if r != rows - 1:
                self.y += gap
        return self

    def note(self, h=96.0, **kw):
        y = self._adv(h)
        K.note(self.im, (self.x0, y, self.x1, y + h), **kw)
        return self

    def card(self, h, fn, **kw):
        y = self._adv(h)
        fn((self.x0, y, self.x1, y + h), self.im, **kw)
        return self


# ---------------------------------------------------------------------------
# 01 - LAUNCH
# ---------------------------------------------------------------------------
def launch(im):
    K.screen_header(im, "Ready when you are", "Aerix Liquid Crystal",
                    kicker="AERIX",
                    right=[{"icon": "bell", "tint": WHITE},
                           {"icon": "user-round", "tint": CYAN, "glow": CYAN}])
    s = Stack(im, 306)

    # --- hero: selected instance ------------------------------------------
    def hero(b, _im):
        x0, y0, x1, y1 = b
        K.glass(im, b, radius=40, alpha=0.12, blur=32, border=0.42, sheen=0.16,
                foot=0.10, glowc=MINT, glow_a=0.22, glow_r=40)
        K.icon_orb(im, x0 + 74, y0 + 96, size=104, name="box", tint=MINT, strong=True)
        K.txt(im, "Aerix Survival", x0 + 148, y0 + 46, size=36, weight=800,
              color=INK, tracking=-0.6, maxw=(x1 - x0) - 260)
        K.txt(im, "Minecraft 1.21.4 · Fabric 0.16.9 · 18 mods", x0 + 150, y0 + 96,
              size=20, weight=550, color=INK_DIM, maxw=(x1 - x0) - 280)
        chips_row(im, x0 + 150, y0 + 148,
                  [("RELEASE", MINT), ("FABRIC", VIOLET), ("18 MODS", AZURE)])
        K.orb(im, x1 - 62, y0 + 96, size=64, name="chevron-right",
              icon_color=INK_2, alpha=0.12)
    s.card(220, hero).gap(28)

    # --- launch console ---------------------------------------------------
    def console(b, _im=None):
        x0, y0, x1, y1 = b
        K.glass(im, b, radius=48, alpha=0.11, blur=34, border=0.40, sheen=0.16,
                foot=0.12)
        cx, cy = (x0 + x1) / 2, y0 + 196
        K.glow(im, (cx - 150, cy - 150, cx + 150, cy + 150), CYAN, 0.42, 60,
               shape="circle")
        # halo ring
        K.glass(im, (cx - 148, cy - 148, cx + 148, cy + 148), radius=148,
                alpha=0.06, blur=20, border=0.22, sheen=0.08, foot=0.0,
                shadow=False, shape="circle")
        arc(im, (cx - 148, cy - 148, cx + 148, cy + 148), 200, 340, VIOLET, 6, 0.85)
        arc(im, (cx - 166, cy - 166, cx + 166, cy + 166), 130, 190, CYAN, 4, 0.55)
        # crystal core
        body = K.gradient((int(230 * S), int(230 * S)),
                          [(0.0, (132, 246, 255, 1.0)),
                           (0.45, (124, 255, 203, 0.98)),
                           (1.0, (167, 139, 250, 1.0))], "du")
        K.stamp(im, body, (cx - 115, cy - 115), K.circle_mask(body.size))
        gloss = K.gradient((int(230 * S), int(120 * S)),
                           [(0.0, (255, 255, 255, 0.42)),
                            (1.0, (255, 255, 255, 0.0))], "v")
        gloss.putalpha(ImageChops.multiply(
            gloss.split()[3], K.rr_mask((int(230 * S), int(120 * S)), int(115 * S))))
        im.alpha_composite(gloss, (int((cx - 115) * S), int((cy - 115) * S)))
        K.icon(im, "play", cx - 6, cy, size=94, color=(6, 18, 28))
        K.txt(im, "LAUNCH", cx, cy + 156, size=25, weight=800, color=INK,
              halign="center", tracking=7.0)
        K.txt(im, "Hold for quick settings", cx, cy + 192, size=19, weight=500,
              color=INK_DIM, halign="center")
        chips_row(im, x0 + 190, y1 - 54,
                  [("1.21.4", MINT), ("JRE 21", CYAN), ("4096 MB", VIOLET),
                   ("MobileGlues", AZURE)])
    s.card(452, console).gap(30)

    s.section("QUICK ACTIONS", right="6 actions")
    s.tiles([
        {"name": "gamepad-2", "title": "Custom controls", "tint": AZURE},
        {"name": "package", "title": "Execute a .jar", "tint": AMBER},
        {"name": "share-2", "title": "Share log file", "tint": CYAN},
    ], h=108, cols=3).gap(12)
    s.tiles([
        {"name": "folder-open", "title": "Game directory", "tint": MINT},
        {"name": "book-open", "title": "Wiki & news", "tint": VIOLET},
        {"name": "users", "title": "Community", "tint": ROSE},
    ], h=108, cols=3).gap(30)

    s.section("ACTIVE TASKS", right="2 running")
    s.card(190, lambda b, _i=None: _tasks(im, b)).gap(30)

    s.section("ACCOUNT", right="switch")
    s.row(h=96, name="user-round", tint=CYAN, title="Notch",
          sub="Microsoft account · signed in", control="toggle_on")
    s.gap(12)
    s.row(h=96, name="user", tint=VIOLET, title="Steve",
          sub="Offline (local) account", control="chevron")
    s.gap(18)
    s.card(96, lambda b, _i=None: _add_account(im, b))
    K.dock(im, 0)


def _tasks(im, b):
    x0, y0, x1, y1 = b
    K.glass(im, b, radius=36, alpha=0.10, blur=28, border=0.32, sheen=0.12, foot=0.08)
    K.icon_orb(im, x0 + 56, y0 + 58, size=56, name="download", tint=CYAN)
    K.txt(im, "Downloading assets", x0 + 96, y0 + 30, size=21, weight=650, color=INK,
          maxw=(x1 - x0) - 260)
    K.txt(im, "62% · 148.2 MB / 239 MB", x0 + 96, y0 + 62, size=18, weight=500,
          color=INK_DIM, maxw=(x1 - x0) - 260)
    progress(im, x0 + 96, y0 + 116, (x1 - x0) - 260, 0.62, CYAN)
    K.icon_orb(im, x0 + 56, y0 + 152, size=56, name="file-archive", tint=MINT)
    K.txt(im, "Unpacking JRE 21", x0 + 96, y0 + 124, size=21, weight=650, color=INK,
          maxw=(x1 - x0) - 260)
    progress(im, x0 + 96, y0 + 168, (x1 - x0) - 260, 0.28, MINT)


def _add_account(im, b):
    x0, y0, x1, y1 = b
    K.glass(im, b, radius=32, alpha=0.09, blur=24, border=0.28, sheen=0.10, foot=0.06)
    K.txt(im, "ADD ACCOUNT", x0 + 30, y0 + 34, size=16, weight=800, color=INK_FAINT,
          tracking=2.2, valign="middle", maxw=190)
    chips_row(im, x0 + 210, y0 + 48,
              [("Microsoft", CYAN), ("Ely.by", VIOLET), ("Offline", MINT)], gap=12)


# ---------------------------------------------------------------------------
# 02 - INSTANCES
# ---------------------------------------------------------------------------
_INSTANCES = [
    ("Aerix Survival", "1.21.4 · Fabric 0.16.9 · 18 mods", "box", MINT, True,
     "12.4 GB"),
    ("Vanilla Plus", "1.20.6 · Vanilla · 0 mods", "layers", CYAN, False, "3.1 GB"),
    ("Beta Adventures", "b1.7.3 · Legacy Fabric", "package", AZURE, False, "1.8 GB"),
    ("NeoForge Lab", "1.21.1 · NeoForge 21.1.72 · 42 mods", "flask-conical",
     VIOLET, False, "7.5 GB"),
]


def instances(im):
    K.screen_header(im, "Instances", "4 instances · 24.8 GB used", kicker="LIBRARY",
                    right=[{"icon": "search", "tint": WHITE},
                           {"icon": "plus", "tint": MINT, "glow": MINT}])
    s = Stack(im, 300)
    s.card(84, lambda b, _i=None: K.search_field(im, b, "Search instances")).gap(18)
    s.card(72, lambda b, _i=None: K.segmented(
        im, (b[0] + 6, b[1] + 6, b[2] - 6, b[3] - 6),
        ["Release", "Snapshot", "Old-alpha", "Old-beta"], 0)).gap(28)

    s.section("INSTALLED", right="edit")
    for name, sub, ico, tint, active, size in _INSTANCES:
        s.card(122, lambda b, _im=None, n=name, sb=sub, i=ico, t=tint, a=active,
               sz=size: _instance_card(im, b, n, sb, i, t, a, sz)).gap(12)
    s.gap(18)

    s.section("INSTANCE EDITOR", right="Aerix Survival")
    s.card(700, lambda b, _i=None: _instance_editor(im, b))
    K.dock(im, 1)


def _instance_card(im, b, name, sub, ico, tint, active, size):
    x0, y0, x1, y1 = b
    K.glass(im, b, radius=34,
            alpha=0.16 if active else 0.09, tint=tint if active else WHITE,
            blur=28, border=0.56 if active else 0.28, sheen=0.14, foot=0.08,
            glowc=tint if active else None, glow_a=0.22, glow_r=26)
    K.icon_orb(im, x0 + 68, (y0 + y1) / 2, size=88, name=ico, tint=tint,
               strong=active)
    K.txt(im, name, x0 + 130, y0 + 30, size=26, weight=750, color=INK,
          maxw=(x1 - x0) - 340)
    K.txt(im, sub, x0 + 132, y0 + 68, size=18, weight=500, color=INK_DIM,
          maxw=(x1 - x0) - 340)
    K.chip(im, x1 - 176, y0 + 46, size, tint=tint, size=16, padx=14)
    K.orb(im, x1 - 62, (y0 + y1) / 2, size=60, name="more-horizontal",
          icon_color=INK_2, alpha=0.12)
    if active:
        K.txt(im, "RUNNING", x0 + 132, y0 + 92, size=15, weight=800, color=MINT,
              tracking=2.0)


def _instance_editor(im, b):
    x0, y0, x1, y1 = b
    K.glass(im, b, radius=44, alpha=0.12, blur=34, border=0.40, sheen=0.15, foot=0.12)
    cx = (x0 + x1) / 2
    K.icon_orb(im, cx, y0 + 74, size=104, name="box", tint=MINT, strong=True)
    K.orb(im, cx + 40, y0 + 104, size=44, name="edit-3", tint=WHITE,
          icon_color=INK, alpha=0.30)
    K.txt(im, "PROFILE NAME", x0 + 40, y0 + 148, size=16, weight=800,
          color=INK_FAINT, tracking=2.2, maxw=400)
    K.glass(im, (x0 + 40, y0 + 172, x1 - 40, y0 + 240), radius=26, alpha=0.09,
            blur=20, border=0.26, sheen=0.08, foot=0.05)
    K.txt(im, "Aerix Survival", x0 + 66, y0 + 206, size=24, weight=650, color=INK,
          valign="middle", maxw=(x1 - x0) - 120)

    rows = [
        ("Version", "Minecraft 1.21.4", "layers", MINT, "value"),
        ("Control scheme", "Aerix default", "gamepad-2", AZURE, "value"),
        ("Shared data", "Disabled — private game directory", "database", VIOLET,
         "toggle_off"),
        ("JVM arguments", "-Xmx4096M -XX:+UseG1GC", "terminal", CYAN, "value"),
        ("Java runtime", "JRE 21.0.3", "coffee", AMBER, "value"),
        ("Renderer", "MobileGlues (OpenGL ES)", "monitor-play", ROSE, "value"),
    ]
    y = y0 + 262
    for title, value, ico, tint, control in rows:
        K.row(im, (x0 + 40, y, x1 - 40, y + 92), name=ico, title=title, sub=value,
              control=control, tint=tint, value=value, title_size=21)
        y += 104
    K.action_button(im, (x0 + 40, y1 - 108, cx - 10, y1 - 24), "SAVE",
                    icon_name="check", size=23)
    K.action_button(im, (cx + 10, y1 - 108, x1 - 40, y1 - 24), "DELETE", accent=False,
                    icon_name="trash-2", size=23, text_color=ROSE)


# ---------------------------------------------------------------------------
# 03 - MODS & PACKS
# ---------------------------------------------------------------------------
_PROFILE_TYPES = [
    ("Vanilla", "layers", MINT), ("OptiFine", "eye", CYAN),
    ("Fabric", "puzzle", VIOLET), ("Quilt", "boxes", AZURE),
    ("Legacy Fabric", "archive", ROSE), ("Forge", "flame", AMBER),
    ("NeoForge", "box", LIME), ("Modpack", "package", CYAN),
    ("BTA", "hammer", VIOLET),
]


def mods(im):
    K.screen_header(im, "Mods & packs", "Install loaders, browse mods",
                    kicker="DISCOVER",
                    right=[{"icon": "download", "tint": MINT, "glow": MINT},
                           {"icon": "search", "tint": WHITE}])
    s = Stack(im, 300)
    s.card(84, lambda b, _i=None: K.search_field(im, b, "Search mods and modpacks")).gap(26)
    s.section("NEW INSTANCE", right="9 types")
    s.tiles([{"name": ico, "title": name, "tint": tint}
             for name, ico, tint in _PROFILE_TYPES], h=104, cols=3).gap(28)

    s.section("INSTALL FABRIC", right="step 2 of 2")
    s.card(380, lambda b, _i=None: _installer(im, b)).gap(28)

    s.section("BROWSE MODS", right="1.2k results")
    for name, author, dl, ver, tint in [
        ("Sodium", "CaffeineMC · 24.6M downloads", "1.21.4", "0.6.9", MINT),
        ("Iris Shaders", "IrisDevs · 18.2M downloads", "1.21.4", "1.8.0", VIOLET),
        ("JEI", "mezz · 42.1M downloads", "1.21.1", "19.21.0", CYAN),
    ]:
        s.card(118, lambda b, _im=None, n=name, a=author, v=ver, d=dl, t=tint:
               _mod_card(im, b, n, a, d, v, t)).gap(12)
    s.gap(20)
    s.card(96, lambda b, _i=None: K.action_button(
        im, (b[0] + 20, b[1] + 18, b[2] - 20, b[3] - 18), "IMPORT LOCAL MODPACK",
        accent=False, icon_name="file-archive", size=23))
    K.dock(im, 2)


def _installer(im, b):
    x0, y0, x1, y1 = b
    K.glass(im, b, radius=40, alpha=0.12, blur=32, border=0.40, sheen=0.15, foot=0.10,
            glowc=VIOLET, glow_a=0.20, glow_r=30)
    K.icon_orb(im, x0 + 66, y0 + 66, size=84, name="puzzle", tint=VIOLET, strong=True)
    K.txt(im, "Fabric Loader", x0 + 124, y0 + 34, size=30, weight=800, color=INK,
          maxw=(x1 - x0) - 240)
    K.txt(im, "Lightweight mod loader for modern versions", x0 + 126, y0 + 76,
          size=18.5, weight=500, color=INK_DIM, maxw=(x1 - x0) - 240)
    K.chip(im, x1 - 92, y0 + 62, "STEP 2", tint=VIOLET, size=16, padx=16)
    y = y0 + 132
    K.row(im, (x0 + 30, y, x1 - 30, y + 88), name="layers", title="Game version",
          value="1.21.4", control="value", tint=MINT, title_size=21)
    y += 100
    K.row(im, (x0 + 30, y, x1 - 30, y + 88), name="package", title="Loader version",
          value="0.16.9", control="value", tint=CYAN, title_size=21)
    y += 100
    K.row(im, (x0 + 30, y, x1 - 30, y + 88), name="shield-check",
          title="Only stable releases", control="toggle_on", tint=VIOLET,
          title_size=21)


def _mod_card(im, b, name, author, version, size, tint):
    x0, y0, x1, y1 = b
    K.glass(im, b, radius=32, alpha=0.09, blur=26, border=0.28, sheen=0.12, foot=0.06)
    K.icon_orb(im, x0 + 62, (y0 + y1) / 2, size=76, name="puzzle", tint=tint)
    K.txt(im, name, x0 + 116, y0 + 26, size=25, weight=750, color=INK,
          maxw=(x1 - x0) - 330)
    K.txt(im, author, x0 + 118, y0 + 62, size=18, weight=500, color=INK_DIM,
          maxw=(x1 - x0) - 330)
    K.txt(im, version, x0 + 118, y0 + 88, size=17, weight=600, color=INK_FAINT,
          maxw=(x1 - x0) - 330)
    K.chip(im, x1 - 168, y0 + 42, size, tint=tint, size=16.5, padx=15)
    K.orb(im, x1 - 62, (y0 + y1) / 2, size=60, name="download", icon_color=tint,
          alpha=0.12)


# ---------------------------------------------------------------------------
# 04 - CONTROL STUDIO
# ---------------------------------------------------------------------------
def controls(im):
    K.screen_header(im, "Control studio", "Aerix default · 24 buttons",
                    kicker="DESIGN",
                    right=[{"icon": "gamepad", "tint": VIOLET, "glow": VIOLET},
                           {"icon": "save", "tint": MINT}])
    s = Stack(im, 300)
    s.card(600, lambda b, _i=None: _stage(im, b)).gap(20)
    s.card(96, lambda b, _i=None: _toolbar(im, b)).gap(26)

    s.section("BUTTON", right="KEY_JUMP")
    s.row(h=88, name="keyboard", title="Mapping", value="KEY_SPACE", control="value",
          tint=CYAN, title_size=21).gap(12)
    s.row(h=88, name="scaling", title="Size", value="96 × 96", control="value",
          tint=AZURE, title_size=21).gap(12)
    s.row(h=88, name="palette", title="Background", value="color", control="value",
          tint=MINT, title_size=21).gap(12)
    s.row(h=88, name="pipette", title="Stroke", value="color", control="value",
          tint=VIOLET, title_size=21).gap(20)
    s.tiles([
        {"name": "toggle-left", "title": "Toggleable", "control": "toggle_on",
         "tint": CYAN},
        {"name": "mouse-pointer-2", "title": "Mouse pass-thru",
         "control": "toggle_off", "tint": AZURE},
    ], h=94).gap(12)
    s.tiles([
        {"name": "move", "title": "Swipeable", "control": "toggle_off",
         "tint": VIOLET},
        {"name": "lock", "title": "Forward lock", "control": "toggle_off",
         "tint": ROSE},
    ], h=94).gap(12)
    s.tiles([
        {"name": "crosshair", "title": "Absolute finger tracking",
         "control": "toggle_on", "tint": MINT},
    ], h=94, cols=1).gap(20)
    s.slider(h=100, name="scan", title="Corner radius", value=0.5,
             value_text="50% · 48 dp", tint=CYAN).gap(12)
    s.slider(h=100, name="ruler", title="Stroke width", value=0.3,
             value_text="3 dp", tint=AZURE).gap(12)
    s.slider(h=100, name="droplet", title="Button opacity", value=0.85,
             value_text="85%", tint=MINT)
    K.dock(im, 3)


def _stage(im, b):
    x0, y0, x1, y1 = b
    K.glass(im, b, radius=42, alpha=0.08, blur=30, border=0.32, sheen=0.12, foot=0.10)
    # faint game scene behind
    scene = K.gradient((int((x1 - x0 - 32) * S), int((y1 - y0 - 32) * S)),
                       [(0.0, (58, 128, 196, 0.55)), (0.55, (96, 178, 148, 0.45)),
                        (1.0, (38, 46, 66, 0.65))], "v")
    K.stamp(im, scene, (x0 + 16, y0 + 16),
            K.rr_mask(scene.size, int(30 * S)))
    # grid
    for gx in range(1, 8):
        K.hairline(im, x0 + 16 + (x1 - x0 - 32) * gx / 8, y0 + 20,
                   x0 + 16 + (x1 - x0 - 32) * gx / 8, y1 - 20, alpha=0.05)
    for gy in range(1, 5):
        K.hairline(im, x0 + 20, y0 + 16 + (y1 - y0 - 32) * gy / 5,
                   x1 - 20, y0 + 16 + (y1 - y0 - 32) * gy / 5, alpha=0.05)

    # joystick
    jx, jy = x0 + 150, y0 + 372
    K.glass(im, (jx - 84, jy - 84, jx + 84, jy + 84), radius=84, alpha=0.10,
            blur=18, border=0.42, sheen=0.16, foot=0.0, shadow=False, shape="circle")
    K.glow(im, (jx - 44, jy - 44, jx + 44, jy + 44), CYAN, 0.45, 20, shape="circle")
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse([(jx - 44) * S, (jy - 44) * S, (jx + 44) * S,
                                   (jy + 44) * S], fill=(190, 250, 255, 210))
    im.alpha_composite(layer)

    # action buttons cluster
    def cbtn(cx, cy, label, tint, r=44.0, selected=False):
        K.glass(im, (cx - r, cy - r, cx + r, cy + r), radius=r, alpha=0.14,
                tint=tint, blur=16, border=0.55 if selected else 0.34,
                sheen=0.18, foot=0.0, shadow=False, shape="circle",
                glowc=tint if selected else None, glow_a=0.35, glow_r=20)
        K.txt(im, label, cx, cy + 1, size=26, weight=800, color=INK,
              halign="center", valign="middle")
        if selected:
            arc(im, (cx - r - 12, cy - r - 12, cx + r + 12, cy + r + 12), 0, 359,
                CYAN, 4, 0.9)

    cbtn(x0 + 792, y0 + 300, "A", MINT)
    cbtn(x0 + 900, y0 + 392, "B", ROSE)
    cbtn(x0 + 792, y0 + 484, "X", AZURE)
    cbtn(x0 + 900, y0 + 576, "Y", AMBER)
    cbtn(x0 + 470, y0 + 470, "⇧", VIOLET, r=40, selected=True)

    # selection frame with handles
    K.hairline(im, x0 + 470 - 52, y0 + 470 - 52, x0 + 470 + 52, y0 + 470 - 52,
               CYAN, 0.9, 2.6)
    K.hairline(im, x0 + 470 - 52, y0 + 470 + 52, x0 + 470 + 52, y0 + 470 + 52,
               CYAN, 0.9, 2.6)
    K.hairline(im, x0 + 470 - 52, y0 + 470 - 52, x0 + 470 - 52, y0 + 470 + 52,
               CYAN, 0.9, 2.6)
    K.hairline(im, x0 + 470 + 52, y0 + 470 - 52, x0 + 470 + 52, y0 + 470 + 52,
               CYAN, 0.9, 2.6)
    for hx, hy in [(-52, -52), (52, -52), (-52, 52), (52, 52)]:
        layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
        ImageDraw.Draw(layer).ellipse([(x0 + 470 + hx - 11) * S,
                                       (y0 + 470 + hy - 11) * S,
                                       (x0 + 470 + hx + 11) * S,
                                       (y0 + 470 + hy + 11) * S],
                                      fill=(255, 255, 255, 240))
        im.alpha_composite(layer)

    # hotbar
    hx0 = x0 + 250
    K.glass(im, (hx0, y0 + 96, hx0 + 500, y0 + 156), radius=26, alpha=0.10,
            blur=16, border=0.28, sheen=0.10, foot=0.0, shadow=False)
    for i in range(9):
        bx = hx0 + 14 + i * 53
        K.glass(im, (bx, y0 + 106, bx + 44, y0 + 146), radius=12, alpha=0.12,
                tint=CYAN if i == 2 else WHITE, blur=10, border=0.3, sheen=0.10,
                foot=0.0, shadow=False)
    # virtual mouse
    K.icon(im, "mouse-pointer-2", x0 + 560, y0 + 240, size=52, color=(235, 252, 255),
           alpha=0.9)
    K.txt(im, "GRID 8 × 8  ·  SNAP ON", x0 + 34, y0 + 540, size=16, weight=800,
          color=INK_FAINT, tracking=2.2, maxw=400)


def _toolbar(im, b):
    x0, y0, x1, y1 = b
    K.glass(im, b, radius=32, alpha=0.13, blur=26, border=0.36, sheen=0.14, foot=0.06)
    items = ["plus", "columns-3", "circle-dot", "copy-plus", "folder-open", "save",
             "rotate-ccw"]
    tints = [MINT, CYAN, AZURE, VIOLET, AMBER, MINT, ROSE]
    n = len(items)
    slot = (x1 - x0) / n
    for i, (name, tint) in enumerate(zip(items, tints)):
        cx = x0 + slot * (i + 0.5)
        K.icon_orb(im, cx, (y0 + y1) / 2, size=62, name=name, tint=tint,
                   strong=(i == 0))


# ---------------------------------------------------------------------------
# 05 - SETTINGS
# ---------------------------------------------------------------------------
_CATEGORIES = [
    ("Video and renderer", "Resolution, renderer and performance", "monitor-play",
     ROSE),
    ("Control customization", "Gestures, buttons and scaling", "gamepad-2", AZURE),
    ("Java tweaks", "Runtimes, JVM arguments, RAM and sandbox", "coffee", AMBER),
    ("Miscellaneous", "Version list and libraries check", "folder-cog", CYAN),
    ("Experimental", "Use with consideration, no support", "flask-conical",
     VIOLET),
]


def _category_card(im, box, title, sub, ico, tint, count):
    x0, y0, x1, y1 = box
    K.glass(im, box, radius=40, alpha=0.10, blur=30, border=0.34, sheen=0.14,
            foot=0.10)
    K.icon_orb(im, x0 + 34 + 34, y0 + 82, size=68, name=ico, tint=tint)
    K.txt(im, title, x0 + 112, y0 + 30, size=24, weight=750, color=INK,
          maxw=(x1 - x0) - 170)
    for i, line in enumerate(K.wrap(sub, 18, 500, ((x1 - x0) - 170) * S)):
        K.txt(im, line, x0 + 112, y0 + 78 + i * 26, size=18, weight=500,
              color=INK_DIM, maxw=(x1 - x0) - 170)
    K.txt(im, count, x0 + 112, y0 + 152, size=17, weight=700, color=tint,
          tracking=1.4, maxw=(x1 - x0) - 170)
    K.orb(im, x1 - 46, y0 + 46, size=56, name="chevron-right", icon_color=INK_2,
          alpha=0.12)


def settings(im):
    K.screen_header(im, "Settings", "Everything in one control room",
                    kicker="CONTROL ROOM",
                    right=[{"icon": "search", "tint": WHITE},
                           {"icon": "rotate-ccw", "tint": MINT}])
    s = Stack(im, 306)
    s.section("CATEGORIES", right="5")
    counts = ["3 sections", "6 sections", "4 settings", "8 settings", "5 flags"]
    boxes = [(X0, 372, X0 + 490, 622), (X0 + 510, 372, X1, 622),
             (X0, 638, X0 + 490, 888), (X0 + 510, 638, X1, 888),
             (X0, 904, X1, 1154)]
    for (title, sub, ico, tint), count, box in zip(_CATEGORIES, counts, boxes):
        _category_card(im, box, title, sub, ico, tint, count)
    s.at(1180)

    s.section("GENERAL", right="restart may apply")
    s.row(h=90, name="languages", title="Force language to English",
          sub="See original strings, as intended by developers",
          control="toggle_off", tint=CYAN).gap(12)
    s.row(h=90, name="bell", title="Ask for notification permission",
          sub="Needed for download progress notifications", control="chevron",
          tint=AMBER).gap(12)
    s.row(h=90, name="info", title="About Aerix", sub="Version 3.0 · Liquid Crystal",
          control="chevron", tint=MINT).gap(12)
    s.row(h=90, name="scroll-text", title="Open source licenses",
          sub="Mesa, GL4ES, ANGLE, LWJGL and more", control="chevron",
          tint=VIOLET).gap(26)

    s.section("SUPPORT", right="community")
    s.row(h=90, name="book-open", title="Wiki and news", control="chevron",
          tint=AZURE).gap(12)
    s.row(h=90, name="users", title="Community and socials", control="chevron",
          tint=ROSE).gap(26)
    s.note(h=100, text="Changing the launcher language requires a full restart.",
           icon_name="refresh-cw", tint=AMBER)
    K.dock(im, 4)


# ---------------------------------------------------------------------------
# 06 - VIDEO
# ---------------------------------------------------------------------------
_RENDERERS = [
    ("GL4ES", "OpenGL ES 2 · 1.21.4-", 1),
    ("Krypton Wrapper", "OpenGL ES 3.2 · 26.2-", 0),
    ("Zink", "Vulkan · all versions", 0),
    ("LTW", "OpenGL ES 3 · 1.17+", 0),
    ("MobileGlues", "OpenGL ES · 1.17+", 1),
    ("SFPEW / MobileGlues", "OpenGL ES · all", 0),
    ("Freedreno (KGSL)", "all versions", 0),
    ("Mesa (DRM)", "all versions", 0),
    ("Mesa (DRM, external)", "all versions", 0),
    ("Legacy Zink", "Vulkan · all versions", 0),
]


def _renderer_tile(im, box, name, note, active):
    x0, y0, x1, y1 = box
    K.glass(im, box, radius=26, alpha=0.18 if active else 0.08,
            tint=MINT if active else WHITE, blur=22,
            border=0.6 if active else 0.24, sheen=0.14, foot=0.05,
            glowc=MINT if active else None, glow_a=0.24, glow_r=18)
    dx = x0 + 36
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    if active:
        d.ellipse([(dx - 15) * S, ((y0 + y1) / 2 - 15) * S,
                   (dx + 15) * S, ((y0 + y1) / 2 + 15) * S], fill=(124, 255, 203, 255))
        d.ellipse([(dx - 6) * S, ((y0 + y1) / 2 - 6) * S,
                   (dx + 6) * S, ((y0 + y1) / 2 + 6) * S], fill=(8, 22, 30, 255))
    else:
        d.ellipse([(dx - 14) * S, ((y0 + y1) / 2 - 14) * S,
                   (dx + 14) * S, ((y0 + y1) / 2 + 14) * S],
                  outline=(150, 180, 205, 190), width=int(2.4 * S))
    im.alpha_composite(layer)
    K.txt(im, name, dx + 34, (y0 + y1) / 2 - 16, size=21, weight=750,
          color=INK if active else INK_2, maxw=(x1 - x0) - 110)
    K.txt(im, note, dx + 34, (y0 + y1) / 2 + 14, size=16.5, weight=500,
          color=INK_FAINT, maxw=(x1 - x0) - 110)


def video(im):
    K.screen_header(im, "Video and renderer", "MobileGlues · 100% resolution",
                    kicker="PERFORMANCE",
                    right=[{"icon": "gauge", "tint": ROSE, "glow": ROSE},
                           {"icon": "rotate-ccw", "tint": WHITE}])
    s = Stack(im, 300)
    s.section("RENDERER", right="10 available")
    grid_y = s.y
    w = (X1 - X0 - 12) / 2
    for i, (name, note, active) in enumerate(_RENDERERS):
        r, c = divmod(i, 2)
        y = grid_y + r * 92
        bx = X0 + c * (w + 12)
        _renderer_tile(im, (bx, y, bx + w, y + 80), name, note, active)
    s.at(grid_y + 5 * 92 + 18)

    s.section("PIPELINE", right="per instance")
    s.row(h=88, name="sliders-horizontal", title="Renderer settings",
          value="OpenGL ES 3.2 · MSAA x2", control="value", tint=VIOLET,
          title_size=21).gap(12)
    s.slider(h=100, name="image", title="Resolution scaler", value=1.0,
             value_text="100% · 2400 × 1080", tint=CYAN).gap(26)

    s.section("DISPLAY AND POWER", right="battery aware")
    s.tiles([
        {"name": "smartphone", "title": "Ignore notch", "control": "toggle_on",
         "tint": MINT},
        {"name": "thermometer", "title": "Sustained performance",
         "control": "toggle_off", "tint": AMBER},
    ], h=94).gap(12)
    s.tiles([
        {"name": "frame", "title": "Alternate surface", "control": "toggle_off",
         "tint": AZURE},
        {"name": "activity", "title": "Force VSync", "control": "toggle_on",
         "tint": CYAN},
    ], h=94).gap(26)

    s.section("DRIVERS", right="advanced")
    s.row(h=88, name="cpu", title="Use ANGLE", sub="ANGLE instead of the system driver",
          control="toggle_on", tint=CYAN, title_size=21).gap(12)
    s.row(h=88, name="server", title="System ANGLE",
          sub="Use the ANGLE library shipped with the system",
          control="toggle_off", tint=AZURE, title_size=21).gap(12)
    s.row(h=88, name="layers", title="Zink prefer system driver",
          sub="Prefer the system Vulkan driver over Turnip",
          control="toggle_off", tint=VIOLET, title_size=21).gap(24)
    s.note(h=96, text="Renderer changes apply the next time the game starts.",
           icon_name="monitor-play", tint=ROSE)
    K.dock(im, 5)


# ---------------------------------------------------------------------------
# 07 - INPUT
# ---------------------------------------------------------------------------
def input_screen(im):
    K.screen_header(im, "Input and controls", "Gestures, mouse, gyro and gamepad",
                    kicker="TOUCH",
                    right=[{"icon": "gamepad", "tint": MINT, "glow": MINT},
                           {"icon": "rotate-ccw", "tint": WHITE}])
    s = Stack(im, 300)
    s.section("GESTURES", right="touch")
    s.tiles([
        {"name": "hand", "title": "Disable gestures", "control": "toggle_off",
         "tint": AZURE},
        {"name": "mouse-pointer-click", "title": "Disable double tap",
         "control": "toggle_off", "tint": CYAN},
    ], h=88).gap(10)
    s.slider(h=86, name="timer", title="Trigger long press delay", value=0.42,
             value_text="270 ms", tint=CYAN).gap(20)

    s.section("BUTTONS", right="on screen")
    s.slider(h=86, name="scaling", title="Control buttons scaling", value=0.5,
             value_text="100%", tint=AZURE).gap(10)
    s.slider(h=86, name="droplet", title="Button transparency", value=1.0,
             value_text="100%", tint=VIOLET).gap(10)
    s.tiles([
        {"name": "type", "title": "Uppercase button labels",
         "control": "toggle_off", "tint": AMBER},
    ], h=88, cols=1).gap(20)

    s.section("VIRTUAL MOUSE", right="in game")
    s.slider(h=86, name="mouse", title="Mouse scaling", value=0.5,
             value_text="100% · 24 dp", tint=MINT).gap(10)
    s.slider(h=86, name="gauge", title="Mouse speed", value=0.6,
             value_text="100% · 1.0x", tint=CYAN).gap(10)
    s.tiles([
        {"name": "mouse-pointer-2", "title": "Start with virtual mouse on",
         "control": "toggle_on", "tint": MINT},
    ], h=88, cols=1).gap(20)

    s.section("GYROSCOPE", right="sensor")
    s.tiles([
        {"name": "compass", "title": "Enable gyroscope", "control": "toggle_on",
         "tint": CYAN},
        {"name": "waves", "title": "Gyroscope smoothing", "control": "toggle_off",
         "tint": AZURE},
    ], h=88).gap(10)
    s.tiles([
        {"name": "move-horizontal", "title": "Invert X axis", "control": "toggle_off",
         "tint": VIOLET},
        {"name": "move-vertical", "title": "Invert Y axis", "control": "toggle_off",
         "tint": VIOLET},
    ], h=88).gap(10)
    s.slider(h=86, name="crosshair", title="Gyro sensitivity", value=0.62,
             value_text="100%", tint=CYAN).gap(10)
    s.slider(h=86, name="activity", title="Gyro sample rate", value=0.4,
             value_text="100 Hz", tint=MINT).gap(20)

    s.section("CONTROLLER", right="physical")
    s.row(h=84, name="gamepad", title="Remap controller buttons", control="chevron",
          tint=AZURE, title_size=21).gap(10)
    s.row(h=84, name="trash-2", title="Wipe controller mappings",
          control="chevron", tint=ROSE, title_size=21).gap(10)
    s.slider(h=86, name="circle-dot", title="Controller deadzone", value=0.3,
             value_text="30%", tint=VIOLET).gap(20)

    s.section("KEYBOARD", right="hardware")
    s.tiles([
        {"name": "keyboard", "title": "Keyboard auto-panning",
         "control": "toggle_on", "tint": MINT},
    ], h=88, cols=1)
    K.dock(im, 6)


# ---------------------------------------------------------------------------
# 08 - JAVA
# ---------------------------------------------------------------------------
def _runtime_tile(im, box, name, note, active, tint):
    x0, y0, x1, y1 = box
    K.glass(im, box, radius=34, alpha=0.18 if active else 0.09,
            tint=tint if active else WHITE, blur=26,
            border=0.60 if active else 0.28, sheen=0.14, foot=0.08,
            glowc=tint if active else None, glow_a=0.22, glow_r=22)
    K.icon_orb(im, x0 + 62, (y0 + y1) / 2, size=76, name="coffee", tint=tint,
               strong=active)
    K.txt(im, name, x0 + 116, y0 + 34, size=26, weight=750, color=INK,
          maxw=(x1 - x0) - 200)
    K.txt(im, note, x0 + 118, y0 + 74, size=18.5, weight=500, color=INK_DIM,
          maxw=(x1 - x0) - 200)
    if active:
        K.chip(im, x1 - 92, y0 + 56, "DEFAULT", tint=tint, size=15.5, padx=14)
    else:
        K.orb(im, x1 - 62, (y0 + y1) / 2, size=56, name="download",
              icon_color=INK_2, alpha=0.12)


def java(im):
    K.screen_header(im, "Java tweaks", "JRE 21 · 4096 MB · sandbox on",
                    kicker="RUNTIME",
                    right=[{"icon": "plus", "tint": AMBER, "glow": AMBER},
                           {"icon": "search", "tint": WHITE}])
    s = Stack(im, 300)
    s.section("RUNTIMES", right="4 installed")
    box_w = (X1 - X0 - 12) / 2
    runtimes = [("JRE 8", "Legacy · 1.16.5 and lower", False, CYAN),
                ("JRE 11", "Older mod loaders and 1.17", False, AZURE),
                ("JRE 17", "Modern versions · 1.18 - 1.20.4", False, MINT),
                ("JRE 21", "Default · 1.20.5 and newer", True, AMBER)]
    gy = s.y
    for i, (name, note, active, tint) in enumerate(runtimes):
        r, c = divmod(i, 2)
        y = gy + r * 144
        bx = X0 + c * (box_w + 12)
        _runtime_tile(im, (bx, y, bx + box_w, y + 132), name, note, active, tint)
    s.at(gy + 2 * 144 + 6)
    s.row(h=88, name="download", title="Install a Java runtime",
          sub="Pick a version and download it from the runtime repository",
          control="chevron", tint=AMBER, title_size=21).gap(26)

    s.section("MEMORY", right="auto detected")
    s.slider(h=100, name="memory-stick", title="Memory allocation", value=0.62,
             value_text="4096 MB · 52% of device memory", tint=CYAN).gap(14)
    s.note(h=96, text="The launcher keeps 1.2 GB free for Android. Values above the "
                      "limit are clamped at launch.",
           icon_name="gauge", tint=CYAN).gap(26)

    s.section("ARGUMENTS", right="advanced")
    s.row(h=88, name="terminal", title="JVM launch arguments",
          value="custom", control="value", tint=VIOLET, title_size=21).gap(12)
    s.card(96, lambda b, _i=None: field(im, (b[0] + 6, b[1] + 6, b[2] - 6, b[3] - 6),
                                        "Arguments",
                                        "-Xmx4096M -XX:+UseG1GC -XX:+ParallelRefProcEnabled",
                                        icon_name="terminal", tint=VIOLET)).gap(26)

    s.section("SECURITY", right="sandbox")
    s.row(h=88, name="shield-check", title="Sandbox .jar execution",
          sub="Controls the sandbox security manager for .jar files",
          control="toggle_on", tint=MINT, title_size=21).gap(14)
    s.note(h=96, text="Sandboxing stops executed .jar files from touching files "
                      "outside of their own folder.",
           icon_name="lock", tint=MINT).gap(26)

    s.section("RUNTIME INFO", right="device")
    s.row(h=88, name="coffee", title="Default runtime", value="JRE 21.0.3",
          control="value", tint=AMBER, title_size=21).gap(12)
    s.row(h=88, name="folder-open", title="Runtime path",
          value="/data/data/app/runtimes/jre21", control="value", tint=CYAN,
          title_size=21).gap(12)
    s.row(h=88, name="cpu", title="Available memory", value="7.8 GB free",
          control="text", tint=AZURE, title_size=21)
    K.dock(im, 7)


# ---------------------------------------------------------------------------
# 09 - MISC
# ---------------------------------------------------------------------------
def misc(im):
    K.screen_header(im, "Miscellaneous", "Integrity, downloads and storage",
                    kicker="MAINTENANCE",
                    right=[{"icon": "refresh-cw", "tint": CYAN, "glow": CYAN},
                           {"icon": "search", "tint": WHITE}])
    s = Stack(im, 300)
    s.section("INTEGRITY", right="launch time")
    s.row(h=90, name="shield-check", title="Verify game files",
          sub="Re-download broken or modified game libraries",
          control="chevron", tint=MINT).gap(12)
    s.row(h=90, name="zap", title="Fast startup check",
          sub="Skip the full integrity check when starting",
          control="toggle_on", tint=CYAN).gap(12)
    s.row(h=90, name="file-check", title="Verify manifest",
          sub="Check the version manifest hash before downloading",
          control="toggle_on", tint=AZURE).gap(26)

    s.section("DOWNLOADS", right="sources")
    s.row(h=90, name="cloud-download", title="Download source",
          value="Default mirror", control="value", tint=VIOLET).gap(26)

    s.section("PERMISSIONS", right="android")
    s.row(h=90, name="mic", title="Microphone access",
          sub="Allow in-game voice chat to use the microphone",
          control="chevron", tint=ROSE).gap(26)

    s.section("DATA", right="migration")
    s.row(h=90, name="database", title="Run data migration",
          sub="Move the launcher data to the current storage layout",
          control="chevron", tint=AMBER).gap(12)
    s.row(h=90, name="trash-2", title="Clear metadata cache",
          sub="Remove cached mod and version metadata",
          control="chevron", tint=ROSE).gap(12)
    s.row(h=90, name="memory-stick", title="Show memory warning",
          sub="Warn when the allocation is too high for this device",
          control="toggle_on", tint=CYAN).gap(26)

    s.section("FILES", right="external")
    s.row(h=90, name="folder-open", title="Open game directory",
          control="chevron", tint=MINT).gap(12)
    s.row(h=90, name="share-2", title="Share log file", control="chevron",
          tint=CYAN).gap(12)
    s.row(h=90, name="package", title="Execute a .jar", control="chevron",
          tint=AMBER).gap(26)

    s.section("STORAGE", right="30.4 GB used")
    s.card(210, lambda b, _i=None: _storage(im, b))
    K.dock(im, 8)


def _storage(im, b):
    x0, y0, x1, y1 = b
    K.glass(im, b, radius=38, alpha=0.10, blur=30, border=0.32, sheen=0.13, foot=0.10)
    items = [("Instances", "24.8 GB", 0.82, MINT), ("Runtimes", "3.6 GB", 0.34, AMBER),
             ("Cache", "2.0 GB", 0.20, VIOLET)]
    y = y0 + 44
    for label, size, value, tint in items:
        K.icon_orb(im, x0 + 56, y, size=52, name="hard-drive", tint=tint)
        K.txt(im, label, x0 + 96, y - 16, size=21, weight=650, color=INK, maxw=260)
        K.txt(im, size, x1 - 60, y - 16, size=19, weight=700, color=tint,
              halign="right", maxw=200)
        progress(im, x0 + 96, y + 22, (x1 - x0) - 200, value, tint)
        y += 66


# ---------------------------------------------------------------------------
# 10 - LABS
# ---------------------------------------------------------------------------
def labs(im):
    K.screen_header(im, "Experimental", "No support is provided for anything here",
                    kicker="LABS",
                    right=[{"icon": "flask-conical", "tint": VIOLET, "glow": VIOLET},
                           {"icon": "rotate-ccw", "tint": WHITE}])
    s = Stack(im, 300)
    s.note(h=104, text="Experimental options can break rendering, audio or saves. "
                       "Turn everything off before reporting a bug.",
           icon_name="triangle-alert", tint=AMBER).gap(26)

    s.section("GRAPHICS", right="3 flags")
    s.row(h=90, name="file-code", title="Dump shaders",
          sub="Write every shader the driver compiles to the log folder",
          control="toggle_off", tint=CYAN).gap(12)
    s.row(h=90, name="cpu", title="Force big core affinity",
          sub="Pin the game threads to the fastest cores",
          control="toggle_off", tint=AMBER).gap(12)
    s.row(h=90, name="memory-stick", title="Freedreno system memory",
          sub="Force the freedreno driver to allocate in system memory",
          control="toggle_off", tint=VIOLET).gap(26)

    s.section("AUDIO AND BUFFERS", right="2 flags")
    s.row(h=90, name="audio-lines", title="Force OpenSL in OpenAL Soft",
          sub="Use the OpenSL ES backend instead of AAudio",
          control="toggle_off", tint=MINT).gap(12)
    s.row(h=90, name="layers", title="UBWC workaround",
          sub="Work around broken compressed framebuffer formats",
          control="toggle_off", tint=ROSE).gap(26)

    s.section("LOGGING", right="debug")
    s.row(h=90, name="scroll-text", title="Live log view",
          sub="Show the game log overlay while playing", control="chevron",
          tint=CYAN).gap(12)
    s.row(h=90, name="share-2", title="Share log file", control="chevron",
          tint=AZURE).gap(12)
    s.row(h=90, name="bug", title="Verbose JNI logging",
          sub="Log every native call, slows the game down a lot",
          control="toggle_off", tint=ROSE).gap(26)

    s.section("DANGER ZONE", right="reset")
    s.card(148, lambda b, _i=None: _danger(im, b)).gap(26)

    s.section("ACTIVE EXPERIMENTS", right="session")
    s.card(200, lambda b, _i=None: _active_experiments(im, b))
    K.dock(im, 9)


def _danger(im, b):
    x0, y0, x1, y1 = b
    K.glass(im, b, radius=36, alpha=0.10, tint=ROSE, blur=28, border=0.40,
            sheen=0.12, foot=0.08, glowc=ROSE, glow_a=0.16, glow_r=26)
    K.icon_orb(im, x0 + 58, y0 + 62, size=60, name="triangle-alert", tint=ROSE)
    K.txt(im, "Reset experimental options", x0 + 100, y0 + 30, size=23, weight=750,
          color=INK, maxw=(x1 - x0) - 200)
    K.txt(im, "Every flag on this page returns to its default value", x0 + 102,
          y0 + 66, size=18, weight=500, color=INK_DIM, maxw=(x1 - x0) - 200)
    K.action_button(im, (x0 + 34, y1 - 78, x1 - 34, y1 - 22), "RESET ALL FLAGS",
                    accent=False, icon_name="rotate-ccw", size=21, text_color=ROSE)


def _active_experiments(im, b):
    x0, y0, x1, y1 = b
    K.glass(im, b, radius=36, alpha=0.09, blur=28, border=0.30, sheen=0.12, foot=0.08)
    K.txt(im, "SESSION STATE", x0 + 34, y0 + 30, size=16, weight=800,
          color=INK_FAINT, tracking=2.4, maxw=400)
    chips_row(im, x0 + 34, y0 + 84, [("ANGLE", CYAN), ("Force VSync", MINT),
                                     ("Big-core", AMBER)], gap=12)
    chips_row(im, x0 + 34, y0 + 142, [("Shaders dumped", VIOLET),
                                      ("Verbose off", INK_FAINT)], gap=12)


SCREENS = [
    ("01-launch", launch, 0.00),
    ("02-instances", instances, 0.11),
    ("03-mods", mods, 0.22),
    ("04-controls", controls, 0.33),
    ("05-settings", settings, 0.44),
    ("06-video", video, 0.55),
    ("07-input", input_screen, 0.66),
    ("08-java", java, 0.77),
    ("09-misc", misc, 0.88),
    ("10-labs", labs, 0.99),
]
