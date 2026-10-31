package aerix.client.mod;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Optional local-only telemetry for the Android HUD. Never sends player data over the network. */
final class ClientTelemetry {
    private static final AtomicBoolean started = new AtomicBoolean();
    private static final AtomicBoolean pending = new AtomicBoolean();
    private static final AtomicReference<Properties> snapshot = new AtomicReference<Properties>();

    static void start() {
        if (!started.compareAndSet(false, true)) return;
        Thread thread = new Thread(new Runnable() {
            @Override public void run() { loop(); }
        }, "Aerix-Telemetry");
        thread.setDaemon(true);
        thread.start();
    }

    private static void loop() {
        File config = new File("config");
        File enabled = new File(config, "aerix_telemetry.enabled");
        File output = new File(config, "aerix_telemetry.properties");
        for (;;) {
            try {
                Thread.sleep(1000L);
                Properties data = snapshot.getAndSet(null);
                if (data != null && enabled.isFile()) {
                    File temp = new File(config, "aerix_telemetry.properties.tmp");
                    try (FileOutputStream stream = new FileOutputStream(temp)) { data.store(stream, null); }
                    if (!temp.renameTo(output)) temp.delete();
                }
                if (!enabled.isFile() || !pending.compareAndSet(false, true)) continue;
                try {
                    Class<?> client = Class.forName("net.minecraft.class_310");
                    Object mc = invoke(client, null, "method_1551");
                    if (!(mc instanceof Executor)) { pending.set(false); continue; }
                    final Object clientObject = mc;
                    ((Executor) mc).execute(new Runnable() {
                        @Override public void run() {
                            try { snapshot.set(readSnapshot(clientObject)); }
                            catch (Throwable ignored) { snapshot.set(null); }
                            finally { pending.set(false); }
                        }
                    });
                } catch (Throwable ignored) { pending.set(false); }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable ignored) { pending.set(false); }
        }
    }

    private static Properties readSnapshot(Object mc) throws Exception {
        Properties p = new Properties();
        p.setProperty("updated", Long.toString(System.currentTimeMillis()));
        Object player = field(mc, "field_1724");
        if (player == null) { p.setProperty("available", "false"); return p; }
        p.setProperty("available", "true");
        putValue(p, "x", player, "method_23317");
        putValue(p, "y", player, "method_23318");
        putValue(p, "z", player, "method_23321");
        try { p.setProperty("yaw", String.valueOf(invoke(player.getClass(), player, "method_5705", float.class, 1.0f))); }
        catch (Throwable ignored) { putValue(p, "yaw", player, "method_36454"); }
        putValue(p, "health", player, "method_6032");
        putValue(p, "maxHealth", player, "method_6063");
        try {
            Object handler = invoke(mc.getClass(), mc, "method_1562");
            Object uuid = invoke(player.getClass(), player, "method_5667");
            if (handler != null && uuid != null) {
                Object entry = invoke(handler.getClass(), handler, "method_2871", java.util.UUID.class, uuid);
                if (entry != null) putValue(p, "ping", entry, "method_2959");
            }
        } catch (Throwable ignored) { }
        Object world = field(mc, "field_1687");
        if (world != null) {
            putValue(p, "dayTime", world, "method_8532");
            try {
                Object pos = invoke(player.getClass(), player, "method_24515");
                Class<?> posClass = Class.forName("net.minecraft.class_2338");
                p.setProperty("light", String.valueOf(invoke(world.getClass(), world, "method_22339", posClass, pos)));
            } catch (Throwable ignored) { }
            try {
                Object key = invoke(world.getClass(), world, "method_27983");
                Object id = invoke(key.getClass(), key, "method_29177");
                p.setProperty("dimension", id.toString());
            } catch (Throwable ignored) { }
        }
        try {
            Object server = invoke(mc.getClass(), mc, "method_1558");
            if (server != null) p.setProperty("server", String.valueOf(field(server, "field_3761")));
            else p.setProperty("server", "Singleplayer");
        } catch (Throwable ignored) { }
        try {
            Object armor = invoke(player.getClass(), player, "method_5661");
            if (armor instanceof Iterable) {
                int i = 0;
                for (Object stack : (Iterable<?>) armor) { if (i >= 4) break; writeItem(p, "armor" + i++, stack); }
            }
        } catch (Throwable ignored) { }
        try { writeItem(p, "held", invoke(player.getClass(), player, "method_6047")); } catch (Throwable ignored) { }
        try { writeItem(p, "offhand", invoke(player.getClass(), player, "method_6079")); } catch (Throwable ignored) { }
        try {
            Object effects = invoke(player.getClass(), player, "method_6026");
            if (effects instanceof Iterable) {
                int i = 0;
                for (Object effect : (Iterable<?>) effects) {
                    if (i >= 12) break;
                    putValue(p, "effect" + i + "Name", effect, "method_5586");
                    putValue(p, "effect" + i + "Ticks", effect, "method_5584");
                    putValue(p, "effect" + i + "Level", effect, "method_5578");
                    i++;
                }
                p.setProperty("effects", Integer.toString(i));
            }
        } catch (Throwable ignored) { }
        return p;
    }

    private static void writeItem(Properties p, String prefix, Object stack) {
        if (stack == null) return;
        putValue(p, prefix + "Damage", stack, "method_7919");
        putValue(p, prefix + "Max", stack, "method_7936");
        putValue(p, prefix + "Count", stack, "method_7947");
        try {
            Object text = invoke(stack.getClass(), stack, "method_7964");
            p.setProperty(prefix + "Name", String.valueOf(invoke(text.getClass(), text, "method_10851")));
        } catch (Throwable ignored) { }
    }

    private static void putValue(Properties p, String key, Object object, String method) {
        try { Object value = invoke(object.getClass(), object, method); if (value != null) p.setProperty(key, String.valueOf(value)); }
        catch (Throwable ignored) { }
    }

    private static Object field(Object object, String name) throws Exception {
        for (Class<?> c = object.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(object); }
            catch (NoSuchFieldException ignored) { }
        }
        return null;
    }

    private static Object invoke(Class<?> type, Object target, String name, Object... args) throws Exception {
        Class<?>[] params = new Class<?>[args.length / 2];
        Object[] values = new Object[params.length];
        for (int i = 0; i < params.length; i++) { params[i] = (Class<?>) args[i * 2]; values[i] = args[i * 2 + 1]; }
        try { return type.getMethod(name, params).invoke(target, values); }
        catch (NoSuchMethodException ignored) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                try { Method m = c.getDeclaredMethod(name, params); m.setAccessible(true); return m.invoke(target, values); }
                catch (NoSuchMethodException missing) { }
            }
            throw ignored;
        }
    }
}
