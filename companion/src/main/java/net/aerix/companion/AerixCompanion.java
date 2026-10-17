package net.aerix.companion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.minecraft.UserApiService;
import net.aerix.companion.mixin.ClientSessionAccess;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.report.AbuseReportContext;
import net.minecraft.client.report.ReporterEnvironment;
import net.minecraft.client.util.ProfileKeys;
import net.minecraft.client.util.Session;
import net.minecraft.client.util.telemetry.TelemetryManager;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AerixCompanion implements ClientModInitializer {
    private static Path bridge;
    private static int ticks;
    private static volatile boolean switchedSession;
    public static boolean hasSwitchedSession() { return switchedSession; }
    private static volatile String requestId = "";
    private static volatile String message = "Ready. Disconnect before switching accounts.";
    private static final AtomicBoolean SWITCHING = new AtomicBoolean();
    private static final java.util.concurrent.ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AerixSessionIO"); t.setDaemon(true); return t;
    });

    @Override public void onInitializeClient() {
        CapeCache.initialize(MinecraftClient.getInstance().runDirectory.toPath());
        String path = System.getProperty("aerix.bridge.path", "");
        if (!path.isEmpty()) {
            bridge = Path.of(path);
            try {
                Files.createDirectories(bridge);
                for (String stale : new String[]{"switch.json", "switch-pending.json", "switch-pending.json.bak", "switch-pending.json.new"}) Files.deleteIfExists(bridge.resolve(stale));
            } catch (Exception ignored) { bridge = null; }
        }
    }

    public static Identifier cape(UUID uuid) { return CapeCache.texture(uuid); }

    private static boolean disconnected(MinecraftClient client) {
        return client.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen
                && client.world == null && client.getNetworkHandler() == null && !client.isIntegratedServerRunning();
    }

    public static void tick(MinecraftClient client) {
        if (bridge == null || ++ticks % 20 != 0) return;
        JsonObject status = new JsonObject();
        status.addProperty("updated", System.currentTimeMillis());
        status.addProperty("username", client.getSession().getUsername());
        status.addProperty("disconnected", disconnected(client));
        status.addProperty("message", message);
        status.addProperty("requestId", requestId);
        status.addProperty("switching", SWITCHING.get());
        IO.execute(() -> {
            try {
                write(bridge.resolve("status.json"), status.toString());
                Path request = bridge.resolve("switch.json");
                if (!Files.isRegularFile(request) || SWITCHING.get()) return;
                if (Files.size(request) > 16384) { Files.deleteIfExists(request); return; }
                String contents = Files.readString(request);
                Files.deleteIfExists(request);
                JsonObject json = JsonParser.parseString(contents).getAsJsonObject();
                long age = System.currentTimeMillis() - json.get("created").getAsLong();
                if (age < 0 || age > 30_000) return;
                String nextId = json.get("id").getAsString();
                if (!SWITCHING.compareAndSet(false, true)) return;
                requestId = nextId;
                message = "Preparing account switch…";
                client.execute(() -> prepare(client, json));
            } catch (Exception ignored) { message = "Account request could not be read; retry from Aerix."; }
        });
    }

    private static void prepare(MinecraftClient client, JsonObject json) {
        try {
            if (!disconnected(client)) throw new IllegalStateException("Save, disconnect and return to the title screen first. Your account was not changed.");
            if (!json.get("provider").getAsString().equals(System.getProperty("aerix.auth.provider", "mojang"))) throw new IllegalStateException("Changing authentication provider requires restarting Minecraft.");
            String type = json.get("type").getAsString();
            if (!type.equals("local") && json.get("expires").getAsLong() <= System.currentTimeMillis()) throw new IllegalStateException("Account expired. Sign in again in the launcher.");
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            String token = json.get("token").getAsString();
            Session session = new Session(json.get("username").getAsString(), uuid.toString().replace("-", ""), token,
                    json.has("xuid") && !json.get("xuid").isJsonNull() ? Optional.of(json.get("xuid").getAsString()) : Optional.empty(),
                    Optional.empty(), Session.AccountType.byName(type.equals("microsoft") ? "msa" : "legacy"));
            Session previous = client.getSession();
            IO.execute(() -> {
                try {
                    UserApiService api = type.equals("microsoft") ? ((ClientSessionAccess) client).aerix$authentication().createUserApiService(token) : UserApiService.OFFLINE;
                    ProfileKeys keys = ProfileKeys.create(api, session, client.runDirectory.toPath());
                    client.execute(() -> {
                        try {
                            if (!disconnected(client) || client.getSession() != previous) throw new IllegalStateException("Session changed while preparing the switch. Retry from the title screen.");
                            SocialInteractionsManager social = new SocialInteractionsManager(client, api);
                            AbuseReportContext reports = AbuseReportContext.create(ReporterEnvironment.ofIntegratedServer(), api);
                            TelemetryManager telemetry = new TelemetryManager(client, api, session);
                            ClientSessionAccess access = (ClientSessionAccess) client;
                            client.getTelemetryManager().close();
                            access.aerix$session(session);
                            access.aerix$api(api);
                            access.aerix$keys(keys);
                            access.aerix$social(social);
                            access.aerix$reports(reports);
                            access.aerix$telemetry(telemetry);
                            access.aerix$properties().clear();
                            switchedSession = true;
                            message = "Switched to " + session.getUsername() + ". You can rejoin now. Realms requires a restart.";
                        } catch (Exception e) { message = "Switch failed; restart Minecraft before joining."; }
                        finally { SWITCHING.set(false); }
                    });
                } catch (Exception e) {
                    message = "Authentication failed. Sign in again through the launcher.";
                    SWITCHING.set(false);
                }
            });
        } catch (Exception e) { message = e.getMessage(); SWITCHING.set(false); }
    }

    private static void write(Path path, String value) throws Exception {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, value, StandardCharsets.UTF_8);
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
