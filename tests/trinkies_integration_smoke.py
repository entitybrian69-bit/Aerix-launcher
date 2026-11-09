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
        self.assertIn('TRINKIES_ARTIFACT_NAME = "trinkies-aerix.jar"', installer)
        self.assertIn('TRINKIES_MANIFEST_NAME = "trinkies-aerix.manifest.json"', installer)
        self.assertIn("sha256(target)", installer)
        self.assertIn("findFabricModJar(modsDir, \"trinkies\", target)", installer)
        self.assertIn("TrinkiesDeploymentStatus.CONFLICT", installer)
        self.assertNotIn('File(modsDir, "aerix-client-animator.jar").delete()', installer)
        self.assertNotIn("temporary.copyTo(target, overwrite = true)", installer)

    def test_shared_cosmetic_assets_are_packaged_from_one_source(self):
        catalog = json.loads((ROOT / "trinkies_assets/trinkies/catalog.json").read_text())
        self.assertEqual(len(catalog["items"]), 30)
        self.assertEqual(catalog["status"], "vertical_slice_unverified_in_game")
        app_gradle = (ROOT / "app_pojavlauncher/build.gradle").read_text()
        mod_gradle = (ROOT / "aerix_client_mod/build.gradle").read_text()
        self.assertIn("assets.srcDir rootProject.file('trinkies_assets')", app_gradle)
        self.assertIn("from(rootProject.file('trinkies_assets/trinkies'))", mod_gradle)
        self.assertIn("into 'assets/trinkies'", mod_gradle)

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
        shell = (APP / "ui/AerixLauncherScreen.kt").read_text()
        self.assertIn('animationName = "NewIdle"', home)
        self.assertIn("cameraDistance = 48", home)
        self.assertIn(".clipToBounds()", home)
        # Both the live stage and its retained fallback must follow the same world-rotation
        # convention: dragging right subtracts azimuth, while its negated release velocity is
        # integrated positively so the fling continues in the same direction.
        self.assertEqual(home.count("dollAzimuth - dragAmount"), 2)
        self.assertEqual(home.count("val releaseVelocity = -"), 2)
        self.assertEqual(home.count("dollAzimuth = (dollAzimuth + velocity * dt)"), 2)
        self.assertNotIn(".background(AerixSurface.canvas.copy(alpha = 0.28f))", home)
        rail_position = shell.index("AerixHomeUtilityRail(")
        content_position = shell.index("AnimatedContent(")
        self.assertGreater(rail_position, content_position)
        self.assertIn("section != LauncherSection.TRINKIES_WARDROBE", shell)

        wardrobe = (APP / "ui/screens/TrinkiesWardrobeScreen.kt").read_text()
        for category in ("LOOKS", "SHOP", "SKINS", "CAPES", "HEADWEAR", "SUITS", "BACK_ITEMS", "PETS"):
            self.assertIn(f"{category}(\"", wardrobe)
        self.assertIn('WardrobeTopBar(', wardrobe)
        self.assertIn('WardrobeFilterTabs(', wardrobe)
        self.assertIn("cameraDistance = 48", wardrobe)
        self.assertIn("autoRotate = true", wardrobe)
        self.assertIn('Font(R.font.minecraft_ten)', wardrobe)
        self.assertIn("Vanilla, Forge and NeoForge instances are not modified", wardrobe)
        self.assertIn("when it and the server allow", wardrobe)
        self.assertIn("remote viewers are not guaranteed to see them", wardrobe)
        self.assertIn("No pet pack is bundled in this APK yet", wardrobe)


if __name__ == "__main__":
    unittest.main()
