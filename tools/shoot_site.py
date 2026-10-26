#!/usr/bin/env python3
"""Render the static site in headless Chromium and save screenshots.

Run by .github/workflows/preview-shots.yml. Needs `playwright` + `pillow` and a
server on http://127.0.0.1:8123 serving docs/.
"""

import pathlib
import sys

from PIL import Image
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:8123"
OUT = pathlib.Path("site-preview")
PAGES = ["/", "/features.html", "/renderers.html", "/screenshots.html", "/install.html", "/faq.html", "/404.html"]

DESKTOP = {"width": 1440, "height": 1000}
PHONE = {"width": 390, "height": 844}


def shot(page, url, path, viewport, full=True, scroll=0):
    page.set_viewport_size(viewport)
    page.goto(BASE + url, wait_until="networkidle")
    page.wait_for_timeout(900)  # let reveals + release fetch settle
    if scroll:
        page.evaluate(f"window.scrollTo(0, {scroll})")
        page.wait_for_timeout(700)
    page.screenshot(path=str(path), full_page=full and not scroll)
    with Image.open(path) as im:
        print(f"{path.name:28} {im.width}x{im.height}")


def downscale(path, width=1000):
    if not path.exists():
        return
    with Image.open(path) as im:
        if im.width > width:
            h = round(im.height * width / im.width)
            im.resize((width, h), Image.LANCZOS).save(path, optimize=True)


def main():
    OUT.mkdir(exist_ok=True)
    with sync_playwright() as p:
        browser = p.chromium.launch(args=["--force-color-profile=srgb", "--font-render-hinting=none"])
        page = browser.new_page(device_scale_factor=1)

        for url in PAGES:
            name = "home" if url == "/" else url.strip("/").replace(".html", "")
            shot(page, url, OUT / f"{name}.png", DESKTOP)

        # section-level crops of the home page, in reading order
        for label, y in [("hero", 0), ("features", 1250), ("clips", 2600), ("download", 3900)]:
            shot(page, "/", OUT / f"home-{label}.png", DESKTOP, full=False, scroll=y)

        shot(page, "/", OUT / "home-phone.png", PHONE)
        shot(page, "/features.html", OUT / "features-phone.png", PHONE)
        browser.close()

    for f in OUT.glob("home*.png"):
        downscale(f)
    for f in OUT.glob("features*.png"):
        downscale(f)


if __name__ == "__main__":
    sys.exit(main())
