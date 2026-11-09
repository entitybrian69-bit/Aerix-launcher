"""Structural checks for Trinkies assets, the wardrobe bake path and honest limitations."""
from collections import Counter
from pathlib import Path
import json
import re
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from PIL import Image  # noqa: E402

# The bake is only correct while the launcher and the asset tooling agree on the skin layout, so the
# checks below read the layout from the tooling itself instead of restating the numbers.
from tools.trinkies_assets.mc_skin_render import CATEGORY_PATCH_PARTS, FACES, SKIN_PARTS  # noqa: E402
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
        self.assertEqual(Counter(item["rarity"] for item in catalog["items"]), {
            "common": 121, "epic": 64, "legendary": 61, "mythic": 49, "eternal": 5
        })
        self.assertEqual({item["id"] for item in catalog["items"] if item["rarity"] == "eternal"}, {
            "trinkies:hat_full_frost_circlet",
            "trinkies:suit_full_crimson_mechanist",
            "trinkies:back_full_aurora_drone",
            "trinkies:pet_full_moss_crab",
            "trinkies:aura_full_aurora_spectrum",
        })
        # Every category ships a garment patch, so every one is locally baked into the served skin.
        self.assertTrue(all(item["local_render_support"] for item in catalog["items"]))
        for item in catalog["items"]:
            folder = f"items/{item['id'].split(':', 1)[1]}"
            self.assertTrue((ROOT / "trinkies_assets/trinkies" / item["texture"]).is_file(), item["id"])
            self.assertEqual(item["preview"], f"{folder}/preview.png")
            self.assertEqual(item["garment"], f"{folder}/garment.png")
            preview = ROOT / "trinkies_assets/trinkies" / item["preview"]
            self.assertTrue(preview.is_file(), item["id"])
            with Image.open(preview) as image:
                # The card is the mannequin wearing the item, not an isolated prop render.
                self.assertEqual(image.size, (192, 192))
                self.assertEqual(image.mode, "RGBA")
                bbox = image.getchannel("A").getbbox()
                self.assertIsNotNone(bbox)
                self.assertGreater(bbox[3] - bbox[1], bbox[2] - bbox[0], item["id"])
            with Image.open(ROOT / "trinkies_assets/trinkies" / item["garment"]) as garment:
                self.assertEqual(garment.size, (64, 64))
                self.assertEqual(garment.mode, "RGBA")
            cape = ROOT / "trinkies_assets/trinkies" / folder / "cape_patch.png"
            self.assertEqual(cape.is_file(), item["category"] == "back", item["id"])

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

    def test_garment_patch_footprint_matches_the_launcher_bake_table(self):
        """The bake is only correct while both sides agree on the skin layout. The Kotlin table is
        the one that runs on device, so assert against it rather than against a copy of it here."""
        composite = (APP / "client/TrinkiesGarmentComposite.kt").read_text()
        blocks = dict(re.findall(r'"(\w+)" to intArrayOf\(([-0-9, ]+)\)', composite))
        self.assertEqual(len(blocks), 12, "expected one UV block per player part")
        for part, values in blocks.items():
            expected = SKIN_PARTS[part]
            self.assertEqual([int(v) for v in values.split(",")],
                             [expected.u, expected.v, expected.width, expected.height, expected.depth], part)
        kotlin_categories = {
            category: set(re.findall(r'"([a-z_]+)"', match))
            for category, match in re.findall(
                r'"(hat|suit|back|pet|emote|aura)" to listOf\(([^)]*)\)', composite)
        }
        for category, allowed in CATEGORY_PATCH_PARTS.items():
            self.assertEqual(kotlin_categories[category], set(allowed), category)
        self.assertEqual(re.search(r'BAKE_ORDER: List<String> = listOf\(([^)]*)\)', composite)[1]
                         .replace('"', "").replace(",", " ").split(),
                         ["suit", "back", "pet", "emote", "aura", "hat"])
        # A part's footprint must stay the union of its six faces. Testing the block as one
        # bounding box instead silently accepts the unused corner cells of the unfold, which is
        # exactly where a patch that repaints the face would hide.
        covers = composite[composite.index("private fun covers("):composite.index("private fun inside(")]
        for face in ("top", "bottom", "right", "front", "left", "back"):
            self.assertIn(f"// {face}", covers)
        self.assertEqual(covers.count("inside(x, y,"), 6, "one test per face, no bounding-box shortcut")
        self.assertIn("part in OVERLAY_BLOCKS", covers)

    def test_baked_garments_stay_inside_their_category_footprint(self):
        """A patch that painted the face or the legs would rewrite the player's own skin rather than
        a cosmetic, so both the generator and the bake refuse it. This checks the shipped files
        satisfy the rule, using the layout the renderer itself exposes."""
        footprints = {}
        for category, parts in CATEGORY_PATCH_PARTS.items():
            cells = set()
            for part in parts:
                for face in FACES:
                    u, v, w, h = SKIN_PARTS[part].face_rect(face)
                    cells.update((x, y) for y in range(v, v + h) for x in range(u, u + w))
            footprints[category] = cells
        for item_dir in sorted((ROOT / "trinkies_assets/trinkies/items").iterdir()):
            category = item_dir.name.split("_", 1)[0]
            with Image.open(item_dir / "garment.png") as image:
                alpha = list(image.convert("RGBA").getchannel("A").getdata())
            allowed = footprints[category]
            stray = [index for index, value in enumerate(alpha)
                     if value and (index % 64, index // 64) not in allowed]
            self.assertFalse(stray, f"{item_dir.name} paints outside its {category} regions")

    def test_equipped_wardrobe_reaches_baked_skin_and_per_instance_loadout(self):
        catalog = json.loads(CATALOG_PATH.read_text())
        reader = (APP / "client/TrinkiesCatalog.kt").read_text()
        cosmetics = (APP / "client/AerixClientCosmetics.kt").read_text()
        skin_server = (APP / "bridge/AerixSkinServer.kt").read_text()
        loadout = (APP / "client/TrinkiesLoadout.kt").read_text()
        wardrobe = (APP / "ui/screens/TrinkiesWardrobeScreen.kt").read_text()
        schema = json.loads((ROOT / "docs/wardrobe/schema/equipped.schema.json").read_text())

        self.assertIn('assets.open(ASSET_CATALOG)', reader)
        self.assertIn('textureAsset = "trinkies/$relativeTexture"', reader)
        self.assertIn('previewAsset = "trinkies/$relativePreview"', reader)
        self.assertIn("context.assets.open(it.previewAsset)", wardrobe)
        self.assertIn('"eternal" to TrinkiesRarityStyle', wardrobe)
        self.assertIn('assets.open(ASSET_CATALOG)', reader)
        self.assertIn('garmentAsset = "trinkies/$relativeGarment"', reader)
        self.assertIn('capeAsset = relativeCape.takeIf { it.isNotEmpty() }', reader)
        self.assertIn("TrinkiesCatalog.find(context, id)?.takeIf { it.locallyRendered }", cosmetics)
        self.assertIn("TrinkiesGarmentComposite.BAKE_ORDER", cosmetics)
        self.assertIn("TrinkiesGarmentComposite.bakeInto(copy, patches)", cosmetics)
        self.assertIn("isUsableGarment(pixels, category)", cosmetics)
        # Back items are the one category with a second sheet, so their patch has to reach the cape
        # the game reads: the served PNG and every animation frame written into the instance.
        self.assertIn('AerixClientStore.equippedIdFor("back")', cosmetics)
        self.assertIn("overlayCapePatch(bmp, trinkiesCapePatchPixels(context))", cosmetics)
        self.assertIn(
            "val trinkiesCape = if (importedCape == null) trinkiesCapePatchPixels(context) else null",
            cosmetics,
        )
        self.assertIn("overlayCapePatch(bmp, trinkiesCape)", cosmetics)
        self.assertIn("TrinkiesGarmentComposite.bakeCapeInto(frame, patch)", cosmetics)
        composite = (APP / "client/TrinkiesGarmentComposite.kt").read_text()
        # A cape patch is authored in the cape's own UV space, so the bake trusts only an exact
        # sheet: anything else is refused rather than stretched across the wings.
        self.assertIn("if (width != CAPE_WIDTH || height != CAPE_HEIGHT) return false", composite)
        self.assertIn("if (cape.size < area || patch.size < area) return false", composite)
        self.assertIn("AerixClientCosmetics.applyWardrobeToSkin(", skin_server)
        self.assertIn("applyClientCosmetics = true", wardrobe)
        self.assertIn("item?.description", wardrobe)
        self.assertIn('AerixClientStore.setEquippedId(category, item?.id ?: "none")', wardrobe)
        # The bespoke per-family pixel painting is gone: the asset is the renderer now.
        self.assertNotIn("catalogHat.shape.lowercase()", cosmetics)

        state_fields = set(schema["required"])
        self.assertEqual(state_fields - {"schema", "emote_wheel"}, {"hat", "suit", "back", "pet", "emote", "aura"})
        # The writer enumerates the same list, so a new schema field cannot go unwritten silently.
        self.assertIn('private val CATEGORIES = listOf("hat", "suit", "back", "pet", "emote", "aura")', loadout)
        self.assertIn("put(category, id ?: JSONObject.NULL)", loadout)
        self.assertIn('put("emote_wheel"', loadout)
        self.assertIn("TrinkiesCatalog.hasId(context, id, category)", loadout)
        self.assertIn("stream.fd.sync()", loadout)
        self.assertIn("Os.rename(temporary.absolutePath, target.absolutePath)", loadout)
        self.assertIn('File(canonicalDir, FILE_NAME)', loadout)
        self.assertEqual(len(catalog["items"]), 300)
        # A back row without cape_texture parses as an empty path, so equipping it would silently
        # change nothing in game; the catalog has to carry it for all fifty.
        self.assertTrue(
            all("cape_texture" in item for item in catalog["items"] if item["category"] == "back")
        )

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
        # The copy must keep the boundary honest: the bake is real, the 3D extras are not.
        self.assertIn("bakes the item's garment patch into the skin", wardrobe)
        self.assertIn("are not in game yet", wardrobe)
        self.assertIn("In-game appearance has not been visually verified on device yet", wardrobe)
        self.assertNotIn("CATALOG ONLY · NO RENDERER", wardrobe)


if __name__ == "__main__":
    unittest.main()
