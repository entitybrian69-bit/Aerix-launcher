import net.kdt.pojavlaunch.aerix.client.ClientLogoMigration;

public class ClientLogoMigrationSmokeTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        int[] legacy = {
            0xff8defe0, 0xff93eee1, 0xff0b1420, 0xffe0b86d, 0xffe0b86d,
            0xffd7e2ea, 0xff0e151e, 0xff15202e, 0xffd7e2ea, 0xff0d1521
        };
        check(ClientLogoMigration.matchesLegacy(128, 128, legacy));
        int[] compressed = legacy.clone();
        compressed[0] = 0xff86e8d8;
        check(ClientLogoMigration.matchesLegacy(128, 128, compressed));
        compressed[3] = 0xff000000;
        check(!ClientLogoMigration.matchesLegacy(128, 128, compressed));
        check(!ClientLogoMigration.matchesLegacy(256, 256, legacy));
        check(!ClientLogoMigration.matchesLegacy(128, 128, null));
        System.out.println("Client logo migration smoke test passed");
    }
}
