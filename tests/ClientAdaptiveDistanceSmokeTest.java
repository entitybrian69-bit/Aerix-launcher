package aerix.client.mod;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class ClientAdaptiveDistanceSmokeTest {
    public static final class ModernOption {
        Object value = Integer.valueOf(10);
        public Object method_41753() { return value; }
        public void method_41748(Object v) { value = v; }
    }
    public static final class OldOptions { public int field_1870 = 12; }
    public static final class NewOptions { public ModernOption field_1870 = new ModernOption(); }
    public static final class Client { public Object field_1690; }
    public static final class NamedOption {
        Object value = Integer.valueOf(11);
        public Object get() { return value; }
        public void set(Object v) { value = v; }
    }
    public static final class NamedOptions {
        public NamedOption distance = new NamedOption();
        public NamedOption renderDistance() { return distance; }
    }
    public static final class NamedClient { public NamedOptions options = new NamedOptions(); }
    static void check(boolean v) { if (!v) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("aerix-distance-test").toFile();
        File config = new File(dir, "config"); check(config.mkdir());
        File settings = new File(config, "aerix_adaptive_distance.properties");
        Files.write(settings.toPath(), "distance=6\n".getBytes(StandardCharsets.UTF_8));
        Client c = new Client(); OldOptions old = new OldOptions(); c.field_1690 = old;
        ClientAdaptiveDistance bridge = new ClientAdaptiveDistance();
        bridge.tick(c, dir); check(old.field_1870 == 6);
        NewOptions modern = new NewOptions(); c.field_1690 = modern;
        bridge.tick(c, dir); check(old.field_1870 == 12 && modern.field_1870.value.equals(6));
        long next = settings.lastModified() + 2000;
        Files.write(settings.toPath(), "distance=-1\n".getBytes(StandardCharsets.UTF_8)); check(settings.setLastModified(next));
        for (int i = 0; i < 8; i++) bridge.tick(c, dir);
        check(modern.field_1870.value.equals(10));
        Files.write(settings.toPath(), "distance=7\n".getBytes(StandardCharsets.UTF_8)); check(settings.setLastModified(next + 2000));
        NamedClient named = new NamedClient();
        for (int i = 0; i < 8; i++) bridge.tick(named, dir);
        check(named.options.distance.value.equals(7));
        Files.write(settings.toPath(), "distance=-1\n".getBytes(StandardCharsets.UTF_8)); check(settings.setLastModified(next + 4000));
        for (int i = 0; i < 8; i++) bridge.tick(named, dir);
        check(named.options.distance.value.equals(11));
        settings.delete(); config.delete(); dir.delete();
        System.out.println("Client adaptive bridge smoke tests passed");
    }
}
