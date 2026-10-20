#!/usr/bin/env python3
"""Import only the exact user-requested prebuilt; never rebuild/patch/execute its ELF files.

This is a separately scoped experimental policy, not a relaxation of the audited source
builder. Artifact identity is NOT an upstream Git revision or dependency-license clearance.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

VERSION = '0.3.3'  # Aerix packaging generation; original plugin still identifies as 0.3.0.
APK_SHA256 = '4ce0d57776132e0d782836d9b5ba7da57b0781b1f15f2eccf0766b239595e73f'
ARCHIVE_SHA256 = '57b1b16929a38f7bb12f282fef759e0a19886ef37c74d965d69645a689469687'
LIBRARIES = {
    'arm64-v8a': (1460976, 'f7d1876591add2c6dcb15aea2e9eae22a7eea3fede68210a539a84075c15b49f', 2, 183, 16384),
    'armeabi-v7a': (974036, '106e086b6b5b111bdf0eb63f895035ce8911e013775888573487a3092ba3d271', 1, 40, 4096),
    'x86': (1594064, 'd1e43b9f27a39b36c144a71b8555bf881c3347d56fcdc494ace7fb124f54436e', 1, 3, 4096),
    'x86_64': (1467656, '507ad2e47bec36a33d662f530b91b5ddc67ea8120bc3a2d8d4560cd4582f458d', 2, 62, 16384),
}
NOTICE = '''User-requested experimental RustGL prebuilt replacement
Original plugin: dev.rustrenderer.plugin 0.3.0 (version code 23).
Aerix packaging generation: 0.3.3; NOT an upstream version or source revision.
Original uploaded APK SHA256: ''' + APK_SHA256 + '''
Native libraries are copied byte-for-byte; plugin DEX/resources/debug certificate
are not installed or bundled. APK authenticity/source revision are unverified.
Upstream README declares MIT, but this APK contains no LICENSE/NOTICE files;
dependency provenance/license coverage for this prebuilt is unresolved. This notice
is provenance disclosure, NOT a replacement license grant or legal clearance.
User explicitly requested this replacement after being informed that the uploaded
ARM64/x86_64 glGetStringi retains a use-after-free. Not a crash fix or stable release.
No claim of Vulkan, Android gameplay, benchmark or security verification.
'''


def sha(data):
    return hashlib.sha256(data).hexdigest()


def validate_elf(data, abi, readelf, file):
    size, digest, bits, machine, alignment = LIBRARIES[abi]
    if len(data) != size or sha(data) != digest:
        raise ValueError('Uploaded native library checksum mismatch: ' + abi)
    if (data[:7] != b'\x7fELF' + bytes([bits, 1, 1]) or
            int.from_bytes(data[16:18], 'little') != 3 or
            int.from_bytes(data[18:20], 'little') != machine):
        raise ValueError('Wrong ELF ABI/type: ' + abi)
    details = subprocess.check_output([readelf, '-dW', '-lW', str(file)], text=True)
    needed = set(re.findall(r'\(NEEDED\).*?\[([^\]]+)\]', details))
    if not needed <= {'libc.so', 'libm.so', 'libdl.so', 'liblog.so'}:
        raise ValueError('Unexpected native dependency')
    loads = [line.split() for line in details.splitlines() if line.strip().startswith('LOAD ')]
    if not loads or any(int(line[-1], 16) < alignment for line in loads):
        raise ValueError('Unsupported ELF alignment')
    if any('W' in ''.join(line[6:-1]) and 'E' in ''.join(line[6:-1]) for line in loads):
        raise ValueError('Writable-executable load segment')


def prepare(archive, output, readelf='readelf', authorized=False):
    if not authorized:
        raise ValueError('Explicit experimental user-prebuilt authorization required')
    if archive.is_symlink() or sha(archive.read_bytes()) != ARCHIVE_SHA256:
        raise ValueError('Unrecognized user-provided native archive')
    # Refuse to merge in a stale source-built payload or overwrite an immutable generation.
    if output.exists():
        raise ValueError('Output must be absent; do not mix previous RustGL payloads')
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='rustgl-upload-', dir=output.parent) as tmp:
        stage = Path(tmp) / 'bundle'
        stage.mkdir()
        release_id = hashlib.sha1(archive.read_bytes()).hexdigest()
        releases = []
        with zipfile.ZipFile(archive) as z:
            expected = {'lib/' + abi + '/librust_gl.so' for abi in LIBRARIES}
            if len(z.infolist()) != 4 or set(z.namelist()) != expected:
                raise ValueError('Unexpected archive entries')
            for abi in LIBRARIES:
                info = z.getinfo('lib/' + abi + '/librust_gl.so')
                if info.file_size != LIBRARIES[abi][0]:
                    raise ValueError('Unexpected native size')
                data = z.read(info)
                folder = stage / VERSION / abi
                folder.mkdir(parents=True)
                file = folder / 'librust_gl.so'
                file.write_bytes(data)
                validate_elf(data, abi, readelf, file)
                notice = NOTICE.encode()
                (folder / 'NOTICE.txt').write_bytes(notice)
                releases.append({'version': VERSION, 'revision': release_id, 'abi': abi, 'files': [
                    {'name': 'librust_gl.so', 'bytes': len(data), 'sha256': sha(data)},
                    {'name': 'NOTICE.txt', 'bytes': len(notice), 'sha256': sha(notice)}]})
        index = {'schema': 1, 'backend': 'gles', 'redistributionApproved': False,
                 'experimentalUserAuthorized': True, 'payloadKind': 'user-provided-prebuilt',
                 'sourceApkSha256': APK_SHA256, 'nativeArchiveSha256': ARCHIVE_SHA256,
                 'revisionKind': 'native-archive-sha1-NOT-upstream-git',
                 'upstreamSourceRevisionVerified': False, 'dependencyLicenseCoverageVerified': False,
                 'originalPluginVersion': '0.3.0', 'releases': releases}
        (stage / 'index.json').write_text(json.dumps(index, indent=2) + '\n')
        shutil.move(str(stage), output)
    print('Imported EXACT uploaded native payloads; no source-built RustGL retained. Known defect preserved; device success unverified.')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--archive', type=Path, default=Path('third_party/rustgl-upload/native-libraries.zip'))
    p.add_argument('--output', type=Path, default=Path('app_pojavlauncher/src/main/assets/renderer-bundles/rustgl'))
    p.add_argument('--readelf', default='readelf')
    p.add_argument('--experimental-user-prebuilt', action='store_true')
    args = p.parse_args()
    prepare(args.archive, args.output, args.readelf, args.experimental_user_prebuilt)
