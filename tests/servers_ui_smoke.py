from pathlib import Path
root = Path(__file__).resolve().parents[1]
base = root / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix'
rail = (base / 'ui/AerixNavigationRail.kt').read_text()
router = (base / 'ui/AerixLauncherScreen.kt').read_text()
screen = (base / 'ui/screens/ServersScreen.kt').read_text()
store = (base / 'servers/SavedServers.kt').read_text()
assert 'LauncherSection.SERVERS' in rail and 'ServersScreen(activity)' in router
for expected in ['ServerStatusClient.ping', 'SrvLookup.resolve', 'Add server', 'Choose banner', 'Remove banner', 'Copy address', 'Edit server', 'Remove server', 'joinSocialSession', 'PojavInstanceBridge.select', "Minecraft's own server list is separate"]:
    assert expected in screen, expected
assert 'server_banners' in store and '8 * 1024 * 1024' in store
# Left list + right detail: scrollable names on the left, details switch on tap.
assert 'LazyColumn' in screen and 'items(entries, key = { it.id })' in screen
assert 'BoxWithConstraints' in screen and 'VerticalDivider' in screen
assert 'ServerListItem' in screen and 'ServerDetail' in screen
assert 'selectedId = entry.id' in screen and 'selectedId' in screen
assert 'automaticBannerFor(entry.id)' in screen and 'other banners are decorative' in screen
assert 'if (banner != null) Image(banner' in screen and 'painterResource(automaticBanner)' in screen
assert 'ServerArtworkImages.load(artwork)' in screen and 'artworkCatalog?.find(selected.address)' in screen
for scene in ('forest', 'nether', 'night'):
    assert (root / f'app_pojavlauncher/src/main/res/drawable-nodpi/server_scene_{scene}.jpg').is_file()
# Existing server features stay untouched: instance auto-join and the social party server hub.
editor = (base / 'ui/screens/InstanceEditorScreen.kt').read_text()
socials = (base / 'ui/screens/SocialPartiesAndDiscover.kt').read_text()
assert 'aerix_inst_server_ip_' in editor and 'SocialServerHubSection' in socials
assert 'serverAddress = party?.serverAddress ?: toast.serverAddress' in (base / 'ui/AerixLauncherScreen.kt').read_text()
print('Servers UI and preservation smoke test passed')
