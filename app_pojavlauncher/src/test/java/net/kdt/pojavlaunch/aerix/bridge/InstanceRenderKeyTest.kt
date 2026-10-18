package net.kdt.pojavlaunch.aerix.bridge

import org.junit.Assert.*
import org.junit.Test

class InstanceRenderKeyTest {
    @Test fun repeatedReportedKeysCannotCollideAtTheRenderingBoundary() {
        val ids = List(20) { "Spunky Boost_fabric-loader-0.16.10-1.20.1" }
        val keys = ids.mapIndexed { index, id -> instanceRenderKey(index, id) }
        assertEquals(ids.size, keys.toSet().size)
        assertTrue(keys.all { it.startsWith("instance-slot:") })
    }

    @Test fun separatorsAndBlankIdsCannotCauseCrossSlotCollisions() {
        val ids = listOf("", "", "1:", "0:1:", "same", "same")
        assertEquals(ids.size, ids.mapIndexed(::instanceRenderKey).toSet().size)
    }

    @Test fun sameSnapshotGetsRepeatableKeysAndReplacedSlotsChangeIdentity() {
        assertEquals(instanceRenderKey(0, "a"), instanceRenderKey(0, "a"))
        assertNotEquals(instanceRenderKey(0, "a"), instanceRenderKey(0, "b"))
        assertNotEquals(instanceRenderKey(0, "a"), instanceRenderKey(1, "a"))
    }
}
