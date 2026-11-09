package net.aerix.trinkies.gametest;

import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.CameraType;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Infrastructure smoke test only: proves the packaged client JAR loads and the 1.21.11
 * client-gametest runner can create a local player and capture a third-person screenshot.
 * It deliberately does not claim any cosmetic is rendered; the hat acceptance test comes later.
 */
public final class BaselinePlayerGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("trinkies")) {
            throw new AssertionError("The packaged Trinkies mod was not loaded by the client game test");
        }

        Path screenshot;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            context.runOnClient(client -> {
                if (client.player == null) {
                    throw new AssertionError("Expected a local player in the generated singleplayer world");
                }
                client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            });
            context.waitTick();
            screenshot = context.takeScreenshot("trinkies-baseline-player-third-person");
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        }

        if (!Files.isRegularFile(screenshot)) {
            throw new AssertionError("Client GameTest screenshot was not written: " + screenshot);
        }
    }
}
