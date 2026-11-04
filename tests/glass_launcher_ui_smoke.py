#!/usr/bin/env python3
"""Structural checks that the glass redesign keeps launcher destinations and live actions."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = root / 'app_pojavlauncher/src/main'
ui = main / 'java/net/kdt/pojavlaunch/aerix/ui'
shell = (ui / 'AerixLauncherScreen.kt').read_text()
nav = (ui / 'AerixBottomNavigation.kt').read_text()
home = (ui / 'screens/GlassHomeScreen.kt').read_text()
discover = (ui / 'screens/DiscoverScreen.kt').read_text()
accounts = (ui / 'screens/AccountsScreen.kt').read_text()
settings = (ui / 'screens/SettingsScreen.kt').read_text()
for destination in ('HOME', 'LIBRARY', 'DISCOVER', 'AERIX_CLIENT', 'SOCIALS', 'SETTINGS',
                    'FILES', 'LOGS', 'WALLPAPERS', 'OTHER_RELEASES', 'CONTRIBUTION', 'ABOUT', 'ACCOUNTS', 'CREATE_INSTANCE'):
    assert 'LauncherSection.' + destination in nav, destination
for section in ('HOME', 'LIBRARY', 'DISCOVER', 'AERIX_CLIENT', 'SOCIALS', 'SETTINGS', 'ACCOUNTS', 'INSTANCE_EDITOR'):
    assert 'LauncherSection.' + section in shell, section
assert 'AerixBottomNavigation(' in shell and 'GlassHomeScreen(activity, navigate)' in shell
assert 'HomeScreen(activity, navigate)' in shell # Existing tools/shortcuts remain accessible.
for action in ('PojavLaunchBridge.play(activity)', 'PojavInstanceBridge.edit', 'PojavNavigator.openCustomControls',
               'LauncherSection.CREATE_INSTANCE', 'LauncherSection.FILES', 'LauncherSection.LOGS'):
    assert action in home, action
for category in ('MODS', 'MODPACKS', 'RESOURCE_PACKS', 'SHADERS', 'WORLDS', 'SKINS'):
    assert category in discover, category
assert 'MiraiSkinLibraryPanel(activity, PojavAccountBridge.current)' in discover
assert 'PojavAccountBridge.select(account)' in accounts and 'PojavAccountBridge.delete(account)' in accounts
assert 'AerixNav.TAB_STYLES -> FontStylesTab(activity)' in settings
assert (main / 'assets/wallpapers/aerix_twilight.jpg').is_file()
assert 'aerix_twilight' in (main / 'java/net/kdt/pojavlaunch/aerix/theme/AerixThemeManager.kt').read_text()
print('Glass launcher navigation, home actions, skins and retained settings: smoke checks passed')
