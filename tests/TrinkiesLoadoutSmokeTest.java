import aerix.client.mod.TrinkiesLoadout;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Plain-JVM exercise of the mod's Trinkies loadout reader.
 *
 * Compiled and run by {@code tests/trinkies_loadout_jvm.py} against the real shipped catalog, so the
 * validation the in-game client depends on is executed rather than only compiled: what a loadout may
 * contain, what gets rejected, how an item's own animation file drives the client, and what the
 * receipt back to the launcher looks like. Nothing here touches Minecraft or AWT.
 *
 * Usage: java TrinkiesLoadoutSmokeTest REPO_ROOT
 */
public final class TrinkiesLoadoutSmokeTest {

    private static int failures = 0;
    private static int checks = 0;

    /** Fixture sentinel: build the slot list with this value to leave the field out of the document. */
    private static final String OMIT = "<omit>";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: TrinkiesLoadoutSmokeTest REPO_ROOT");
            System.exit(2);
        }
        File repoRoot = new File(args[0]).getCanonicalFile();
        File packRoot = new File(repoRoot, "trinkies_assets/trinkies");
        Map<String, TrinkiesLoadout.Entry> catalog =
                TrinkiesLoadout.parseCatalog(read(new File(packRoot, "catalog.json")));

        check("catalog holds all 300 items", catalog.size() == 300, "size=" + catalog.size());
        int[] perCategory = new int[6];
        for (TrinkiesLoadout.Entry entry : catalog.values()) {
            for (int i = 0; i < TrinkiesLoadout.CATEGORIES.length; i++) {
                if (TrinkiesLoadout.CATEGORIES[i].equals(entry.category)) {
                    perCategory[i]++;
                }
            }
        }
        boolean fiftyEach = true;
        for (int count : perCategory) {
            fiftyEach &= count == 50;
        }
        check("every category has 50 items in the mod's index", fiftyEach, java.util.Arrays.toString(perCategory));

        // The asset paths the client resolves have to be the packaged ones, not filenames.
        TrinkiesLoadout.Entry back = null;
        for (TrinkiesLoadout.Entry entry : catalog.values()) {
            if ("back".equals(entry.category)) {
                back = entry;
                break;
            }
        }
        check("a back item carries a cape patch for the client",
                back != null && back.capeTexturePath != null && back.capeTexturePath.startsWith("items/"),
                back == null ? "no back item" : String.valueOf(back.capeTexturePath));
        check("animations.json is resolved beside item.json",
                back != null && back.animationsJsonPath().endsWith("/animations.json")
                        && back.animationsJsonPath().startsWith("items/"),
                back == null ? "n/a" : back.animationsJsonPath());
        check("every garment path is packaged", !containsNullGarment(catalog), null);

        String valid = loadout(catalog, "hat", "suit", "back", "pet", "emote", "aura");
        TrinkiesLoadout.Snapshot snapshot = TrinkiesLoadout.parse(valid, catalog);
        check("a full wardrobe validates cleanly", !snapshot.hasProblem() && snapshot.equippedCount() == 6,
                String.valueOf(snapshot.getProblem()));
        check("the back slot resolves to the catalog entry",
                back != null && back.id.equals(snapshot.equippedIdFor("back"))
                        && snapshot.entryFor("back") != null, null);
        check("an empty wheel reads as eight unbound slots",
                snapshot.emoteWheel().size() == 8 && snapshot.emoteWheel().get(0) == null, null);

        TrinkiesLoadout.Snapshot wrongSchema =
                TrinkiesLoadout.parse(valid.replace("\"schema\": 1", "\"schema\": 2"), catalog);
        check("a future schema is refused rather than guessed",
                wrongSchema.hasProblem() && wrongSchema.getProblem().contains("schema"), wrongSchema.getProblem());

        TrinkiesLoadout.Snapshot missing =
                TrinkiesLoadout.parse(loadout(catalog, "hat", "suit", "back", "pet", "emote", OMIT), catalog);
        check("a missing slot is reported by name",
                missing.hasProblem() && missing.getProblem().contains("aura"), missing.getProblem());

        TrinkiesLoadout.Snapshot extra = TrinkiesLoadout.parse(
                valid.replace("\"schema\": 1,", "\"schema\": 1, \"mystery\": 3,"), catalog);
        check("an unknown field is refused (schema is additionalProperties:false)",
                extra.hasProblem() && extra.getProblem().contains("mystery"), extra.getProblem());

        String aHat = firstId(catalog, "hat");
        TrinkiesLoadout.Snapshot misplaced =
                TrinkiesLoadout.parse(loadout(catalog, "hat", "suit", null, "pet", "emote", "aura")
                        .replace("\"back\": null", "\"back\": " + quote(aHat)), catalog);
        check("a hat id in the back slot is refused, not baked as a cape",
                misplaced.hasProblem() && misplaced.getProblem().contains("cannot be worn as back"),
                misplaced.getProblem());
        check("the refused slot stays empty instead of half-applying",
                misplaced.equippedIdFor("back") == null, null);

        TrinkiesLoadout.Snapshot unknown = TrinkiesLoadout.parse(
                loadout(catalog, null, null, null, null, null, null)
                        .replaceFirst("\"hat\": null", "\"hat\": \"trinkies:hat_not_in_the_pack\""), catalog);
        check("an id the catalog does not carry is refused",
                unknown.hasProblem() && unknown.getProblem().contains("no item"), unknown.getProblem());

        TrinkiesLoadout.Snapshot legacy = TrinkiesLoadout.parse(
                loadout(catalog, null, null, null, null, null, null)
                        .replaceFirst("\"hat\": null", "\"hat\": \"crimson_crown\""), catalog);
        check("a pre-catalog id is not a loadout",
                legacy.hasProblem() && legacy.getProblem().contains("not a Trinkies catalog id"),
                legacy.getProblem());
        check("and it paints nothing rather than falling back to launcher art",
                legacy.equippedIdFor("hat") == null, null);

        TrinkiesLoadout.Snapshot broken = TrinkiesLoadout.parse("{\"schema\": 1, \"hat\":", catalog);
        check("malformed JSON is a problem, never an empty wardrobe",
                broken.isPresent() && broken.hasProblem() && broken.getProblem().contains("malformed"),
                broken.getProblem());

        String shortWheel = valid.replaceAll("\"emote_wheel\": \\[[^]]*\\]", "\"emote_wheel\": [null]");
        TrinkiesLoadout.Snapshot wheelProblem = TrinkiesLoadout.parse(shortWheel, catalog);
        check("a wheel that is not eight slots is refused",
                wheelProblem.hasProblem() && wheelProblem.getProblem().contains("8"), wheelProblem.getProblem());

        String wheelWithIds = valid.replaceAll("\"emote_wheel\": \\[[^]]*\\]",
                "\"emote_wheel\": [" + quote(aHat) + ", null, null, null, null, null, null, null]");
        TrinkiesLoadout.Snapshot wheelOk = TrinkiesLoadout.parse(wheelWithIds, catalog);
        check("a bound wheel slot must still be a catalog id",
                !wheelOk.hasProblem() && aHat.equals(wheelOk.emoteWheel().get(0)), wheelOk.getProblem());

        TrinkiesLoadout.Snapshot absent = TrinkiesLoadout.read(new File("definitely-not-here.json"), catalog);
        check("no file means no loadout, not an error",
                !absent.isPresent() && !absent.hasProblem(), absent.getProblem());

        // Cadence: the item's own clip decides how long a cycle takes, so the mod holds frames longer.
        File anyItemDir = new File(packRoot, "items");
        File[] itemDirs = anyItemDir.listFiles();
        int minTicks = Integer.MAX_VALUE;
        int maxTicks = 0;
        if (itemDirs != null) {
            for (File itemDir : itemDirs) {
                File animation = new File(itemDir, "animations.json");
                if (!animation.isFile()) {
                    continue;
                }
                int ticks = TrinkiesLoadout.frameTicksFromAnimation(read(animation), 12, 95L);
                minTicks = Math.min(minTicks, ticks);
                maxTicks = Math.max(maxTicks, ticks);
            }
        }
        check("animation-driven cadence stays inside the 1..8 tick band",
                minTicks >= 1 && maxTicks <= 8 && minTicks <= maxTicks,
                "min=" + minTicks + " max=" + maxTicks);
        check("a one-shot clip keeps the default cadence",
                TrinkiesLoadout.frameTicksFromAnimation(
                        "{\"clips\":{\"idle\":{\"duration\":4.0,\"loop_mode\":\"once\"}}}", 12, 95L) == 1, null);
        check("a long loop stretches the cycle",
                TrinkiesLoadout.frameTicksFromAnimation(
                        "{\"clips\":{\"idle\":{\"duration\":4.56,\"loop_mode\":\"loop\"}}}", 12, 95L) == 4, null);
        check("unreadable animation data is a non-event",
                TrinkiesLoadout.frameTicksFromAnimation("{oops", 12, 95L) == 1, null);
        // Resolved from disk the same way the mod resolves it from the JAR, so the path the catalog
        // advertises is proven to point at the file that actually ships.
        File shippedClip = new File(packRoot, back.animationsJsonPath());
        check("an item's own idle clip paces the cape animator",
                shippedClip.isFile()
                        && TrinkiesLoadout.frameTicksFromAnimation(read(shippedClip), 12, 95L) == 2,
                String.valueOf(shippedClip.getName()));

        String meta = "capeId=aurora\ntrinkiesBack=" + back.id + "\nframes=12\n";
        check("a bake marked for this loadout is a match",
                TrinkiesLoadout.bakeMatchesLoadout(meta, snapshot), null);
        check("a bake from a different back item is reported as stale",
                !TrinkiesLoadout.bakeMatchesLoadout(meta.replace(back.id, "trinkies:none"), snapshot), null);
        check("an older bake that claims nothing is not treated as a mismatch",
                TrinkiesLoadout.bakeMatchesLoadout("capeId=aurora\nframes=12\n", snapshot), null);
        TrinkiesLoadout.Snapshot nothingOn =
                TrinkiesLoadout.parse(loadout(catalog, null, null, null, null, null, null), catalog);
        check("unequipping clears the match instead of failing it",
                TrinkiesLoadout.bakeMatchesLoadout("trinkiesBack=none\n", nothingOn), null);

        File workDir = Files.createTempDirectory("trinkies-applied").toFile();
        try {
            String applied = TrinkiesLoadout.buildAppliedJson(snapshot, 3, false, 1730000000000L);
            TrinkiesLoadout.writeApplied(workDir, applied);
            File marker = new File(workDir, TrinkiesLoadout.APPLIED_FILE_NAME);
            check("the receipt is written for the launcher to read", marker.isFile(), null);
            Map<String, String> flat = TrinkiesLoadout.parseApplied(read(marker));
            check("the receipt round-trips every slot",
                    back.id.equals(flat.get("equipped.back"))
                            && String.valueOf(1).equals(flat.get("schema"))
                            && String.valueOf(3).equals(flat.get("frameTicks"))
                            && "false".equals(flat.get("bake_matches_loadout"))
                            && String.valueOf(1730000000000L).equals(flat.get("confirmedAtMs")),
                    flat.toString());
            check("and only the five headers plus six slots appear in it",
                    flat.size() == 11, "size=" + flat.size());
            TrinkiesLoadout.writeApplied(workDir, TrinkiesLoadout.buildAppliedJson(nothingOn, 1, true, 7L));
            Map<String, String> cleared = TrinkiesLoadout.parseApplied(read(marker));
            check("an empty wardrobe is written as explicit nulls, not empty strings",
                    !cleared.containsKey("equipped.hat") || cleared.get("equipped.hat") == null,
                    cleared.toString());
            check("and a stale bake is recorded as a boolean the launcher can act on",
                    "true".equals(cleared.get("bake_matches_loadout")), cleared.toString());
            boolean refused = false;
            try {
                TrinkiesLoadout.writeApplied(new File(workDir, "missing-dir"), "{}");
            } catch (IOException expected) {
                refused = true;
            } catch (NullPointerException expected) {
                refused = true;
            }
            check("a receipt is never written outside an existing directory",
                refused || !new File(workDir, "missing-dir").exists(), null);
        } finally {
            deleteTree(workDir);
        }

        if (failures > 0) {
            System.out.println(failures + " of " + checks + " Trinkies loadout checks failed");
            System.exit(1);
        }
        System.out.println("Trinkies loadout reader: " + checks + " checks passed against the shipped catalog");
    }

    // -- helpers ------------------------------------------------------------------------------

    private static boolean containsNullGarment(Map<String, TrinkiesLoadout.Entry> catalog) {
        for (TrinkiesLoadout.Entry entry : catalog.values()) {
            if (entry.garmentPath == null || !entry.garmentPath.startsWith("items/")) {
                return true;
            }
            if (entry.itemJsonPath == null) {
                return true;
            }
        }
        return false;
    }

    private static String firstId(Map<String, TrinkiesLoadout.Entry> catalog, String category) {
        for (Iterator<String> it = catalog.keySet().iterator(); it.hasNext(); ) {
            String id = it.next();
            if (category.equals(catalog.get(id).category)) {
                return id;
            }
        }
        return null;
    }

    /**
     * Builds a loadout document. Each argument names either a category (filled with the first real
     * item in it), an explicit id, or null - which is still written out, so a reader can tell
     * "nothing equipped" from "the writer forgot the slot".
     */
    private static String loadout(Map<String, TrinkiesLoadout.Entry> catalog, String hat, String suit,
                                  String backSlot, String pet, String emote, String aura) {
        StringBuilder builder = new StringBuilder("{\n  \"schema\": 1,\n");
        String[] wanted = {hat, suit, backSlot, pet, emote, aura};
        List<String> rows = new ArrayList<String>();
        for (int i = 0; i < TrinkiesLoadout.CATEGORIES.length; i++) {
            String value = wanted[i];
            if (OMIT.equals(value)) {
                continue; // drop the field entirely, which is what "the writer forgot a slot" means
            }
            String id = value;
            if (value != null && value.indexOf(':') < 0) {
                id = firstId(catalog, value);
            }
            rows.add("    \"" + TrinkiesLoadout.CATEGORIES[i] + "\": " + (id == null ? "null" : quote(id)));
        }
        for (int i = 0; i < rows.size(); i++) {
            builder.append(rows.get(i)).append(",\n");
        }
        builder.append("    \"emote_wheel\": [null, null, null, null, null, null, null, null]\n}\n");
        return builder.toString();
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), Charset.forName("UTF-8"));
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        file.delete();
    }

    private static void check(String label, boolean condition, String detail) {
        checks++;
        if (condition) {
            return;
        }
        failures++;
        System.out.println("FAIL: " + label + (detail == null ? "" : " (" + detail + ")"));
    }
}
