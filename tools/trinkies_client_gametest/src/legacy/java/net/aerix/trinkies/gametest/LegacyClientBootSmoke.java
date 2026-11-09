package net.aerix.trinkies.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Test-only 1.20.1 fallback: that Fabric API release predates client GameTests, so boot the
 * real client, assert Trinkies loaded, and write a CI marker when the client-started event fires.
 */
public final class LegacyClientBootSmoke implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("trinkies")) {
            throw new AssertionError("The packaged Trinkies mod was not loaded by the 1.20.1 client");
        }

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            String markerPath = System.getProperty("trinkies.test.success_marker");
            if (markerPath == null || markerPath.isEmpty()) {
                throw new IllegalStateException("Missing trinkies.test.success_marker system property");
            }

            Path marker = Paths.get(markerPath);
            try {
                Files.createDirectories(marker.getParent());
                Files.write(marker, (
                        "minecraft=" + System.getProperty("trinkies.test.minecraft_version", "unknown") + "\n"
                        + "java=" + System.getProperty("java.version", "unknown") + "\n"
                        + "trinkies_loaded=true\n"
                ).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException error) {
                throw new RuntimeException("Could not write client boot smoke marker", error);
            }

            System.out.println("TRINKIES_CLIENT_BOOT_SMOKE_PASS: " + marker);
            // This test-only mod runs in a disposable CI client process, not in the packaged Trinkies JAR.
            System.exit(0);
        });
    }
}
