#!/usr/bin/env python3
"""Render the static site in headless Chromium and save screenshots.

Run by .github/workflows/preview-shots.yml. Needs `playwright` + `pillow` and a
server on http://127.0.0.1:8123 serving docs/.

The pages reveal sections with IntersectionObserver, so the whole page is
scrolled once before capturing — otherwise off-screen sections stay at opacity 0.
"""

import pathlib
import sys

from PIL import Image
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:8123"
OUT = pathlib.Path("site-preview")
PAGES = ["/features.html", "/renderers.html", "/screenshots.html", "/install.html", "/faq.html", "/404.html"]

DESKTOP = {"width": 1440, "height": 960}
PHONE = {"width": 390, "height": 844}

PRIME = """
async () => {
  const step = Math.round(window.innerHeight * 0.75);
  const end = document.body.scrollHeight;
  for (let y = 0; y < end; y += step) {
    window.scrollTo(0, y);
    await new Promise(r => setTimeout(r, 110));
  }
  window.scrollTo(0, 0);
  await new Promise(r => setTimeout(r, 700));
}
"""


def capture(page, url, path, viewport, *, full=True, bands=0, label=""):
    page.set_viewport_size(viewport)
    page.goto(BASE + url, wait_until="networkidle")
    page.evaluate(PRIME)
    page.wait_for_timeout(400)
    page.screenshot(path=str(path), full_page=full)
    with Image.open(path) as im:
        print(f"{path.name:26} {im.width}x{im.height}")

    if bands:
        with Image.open(path) as im:
            for i, top in enumerate(range(0, im.height, bands), start=1):
                out = OUT / f"{label}-{i:02d}.png"
                im.crop((0, top, im.width, min(top + bands, im.height))).save(out, optimize=True)


def main():
    OUT.mkdir(exist_ok=True)
    with sync_playwright() as p:
        browser = p.chromium.launch(args=["--force-color-profile=srgb"])
        page = browser.new_page(device_scale_factor=1)

        capture(page, "/", OUT / "home.png", DESKTOP, bands=1150, label="home")

        for url in PAGES:
            name = url.strip("/").replace(".html", "")
            capture(page, url, OUT / f"{name}.png", DESKTOP)

        capture(page, "/", OUT / "home-phone.png", PHONE, full=False)
        capture(page, "/install.html", OUT / "install-phone.png", PHONE, full=False)
        browser.close()

    for f in OUT.glob("*.png"):
        with Image.open(f) as im:
            if im.width > 1000:
                im.resize((1000, round(im.height * 1000 / im.width)), Image.LANCZOS).save(f, optimize=True)


if __name__ == "__main__":
    sys.exit(main())
