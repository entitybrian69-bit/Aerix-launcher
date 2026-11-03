#!/usr/bin/env python3
"""Fetch the exact upstream jars on CI and verify metadata, integrity and bundled dependencies."""
import hashlib
import io
import json
from pathlib import Path
from urllib.request import Request, urlopen
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / 'app_pojavlauncher/src/main/assets/components/fastclient/performance-mods.json').read_text())
assert set(manifest) == {'1.21.11', '26.1.2', '26.2'}
for game, entries in manifest.items():
    assert {entry['id'] for entry in entries} == {'lithium', 'entityculling'}
    for entry in entries:
        req = Request(entry['url'], headers={'User-Agent': 'AerixLauncher/CI'})
        with urlopen(req, timeout=45) as response:
            data = response.read(40_000_001)
        assert len(data) <= 40_000_000
        assert hashlib.sha256(data).hexdigest() == entry['sha256'], entry['url']
        with ZipFile(io.BytesIO(data)) as jar:
            metadata = json.loads(jar.read('fabric.mod.json'))
            assert metadata['id'] == entry['id']
            nested_ids = set()
            for nested in metadata.get('jars', []):
                with ZipFile(io.BytesIO(jar.read(nested['file']))) as nested_jar:
                    nested_ids.add(json.loads(nested_jar.read('fabric.mod.json'))['id'])
            required = set(metadata.get('depends', {}))
            unknown = required - {'minecraft', 'java', 'fabricloader', 'fabric-api', 'fabric'} - nested_ids
            assert not unknown, (game, entry['id'], unknown, nested_ids)
            print(f"::notice title=FastClient mod metadata::{game} {entry['id']} {metadata.get('version')} depends={json.dumps(metadata.get('depends', {}), separators=(',', ':'))} nested={','.join(sorted(nested_ids))}")
print('Pinned FastClient performance mod downloads verified')
