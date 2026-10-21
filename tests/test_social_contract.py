"""Source-level contracts. These do not replace Android rendering or runtime tests."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch'
SOCIAL = JAVA / 'aerix/ui/screens/social'

class SocialContract(unittest.TestCase):
    def test_social_is_outside_launcher_chrome(self):
        code = (JAVA / 'aerix/ui/AerixLauncherScreen.kt').read_text()
        root = code.split('private fun LauncherChrome')[0]
        self.assertIn('SocialShellState.isOpen', root)
        self.assertIn('else LauncherChrome(activity)', root)
        self.assertNotIn('AerixBackground {', root)
        self.assertIn('!net.kdt.pojavlaunch.aerix.ui.screens.social.SocialShellState.isOpen && AerixNav.canGoBack', code)

    def test_no_social_launcher_destination(self):
        for path in ['aerix/ui/AerixNav.kt', 'aerix/ui/AerixNavigationRail.kt']:
            self.assertNotIn('SOCIAL', (JAVA / path).read_text())

    def test_social_has_no_launcher_accent_or_network(self):
        for path in SOCIAL.glob('*.kt'):
            code = path.read_text()
            for forbidden in ['AerixThemeManager', 'AerixSurface.accent', 'java.net', 'DownloadUtils', 'HttpURLConnection', 'PojavAccountBridge']:
                self.assertNotIn(forbidden, code, str(path))

    def test_both_dashboard_layouts_have_one_entry(self):
        code = (JAVA / 'aerix/ui/screens/HomeScreen.kt').read_text()
        self.assertEqual(2, code.count('SocialWidgetCard()'))
        self.assertNotIn('SocialRoute', code)

    def test_human_vectors_exist(self):
        drawable = ROOT / 'app_pojavlauncher/src/main/res/drawable'
        for i in range(1, 7):
            self.assertEqual('vector', ET.parse(drawable / f'aerix_social_avatar_{i}.xml').getroot().tag)
        self.assertNotIn('aerix_social_game_head', (SOCIAL / 'SocialTheme.kt').read_text())

    def test_onboarding_order_is_explicit(self):
        code = (SOCIAL / 'SocialStore.kt').read_text()
        self.assertLess(code.index('!SocialStore.data.signedIn -> SocialRoute.AUTH'), code.index('!SocialStore.data.appearanceComplete -> SocialRoute.APPEARANCE'))
        self.assertIn('else -> SocialRoute.DASHBOARD', code)
        self.assertIn('Mock Google sign-in only', code)

    def test_minecraft_accounts_are_not_deleted(self):
        code = (SOCIAL / 'SocialStore.kt').read_text()
        deletion = code.split('fun deleteAccount()')[1].split('fun request(')[0]
        self.assertNotIn('Accounts.', deletion)
        self.assertNotIn('Instances.', deletion)
        self.assertIn('social_skin.png', deletion)
        self.assertIn('social_cape.png', deletion)

    def test_settings_and_social_before_legacy_menu(self):
        code = (JAVA / 'game/GameActivity.java').read_text()
        self.assertIn('GameMenuHost.show(this, this::openLegacyMenu)', code)
        menu = (JAVA / 'aerix/ui/game/GameMenuScreen.kt').read_text()
        self.assertIn('SocialActionRow("Settings"', menu)
        self.assertIn('SocialButton("Social"', menu)

    def test_screens_exist(self):
        code = '\n'.join(p.read_text() for p in SOCIAL.glob('*.kt'))
        for name in ['SocialAuthScreen', 'SocialAppearanceScreen', 'SocialDashboardScreen', 'SocialProfileScreen',
                     'SocialSkinPicker', 'SocialCapePicker', 'SocialPrivacyScreen', 'SocialNotificationsScreen',
                     'SocialVoiceScreen', 'SocialSafetyScreen', 'SocialDeletionScreen', 'SocialCompactPanel',
                     'SocialBlockDialog', 'SocialLeaveGameDialog', 'SocialReportScreen']:
            self.assertRegex(code, r'fun '+name+r'\(')

    def test_app_string_references_resolve(self):
        resources = ROOT / 'app_pojavlauncher/src/main/res/values'
        defined = set()
        for path in resources.glob('*.xml'):
            defined.update(re.findall(r'<string[^>]*name="([^"]+)"', path.read_text()))
        defined.update({'app_name', 'app_short_name', 'aerix_app_label', 'application_package',
                        'curseforge_api_key', 'group_id', 'storageProviderAuthorities'})
        referenced = set()
        for path in JAVA.rglob('*'):
            if path.suffix in {'.java', '.kt'}:
                referenced.update(re.findall(r'(?<!android\.)R\.string\.(\w+)', path.read_text()))
        self.assertEqual(set(), referenced - defined)

    def test_workflow_does_not_publish(self):
        workflow = (ROOT / '.github/workflows/android.yml').read_text()
        self.assertNotIn('gh release create', workflow)
        self.assertNotIn('upload-google-play', workflow)
        self.assertIn('assembleFullRelease', workflow)
        self.assertIn('assembleNoruntimeRelease', workflow)
        self.assertIn('actions/upload-artifact@v4', workflow)

if __name__ == '__main__':
    unittest.main()
