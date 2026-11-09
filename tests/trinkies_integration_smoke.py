"""Structural checks for Trinkies assets, local hat wiring and honest limitations."""
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix"
CATALOG_PATH = ROOT / "trinkies_assets/trinkies/catalog.json"


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
        catalog = json.loads(CATALOG_PATH.read_text())
        expected_counts = {"hat": 50, "suit": 50, "back": 50, "pet": 50, "emote": 50, "aura": 50}
        self.assertEqual(len(catalog["items"]), 300)
        self.assertEqual(catalog["item_count"], 300)
        self.assertEqual(catalog["category_counts"], expected_counts)
        self.assertEqual(catalog["status"], "full_catalog_unverified_in_game")
        self.assertTrue(all(item.get("description") for item in catalog["items"]))
        hats = [item for item in catalog["items"] if item["category"] == "hat"]
        self.assertEqual(len(hats), 50)
        self.assertTrue(all(item["local_render_support"] for item in hats))
        self.assertTrue(all(not item["local_render_support"] for item in catalog["items"] if item["category"] != "hat"))
        for item in catalog["items"]:
            self.assertTrue((ROOT / "trinkies_assets/trinkies" / item["texture"]).is_file(), item["id"])

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
        self.assertIn("TrinkiesLoadout.writeForInstance(this, gamedir)", game)
        self.assertIn("TrinkiesInstaller.installIntoSupportedInstances(activity)", (APP / "ui/screens/HomeScreen.kt").read_text())

    def test_selected_hat_reaches_preview_skin_overlay_and_per_instance_loadout(self):
        catalog = json.loads(CATALOG_PATH.read_text())
        reader = (APP / "client/TrinkiesCatalog.kt").read_text()
        cosmetics = (APP / "client/AerixClientCosmetics.kt").read_text()
        skin_server = (APP / "bridge/AerixSkinServer.kt").read_text()
        loadout = (APP / "client/TrinkiesLoadout.kt").read_text()
        wardrobe = (APP / "ui/screens/TrinkiesWardrobeScreen.kt").read_text()
        schema = json.loads((ROOT / "docs/wardrobe/schema/equipped.schema.json").read_text())

        self.assertIn('assets.open(ASSET_CATALOG)', reader)
        self.assertIn('textureAsset = "trinkies/$relativeTexture"', reader)
        self.assertIn('TrinkiesCatalog.find(context, hatId)', cosmetics)
        self.assertIn('catalogHat.shape.lowercase()', cosmetics)
        for family in ("spire", "hood", "halo", "aviator", "circlet", "visor", "antlers", "wreath", "mask"):
            self.assertIn(f'"{family}"', cosmetics)
        self.assertIn("AerixClientCosmetics.applyHatAccessoryToSkin(", skin_server)
        self.assertIn("applyClientHat = true", wardrobe)
        self.assertIn("item?.description", wardrobe)
        self.assertIn("AerixClientStore.equippedHatId = item?.id ?: \"none\"", wardrobe)

        expected_state_fields = set(schema["required"])
        for field in expected_state_fields - {"schema", "emote_wheel"}:
            self.assertIn(f'.put("{field}"', loadout)
        self.assertIn('put("emote_wheel"', loadout)
        self.assertIn("TrinkiesCatalog.hasId(context, id, \"hat\")", loadout)
        self.assertIn("stream.fd.sync()", loadout)
        self.assertIn("Os.rename(temporary.absolutePath, target.absolutePath)", loadout)
        self.assertIn('File(canonicalDir, FILE_NAME)', loadout)
        self.assertEqual(len(catalog["items"]), 300)

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
        for category in ("LOOKS", "SHOP", "SKINS", "CAPES", "HEADWEAR", "SUITS", "BACK_ITEMS", "PETS", "EMOTES", "AURAS"):
            self.assertIn(f"{category}(\"", wardrobe)
        self.assertIn('WardrobeTopBar(', wardrobe)
        self.assertIn('WardrobeFilterTabs(', wardrobe)
        self.assertIn("cameraDistance = 48", wardrobe)
        self.assertIn("autoRotate = true", wardrobe)
        self.assertIn('Font(R.font.minecraft_ten)', wardrobe)
        self.assertIn("Vanilla, Forge and NeoForge instances are not modified", wardrobe)
        self.assertIn("when it and the server allow", wardrobe)
        self.assertIn("remote viewers are not guaranteed to see them", wardrobe)
        self.assertIn("in-game renderer is not enabled in this build", wardrobe)


if __name__ == "__main__":
    unittest.main()
