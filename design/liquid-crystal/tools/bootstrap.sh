#!/usr/bin/env bash
# One-shot setup for the mockup renderer: virtualenv, fonts, icon font.
# Everything is fetched from pypi.org / registry.npmjs.org.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
FONT_DIR="${AERIX_FONT_DIR:-$HOME/.fonts_build}"
VENV="${AERIX_VENV:-$HOME/.venv}"
WORK="$(mktemp -d)"

mkdir -p "$FONT_DIR"
python3 -m venv "$VENV"
"$VENV/bin/pip" install --quiet Pillow numpy fonttools brotli

echo "== fetching fonts =="
( cd "$WORK" && mkdir -p inter lucide
  npm pack @fontsource-variable/inter >/dev/null 2>&1
  tar xzf fontsource-variable-inter-*.tgz -C inter
  npm pack lucide-static >/dev/null 2>&1
  tar xzf lucide-static-*.tgz -C lucide )

cp "$WORK"/lucide/package/font/lucide.ttf "$FONT_DIR/"
cp "$WORK"/lucide/package/font/codepoints.json "$FONT_DIR/lucide_codepoints.json"

"$VENV/bin/python" - "$WORK" "$FONT_DIR" <<'PY'
import sys
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

work, out = sys.argv[1], sys.argv[2]
src = f"{work}/inter/package/files/inter-latin-wght-normal.woff2"
try:
    f = TTFont(src)
except Exception:
    import glob
    src = glob.glob(f"{work}/inter/package/files/inter-latin-wght-normal.woff2")[0]
    f = TTFont(src)
for w, name in [(400, 'Regular'), (500, 'Medium'), (600, 'SemiBold'),
                (700, 'Bold'), (800, 'ExtraBold')]:
    inst = instancer.instantiateVariableFont(f, {'wght': w}, inplace=False)
    inst.flavor = None
    inst.save(f"{out}/Inter-{name}.ttf")
print("Inter static instances written to", out)
PY

echo "== rendering =="
"$VENV/bin/python" "$ROOT/design/liquid-crystal/build.py" "${1:-$HOME/mockups}"
"$VENV/bin/python" "$ROOT/design/liquid-crystal/tools/contact_sheet.py" "${1:-$HOME/mockups}"
echo "done"
