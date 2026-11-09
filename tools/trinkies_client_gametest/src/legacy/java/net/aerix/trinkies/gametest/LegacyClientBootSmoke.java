package net.aerix.trinkies.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Test-only 1.20.1 fallback: that Fabric API release predates client GameTests, so boot the real
 * client, assert Trinkies loaded, and drive the same wardrobe round trip {@code BaselineClientGameTest}
 * performs - {@code trinkies/equipped.json} in, the client's {@code trinkies/applied.json} back out.
 *
 * <p>The assertion runs from an end-of-tick handler rather than {@code CLIENT_STARTED} because the
 * Trinkies mod answers the wardrobe from its own worker thread: blocking the client thread here would
 * stall the very loop the test depends on, and a title screen with no world is enough to prove the
 * reader runs. The tick budget is generous (~45 s) because this is software rendering on llvmpipe.
 */
public final class LegacyClientBootSmoke implements ClientModInitializer {

    private static final int TICK_BUDGET = 900;

    private final List<Path> gameDirs = LoadoutRoundTrip.candidateGameDirs(
            FabricLoader.getInstance().getGameDir());
    private final AtomicInteger ticks = new AtomicInteger();

    @Override
    public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("trinkies")) {
            throw new AssertionError("The packaged Trinkies mod was not loaded by the 1.20.1 client");
        }

        final String hatId = LoadoutRoundTrip.firstCatalogId("hat");
        final String backId = LoadoutRoundTrip.firstCatalogId("back");
        final String emoteId = LoadoutRoundTrip.firstCatalogId("emote");
        try {
            LoadoutRoundTrip.seed(gameDirs, hatId, backId, emoteId, null);
        } catch (IOException error) {
            throw new AssertionError("Could not write the test wardrobe for the 1.20.1 boot smoke", error);
        }

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> System.out.println(
                "CLIENT_STARTED trinkies-legacy-boot-smoke dirs=" + gameDirs + " hat=" + hatId));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            int tick = ticks.incrementAndGet();
            Map<String, String> receipt = LoadoutRoundTrip.firstReceipt(gameDirs);
            if (receipt == null) {
                if (tick > TICK_BUDGET) {
                    fail("the Trinkies mod never answered trinkies/equipped.json in "
                            + tick + " client ticks");
                }
                return;
            }
            try {
                LoadoutRoundTrip.requireWardrobeApplied(gameDirs, receipt, hatId, backId, true);
                LoadoutRoundTrip.requireWheelAccepted(receipt, 1);
            } catch (AssertionError rejection) {
                fail(rejection.getMessage());
                return;
            }
            writeMarker(receipt);
            System.out.println("TRINKIES_CLIENT_BOOT_SMOKE_PASS: " + LoadoutRoundTrip.describe(receipt));
            // This test-only mod runs in a disposable CI client process, not in the packaged Trinkies JAR.
            System.exit(0);
        });
    }

    private void fail(String reason) {
        System.out.println("TRINKIES_CLIENT_BOOT_SMOKE_FAIL: " + reason);
        System.exit(1);
    }

    /**
     * The marker the Gradle task requires. Two lines are matched exactly by {@code run_matrix.sh}
     * ({@code minecraft=} and {@code trinkies_loaded=true}), so the loadout proof is appended after
     * them rather than replacing anything.
     */
    private void writeMarker(Map<String, String> receipt) {
        String markerPath = System.getProperty("trinkies.test.success_marker");
        if (markerPath == null || markerPath.isEmpty()) {
            throw new IllegalStateException("Missing trinkies.test.success_marker system property");
        }
        Path marker = java.nio.file.Paths.get(markerPath);
        StringBuilder body = new StringBuilder();
        body.append("minecraft=").append(System.getProperty("trinkies.test.minecraft_version", "unknown")).append('\n')
                .append("java=").append(System.getProperty("java.version", "unknown")).append('\n')
                .append("trinkies_loaded=true\n")
                .append("trinkies_loadout_applied=true\n")
                .append("trinkies_receipt_frame_ticks=").append(receipt.get("frameTicks")).append('\n')
                .append("trinkies_emote_wheel_slots=").append(receipt.get("emoteWheelSlots")).append('\n');
        try {
            Files.createDirectories(marker.getParent());
            Files.write(marker, body.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new RuntimeException("Could not write client boot smoke marker", error);
        }
    }
}
