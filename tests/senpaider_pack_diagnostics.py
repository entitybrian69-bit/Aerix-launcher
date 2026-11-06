#!/usr/bin/env python3
"""Inspect published resource pack files without bundling or modifying them."""
import hashlib
import json
import struct
import time
import urllib.request
import zipfile
from io import BytesIO

PACKS = [
    ('animated', 'https://cdn.modrinth.com/data/QdBlb8o2/versions/sSFc5jXV/%21%20%20%20%20%20%20%C2%A7f%C2%A7lSenpaiSpider%201M.zip',
     '8f165ce7b2867e5ad665be374df4a238017d74c492d5567f6721f9bf1dc165635a54b4e4d7753d8ad9c98fb771cebb632b7bb62aa357100f03d967b1107cebc4'),
    ('not animated', 'https://cdn.modrinth.com/data/QdBlb8o2/versions/AT22R4ES/%C2%A7f%C2%A7lSenpaiSpider%20Not%20Animated%20%281%29.zip',
     'ab2a538330ecad7b76b6ce0a31d3f274ac010636cbfd74f0a52799761c947ca5ba9a178f2abf2208a74a7ec5b41b00d01697d5fa48351a77120b0f42c8dd744f'),
]


def inspect(payload):
    dimensions = []
    metadata = []
    invalid = []
    with zipfile.ZipFile(BytesIO(payload)) as archive:
        infos = archive.infolist()
        files = {info.filename: info for info in infos}
        for entry in infos:
            if entry.filename.lower().endswith('.png'):
                with archive.open(entry) as image:
                    header = image.read(24)
                if len(header) < 24 or header[:8] != b'\x89PNG\r\n\x1a\n' or header[12:16] != b'IHDR':
                    invalid.append(entry.filename)
                    continue
                w, h = struct.unpack('>II', header[16:24])
                dimensions.append((w*h, w, h, entry.filename))
            if entry.filename.endswith('.png.mcmeta'):
                if entry.file_size > 200000:
                    invalid.append(entry.filename + ': oversized metadata')
                    continue
                try:
                    meta = json.loads(archive.read(entry))
                    if 'animation' in meta:
                        metadata.append(entry.filename)
                    if entry.filename[:-7] not in files:
                        invalid.append(entry.filename + ': missing PNG')
                except (ValueError, UnicodeError):
                    invalid.append(entry.filename + ': invalid metadata')
        return {
            'entries': len(infos), 'expanded_mib': round(sum(x.file_size for x in infos) / 1048576, 1),
            'pngs': len(dimensions), 'animated_pngs': len(metadata), 'invalid': invalid[:6],
            'biggest': sorted(dimensions, reverse=True)[:5],
            'large_4096': sum(w > 4096 or h > 4096 for _, w, h, _ in dimensions),
            'pack_mcmeta': archive.read('pack.mcmeta')[:1024].decode('utf-8', errors='replace') if 'pack.mcmeta' in files else 'missing at root',
        }


def main():
    for name, url, digest in PACKS:
        err = None
        for i in range(3):
            try:
                req = urllib.request.Request(url, headers={'User-Agent': 'Aerix-Launcher-ResourcePack-Diagnostics/1.0'})
                with urllib.request.urlopen(req, timeout=30) as response:
                    data = response.read(12_000_001)
                if len(data) > 12_000_000 or hashlib.sha512(data).hexdigest() != digest:
                    raise ValueError('Pack size or SHA-512 does not match publisher metadata')
                info = inspect(data)
                print('::notice title=SenpaiSpider ' + name + ' pack::' + json.dumps(info, ensure_ascii=True, separators=(',', ':')).replace('%', '%25'))
                err = None
                break
            except Exception as exc:
                err = exc
                if i < 2:
                    time.sleep(2)
        if err is not None:
            print('::warning title=SenpaiSpider diagnostic unavailable::' + name + ' could not be inspected: ' + str(err).replace('%', '%25'))


if __name__ == '__main__':
    main()
