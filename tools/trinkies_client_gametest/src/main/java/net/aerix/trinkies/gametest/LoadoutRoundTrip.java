package net.aerix.trinkies.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import aerix.client.mod.TrinkiesLoadout;

/**
 * Drives the packaged Trinkies mod's loadout reader from inside a running client: writes a wardrobe
 * into the game directory, waits for the mod's receipt, and asserts what the client actually accepted.
 *
 * <p>This lives in the harness's own source tree and both entrypoints carry a copy of the small
 * pieces they need, because the generated client-GameTest mod and the 1.20.1 boot-smoke mod are
 * assembled from different source sets per Minecraft version; a shared class would quietly depend on
 * how Loom happens to wire those classpaths. Self-contained duplication is the safer trade here.
 *
 * <p>Everything it asserts is the mod's own behavior - it never re-implements the read or the receipt,
 * it only writes {@code trinkies/equipped.json} and reads back {@code trinkies/applied.json} with the
 * same {@link TrinkiesLoadout} methods the launcher-facing code uses.
 */
final class LoadoutRoundTrip {

    private LoadoutRoundTrip() {
    }

    /** The first real catalog id in a category, taken from the pack inside the mod JAR itself. */
    static String firstCatalogId(String category) {
        Map<String, TrinkiesLoadout.Entry> catalog = TrinkiesLoadout.catalogFromJar();
        for (TrinkiesLoadout.Entry entry : catalog.values()) {
            if (category.equals(entry.category)) {
                return entry.id;
            }
        }
        throw new AssertionError("the packaged catalog contains no " + category + " item");
    }

    static Map<String, TrinkiesLoadout.Entry> catalog() {
        return TrinkiesLoadout.catalogFromJar();
    }

    /**
     * Writes {@code trinkies/equipped.json} exactly as the launcher shapes it - every slot present,
     * the wheel as eight explicit entries - and returns the file so a caller can compare mtimes.
     */
    static Path writeWardrobe(Path gameDir, String hatId, String backId, String wheelEmoteId,
                              String misplacedBackId) throws IOException {
        Path directory = gameDir.resolve("trinkies");
        Files.createDirectories(directory);
        StringBuilder json = new StringBuilder("{\n  \"schema\": 1,\n");
        String[] values = new String[6];
        values[0] = hatId;
        values[1] = null;
        values[2] = misplacedBackId != null ? misplacedBackId : backId;
        values[3] = null;
        values[4] = null;
        values[5] = null;
        for (int i = 0; i < TrinkiesLoadout.CATEGORIES.length; i++) {
            json.append("    \"").append(TrinkiesLoadout.CATEGORIES[i]).append("\": ")
                    .append(values[i] == null ? "null" : "\"" + values[i] + "\"").append(",\n");
        }
        json.append("    \"emote_wheel\": [")
                .append(wheelEmoteId == null ? "null" : "\"" + wheelEmoteId + "\"");
        for (int i = 1; i < 8; i++) {
            json.append(", null");
        }
        json.append("]\n}\n");

        Path target = directory.resolve(TrinkiesLoadout.FILE_NAME);
        Path temporary = Files.createTempFile(directory, ".gametest-", ".tmp");
        Files.write(temporary, json.toString().getBytes(StandardCharsets.UTF_8));
        Files.deleteIfExists(target);
        Files.move(temporary, target);
        return target;
    }

    /** The mod's receipt, parsed with the mod's own reader. Null when it has not answered yet. */
    static Map<String, String> readReceipt(Path gameDir) {
        Path marker = gameDir.resolve("trinkies").resolve(TrinkiesLoadout.APPLIED_FILE_NAME);
        if (!Files.isRegularFile(marker)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(marker);
            return TrinkiesLoadout.parseApplied(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new AssertionError("the client wrote an unreadable Trinkies receipt: " + error, error);
        }
    }

    static long receiptMtime(Path gameDir) {
        try {
            return Files.getLastModifiedTime(
                    gameDir.resolve("trinkies").resolve(TrinkiesLoadout.APPLIED_FILE_NAME)).toMillis();
        } catch (IOException error) {
            return -1L;
        }
    }

    /**
     * What a receipt has to say once the client has read a wardrobe. Every failure names the file it
     * looked at, because the CI log is the only view of a headless client run.
     */
    static void requireWardrobeApplied(List<Path> dirs, Map<String, String> receipt, String hatId,
                                       String backId, boolean expectBack) {
        if (receipt == null) {
            throw new AssertionError("the Trinkies mod never wrote trinkies/" + TrinkiesLoadout.APPLIED_FILE_NAME
                    + " under " + dirs + " - the loadout poll is not running in this client");
        }
        if (!"1".equals(receipt.get("schema"))) {
            throw new AssertionError("unexpected receipt schema from the client: " + receipt);
        }
        if (!hatId.equals(receipt.get("equipped.hat"))) {
            throw new AssertionError("the client did not wear the hat from equipped.json: wanted " + hatId
                    + ", got " + receipt.get("equipped.hat") + " in " + receipt);
        }
        String worn = receipt.get("equipped.back");
        if (expectBack && !backId.equals(worn)) {
            throw new AssertionError("the client did not wear the back item: wanted " + backId + ", got " + worn);
        }
        if (!expectBack && worn != null) {
            throw new AssertionError("the client accepted a misplaced slot instead of refusing it: " + receipt);
        }
        if (!expectBack) {
            // With no back item worn there is nothing to pace, and the client must say so plainly
            // rather than stretch a cycle for an item it refused.
            if (!"1".equals(receipt.get("frameTicks"))) {
                throw new AssertionError("a refused slot must not drive the cape pace: " + receipt);
            }
            return;
        }
        String pace = receipt.get("frameTicks");
        if (pace == null) {
            throw new AssertionError("the receipt carries no cape pace, so the animation data was ignored: " + receipt);
        }
        int ticks = Integer.parseInt(pace);
        if (ticks < 2 || ticks > 8) {
            throw new AssertionError("the item's own idle clip did not drive the cape pace (got " + ticks
                    + ", expected a stretched 2..8): " + receipt);
        }
        if (!"true".equals(receipt.get("bake_matches_loadout"))) {
            throw new AssertionError("a client without launcher-baked frames should not report a stale bake: "
                    + receipt);
        }
    }

    /**
     * The wheel is the second half of the contract: eight keybound ids that paint nothing on the skin
     * and must still be validated item by item. The count is what the client itself accepted, so a
     * hallucinated or truncated binding cannot pass by being echoed back.
     */
    static void requireWheelAccepted(Map<String, String> receipt, int expectedSlots) {
        if (receipt == null) {
            throw new AssertionError("no receipt, so the emote wheel was never answered");
        }
        String accepted = receipt.get("emoteWheelSlots");
        if (accepted == null) {
            throw new AssertionError("the receipt reports no emote wheel at all: " + receipt);
        }
        int slots = Integer.parseInt(accepted);
        if (slots != expectedSlots) {
            throw new AssertionError("the client accepted " + slots + " of " + expectedSlots
                    + " wheel bindings: " + receipt);
        }
    }

    /** Directory the mod treats as the game directory; dev launchers report either of two paths. */
    static List<Path> candidateGameDirs(Path fabricGameDir) {
        List<Path> dirs = new ArrayList<Path>();
        dirs.add(fabricGameDir.toAbsolutePath().normalize());
        Path cwd = java.nio.file.Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        if (!dirs.contains(cwd)) {
            dirs.add(cwd);
        }
        return dirs;
    }

    /** Writes the same loadout into every candidate so the mod's own directory choice cannot matter. */
    static void seed(List<Path> dirs, String hatId, String backId, String wheelId, String misplacedBackId)
            throws IOException {
        for (Path dir : dirs) {
            writeWardrobe(dir, hatId, backId, wheelId, misplacedBackId);
        }
    }

    static Map<String, String> firstReceipt(List<Path> dirs) {
        for (Path dir : dirs) {
            Map<String, String> receipt = readReceipt(dir);
            if (receipt != null) {
                return receipt;
            }
        }
        return null;
    }

    static long newestMtime(List<Path> dirs) {
        long newest = -1L;
        for (Path dir : dirs) {
            newest = Math.max(newest, receiptMtime(dir));
        }
        return newest;
    }

    static Path loadoutFile(List<Path> dirs) {
        for (Path dir : dirs) {
            Path file = dir.resolve("trinkies").resolve(TrinkiesLoadout.FILE_NAME);
            if (Files.isRegularFile(file)) {
                return file;
            }
        }
        return dirs.get(0).resolve("trinkies").resolve(TrinkiesLoadout.FILE_NAME);
    }

    /** What the client answered, formatted for a CI log line. */
    static String describe(Map<String, String> receipt) {
        return receipt == null ? "{}" : receipt.toString();
    }
}
