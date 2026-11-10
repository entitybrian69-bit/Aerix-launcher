package aerix.client.mod;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Live client options: applies launcher requests (Fullbright, the ZOOM button and Server Boost) to
 * the Options object of the running game, and puts the player's own values back when a request ends.
 *
 * An option is resolved by name only: the options.txt key first (a Mojang-mapped runtime exposes an
 * accessor or a field of exactly that name), then an intermediary alias that this repository already
 * verifies elsewhere. A value is written only when the member's current type matches and the number
 * sits inside the range that option legally takes, so an unknown Minecraft version reports the key
 * as not applied in {@link #statusLine()} instead of corrupting a neighbouring option.
 */
final class ClientLiveOptions {
    /** Launcher to mod requests: {@code gamma}, {@code zoomFov}. */
    static final String LIVE_FILE = "config/aerix_live.properties";
    /** Server Boost profile; only engaged while the client is inside a multiplayer world. */
    static final String BOOST_FILE = "config/aerix_server_boost.properties";
    /**
     * Values the game had before this class changed them. The launcher replays them into options.txt
     * before the next launch, so a game that saved while boosted cannot keep the boosted numbers.
     */
    static final String RECOVERY_FILE = "config/aerix_live_recovery.properties";

    private static final String KEY_GAMMA = "gamma";
    private static final String KEY_FOV = "fov";

    /** Options Server Boost may write, with the range each one legally takes. */
    private static final String[] BOOST_KEYS = new String[] {
            "renderDistance", "simulationDistance", "entityDistanceScaling", "mipmapLevels",
            "particles", "entityShadows"
    };
    private static final double[] BOOST_MIN = new double[] { 2, 2, 0.5, 0, 0, 0 };
    private static final double[] BOOST_MAX = new double[] { 32, 32, 3.0, 4, 2, 1 };

    private final Properties live = new Properties();
    private final Properties boost = new Properties();
    private final Map<String, Object> savedBoost = new LinkedHashMap<String, Object>();
    private final List<String> applied = new ArrayList<String>(8);
    private long liveStamp = Long.MIN_VALUE;
    private long boostStamp = Long.MIN_VALUE;
    private boolean boostFilePresent;
    private Object zoomOptions;
    private Object zoomOriginal;
    /** FOV has to be found once and then reused for the restore, so a discovered member is cached. */
    private Member fovHandle;
    private Object fovHandleOwner;
    private String lastError = "";
    private String lastRecoveryText = "";

    /**
     * @param multiplayer three-state hint for the current world: {@code Boolean.TRUE} when the
     *                  client is on a server, {@code Boolean.FALSE} in a local world, {@code null}
     *                  when the game did not expose enough to tell.
     */
    void tick(Object mc, File runDir, Object multiplayer) {
        try {
            readFiles(runDir);
            Object options = optionsOf(mc);
            if (options == null) return;
            applied.clear();
            applyGamma(options);
            applyZoom(options);
            applyBoost(options, multiplayer);
            writeRecovery(runDir);
        } catch (Throwable error) {
            lastError = shortText(error);
        }
    }

    /** @return true when the option was found and its value is (or just became) {@code raw}. */
    boolean apply(Object options, String key, String raw, double minimum, double maximum) {
        if (options == null || raw == null) return false;
        String text = raw.trim();
        if (text.isEmpty()) return false;
        try {
            Member member = KEY_FOV.equals(key) ? fovHandle(options) : resolve(options, key);
            if (member == null) return false;
            Object current = member.read(options);
            Object wanted = coerce(current, text);
            if (wanted == null) return false;
            if (wanted instanceof Number) {
                double value = ((Number) wanted).doubleValue();
                // Refusing an out-of-range number is also what stops an intermediary alias from
                // landing on a completely unrelated field of the same type.
                if (value < minimum || value > maximum) return false;
            }
            if (equalsValue(current, wanted)) return true;
            member.write(options, wanted);
            return true;
        } catch (Throwable error) {
            lastError = key + ": " + shortText(error);
            return false;
        }
    }

    /** @return the current value of an option, or null when this version does not expose it. */
    Object read(Object options, String key) {
        try {
            Member member = KEY_FOV.equals(key) ? fovHandle(options) : resolve(options, key);
            return member == null ? null : member.read(options);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** What the last tick actually managed to change, for the launcher's status line. */
    String statusLine() {
        StringBuilder out = new StringBuilder();
        out.append(applied.isEmpty() ? "idle" : join(applied, ","));
        if (!lastError.isEmpty()) out.append(" error=").append(lastError);
        return out.toString();
    }

    boolean isBoostEngaged() {
        return !savedBoost.isEmpty();
    }

    boolean isZoomActive() {
        return zoomOriginal != null;
    }

    /* ── requests ─────────────────────────────────────────────────────────────── */

    private void applyGamma(Object options) {
        String gamma = live.getProperty(KEY_GAMMA);
        if (gamma == null) return;
        if (apply(options, KEY_GAMMA, gamma, 0.0, 1.0)) applied.add("gamma=" + gamma);
    }

    /**
     * The ZOOM button asks for a narrowed FOV while it is held. The request carries the absolute FOV
     * to use, so the strength stays a launcher setting; anything outside the legal range means "no
     * zoom", which hands the player's own FOV back.
     */
    private void applyZoom(Object options) {
        String raw = live.getProperty("zoomFov");
        double wanted = parseDouble(raw, 0.0);
        if (wanted < 30.0 || wanted > 150.0) {
            if (zoomOriginal != null) {
                restore(options, KEY_FOV, zoomOriginal);
                zoomOriginal = null;
                zoomOptions = null;
            }
            return;
        }
        Object current = read(options, KEY_FOV);
        if (current == null) {
            lastError = "fov option not found on this version";
            return;
        }
        if (zoomOriginal == null || options != zoomOptions) {
            // The game may have rebuilt its Options (recreateOptions); remember the newest original.
            zoomOriginal = current;
            zoomOptions = options;
        }
        if (apply(options, KEY_FOV, raw, 30.0, 150.0)) applied.add("fov=" + raw);
    }

    private void applyBoost(Object options, Object multiplayer) {
        boolean wanted = boostFilePresent && !"0".equals(boost.getProperty("enabled", "1"));
        if (!wanted || !Boolean.TRUE.equals(multiplayer)) {
            if (!savedBoost.isEmpty()) restoreBoost(options);
            return;
        }
        for (int i = 0; i < BOOST_KEYS.length; i++) {
            String key = BOOST_KEYS[i];
            String raw = boost.getProperty(key);
            if (raw == null) continue;
            if (!savedBoost.containsKey(key)) {
                Object current = read(options, key);
                if (current != null) savedBoost.put(key, current);
            }
            if (apply(options, key, raw, BOOST_MIN[i], BOOST_MAX[i])) applied.add(key + "=" + raw);
        }
    }

    private void restoreBoost(Object options) {
        List<String> keys = new ArrayList<String>(savedBoost.keySet());
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            restore(options, key, savedBoost.get(key));
        }
        savedBoost.clear();
    }

    private void restore(Object options, String key, Object value) {
        if (value == null) return;
        try {
            Member member = KEY_FOV.equals(key) ? fovHandle(options) : resolve(options, key);
            if (member != null) member.write(options, value);
        } catch (Throwable ignored) {
            // Restoring is best effort; the recovery file still covers the next launch.
        }
    }

    /* ── files ────────────────────────────────────────────────────────────────── */

    private void readFiles(File runDir) {
        File liveFile = new File(runDir, LIVE_FILE);
        long stamp = liveFile.isFile() ? liveFile.lastModified() : -1L;
        if (stamp != liveStamp) {
            liveStamp = stamp;
            loadInto(live, liveFile);
        }
        File boostFile = new File(runDir, BOOST_FILE);
        boolean present = boostFile.isFile();
        long boostMtime = present ? boostFile.lastModified() : -1L;
        if (boostMtime != boostStamp || present != boostFilePresent) {
            boostStamp = boostMtime;
            boostFilePresent = present;
            loadInto(boost, boostFile);
        }
    }

    private static void loadInto(Properties target, File file) {
        target.clear();
        if (!file.isFile() || file.length() > 262_144L) return;
        FileInputStream stream = null;
        try {
            stream = new FileInputStream(file);
            target.load(stream);
        } catch (Throwable ignored) {
            // A half-written file is read again on the next tick instead of applying a partial request.
        } finally {
            close(stream);
        }
    }

    private void writeRecovery(File runDir) {
        Properties out = new Properties();
        for (Map.Entry<String, Object> entry : savedBoost.entrySet()) {
            out.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
        if (zoomOriginal != null) out.setProperty(KEY_FOV, String.valueOf(zoomOriginal));
        String text = render(out);
        File target = new File(runDir, RECOVERY_FILE);
        if (text.equals(lastRecoveryText)) return;
        lastRecoveryText = text;
        if (out.isEmpty()) {
            if (target.isFile()) target.delete();
            return;
        }
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        FileOutputStream stream = null;
        try {
            target.getParentFile().mkdirs();
            stream = new FileOutputStream(temp);
            out.store(stream, "Aerix Client live options - values from before the request");
            close(stream);
            stream = null;
            if (!temp.renameTo(target)) temp.delete();
        } catch (Throwable ignored) {
            close(stream);
            temp.delete();
        }
    }

    private static String render(Properties properties) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(256);
        try {
            properties.store(out, null);
        } catch (Throwable ignored) {
        }
        return out.toString();
    }

    private static void close(java.io.Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Throwable ignored) {
        }
    }

    /* ── option resolution ────────────────────────────────────────────────────── */

    /** Either a plain field of Options or a {@code SimpleOption}-like box behind it. */
    private static final class Member {
        private Field field;
        private Object box;
        private Method getter;
        private Method setter;

        Object read(Object options) throws Exception {
            if (isPlainField()) return field.get(options);
            Object target = box != null ? box : field.get(options);
            if (target == null) throw new NoSuchFieldException("option box is unset");
            return getter.invoke(target);
        }

        void write(Object options, Object value) throws Exception {
            if (isPlainField()) {
                field.set(options, value);
                return;
            }
            Object target = box != null ? box : field.get(options);
            if (target == null) throw new NoSuchFieldException("option box is unset");
            setter.invoke(target, value);
        }

        private boolean isPlainField() {
            return field != null && getter == null && setter == null;
        }
    }

    /** @return the game's field-of-view option, found by name or, failing that, by its signature. */
    private Member fovHandle(Object options) {
        if (fovHandle != null && fovHandleOwner == options) return fovHandle;
        Member found = resolve(options, KEY_FOV);
        if (found == null) found = discoverFov(options);
        fovHandle = found;
        fovHandleOwner = found == null ? null : options;
        return found;
    }

    private Member resolve(Object options, String key) {
        String[] names = namesFor(key);
        for (int i = 0; i < names.length; i++) {
            Method accessor = findNoArgMethod(options.getClass(), names[i]);
            if (accessor != null) {
                Object box;
                try {
                    box = accessor.invoke(options);
                } catch (Throwable ignored) {
                    continue;
                }
                if (box == null) continue;
                Method get = findAnyMethod(box.getClass(), "get", "method_41753");
                Method set = findOptionSetter(box.getClass());
                if (get == null || set == null) continue;
                Member member = new Member();
                member.box = box;
                member.getter = get;
                member.setter = set;
                return member;
            }
            Field field = findField(options.getClass(), names[i]);
            if (field == null) continue;
            Member member = plainOrBox(options, field);
            if (member != null) return member;
        }
        return null;
    }

    /**
     * FOV has no intermediary alias this repository can vouch for across every supported version, so
     * on older runtimes it is found by its signature: exactly one double option whose current value is
     * a legal field of view. More than one candidate, or none, means "not applied".
     */
    private Member discoverFov(Object options) {
        Member found = null;
        int candidates = 0;
        Class<?> current = options.getClass();
        while (current != null && current != Object.class) {
            Field[] fields;
            try {
                fields = current.getDeclaredFields();
            } catch (Throwable ignored) {
                return null;
            }
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
                double value;
                try {
                    field.setAccessible(true);
                    Object raw = field.getType() == double.class ? field.get(options) : readBox(options, field);
                    if (!(raw instanceof Double)) continue;
                    value = ((Double) raw).doubleValue();
                } catch (Throwable ignored) {
                    continue;
                }
                if (value < 30.0 || value > 110.0) continue;
                candidates++;
                if (found == null) found = plainOrBox(options, field);
            }
            current = current.getSuperclass();
        }
        return candidates == 1 ? found : null;
    }

    private static Object readBox(Object options, Field field) throws Exception {
        Member member = plainOrBox(options, field);
        return member == null ? null : member.read(options);
    }

    /** @return a member for a numeric/boolean field, or for a field holding an option box. */
    private static Member plainOrBox(Object options, Field field) {
        try {
            field.setAccessible(true);
        } catch (Throwable ignored) {
            return null;
        }
        Class<?> type = field.getType();
        if (type == double.class || type == float.class || type == int.class || type == long.class
                || type == boolean.class) {
            Member member = new Member();
            member.field = field;
            return member;
        }
        if (type.getName().startsWith("java.")) return null;
        Object box;
        try {
            box = field.get(options);
        } catch (Throwable ignored) {
            return null;
        }
        if (box == null) return null;
        Method get = findAnyMethod(box.getClass(), "get", "method_41753");
        Method set = findOptionSetter(box.getClass());
        if (get == null || set == null) return null;
        Member member = new Member();
        member.field = field;
        member.getter = get;
        member.setter = set;
        return member;
    }

    /** Intermediary aliases are only listed where this repository already relies on them. */
    private static String[] namesFor(String key) {
        if (KEY_GAMMA.equals(key)) return new String[] { "gamma", "field_1840" };
        if (KEY_FOV.equals(key)) return new String[] { "fov", "field_1831" };
        if ("renderDistance".equals(key)) return new String[] { "renderDistance", "field_1870" };
        if ("mipmapLevels".equals(key)) return new String[] { "mipmapLevels", "mipmap" };
        if ("particles".equals(key)) return new String[] { "particles", "particleOptions" };
        if ("maxFps".equals(key)) return new String[] { "maxFps", "framerateLimit" };
        return new String[] { key };
    }

    /* ── value helpers ────────────────────────────────────────────────────────── */

    private static Object coerce(Object current, String text) {
        if (current == null) return null;
        if (current instanceof Boolean) {
            String lower = text.toLowerCase(Locale.ROOT);
            if ("true".equals(lower) || "1".equals(lower) || "on".equals(lower) || "yes".equals(lower))
                return Boolean.TRUE;
            if ("false".equals(lower) || "0".equals(lower) || "off".equals(lower) || "no".equals(lower))
                return Boolean.FALSE;
            return null;
        }
        if (current instanceof String) return text;
        double parsed = parseDouble(text, Double.NaN);
        if (Double.isNaN(parsed)) return null;
        if (current instanceof Double) return Double.valueOf(parsed);
        if (current instanceof Float) return Float.valueOf((float) parsed);
        if (current instanceof Integer) return Integer.valueOf((int) Math.round(parsed));
        if (current instanceof Long) return Long.valueOf(Math.round(parsed));
        return null;
    }

    private static boolean equalsValue(Object a, Object b) {
        if (a == null || b == null) return false;
        if (a instanceof Number && b instanceof Number)
            return Math.abs(((Number) a).doubleValue() - ((Number) b).doubleValue()) < 0.0001;
        return a.equals(b);
    }

    static double parseDouble(String text, double fallback) {
        if (text == null) return fallback;
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String join(List<String> parts, String separator) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(separator);
            out.append(parts.get(i));
        }
        return out.toString();
    }

    static String shortText(Throwable error) {
        if (error == null) return "";
        String message = error.getMessage();
        String text = error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return text.length() <= 180 ? text : text.substring(0, 180);
    }

    /** Whether the client is on a server, local world, or the game did not say (null). */
    static Object multiplayerState(Object mc) {
        Method single = findAnyMethod(mc.getClass(), "isSingleplayer", "method_1759");
        if (single != null) {
            try {
                Object answer = single.invoke(mc);
                if (answer instanceof Boolean) return Boolean.valueOf(!((Boolean) answer).booleanValue());
            } catch (Throwable ignored) {
            }
        }
        // A live connection is what defines "on a server"; a local world has none. Whether the player
        // is inside a world at all is deliberately not enough here, so an unknown version stays null
        // and the boost keeps its hands off the player's options.
        Method serverData = findAnyMethod(mc.getClass(), "getCurrentServer", "getCurrentServerData",
                "method_1742");
        if (serverData != null) {
            try {
                return Boolean.valueOf(serverData.invoke(mc) != null);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    static Object optionsOf(Object mc) {
        Field field = findAnyField(mc.getClass(), "options", "field_1690");
        if (field == null) return null;
        try {
            return field.get(mc);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findAnyMethod(Class<?> owner, String... names) {
        for (int i = 0; i < names.length; i++) {
            Method method = findNoArgMethod(owner, names[i]);
            if (method != null) return method;
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> owner, String name) {
        Class<?> current = owner;
        while (current != null && current != Object.class) {
            try {
                Method method = current.getDeclaredMethod(name);
                if (method.getParameterTypes().length != 0 || method.getReturnType() == void.class) return null;
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /** @return the option box's value setter, which takes one boxed value and may return itself. */
    private static Method findOptionSetter(Class<?> owner) {
        Class<?> current = owner;
        while (current != null && current != Object.class) {
            Method[] methods;
            try {
                methods = current.getDeclaredMethods();
            } catch (Throwable ignored) {
                return null;
            }
            for (int i = 0; i < methods.length; i++) {
                Method method = methods[i];
                if (method.getParameterTypes().length != 1) continue;
                String name = method.getName();
                if (!"set".equals(name) && !"method_41748".equals(name)) continue;
                Class<?> returnType = method.getReturnType();
                if (returnType != void.class && !returnType.getName().contains("Option")) continue;
                if (!method.getParameterTypes()[0].equals(Object.class)) continue;
                method.setAccessible(true);
                return method;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Field findField(Class<?> owner, String name) {
        Class<?> current = owner;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Field findAnyField(Class<?> owner, String... names) {
        for (int i = 0; i < names.length; i++) {
            Field field = findField(owner, names[i]);
            if (field != null) return field;
        }
        return null;
    }
}
