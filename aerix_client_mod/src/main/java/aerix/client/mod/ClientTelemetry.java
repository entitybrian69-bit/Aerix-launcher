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
                    Class<?> client;
                    boolean modern;
                    try { client = Class.forName("net.minecraft.class_310"); modern = false; }
                    catch (ClassNotFoundException oldNamesUnavailable) {
                        client = Class.forName("net.minecraft.client.Minecraft"); modern = true;
                    }
                    Object mc = invoke(client, null, modern ? "getInstance" : "method_1551");
                    if (!(mc instanceof Executor)) { pending.set(false); continue; }
                    final Object clientObject = mc;
                    final boolean modernNames = modern;
                    ((Executor) mc).execute(new Runnable() {
                        @Override public void run() {
                            try { snapshot.set(modernNames ? readModernSnapshot(clientObject) : readSnapshot(clientObject)); }
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

    /** Best-effort Mojang-named 26.x telemetry; missing members are omitted, not guessed. */
    static Properties readModernSnapshot(Object mc) throws Exception {
        Properties p = new Properties();
        p.setProperty("updated", Long.toString(System.currentTimeMillis()));
        putValue(p, "fps", mc, "getFps");
        Object player = field(mc, "player");
        if (player == null) { p.setProperty("available", "false"); return p; }
        p.setProperty("available", "true");
        putValue(p, "x", player, "getX"); putValue(p, "y", player, "getY"); putValue(p, "z", player, "getZ");
        putValue(p, "yaw", player, "getYRot");
        putValue(p, "health", player, "getHealth"); putValue(p, "maxHealth", player, "getMaxHealth");
        try {
            Object options = field(mc, "options");
            Object option = invoke(options.getClass(), options, "renderDistance");
            putValue(p, "renderDistance", option, "get");
        } catch (Throwable ignored) { }
        try {
            Object inventory = invoke(player.getClass(), player, "getInventory");
            Object slot = invoke(inventory.getClass(), inventory, "getSelectedSlot");
            if (slot instanceof Number) p.setProperty("selectedSlot", Integer.toString(((Number) slot).intValue() + 1));
        } catch (Throwable ignored) { }
        try {
            Object world = field(mc, "level");
            if (world != null) {
                putValue(p, "dayTime", world, "getDayTime");
                Object key = invoke(world.getClass(), world, "dimension");
                Object location = invoke(key.getClass(), key, "location");
                if (location != null) p.setProperty("dimension", String.valueOf(location));
            }
        } catch (Throwable ignored) { }
        return p;
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
            Object options = field(mc, "field_1690");
            Object value = field(options, "field_1870");
            if (!(value instanceof Number)) value = invoke(value.getClass(), value, "method_41753");
            if (value instanceof Number) p.setProperty("renderDistance", String.valueOf(((Number) value).intValue()));
        } catch (Throwable ignored) { }
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
                try { p.setProperty("light", String.valueOf(invoke(world.getClass(), world, "method_22339", posClass, pos))); }
                catch (Throwable ignored) { }
                Object biome = invoke(world.getClass(), world, "method_23753", posClass, pos);
                String biomeId = biomeIdentifier(mc, biome);
                if (biomeId != null) p.setProperty("biome", biomeId);
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
        try {
            Object inventory = field(player, "field_7514");
            Object slot = field(inventory, "field_7545");
            if (slot instanceof Number) p.setProperty("selectedSlot", Integer.toString(((Number) slot).intValue() + 1));
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

    private static String biomeIdentifier(Object mc, Object biome) {
        if (biome == null) return null;
        try {
            // 1.18+: biome is a registry entry carrying a registry key.
            Object optional = invoke(biome.getClass(), biome, "method_40230");
            if (optional instanceof java.util.Optional && ((java.util.Optional<?>) optional).isPresent()) {
                Object key = ((java.util.Optional<?>) optional).get();
                Object id = invoke(key.getClass(), key, "method_29177");
                return id == null ? null : String.valueOf(id);
            }
        } catch (Throwable ignored) { }
        try {
            // 1.16.5: resolve the biome object through the server's dynamic biome registry.
            Object handler = invoke(mc.getClass(), mc, "method_1562");
            if (handler == null) return null;
            Object manager = invoke(handler.getClass(), handler, "method_29091");
            Class<?> registryClass = Class.forName("net.minecraft.class_2378");
            Object biomeKey = registryClass.getField("field_25114").get(null);
            Class<?> registryKeyClass = Class.forName("net.minecraft.class_5321");
            Object registry = invoke(manager.getClass(), manager, "method_30530", registryKeyClass, biomeKey);
            Object id = invoke(registry.getClass(), registry, "method_10221", Object.class, biome);
            return id == null ? null : String.valueOf(id);
        } catch (Throwable ignored) { return null; }
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
