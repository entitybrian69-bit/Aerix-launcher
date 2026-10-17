"""Self-hosted public cape storage with UUID-scoped, administrator-issued upload keys."""
import hashlib
import hmac
import io
import json
import os
from pathlib import Path
import re
import tempfile
from uuid import UUID

from fastapi import FastAPI, HTTPException, Request, Response
from fastapi.responses import FileResponse
from PIL import Image, UnidentifiedImageError

MAX_BYTES = 1024 * 1024


def create_app(directory=None, keys=None):
    root = Path(directory or os.environ.get("AERIX_CAPE_DATA", "./cape-data"))
    root.mkdir(parents=True, exist_ok=True)
    digests = keys if keys is not None else json.loads(os.environ.get("AERIX_CAPE_KEYS", "{}"))
    app = FastAPI(title="Aerix cape service", docs_url=None, redoc_url=None)

    def profile(raw):
        try:
            return str(UUID(raw))
        except ValueError:
            raise HTTPException(400, "Invalid profile UUID")

    def authorize(uuid, header):
        token = header.removeprefix("Bearer ") if header.startswith("Bearer ") else ""
        digest = hashlib.sha256(token.encode()).hexdigest()
        expected = digests.get(uuid, "")
        if not token or not expected or not hmac.compare_digest(digest, expected):
            raise HTTPException(403, "Upload key is not authorized for this UUID")

    @app.get("/health")
    def health():
        return {"status": "ok", "protocol": 1}

    @app.get("/v1/capes/{raw}")
    def get_cape(raw: str):
        path = root / (profile(raw) + ".png")
        if not path.is_file():
            raise HTTPException(404, "No cape")
        return FileResponse(path, media_type="image/png", headers={"Cache-Control": "public, max-age=60"})

    @app.put("/v1/capes/{raw}", status_code=204)
    async def upload(raw: str, request: Request):
        uuid = profile(raw)
        authorize(uuid, request.headers.get("authorization", ""))
        if request.headers.get("content-type", "").split(";")[0] != "image/png":
            raise HTTPException(415, "Expected image/png")
        data = bytearray()
        async for chunk in request.stream():
            data.extend(chunk)
            if len(data) > MAX_BYTES:
                raise HTTPException(413, "Cape exceeds 1 MB")
        try:
            with Image.open(io.BytesIO(data)) as image:
                if image.format != "PNG" or image.size != (64, 32) or getattr(image, "n_frames", 1) != 1:
                    raise HTTPException(422, "Use a static 64 x 32 PNG")
                clean = image.convert("RGBA")
                output = io.BytesIO()
                clean.save(output, format="PNG")
        except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError):
            raise HTTPException(422, "Invalid PNG")
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(dir=root, delete=False) as stream:
                temporary = Path(stream.name)
                stream.write(output.getvalue())
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, root / (uuid + ".png"))
        finally:
            if temporary is not None:
                temporary.unlink(missing_ok=True)
        return Response(status_code=204)

    @app.delete("/v1/capes/{raw}", status_code=204)
    def delete(raw: str, request: Request):
        uuid = profile(raw)
        authorize(uuid, request.headers.get("authorization", ""))
        (root / (uuid + ".png")).unlink(missing_ok=True)
        return Response(status_code=204)

    return app


app = create_app()
