#!/usr/bin/env python3
"""Serve the static Aerix Lobby UI preview from the repository root.

    python3 tools/serve_lobby_preview.py

The HTML lives in preview/ but references the real app assets with root-relative URLs, so the
server root is the repository. '/' is rewritten to '/preview/index.html' so the preview opens
straight away in the Arena browser preview.
"""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def do_GET(self):
        if self.path.split("?", 1)[0] in ("/", "/index.html"):
            self.path = "/preview/index.html"
        return super().do_GET()

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", 8000), Handler)
    print("Aerix Lobby preview on http://0.0.0.0:8000/", flush=True)
    server.serve_forever()
