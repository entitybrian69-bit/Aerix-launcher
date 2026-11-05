#!/usr/bin/env python3
"""Stage the verified native Horizon Android 0.7.7 distribution for Aerix builds.

Original APKs and their extracted binary outputs remain under Gradle's ignored build dir.
An offline mirror can be provided via AERIX_HORIZON_APK_DIR. No Minecraft assets are bundled.
"""
import hashlib
import os
from pathlib import Path
import shutil
import sys
import urllib.request
import zipfile

VERSION = '0.7.7'
CHECKSUMS = {
    'arm64-v8a': 'f4e180bb8b48f7a5af309ab16becb7c733c8c5d8282f4946f2c5ad5cbce81133',
    'armeabi-v7a': '99c504d39d589c480816c90e70b126e198c10e6ad1fca2a57cf0de2d433a15b1',
    'x86': 'ac367c62cfa3631369f45a250633c520177af44e1c3efc3cec6bed5ee708ce6b',
    'x86_64': '364140a4f55626473a7388c41ce6e9469977aedd8000d205cbe8a76d0043f9e8',
}
BASE_URL = 'https://github.com/Vasysik/horizon-android/releases/download/v0.7.7/'

def checksum(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()

def apk_for(abi, cache):
    name = f'Horizon-Android-{VERSION}-{abi}.apk'
    override = os.environ.get('AERIX_HORIZON_APK_DIR')
    path = Path(override) / name if override else cache / name
    if path.exists() and checksum(path) == CHECKSUMS[abi]:
        return path
    if override:
        raise ValueError(f'Missing or invalid Horizon APK: {path}')
    cache.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix('.partial')
    try:
        print('Downloading verified Horizon Android', abi, flush=True)
        request = urllib.request.Request(BASE_URL + name, headers={'User-Agent': 'Aerix-Launcher-Build/1.0'})
        with urllib.request.urlopen(request, timeout=120) as response, tmp.open('wb') as output:
            shutil.copyfileobj(response, output, length=1024 * 1024)
        if checksum(tmp) != CHECKSUMS[abi]:
            raise ValueError(f'Horizon APK checksum mismatch: {abi}')
        tmp.replace(path)
    finally:
        tmp.unlink(missing_ok=True)
    return path

def copy_member(archive, member, destination):
    info = archive.getinfo(member)
    if info.file_size < 100 or info.file_size > 200 * 1024 * 1024:
        raise ValueError(f'Unexpected size for {member}')
    destination.parent.mkdir(parents=True, exist_ok=True)
    temp = destination.with_suffix(destination.suffix + '.partial')
    try:
        with archive.open(info) as source, temp.open('wb') as out:
            shutil.copyfileobj(source, out, length=1024 * 1024)
        if temp.stat().st_size != info.file_size:
            raise ValueError(f'Truncated member {member}')
        temp.replace(destination)
    finally:
        temp.unlink(missing_ok=True)

def check_elf(path, abi):
    # ELF little-endian e_machine and class prevent accidentally packaging a wrong ABI.
    expected = {'arm64-v8a': (2, 183), 'armeabi-v7a': (1, 40), 'x86': (1, 3), 'x86_64': (2, 62)}
    with path.open('rb') as f:
        header = f.read(20)
    if len(header) < 20 or header[:4] != b'\x7fELF' or header[5] != 1 or (header[4], int.from_bytes(header[18:20], 'little')) != expected[abi]:
        raise ValueError(f'Unexpected native library ABI: {abi}')

def stage(output):
    cache = output / 'cache'
    for abi in CHECKSUMS:
        apk = apk_for(abi, cache)
        with zipfile.ZipFile(apk) as archive:
            library = output / 'jniLibs' / abi / 'libhorizon_android.so'
            copy_member(archive, f'lib/{abi}/libhorizon_android.so', library)
            check_elf(library, abi)
            if abi == 'arm64-v8a':
                data = output / 'assets' / 'engine-data.zip'
                copy_member(archive, 'assets/engine-data.zip', data)
                with zipfile.ZipFile(data) as engine:
                    if 'horizon-resources.json' not in engine.namelist():
                        raise ValueError('Generated Horizon engine tables are missing')
    print('Verified Horizon Android native engine and generated tables staged.', flush=True)

if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: prepare_horizon.py OUTPUT_DIRECTORY')
    stage(Path(sys.argv[1]))
