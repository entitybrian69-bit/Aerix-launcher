#!/usr/bin/env python3
"""Serves the Millida-style shell preview from the repository root.

Root-relative asset URLs in preview/millida.html (/app_pojavlauncher/...) resolve because the
handler is rooted at the checkout, not at preview/. The previous Blocky Shell preview stays
available at /preview/index.html. Bind 0.0.0.0 so the Arena live preview proxy can reach it.
"""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class PreviewHandler(SimpleHTTPRequestHandler):
    def do_GET(self):  # noqa: N802 - http.server API
        if self.path in ("/", "/index.html", "/millida", "/preview"):
            self.path = "/preview/millida.html"
        return super().do_GET()

    def log_message(self, fmt, *args):
        print("[millida-preview] " + fmt % args)


def main() -> None:
    handler = partial(PreviewHandler, directory=str(ROOT))
    server = ThreadingHTTPServer(("0.0.0.0", 8000), handler)
    print(f"Millida-style shell preview: http://0.0.0.0:8000/ (root {ROOT})", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
