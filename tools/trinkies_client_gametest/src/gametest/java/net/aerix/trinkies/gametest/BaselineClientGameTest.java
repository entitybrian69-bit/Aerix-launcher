package net.aerix.trinkies.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client-runtime test for the packaged Trinkies mod: it must load, it must read the wardrobe the
 * launcher writes, and it must answer with a receipt that says what it accepted.
 *
 * <p>The loadout round trip is the point of this test. A mod that boots and never reads
 * {@code trinkies/equipped.json} would otherwise look fine here, because cosmetics are applied by
 * baking - there is no block, entity, or GUI to assert on. So the test writes a wardrobe, waits for
 * the client to answer, and checks the answer: the hat it should wear, the back item that also paces
 * the cape from its own animation clip, and - in the second half - the refusal of a slot the catalog
 * says belongs to a different category. It does not claim local-world or in-world attachment coverage.
 */
public final class BaselineClientGameTest implements FabricClientGameTest {

    /** 80 attempts at five ticks is ~20 s, against a poll interval of ~1.5 s. */
    private static final int POLL_ATTEMPTS = 80;
    private static final int TICKS_PER_ATTEMPT = 5;

    @Override
    public void runTest(ClientGameTestContext context) {
        String targetVersion = System.getProperty("trinkies.test.minecraft_version", "unknown")
                .replaceAll("[^A-Za-z0-9._-]", "-");
        System.out.println("TRINKIES_CLIENT_GAMETEST_STARTED: minecraft="
                + targetVersion + ", java=" + System.getProperty("java.version", "unknown"));

        if (!FabricLoader.getInstance().isModLoaded("trinkies")) {
            throw new AssertionError("The packaged Trinkies mod was not loaded by the client game test");
        }

        List<Path> gameDirs = LoadoutRoundTrip.candidateGameDirs(FabricLoader.getInstance().getGameDir());
        String hatId = LoadoutRoundTrip.firstCatalogId("hat");
        String backId = LoadoutRoundTrip.firstCatalogId("back");
        String emoteId = LoadoutRoundTrip.firstCatalogId("emote");
        System.out.println("TRINKIES_CLIENT_GAMETEST_WARDROBE: dirs=" + gameDirs
                + ", hat=" + hatId + ", back=" + backId);

        Map<String, String> receipt = null;
        try {
            LoadoutRoundTrip.seed(gameDirs, hatId, backId, emoteId, null);
            for (int attempt = 0; attempt < POLL_ATTEMPTS && receipt == null; attempt++) {
                context.waitTicks(TICKS_PER_ATTEMPT);
                receipt = LoadoutRoundTrip.firstReceipt(gameDirs);
            }
        } catch (IOException error) {
            throw new AssertionError("The harness could not write its test wardrobe: " + error, error);
        }
        LoadoutRoundTrip.requireWardrobeApplied(gameDirs, receipt, hatId, backId, true);
        // The wheel carries an id the client must find in the packaged catalog. The key press itself is
        // not exercised here: the client GameTest input layer dispatches straight to key mappings, so a
        // synthetic press never reaches the native key state the wheel polls, and pretending otherwise
        // would assert a code path that did not run.
        LoadoutRoundTrip.requireWheelAccepted(receipt, 1);

        // A slot the catalog does not offer for that category has to come back refused, with the rest
        // of the wardrobe untouched. This is the client enforcing the contract, not the test asserting
        // its own arithmetic.
        long seenMtime = LoadoutRoundTrip.newestMtime(gameDirs);
        Map<String, String> refused = null;
        try {
            LoadoutRoundTrip.seed(gameDirs, hatId, backId, emoteId, hatId);
            for (int attempt = 0; attempt < POLL_ATTEMPTS; attempt++) {
                context.waitTicks(TICKS_PER_ATTEMPT);
                if (LoadoutRoundTrip.newestMtime(gameDirs) > seenMtime) {
                    refused = LoadoutRoundTrip.firstReceipt(gameDirs);
                    break;
                }
            }
        } catch (IOException error) {
            throw new AssertionError("The harness could not rewrite its test wardrobe: " + error, error);
        }
        if (refused == null) {
            throw new AssertionError("The client did not re-read trinkies/"
                    + aerix.client.mod.TrinkiesLoadout.FILE_NAME + " after the launcher-equivalent write");
        }
        LoadoutRoundTrip.requireWardrobeApplied(gameDirs, refused, hatId, backId, false);
        LoadoutRoundTrip.requireWheelAccepted(refused, 1);

        // The previous approved-runtime test reached the world-builder path after the mod-load
        // assertion but timed out waiting for that world. This startup smoke intentionally does
        // not cover integrated-world creation.
        Path screenshot = context.takeScreenshot("trinkies-client-startup-" + targetVersion);
        if (!Files.isRegularFile(screenshot)) {
            throw new AssertionError("Client GameTest screenshot was not written: " + screenshot);
        }
        System.out.println("TRINKIES_CLIENT_GAMETEST_PASS: minecraft="
                + targetVersion + ", java=" + System.getProperty("java.version", "unknown")
                + ", screenshot=" + screenshot
                + ", wardrobe=" + LoadoutRoundTrip.describe(refused));
    }
}
