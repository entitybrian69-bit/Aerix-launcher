package net.kdt.pojavlaunch.multirt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RuntimeAbiPolicyTest {
    @Test
    public void arm32KeepsThePublishedJava25Runtime() {
        assertTrue(RuntimeAbiPolicy.isRuntimeAvailable("arm", 25));
    }

    @Test
    public void x86RuntimeAvailabilityMatchesTheLauncherPolicy() {
        assertTrue(RuntimeAbiPolicy.isRuntimeAvailable("x86", 8));
        assertTrue(RuntimeAbiPolicy.isRuntimeAvailable("x86", 17));
        assertFalse(RuntimeAbiPolicy.isRuntimeAvailable("x86", 21));
        assertFalse(RuntimeAbiPolicy.isRuntimeAvailable("x86", 25));
    }

    @Test
    public void arm64AndX8664KeepTheCurrentJavaRuntimeChannels() {
        for (String abi : new String[]{"arm64", "x86_64"}) {
            assertTrue(RuntimeAbiPolicy.isRuntimeAvailable(abi, 21));
            assertTrue(RuntimeAbiPolicy.isRuntimeAvailable(abi, 25));
        }
    }

    @Test
    public void rejectsUnknownArchitecturesAndRuntimeMajors() {
        assertFalse(RuntimeAbiPolicy.isRuntimeAvailable("mips", 17));
        assertFalse(RuntimeAbiPolicy.isRuntimeAvailable("arm", 26));
    }
}
