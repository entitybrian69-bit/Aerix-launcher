import hashlib
import io
from fastapi.testclient import TestClient
from PIL import Image
from server import create_app

UUID = "12345678-1234-1234-1234-123456789012"
OTHER = "22345678-1234-1234-1234-123456789012"
KEY = "a-unique-test-key-never-use-in-production"


def client(tmp_path):
    return TestClient(create_app(tmp_path, {UUID: hashlib.sha256(KEY.encode()).hexdigest()}))


def png(size=(64, 32)):
    output = io.BytesIO()
    Image.new("RGBA", size, (50, 100, 200, 255)).save(output, "PNG")
    return output.getvalue()


def headers():
    return {"Authorization": "Bearer " + KEY, "Content-Type": "image/png"}


def test_upload_and_public_download(tmp_path):
    c = client(tmp_path)
    assert c.put("/v1/capes/" + UUID, content=png(), headers=headers()).status_code == 204
    result = c.get("/v1/capes/" + UUID)
    assert result.status_code == 200
    assert Image.open(io.BytesIO(result.content)).size == (64, 32)


def test_key_cannot_modify_another_profile(tmp_path):
    assert client(tmp_path).put("/v1/capes/" + OTHER, content=png(), headers=headers()).status_code == 403


def test_no_anonymous_upload(tmp_path):
    assert client(tmp_path).put("/v1/capes/" + UUID, content=png()).status_code == 403


def test_wrong_dimensions(tmp_path):
    assert client(tmp_path).put("/v1/capes/" + UUID, content=png((128, 64)), headers=headers()).status_code == 422


def test_corrupt_png(tmp_path):
    assert client(tmp_path).put("/v1/capes/" + UUID, content=b"invalid", headers=headers()).status_code == 422


def test_oversized_upload(tmp_path):
    assert client(tmp_path).put("/v1/capes/" + UUID, content=b"x" * (1024 * 1024 + 1), headers=headers()).status_code == 413


def test_delete_is_authenticated(tmp_path):
    c = client(tmp_path)
    c.put("/v1/capes/" + UUID, content=png(), headers=headers())
    assert c.delete("/v1/capes/" + UUID).status_code == 403
    assert c.delete("/v1/capes/" + UUID, headers=headers()).status_code == 204
    assert c.get("/v1/capes/" + UUID).status_code == 404


def test_invalid_uuid(tmp_path):
    assert client(tmp_path).get("/v1/capes/invalid").status_code == 400
