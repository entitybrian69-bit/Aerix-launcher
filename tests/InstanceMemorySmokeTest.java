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
        System.out.println("Instance memory smoke tests passed");
    }
}
