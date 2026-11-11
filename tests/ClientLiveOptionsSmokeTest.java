package aerix.client.mod;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

/**
 * Offline checks for the live-option bridge: the two runtime shapes (Mojang-mapped accessors and
 * intermediary fields) plus the safety rules that keep a request from writing a stranger's option.
 */
public final class ClientLiveOptionsSmokeTest {
    /** A SimpleOption-shaped box, as modern clients store each option. */
    public static final class Box {
        private Object value;

        Box(Object initial) {
            value = initial;
        }

        public Object get() { return value; }
        public void set(Object next) { value = next; }
    }

    /** Mojang-mapped Options: a no-argument accessor per option. */
    public static final class NamedOptions {
        public Box gamma = new Box(Double.valueOf(0.5));
        public Box fov = new Box(Double.valueOf(70.0));
        public Box renderDistance = new Box(Integer.valueOf(12));
        public Box simulationDistance = new Box(Integer.valueOf(10));
        public Box entityDistanceScaling = new Box(Double.valueOf(1.0));
        public Box mipmapLevels = new Box(Integer.valueOf(4));
        public Box particles = new Box(Integer.valueOf(1));
        public Box entityShadows = new Box(Boolean.TRUE);

        public Box gamma() { return gamma; }
        public Box fov() { return fov; }
        public Box renderDistance() { return renderDistance; }
        public Box simulationDistance() { return simulationDistance; }
        public Box entityDistanceScaling() { return entityDistanceScaling; }
        public Box mipmapLevels() { return mipmapLevels; }
        public Box particles() { return particles; }
        public Box entityShadows() { return entityShadows; }
    }

    /** Intermediary Options: plain fields with obfuscated names. */
    public static final class LegacyOptions {
        public double field_1840 = 0.5;
        public int field_1870 = 12;
        public double field_1666 = 70.0;
        public double distortion = 1.0;
    }

    public static final class NamedClient {
        public Object options;
        public boolean singleplayer = true;

        public boolean isSingleplayer() { return singleplayer; }
    }

    public static final class LegacyClient {
        public Object field_1690;
        private Object server = new Object();

        /** A live connection is what makes it a server, and only the game can say so. */
        public Object method_1742() { return server; }
    }

    static void check(boolean condition) {
        if (!condition) throw new AssertionError("check failed");
    }

    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("aerix-live-options").toFile();
        File config = new File(dir, "config");
        check(config.mkdir());
        File live = new File(config, "aerix_live.properties");
        File boost = new File(config, "aerix_server_boost.properties");
        File recovery = new File(config, "aerix_live_recovery.properties");

        ClientLiveOptions bridge = new ClientLiveOptions();
        NamedClient client = new NamedClient();
        NamedOptions options = new NamedOptions();
        client.options = options;

        // 1. Fullbright goes through the game's own option object, and only the requested value.
        write(live, "gamma=1.0\nzoomFov=0\n", 1000L);
        bridge.tick(client, dir, Boolean.FALSE);
        check(((Number) options.gamma.get()).doubleValue() == 1.0);

        // 2. The ZOOM pill narrows the field of view and hands the player's value back on release.
        write(live, "gamma=1.0\nzoomFov=45.0\n", 2000L);
        bridge.tick(client, dir, Boolean.FALSE);
        check(((Number) options.fov.get()).doubleValue() == 45.0);
        check(bridge.isZoomActive());
        check(recovery.isFile());
        Properties recorded = new Properties();
        try (java.io.InputStream recordedStream = Files.newInputStream(recovery.toPath())) {
            recorded.load(recordedStream);
        }
        check(Double.parseDouble(recorded.getProperty("fov")) == 70.0);
        write(live, "gamma=1.0\nzoomFov=0\n", 3000L);
        bridge.tick(client, dir, Boolean.FALSE);
        check(((Number) options.fov.get()).doubleValue() == 70.0);
        check(!bridge.isZoomActive());
        check(!recovery.isFile());

        // 3. A number the option cannot take is refused instead of written.
        write(live, "zoomFov=9999\n", 4000L);
        bridge.tick(client, dir, Boolean.FALSE);
        check(((Number) options.fov.get()).doubleValue() == 70.0);

        // 4. Server Boost only touches the game inside a multiplayer world, and cleans up after.
        long stamp = 5000L;
        write(boost, "enabled=1\nrenderDistance=6\nsimulationDistance=4\n"
                + "entityDistanceScaling=0.5\nmipmapLevels=0\nparticles=0\nentityShadows=false\n", stamp);
        bridge.tick(client, dir, Boolean.FALSE);
        check(((Number) options.renderDistance.get()).intValue() == 12);
        bridge.tick(client, dir, Boolean.TRUE);
        check(((Number) options.renderDistance.get()).intValue() == 6);
        check(((Number) options.mipmapLevels.get()).intValue() == 0);
        check(Boolean.FALSE.equals(options.entityShadows.get()));
        check(bridge.isBoostEngaged());
        bridge.tick(client, dir, Boolean.FALSE);
        check(((Number) options.renderDistance.get()).intValue() == 12);
        check(((Number) options.mipmapLevels.get()).intValue() == 4);
        check(Boolean.TRUE.equals(options.entityShadows.get()));
        check(!bridge.isBoostEngaged());

        // 5. Deleting the request gives the values back even in a world that is still a server.
        bridge.tick(client, dir, Boolean.TRUE);
        check(((Number) options.renderDistance.get()).intValue() == 6);
        check(boost.delete());
        bridge.tick(client, dir, Boolean.TRUE);
        check(((Number) options.renderDistance.get()).intValue() == 12);
        check(!recovery.isFile());

        // 6. Intermediary runtimes: gamma and render distance by alias, FOV by its signature.
        LegacyClient legacy = new LegacyClient();
        LegacyOptions legacyOptions = new LegacyOptions();
        legacy.field_1690 = legacyOptions;
        write(live, "gamma=1.0\nzoomFov=40.0\n", 9000L);
        bridge.tick(legacy, dir, Boolean.TRUE);
        check(legacyOptions.field_1840 == 1.0);
        check(legacyOptions.field_1666 == 40.0);
        check(legacyOptions.distortion == 1.0);
        //    The render-distance alias is the one this repository already relies on elsewhere.
        write(boost, "enabled=1\nrenderDistance=6\n", 12000L);
        bridge.tick(legacy, dir, Boolean.TRUE);
        check(legacyOptions.field_1870 == 6);
        bridge.tick(legacy, dir, Boolean.FALSE);
        check(legacyOptions.field_1870 == 12);
        write(live, "gamma=1.0\nzoomFov=0\n", 10000L);
        bridge.tick(legacy, dir, Boolean.TRUE);
        check(legacyOptions.field_1666 == 70.0);

        // 7. The game decides whether this is a server; a local world must not be boosted.
        check(Boolean.FALSE.equals(ClientLiveOptions.multiplayerState(client)));
        check(Boolean.TRUE.equals(ClientLiveOptions.multiplayerState(new LegacyClient())));
        check(ClientLiveOptions.multiplayerState(new Object()) == null);

        // 8. Nothing is applied when the game has no Options to apply it to.
        NamedClient empty = new NamedClient();
        write(live, "gamma=1.0\nzoomFov=50\n", 11000L);
        bridge.tick(empty, dir, Boolean.TRUE);
        check(bridge.statusLine() != null);

        deleteTree(dir);
        System.out.println("ClientLiveOptions bridge smoke tests passed");
    }

    private static void write(File file, String text, long mtime) throws Exception {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        check(file.setLastModified(mtime));
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (int i = 0; i < children.length; i++) deleteTree(children[i]);
        }
        file.delete();
    }
}
