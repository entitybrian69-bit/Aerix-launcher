#!/usr/bin/env python3
"""Exercise real 1.20.1 Minecraft authlib with the launcher agent and live test fixture.
Does not launch the game/server or accept the Minecraft EULA.
"""
import hashlib, json, os, pathlib, subprocess, tempfile, urllib.request, zipfile
ROOT = pathlib.Path(__file__).resolve().parents[1]
def fetch(url):
    with urllib.request.urlopen(url, timeout=90) as r: return r.read()
try:
    manifest = json.loads(fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
    metadata = json.loads(fetch(next(v['url'] for v in manifest['versions'] if v['id'] == '1.20.1')))
    download = metadata['downloads']['server']
    jar = fetch(download['url'])
    assert hashlib.sha1(jar).hexdigest() == download['sha1'], 'Server artifact integrity mismatch'
    with tempfile.TemporaryDirectory() as tmp:
        temp = pathlib.Path(tmp)
        with zipfile.ZipFile(__import__('io').BytesIO(jar)) as z:
            libs = [n for n in z.namelist() if n.startswith('META-INF/libraries/') and n.endswith('.jar')]
            assert any('/authlib/' in n for n in libs), 'No authlib in server bundle'
            for i, n in enumerate(libs): (temp / f'lib{i}.jar').write_bytes(z.read(n))
        subprocess.run(['javac', '-d', str(temp), str(ROOT / 'tests/AerixAuthlibIntegration.java')], check=True, timeout=45)
        agent = ROOT / 'app_pojavlauncher/src/main/assets/components/authlib-injector/authlib-injector.jar'
        endpoint = 'https://lktupxfszfefsoyksfkg.supabase.co/functions/v1/aerix-auth'
        subprocess.run(['java', f'-javaagent:{agent}={endpoint}', '-cp', f'{temp}:{temp}/*', 'AerixAuthlibIntegration'], check=True, timeout=90)
except Exception as e:
    print('::error title=Live Minecraft authlib integration::' + str(e).replace('\n', ' '), flush=True)
    raise
