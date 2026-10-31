package aerix.client.mod;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;

/** Adjusts only the live client option; restores the original value on disable. */
final class ClientAdaptiveDistance {
    private int poll;
    private long stamp = Long.MIN_VALUE;
    private int requested = -1;
    private Object savedOptions;
    private Integer original;

    void tick(Object mc, File runDir) {
        try {
            if (poll++ % 8 == 0) {
                File file = new File(runDir, "config/aerix_adaptive_distance.properties");
                long current = file.isFile() ? file.lastModified() : -1L;
                if (current != stamp) {
                    Properties p = new Properties();
                    if (file.isFile()) try (FileInputStream stream = new FileInputStream(file)) { p.load(stream); }
                    int value;
                    try { value = Integer.parseInt(p.getProperty("distance", "-1")); }
                    catch (NumberFormatException ignored) { value = -1; }
                    requested = value >= 2 && value <= 64 ? value : -1;
                    stamp = current;
                }
            }
            Object options = field(mc.getClass(), "field_1690").get(mc);
            if (options != savedOptions && savedOptions != null && original != null) {
                writeDistance(savedOptions, original.intValue());
                original = null;
            }
            if (requested < 0) {
                if (savedOptions != null && original != null) writeDistance(savedOptions, original.intValue());
                savedOptions = null;
                original = null;
                return;
            }
            if (options == null) return;
            if (original == null) { original = readDistance(options); savedOptions = options; }
            if (original != null && readDistance(options) != requested) writeDistance(options, requested);
        } catch (Throwable ignored) { }
    }

    private static int readDistance(Object options) throws Exception {
        Object value = field(options.getClass(), "field_1870").get(options);
        if (!(value instanceof Number)) value = method(value.getClass(), "method_41753").invoke(value);
        return ((Number) value).intValue();
    }
    private static void writeDistance(Object options, int value) throws Exception {
        Field f = field(options.getClass(), "field_1870");
        if (f.getType() == int.class) f.setInt(options, value);
        else {
            Object option = f.get(options);
            method(option.getClass(), "method_41748", Object.class).invoke(option, Integer.valueOf(value));
        }
    }
    private static Field field(Class<?> c, String name) throws Exception {
        for (; c != null; c = c.getSuperclass()) try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
        catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    private static Method method(Class<?> c, String name, Class<?>... parameters) throws Exception {
        Method m = c.getMethod(name, parameters); m.setAccessible(true); return m;
    }
}
