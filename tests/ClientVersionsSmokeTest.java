import net.kdt.pojavlaunch.aerix.client.ClientVersions;
public class ClientVersionsSmokeTest {
    private static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) {
        check(ClientVersions.resolve("fabric-loader-0.19.5-26.3", "fallback").equals("26.3"));
        check(ClientVersions.resolve("fabric-loader-0.18.2-1.21.11", "fallback").equals("1.21.11"));
        check(!ClientVersions.resolve("fabric-loader-0.18.2-1.21.10", "fallback").equals("1.21.1"));
        check(ClientVersions.resolve("unknown", "1.20.1").equals("1.20.1"));
        check(ClientVersions.format("1.20.1") == 15);
        check(ClientVersions.format("1.20.6") == 32);
        check(ClientVersions.format("1.21.3") == 42);
        check(ClientVersions.format("1.21.6") == 63);
        check(ClientVersions.format("1.21.10") == 69);
        check(ClientVersions.format("1.21.11") == 75);
        check(ClientVersions.format("26.1.2") == 84);
        check(ClientVersions.format("26.2") == 88);
        check(ClientVersions.format("26.3") == 97 && ClientVersions.formatMinor("26.3") == 1);
        System.out.println("Client version smoke test passed");
    }
}
