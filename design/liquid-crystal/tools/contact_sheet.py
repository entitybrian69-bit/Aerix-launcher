"""Stitch the ten mockups into one labelled overview sheet.

Usage: python3 tools/contact_sheet.py <mockup dir> <output> [tile width]
"""

import glob
import os
import sys

from PIL import Image, ImageDraw, ImageFont

SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/mockups")
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(SRC, "00-overview.png")

COLS, ROWS = 5, 2
TW = int(sys.argv[3]) if len(sys.argv) > 3 else 520
CAP, GAP, PAD, HEAD = 60, 18, 36, 104
TH = int(TW * 2400 / 1080)

FONT_DIR = os.environ.get("AERIX_FONT_DIR", os.path.expanduser("~/.fonts_build"))
BOLD = f"{FONT_DIR}/Inter-Bold.ttf"
REG = f"{FONT_DIR}/Inter-Regular.ttf"
f_head = ImageFont.truetype(BOLD, 44) if os.path.exists(BOLD) else ImageFont.load_default()
f_sub = ImageFont.truetype(REG, 24) if os.path.exists(REG) else ImageFont.load_default()
f_num = ImageFont.truetype(BOLD, 30) if os.path.exists(BOLD) else ImageFont.load_default()
f_lab = ImageFont.truetype(BOLD, 26) if os.path.exists(BOLD) else ImageFont.load_default()

NAMES = ["Launch", "Instances", "Mods & packs", "Control studio", "Settings",
         "Video", "Input", "Java", "Misc", "Labs"]

files = sorted(f for f in glob.glob(os.path.join(SRC, "[01]*.png"))
               if not os.path.basename(f).startswith("00-"))
if not files:
    raise SystemExit("no mockups found in " + SRC)

W = PAD * 2 + COLS * TW + GAP * (COLS - 1)
H = HEAD + PAD + ROWS * (TH + CAP) + GAP * (ROWS - 1) + PAD
sheet = Image.new("RGB", (W, H), (6, 10, 18))
d = ImageDraw.Draw(sheet)

# aurora header wash
for cx, cy, col, r in [(0.2, 0.2, (110, 240, 255), 0.5), (0.8, 0.5, (167, 139, 250), 0.45),
                       (0.5, 0.9, (124, 255, 203), 0.4)]:
    blob = Image.new("RGB", (W, H), (0, 0, 0))
    bd = ImageDraw.Draw(blob)
    bd.ellipse([W * cx - W * r, H * cy - H * r * 0.6, W * cx + W * r, H * cy + H * r * 0.6],
               fill=(int(col[0] * 0.16), int(col[1] * 0.16), int(col[2] * 0.16)))
    blob = blob.filter(__import__("PIL.ImageFilter", fromlist=["x"]).GaussianBlur(180))
    sheet.paste(Image.blend(sheet, Image.blend(sheet, blob, 0.9), 1.0))
    d = ImageDraw.Draw(sheet)

title = "AERIX  ·  LIQUID CRYSTAL"
d.text((PAD + 4, 46), title, font=f_head, fill=(238, 247, 255))
tw = d.textlength(title, font=f_head)
d.text((PAD + 14 + tw, 62), "ten tabs  ·  every launcher feature", font=f_sub,
       fill=(146, 174, 198))
d.line([(PAD + 4, HEAD - 12), (W - PAD - 4, HEAD - 12)], fill=(255, 255, 255, 40), width=2)

for i, f in enumerate(files[:COLS * ROWS]):
    r, c = divmod(i, COLS)
    x = PAD + c * (TW + GAP)
    y = HEAD + PAD + r * (TH + CAP + GAP)
    im = Image.open(f).convert("RGB").resize((TW, TH), Image.LANCZOS)
    sheet.paste(im, (x, y))
    d.rounded_rectangle([x - 1, y - 1, x + TW, y + TH], radius=10,
                        outline=(255, 255, 255, 46), width=2)
    label = f"{i + 1:02d}"
    d.text((x, y + TH + 18), label, font=f_num, fill=(110, 240, 255))
    d.text((x + 52, y + TH + 21), NAMES[i] if i < len(NAMES) else "", font=f_lab,
           fill=(222, 238, 250))

sheet.save(OUT, "PNG", optimize=True)
print("wrote", OUT, f"{W}x{H}", f"{os.path.getsize(OUT)/1e6:.2f} MB",
      "from", len(files), "screens")
