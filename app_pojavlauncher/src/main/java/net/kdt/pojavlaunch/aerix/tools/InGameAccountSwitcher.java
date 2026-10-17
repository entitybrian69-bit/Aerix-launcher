package net.kdt.pojavlaunch.aerix.tools;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import androidx.appcompat.app.AlertDialog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Requests a disconnected-session switch from the optional companion; never rewrites a live server identity. */
public final class InGameAccountSwitcher {
    private static final java.util.concurrent.atomic.AtomicBoolean REQUESTING = new java.util.concurrent.atomic.AtomicBoolean();
    private static JsonObject status(Activity activity) throws Exception {
        File file = new File(activity.getFilesDir(), "aerix-game-bridge/status.json");
        if (!file.isFile() || file.length() > 16384) throw new IllegalStateException("Install the Aerix companion in Fabric 1.20.1, then launch that instance.");
        try (InputStreamReader reader = new InputStreamReader(new AtomicFile(file).openRead(), StandardCharsets.UTF_8)) {
            JsonObject value = JsonParser.parseReader(reader).getAsJsonObject();
            long age = System.currentTimeMillis() - value.get("updated").getAsLong();
            if (age < 0 || age > 5000) throw new IllegalStateException("The companion is not responding. It supports Fabric 1.20.1 only.");
            return value;
        }
    }

    public static void show(Activity activity, Account launchedAccount) {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                JsonObject state = status(activity);
                if (!state.get("disconnected").getAsBoolean()) throw new IllegalStateException("Save and disconnect to Minecraft's title screen first. Minecraft can stay open; the launcher will not force-close your world.");
                List<Account> accounts = Accounts.load().accounts;
                String[] labels = new String[accounts.size()];
                for (int i = 0; i < labels.length; i++) labels[i] = accounts.get(i).username + " · " + accounts.get(i).authType.name();
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    new AlertDialog.Builder(activity).setTitle("Switch account · " + state.get("username").getAsString())
                        .setItems(labels, (dialog, position) -> confirm(activity, launchedAccount, accounts.get(position)))
                        .setNegativeButton(android.R.string.cancel, null).show();
                });
            } catch (Exception e) { explain(activity, e.getMessage()); }
        });
    }

    private static void confirm(Activity activity, Account launched, Account target) {
        new AlertDialog.Builder(activity).setTitle("Switch to " + target.username + "?")
            .setMessage("Minecraft stays open. Rejoin your world/server after the switch succeeds. Changing authentication providers and using Realms require a restart. This does not change the launcher's default account.")
            .setPositiveButton("Switch", (dialog, which) -> request(activity, launched, target))
            .setNegativeButton(android.R.string.cancel, null).show();
    }

    private static void request(Activity activity, Account launched, Account target) {
        if (!REQUESTING.compareAndSet(false, true)) { explain(activity, "An account switch is already pending"); return; }
        PojavApplication.sExecutorService.execute(() -> {
            try {
                if (launched == null || !java.util.Objects.equals(launched.authType.injectorUrl, target.authType.injectorUrl)) throw new IllegalStateException("Switching authentication providers requires restarting Minecraft.");
                if (target.authType != AuthType.LOCAL && target.expiresAt <= System.currentTimeMillis()) throw new IllegalStateException("This account has expired. Sign in again in the launcher first.");
                if (!status(activity).get("disconnected").getAsBoolean()) throw new IllegalStateException("Disconnect before switching accounts.");
                JsonObject json = new JsonObject();
                String id = UUID.randomUUID().toString();
                json.addProperty("id", id);
                json.addProperty("created", System.currentTimeMillis());
                json.addProperty("username", target.username);
                String uuid = target.authType == AuthType.LOCAL
                    ? UUID.nameUUIDFromBytes(("OfflinePlayer:" + target.username).getBytes(StandardCharsets.UTF_8)).toString()
                    : target.profileId.replace("-", "").replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");
                json.addProperty("uuid", UUID.fromString(uuid).toString());
                json.addProperty("token", target.accessToken);
                json.addProperty("xuid", target.xuid);
                json.addProperty("expires", target.expiresAt);
                json.addProperty("type", target.authType == AuthType.MICROSOFT ? "microsoft" : target.authType == AuthType.LOCAL ? "local" : "elyby");
                json.addProperty("provider", target.authType.injectorUrl == null ? "mojang" : target.authType.injectorUrl);
                File directory = new File(activity.getFilesDir(), "aerix-game-bridge");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Unable to open the private game bridge");
                AtomicFile file = new AtomicFile(new File(directory, "switch.json"));
                FileOutputStream stream = file.startWrite();
                try { stream.write(json.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(stream); }
                catch (Exception e) { file.failWrite(stream); throw e; }
                activity.runOnUiThread(() -> waitForResult(activity, id, System.currentTimeMillis() + 20_000));
            } catch (Exception e) { REQUESTING.set(false); explain(activity, e.getMessage()); }
        });
    }

    private static void waitForResult(Activity activity, String id, long deadline) {
        if (activity.isFinishing() || activity.isDestroyed()) { REQUESTING.set(false); return; }
        PojavApplication.sExecutorService.execute(() -> {
            try {
                JsonObject state = status(activity);
                if (state.has("requestId") && id.equals(state.get("requestId").getAsString()) && !state.get("switching").getAsBoolean()) {
                    REQUESTING.set(false);
                    explain(activity, state.get("message").getAsString());
                    return;
                }
                if (System.currentTimeMillis() > deadline) {
                    new AtomicFile(new File(activity.getFilesDir(), "aerix-game-bridge/switch.json")).delete();
                    REQUESTING.set(false);
                    explain(activity, "Switch timed out. Check the companion status before reconnecting.");
                    return;
                }
                new Handler(Looper.getMainLooper()).postDelayed(() -> waitForResult(activity, id, deadline), 1000);
            } catch (Exception e) { REQUESTING.set(false); explain(activity, "The companion stopped responding; restart Minecraft before joining."); }
        });
    }

    private static void explain(Activity activity, String message) {
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) new AlertDialog.Builder(activity)
                .setTitle("Account switcher").setMessage(message).setPositiveButton(android.R.string.ok, null).show();
        });
    }
}
