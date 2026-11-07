#!/usr/bin/env python3
"""Guard the original Aerix layout, live actions, and shared glass treatment."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = root / 'app_pojavlauncher/src/main'
ui = main / 'java/net/kdt/pojavlaunch/aerix/ui'
shell = (ui / 'AerixLauncherScreen.kt').read_text()
nav = (ui / 'AerixNavigationRail.kt').read_text()
home = (ui / 'screens/HomeScreen.kt').read_text()
discover = (ui / 'screens/DiscoverScreen.kt').read_text()
accounts = (ui / 'screens/AccountsScreen.kt').read_text()
settings = (ui / 'screens/SettingsScreen.kt').read_text()
glass = (main / 'java/net/kdt/pojavlaunch/aerix/components/LiquidGlass.kt').read_text()
surfaces = (main / 'java/net/kdt/pojavlaunch/aerix/theme/AerixSurface.kt').read_text()
for section in ('HOME', 'LIBRARY', 'DISCOVER', 'AERIX_CLIENT', 'SOCIALS', 'SETTINGS', 'ACCOUNTS', 'INSTANCE_EDITOR'):
    assert 'LauncherSection.' + section in shell, section
assert 'AerixNavigationRail(' in shell and 'HomeScreen(activity, navigate)' in shell
assert 'GlassHomeScreen' not in shell and 'AerixBottomNavigation' not in shell
assert 'LauncherSection.AERIX_CLIENT' in nav and 'LauncherSection.CREATE_INSTANCE' in nav
for action in ('PojavLaunchBridge.play(activity)', 'PojavInstanceBridge.edit', 'PojavNavigator.openCustomControls',
               'LauncherSection.CREATE_INSTANCE', 'LauncherSection.FILES', 'LauncherSection.LOGS'):
    assert action in home, action
assert 'CompactSkinStageCard' not in home
for category in ('MODS', 'MODPACKS', 'RESOURCE_PACKS', 'SHADERS', 'WORLDS'):
    assert category in discover, category
assert 'PojavAccountBridge.select(account)' in accounts and 'PojavAccountBridge.delete(account)' in accounts
assert 'AerixNav.TAB_STYLES -> FontStylesTab(activity)' in settings
assert 'drawOutline(outline = outline, brush = sheen)' in glass
assert 'drawOutline(outline = outline, brush = rim' in glass
assert 'val accentDefault = Color(0xFF8DEFE0)' in surfaces # The old Aerix accent stays intact.
assert (main / 'assets/wallpapers/aerix_twilight.jpg').is_file() # Keep previously chosen wallpaper valid.
assert 'aerix_twilight' in (main / 'java/net/kdt/pojavlaunch/aerix/theme/AerixThemeManager.kt').read_text()
assert 'DEFAULT_WALLPAPER = "wp_01_lush_caves"' in (main / 'java/net/kdt/pojavlaunch/aerix/bridge/AerixPreferences.kt').read_text()
print('Original Aerix navigation, live actions and improved glass: smoke checks passed')
