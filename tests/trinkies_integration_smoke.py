"""Structural checks for Trinkies branding, per-loader installation and honest limitations."""
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix"


class TrinkiesIntegrationSmoke(unittest.TestCase):
    def test_bundled_fabric_mod_is_branded_trinkies(self):
        descriptor = json.loads((ROOT / "aerix_client_mod/src/main/resources/fabric.mod.json").read_text())
        self.assertEqual(descriptor["id"], "trinkies")
        self.assertEqual(descriptor["name"], "Trinkies")
        self.assertEqual(descriptor["environment"], "client")
        self.assertIn("aerix.client.mod.AerixClientMod", descriptor["entrypoints"]["client"])

        installer = (APP / "client/AerixClientInstaller.kt").read_text()
        self.assertIn('File(modsDir, "trinkies.jar")', installer)
        self.assertIn('File(modsDir, "aerix-client-animator.jar").delete()', installer)

    def test_auto_install_is_limited_to_supported_fabric_family_instances(self):
        installer = (APP / "client/TrinkiesInstaller.kt").read_text()
        self.assertIn('id.contains("fabric")', installer)
        self.assertIn('id.contains("quilt")', installer)
        self.assertIn("fun installIntoSupportedInstances", installer)
        self.assertIn("fun syncLocalAssetsAtGameStart", installer)

        game = (ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/game/GameActivity.java").read_text()
        self.assertIn("TrinkiesInstaller.isSupportedInstance(instance)", game)
        self.assertIn("TrinkiesInstaller.syncLocalAssetsAtGameStart(this, instance)", game)
        self.assertIn("TrinkiesInstaller.installIntoSupportedInstances(activity)", (APP / "ui/screens/HomeScreen.kt").read_text())

    def test_dashboard_wardrobe_and_compatibility_copy_are_present(self):
        rail = (APP / "ui/AerixNavigationRail.kt").read_text()
        for destination in ("QUICK_TOOLS", "INSTANCE_SELECTOR", "TRINKIES_WARDROBE"):
            self.assertIn(destination, rail)
        home = (APP / "ui/screens/HomeScreen.kt").read_text()
        self.assertIn("AerixHomeUtilityRail", home)
        self.assertIn('animationName = "NewIdle"', home)
        wardrobe = (APP / "ui/screens/TrinkiesWardrobeScreen.kt").read_text()
        for category in ("SKINS", "CAPES", "HEADWEAR", "SUITS", "BACK_ITEMS", "PETS", "EMOTES"):
            self.assertIn(f"{category}(\"", wardrobe)
        self.assertIn("Vanilla, Forge and NeoForge instances are not modified", wardrobe)
        self.assertIn("when it and the server allow", wardrobe)
        self.assertIn("remote viewers are not guaranteed to see them", wardrobe)
        self.assertIn("No pet pack is bundled in this APK yet", wardrobe)


if __name__ == "__main__":
    unittest.main()
