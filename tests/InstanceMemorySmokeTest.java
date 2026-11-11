import net.kdt.pojavlaunch.utils.jre.InstanceMemory;
import java.util.Arrays;

/** Runnable with javac alone; does not depend on the unrelated Android unit-test source set. */
public final class InstanceMemorySmokeTest {
    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " but got " + actual);
    }
    public static void main(String[] args) {
        equal(2048, InstanceMemory.effectiveMb(null, 2048));
        equal(2048, InstanceMemory.effectiveMb("-XX:+UseG1GC", 2048));
        String first = InstanceMemory.withOverride("-XX:+UseG1GC -DmyKey=value", 840);
        String second = InstanceMemory.withOverride("-XX:+UseG1GC", 1536);
        equal(840, InstanceMemory.effectiveMb(first, 2048));
        equal(1536, InstanceMemory.effectiveMb(second, 2048));
        equal("-Xmx840M -XX:+UseG1GC -DmyKey=value", first);
        equal("-XX:+UseG1GC -DmyKey=value", InstanceMemory.withOverride(first, -1));
        equal(3072, InstanceMemory.effectiveMb("-Xmx3g -XX:+UseG1GC", 2048));
        equal(1536, InstanceMemory.effectiveMb("-Xmx1G -Xmx1536M", 2048));
        equal(2048, InstanceMemory.effectiveMb("-Xmx999999999999999999999999G", 2048));
        equal(2048, InstanceMemory.effectiveMb("-Dexample=-Xmx1G", 2048));
        equal(Arrays.asList("-Xmx840M", "-Xms840M"), Arrays.asList(InstanceMemory.heapOptions(840)));

        // The ceiling. Pojav's own is "device RAM minus a gigabyte", and that is what three phones
        // dragged their slider to - 6063, 4778 and 3979 MB - and then ran the game at 4 FPS, because
        // the launcher, the GL driver and Android live in the same memory. The safe ceiling keeps room
        // for them and stops where extra heap stops helping.
        equal(1844, InstanceMemory.safeMb(3072, false));
        equal(2458, InstanceMemory.safeMb(4096, false));
        equal(3072, InstanceMemory.safeMb(6144, false));
        equal(3584, InstanceMemory.safeMb(8192, false));
        equal(4096, InstanceMemory.safeMb(12288, false));
        // A reported 6063 MB ceiling came from a 7087 MB device; the cap is now 3072 MB there,
        // which is what Minecraft actually uses on a phone.
        equal(3072, InstanceMemory.safeMb(7087, false));
        // Never below the floor, never more than the device minus what the renderer needs.
        int previous = 0;
        for (long device : new long[] { 1024, 1600, 2048, 3064, 6144, 8192, 12288, 16384 }) {
            int safe = InstanceMemory.safeMb(device, false);
            if (safe < 256) throw new AssertionError("below the floor at " + device);
            if (safe > device) throw new AssertionError("promised more than the device has at " + device);
            if (device >= 2048 && safe > device - 1024)
                throw new AssertionError("left the system no room at " + device);
            if (safe < previous) throw new AssertionError("went backwards at " + device);
            previous = safe;
        }
        // A 32-bit device keeps Pojav's stricter limit of 1 GB.
        equal(1024, InstanceMemory.safeMb(4096, true));
        equal(800, InstanceMemory.safeMb(1600, false));
        System.out.println("Instance memory smoke tests passed");
    }
}
