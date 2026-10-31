package aerix.client.mod;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;

/** Client-world-only display overrides; never changes the integrated or remote server world. */
final class ClientVisualOverrides {
    private long configStamp = Long.MIN_VALUE;
    private int configPoll;
    private String timeMode = "OFF", weatherMode = "OFF";
    private Object currentWorld;
    private boolean timeSaved, weatherSaved;
    private long savedTime;
    private float savedRain, savedThunder;
    private boolean savedRaining;

    void tick(Object mc, File runDir) {
        try {
            File file = new File(runDir, "config/aerix_visual.properties");
            long stamp = configStamp;
            if (configPoll++ % 8 == 0) stamp = file.isFile() ? file.lastModified() : -1L;
            if (stamp != configStamp) {
                Properties p = new Properties();
                if (file.isFile()) try (FileInputStream input = new FileInputStream(file)) { p.load(input); }
                String t = p.getProperty("time", "OFF");
                String w = p.getProperty("weather", "OFF");
                timeMode = t.equals("DAY") || t.equals("SUNSET") || t.equals("NIGHT") ? t : "OFF";
                weatherMode = w.equals("CLEAR") || w.equals("RAIN") ? w : "OFF";
                configStamp = stamp;
            }
            Object world = field(mc, "field_1687");
            if (world != currentWorld) { currentWorld = world; timeSaved = false; weatherSaved = false; }
            if (world == null) return;
            Object properties = field(world, "field_9232");
            if (properties == null) return;
            applyTime(world, properties);
            applyWeather(world, properties);
        } catch (Throwable ignored) {
            // Version-specific internals can differ; leave the real game running if unavailable.
        }
    }

    private void applyTime(Object world, Object properties) {
        try {
            if (!timeMode.equals("OFF")) {
                long time = ((Number) call(world, "method_8532")).longValue();
                if (!timeSaved) { savedTime = time; timeSaved = true; }
                long base = Math.floorDiv(time, 24000L) * 24000L;
                long target = timeMode.equals("DAY") ? 1000L : timeMode.equals("SUNSET") ? 12000L : 13000L;
                call(properties, "method_165", long.class, base + target);
            } else if (timeSaved) {
                call(properties, "method_165", long.class, savedTime);
                timeSaved = false;
            }
        } catch (Throwable ignored) { }
    }

    private void applyWeather(Object world, Object properties) {
        try {
            if (!weatherMode.equals("OFF")) {
                if (!weatherSaved) {
                    savedRain = ((Number) call(world, "method_8430", float.class, 1f)).floatValue();
                    savedThunder = ((Number) call(world, "method_8478", float.class, 1f)).floatValue();
                    savedRaining = (Boolean) call(properties, "method_156");
                    weatherSaved = true;
                }
                boolean rain = weatherMode.equals("RAIN");
                call(properties, "method_157", boolean.class, rain);
                call(world, "method_8519", float.class, rain ? 1f : 0f);
                call(world, "method_8496", float.class, 0f);
            } else if (weatherSaved) {
                call(properties, "method_157", boolean.class, savedRaining);
                call(world, "method_8519", float.class, savedRain);
                call(world, "method_8496", float.class, savedThunder);
                weatherSaved = false;
            }
        } catch (Throwable ignored) { }
    }

    private static Object field(Object value, String name) throws Exception {
        for (Class<?> c = value.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(value); }
            catch (NoSuchFieldException ignored) { }
        }
        return null;
    }

    private static Object call(Object value, String name, Object... arguments) throws Exception {
        Class<?>[] types = new Class<?>[arguments.length / 2];
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) { types[i] = (Class<?>) arguments[i * 2]; args[i] = arguments[i * 2 + 1]; }
        Method m = value.getClass().getMethod(name, types);
        m.setAccessible(true);
        return m.invoke(value, args);
    }
}
