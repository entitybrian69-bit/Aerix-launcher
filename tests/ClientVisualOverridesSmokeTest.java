package aerix.client.mod;

import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

public final class ClientVisualOverridesSmokeTest {
    public static final class Props {
        long time = 28000; boolean rain = false;
        public void method_165(long v) { time = v; }
        public boolean method_156() { return rain; }
        public void method_157(boolean v) { rain = v; }
    }
    public static final class World {
        public Props field_9232 = new Props(); float rain = 0.2f, thunder = 0.3f;
        public long method_8532() { return field_9232.time; }
        public float method_8430(float delta) { return rain; }
        public float method_8478(float delta) { return thunder; }
        public void method_8519(float v) { rain = v; }
        public void method_8496(float v) { thunder = v; }
    }
    public static final class Client { public World field_1687 = new World(); }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("aerix-visual-test").toFile();
        File config = new File(root, "config"); check(config.mkdir());
        File settings = new File(config, "aerix_visual.properties");
        Files.write(settings.toPath(), "time=DAY\nweather=RAIN\n".getBytes(StandardCharsets.UTF_8));
        Client client = new Client();
        ClientVisualOverrides overrides = new ClientVisualOverrides();
        overrides.tick(client, root);
        check(client.field_1687.field_9232.time == 25000 && client.field_1687.field_9232.rain && client.field_1687.rain == 1f);
        long next = settings.lastModified() + 2000;
        Files.write(settings.toPath(), "time=OFF\nweather=OFF\n".getBytes(StandardCharsets.UTF_8));
        check(settings.setLastModified(next));
        for (int i = 0; i < 8; i++) overrides.tick(client, root);
        check(client.field_1687.field_9232.time == 28000 && !client.field_1687.field_9232.rain && client.field_1687.rain == 0.2f && client.field_1687.thunder == 0.3f);
        settings.delete(); config.delete(); root.delete();
        System.out.println("Client visual overrides smoke tests passed");
    }
}
