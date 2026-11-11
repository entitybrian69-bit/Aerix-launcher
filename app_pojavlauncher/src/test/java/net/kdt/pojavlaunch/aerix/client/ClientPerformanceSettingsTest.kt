package net.kdt.pojavlaunch.aerix.client

import org.junit.Assert.assertEquals
import org.junit.Test

class ClientPerformanceSettingsTest {
    @Test fun `override preserves unrelated settings and malformed lines`() {
        val result = ClientPerformanceSettings.mergeOptions(
            listOf("resourcePacks:[\"vanilla\",\"file/custom.zip\"]", "renderDistance:20", "unknown line", "soundCategory_music:0.3"),
            mapOf("renderDistance" to "6", "entityShadows" to "false")
        )
        assertEquals(listOf("resourcePacks:[\"vanilla\",\"file/custom.zip\"]", "renderDistance:6", "unknown line", "soundCategory_music:0.3", "entityShadows:false"), result)
    }

    @Test fun `pvp profile reduces render and entity workload for busy servers`() {
        try {
            ClientPerformanceSettings.profile("PvP")
            val options = ClientPerformanceSettings.overrides
            assertEquals("6", options["renderDistance"])
            assertEquals("5", options["simulationDistance"])
            assertEquals("0.5", options["entityDistanceScaling"])
        } finally {
            ClientPerformanceSettings.restoreImported(emptyMap())
        }
    }
}
