package net.kdt.pojavlaunch.tasks;

public final class DataMigrationPolicySmokeTest {
    public static void main(String[] args) {
        for (String authority : new String[]{
                "net.kdt.pojavlaunch.scoped.gamefolder",
                "net.kdt.pojavlaunch.scoped.gamefolder.debug",
                "net.kdt.pojavlaunch.pub.scoped.gamefolder",
                "net.al.aerixlauncher.scoped.gamefolder",
                "net.al.aerixlauncher.scoped.gamefolder.debug",
                "net.al.aerixlauncher.pub.scoped.gamefolder"
        }) {
            assertTrue(DataMigrationPolicy.isTrustedSourceAuthority(authority), "trusted authority: " + authority);
        }
        assertFalse(DataMigrationPolicy.isTrustedSourceAuthority(null), "null authority is rejected");
        assertFalse(DataMigrationPolicy.isTrustedSourceAuthority("com.android.externalstorage.documents"),
                "generic external-storage provider is rejected");
        assertFalse(DataMigrationPolicy.isTrustedSourceAuthority("net.kdt.pojavlaunch.evil.provider"),
                "authority-prefix lookalikes are rejected");

        long bytesToCopy = 32L * DataMigrationPolicy.BYTES_PER_MEGABYTE;
        long required = DataMigrationPolicy.requiredFreeBytes(bytesToCopy);
        assertEquals(bytesToCopy + 2L * 1024L * DataMigrationPolicy.BYTES_PER_MEGABYTE,
                required, "reserve includes incoming bytes and two free GiB");
        assertEquals(0, DataMigrationPolicy.missingMegabytes(required, bytesToCopy), "enough free space");
        assertEquals(1, DataMigrationPolicy.missingMegabytes(required - 1, bytesToCopy),
                "round one missing byte up to one MiB");
        assertEquals(2, DataMigrationPolicy.missingMegabytes(required - DataMigrationPolicy.BYTES_PER_MEGABYTE - 1,
                bytesToCopy), "round partial MiB up");
        assertEquals(Long.MAX_VALUE, DataMigrationPolicy.saturatedAdd(Long.MAX_VALUE - 5, 10),
                "large tree sizes saturate instead of wrapping");
        System.out.println("Data migration policy smoke test passed");
    }

    private static void assertTrue(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    private static void assertFalse(boolean condition, String description) {
        assertTrue(!condition, description);
    }

    private static void assertEquals(long expected, long actual, String description) {
        if (expected != actual) {
            throw new AssertionError(description + ": expected " + expected + ", got " + actual);
        }
    }
}
