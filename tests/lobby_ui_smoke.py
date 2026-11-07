#!/usr/bin/env python3
"""Guard the Aerix Lobby rebuild: assets, wiring, live actions and opt-out switches."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
main = root / 'app_pojavlauncher/src/main'
ui = main / 'java/net/kdt/pojavlaunch/aerix/ui'
screens = ui / 'screens'
drawable = main / 'res/drawable-nodpi'
strings = (main / 'res/values/aerix_strings.xml').read_text()

lobby = (screens / 'AerixLobbyScreen.kt').read_text()
shell = (ui / 'AerixLauncherScreen.kt').read_text()
background = (main / 'java/net/kdt/pojavlaunch/aerix/components/AerixBackground.kt').read_text()
prefs = (main / 'java/net/kdt/pojavlaunch/aerix/bridge/AerixPreferences.kt').read_text()
theme = (main / 'java/net/kdt/pojavlaunch/aerix/theme/AerixThemeManager.kt').read_text()
settings = (screens / 'SettingsScreen.kt').read_text()

# The new home is wired into the shell, with a way back and no rail while it is active.
assert 'AerixLobbyScreen(activity, navigate)' in shell
assert 'HomeScreen(activity, navigate)' in shell, 'the classic dashboard must stay reachable'
assert 'if (section != LauncherSection.SOCIALS && !lobbyActive)' in shell
assert 'skipWallpaper = villageBackdrop' in shell
assert 'AerixGuideState.running' in shell, 'the first-run tour still needs the rail'
assert 'skipWallpaper: Boolean = false' in background

# Every tile is a real action, not a placeholder.
for action in (
    'PojavLaunchBridge.play(activity)',
    'PojavInstanceBridge.select(it)',
    'navigate(LauncherSection.LIBRARY)',
    'navigate(LauncherSection.ACCOUNTS)',
    'navigate(LauncherSection.SOCIALS)',
    'navigate(LauncherSection.DISCOVER)',
    'navigate(LauncherSection.SETTINGS)',
    'AerixGuideState.replay()',
    'PojavNavigator.openWebsite(activity)',
    'PojavNavigator.openDiscord(activity)',
    'PojavNavigator.shareLogs(activity)',
    'SocialBackend.inviteCode()',
    'aerix-social://friend/$code',
    'AerixInstallQueue.running',
    'AerixInstallQueue.overallProgress',
    'guideAnchor(AerixGuideState.PLAY)',
):
    assert action in lobby, action

# Blocky visual language: no rounded glass, hard stepped shadows, pixel font, big 3D skin.
assert 'FontFamily(Font(R.font.minecraft_ten))' in lobby
assert 'drawRect(' in lobby and 'shadowOffset' in lobby
assert 'AerixSkinPreview3D(' in lobby and 'interactionEnabled = true' in lobby
assert 'LobbyBackdrop()' in lobby
assert 'AerixRadii' not in lobby and 'liquidGlass' not in lobby

# Assets: the village backdrop, the sunburst cards and the chrome icons all ship.
assert (main / 'assets/wallpapers/wp_26_lobby_village.jpg').is_file()
for name in (
    'aerix_lobby_card_shop.png', 'aerix_lobby_card_skin.png', 'aerix_lobby_card_friends.png',
    'aerix_lobby_ic_gift.png', 'aerix_lobby_ic_chest.png', 'aerix_lobby_ic_headset.png',
    'aerix_lobby_ic_cursor.png', 'aerix_lobby_ic_grid.png', 'aerix_lobby_ic_excl.png',
    'aerix_lobby_ic_close.png', 'aerix_lobby_ic_person_add.png', 'aerix_lobby_ic_search.png',
    'aerix_lobby_ic_settings.png', 'aerix_lobby_ic_head.png', 'aerix_lobby_player.png',
):
    assert (drawable / name).is_file(), name
assert 'wp_26_lobby_village' in theme, 'the village is also a selectable wallpaper preset'

# Preferences and settings switches let users go back to the old dashboard / their own wallpaper.
for key in ('KEY_LOBBY_UI', 'KEY_LOBBY_VILLAGE_BACKDROP', 'saveLobbyUi(', 'saveLobbyVillageBackdrop('):
    assert key in prefs, key
assert 'aerix_settings_lobby_home' in settings
assert 'aerix_settings_lobby_village' in settings
assert 'AerixPreferences.saveLobbyUi(false)' in lobby

# Strings referenced by the lobby must exist (and be unique).
used = set(re.findall(r'R\.string\.(\w+)', lobby))
declared = re.findall(r'<string name="(\w+)"', strings)
assert not (used - set(declared)), sorted(used - set(declared))
assert len(declared) == len(set(declared)), 'duplicate string names'

# The static browser preview mirrors the app assets and stays runnable.
preview = (root / 'preview/index.html').read_text()
assert 'wp_26_lobby_village.jpg' in preview and 'aerix_lobby_player.png' in preview
assert (root / 'tools/serve_lobby_preview.py').is_file()
assert (root / 'tools/generate_lobby_art.py').is_file()
assert (root / 'tools/render_lobby_preview.py').is_file()
assert (root / 'preview/lobby-preview.png').is_file()
assert (root / 'docs/lobby.md').is_file()

print('Aerix Lobby rebuild: smoke checks passed')
