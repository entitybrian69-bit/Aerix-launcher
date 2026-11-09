package net.aerix.trinkies.gametest;

import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.CameraType;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Infrastructure smoke test only: proves the packaged client JAR loads and the target
 * client-gametest runner can create a local player and capture a third-person screenshot.
 * It deliberately does not claim any cosmetic is rendered; the hat acceptance test comes later.
 */
public final class BaselinePlayerGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        System.out.println("TRINKIES_CLIENT_GAMETEST_STARTED: minecraft="
                + System.getProperty("trinkies.test.minecraft_version", "unknown")
                + ", java=" + System.getProperty("java.version", "unknown"));
        if (!FabricLoader.getInstance().isModLoaded("trinkies")) {
            throw new AssertionError("The packaged Trinkies mod was not loaded by the client game test");
        }

        Path screenshot;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            // Use only cross-version context methods; TestSingleplayerContext changed in 26.3.
            context.waitTicks(10);
            context.runOnClient(client -> {
                if (client.player == null) {
                    throw new AssertionError("Expected a local player in the generated singleplayer world");
                }
                client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            });
            context.waitTick();
            String targetVersion = System.getProperty("trinkies.test.minecraft_version", "unknown")
                    .replaceAll("[^A-Za-z0-9._-]", "-");
            screenshot = context.takeScreenshot("trinkies-baseline-player-third-person-" + targetVersion);
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        }

        if (!Files.isRegularFile(screenshot)) {
            throw new AssertionError("Client GameTest screenshot was not written: " + screenshot);
        }
    }
}
