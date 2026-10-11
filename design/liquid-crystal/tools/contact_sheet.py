"""Stitch the ten mockups into one overview sheet."""

import glob
import os
import sys

from PIL import Image

SRC = sys.argv[1] if len(sys.argv) > 1 else "/home/user/mockups"
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(SRC, "00-overview.png")

COLS, ROWS = 5, 2
TW, GAP, PAD = 420, 16, 32
TH = int(TW * 2400 / 1080)

files = sorted(glob.glob(os.path.join(SRC, "[01]*.png")))
if not files:
    raise SystemExit("no mockups found in " + SRC)

sheet = Image.new("RGB", (PAD * 2 + COLS * TW + GAP * (COLS - 1),
                          PAD * 2 + ROWS * TH + GAP * (ROWS - 1)), (7, 11, 20))
for i, f in enumerate(files[:COLS * ROWS]):
    r, c = divmod(i, COLS)
    im = Image.open(f).convert("RGB").resize((TW, TH), Image.LANCZOS)
    sheet.paste(im, (PAD + c * (TW + GAP), PAD + r * (TH + GAP)))

sheet.save(OUT, "PNG", optimize=True)
print("wrote", OUT, f"{os.path.getsize(OUT)/1e6:.2f} MB", "from", len(files), "screens")
