#!/usr/bin/env python3
"""Validate the creator-provided builds and their launcher integration without Android."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'app_pojavlauncher/src/main/assets/components/fastclient'
JAVA = ROOT / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch'
BUILDS = {
    '1.21.11': ('4419bf1ca9dfdd8ed4f10c46a794bff3f72524b5d51a62a7223b12d03127f638', '>=0.18.4', '~1.21.11', '>=21'),
    '26.1.2': ('128502036f844b10d8d7023d07ac5aef06b5eae445cfb3782c7cc4995830d835', '>=0.18.5', '~26.1', '>=25'),
    '26.2': ('c8ab99cf7795c31f1bc010184783b73b3fac9defb1e95185108fe468ab8d647d', '>=0.19.3', '~26.2', '>=25'),
}
installer = (JAVA / 'aerix/fastclient/FastClientInstaller.kt').read_text()
for version, (sha, loader, minecraft, java) in BUILDS.items():
    path = BASE / f'fastclient-hud-{version}.jar'
    assert hashlib.sha256(path.read_bytes()).hexdigest() == sha, version
    with zipfile.ZipFile(path) as jar:
        assert jar.testzip() is None, version
        metadata = json.loads(jar.read('fabric.mod.json'))
        assert metadata['id'] == 'fasteclient'
        assert metadata['depends']['fabricloader'] == loader
        assert metadata['depends']['minecraft'] == minecraft
        assert metadata['depends']['java'] == java
        assert metadata['depends']['fabric-api'] == '*'
        assert any(name.startswith('LICENSE_') for name in jar.namelist())
    assert sha in installer and f'FastClientBuild("{version}"' in installer
assert 'instance.sharedData = false' in installer
assert 'instance.isAerixClient = false' in installer
assert 'overwrite = false' in installer
assert 'SHA-512' in installer and 'cdn.modrinth.com' in installer
assert 'ClientHubScreen(activity, clientOwnerPage)' in (JAVA / 'aerix/ui/AerixLauncherScreen.kt').read_text()
hub = (JAVA / 'aerix/ui/screens/ClientHubScreen.kt').read_text()
assert 'ClientOwnerPage.MX_INSPIRE -> FastClientScreen(activity, onBack = back)' in hub
assert 'ClientOwnerPage.SAHIL -> AerixClientScreen(activity, onBack = back)' in hub
assert 'onClick = { onPageChange(ClientOwnerPage.SAHIL) }' in hub
assert 'onClick = { onPageChange(ClientOwnerPage.MX_INSPIRE) }' in hub
assert 'BackHandler(enabled = page != ClientOwnerPage.OWNERS' in hub
assert 'Modifier.size(76.dp).clip(CircleShape)' in hub
assert 'LauncherSection.FAST_CLIENT' not in (JAVA / 'aerix/ui/AerixNavigationRail.kt').read_text()
for name in ('client_owner_sahil.jpg', 'client_owner_mx_inspire.jpg'):
    data = (ROOT / 'app_pojavlauncher/src/main/res/drawable-nodpi' / name).read_bytes()
    assert data[:2] == b'\xff\xd8' and len(data) > 1000, name
for name in ('AerixClientScreen.kt', 'FastClientScreen.kt'):
    assert 'ClientOwnerBackButton(onBack)' in (JAVA / 'aerix/ui/screens' / name).read_text()
screen = (JAVA / 'aerix/ui/screens/FastClientScreen.kt').read_text()
assert 'FastClientInstaller.install(activity, build)' in screen
assert 'PojavLaunchBridge.play(activity)' in screen
print('FastClient asset integrity and navigation smoke test passed')
