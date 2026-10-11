"""Export lightweight JPEG copies of the mockups into the repository.

The PNG masters stay outside the repo; these JPEGs are committed so the design
is always visible in the branch and in pull requests.

Usage: python3 tools/export_preview.py <mockup dir> <output dir>
"""

import glob
import os
import sys

from PIL import Image

SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/mockups")
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "preview")
QUALITY = 82

os.makedirs(OUT, exist_ok=True)
total = 0
for path in sorted(glob.glob(os.path.join(SRC, "*.png"))):
    name = os.path.splitext(os.path.basename(path))[0] + ".jpg"
    dst = os.path.join(OUT, name)
    Image.open(path).convert("RGB").save(dst, quality=QUALITY, optimize=True,
                                         progressive=True)
    size = os.path.getsize(dst)
    total += size
    print(f"{name:20s} {size/1e6:5.2f} MB")
print(f"total {total/1e6:.2f} MB -> {OUT}")
