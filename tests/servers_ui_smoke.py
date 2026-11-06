from pathlib import Path
root = Path(__file__).resolve().parents[1]
base = root / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix'
rail = (base / 'ui/AerixNavigationRail.kt').read_text()
router = (base / 'ui/AerixLauncherScreen.kt').read_text()
screen = (base / 'ui/screens/ServersScreen.kt').read_text()
store = (base / 'servers/SavedServers.kt').read_text()
assert 'LauncherSection.SERVERS' in rail and 'ServersScreen(activity)' in router
for expected in ['ServerStatusClient.ping', 'SrvLookup.resolve', 'Add server', 'Choose banner', 'Remove banner', 'Copy address', 'Edit server', 'Remove server', 'joinSocialSession', 'PojavInstanceBridge.select', 'Minecraft\'s own server list is separate']:
    assert expected in screen, expected
assert 'server_banners' in store and '8 * 1024 * 1024' in store
assert 'SocialServerHubSection' in (base / 'ui/screens/SocialPartiesAndDiscover.kt').read_text()
print('Servers UI and preservation smoke test passed')
