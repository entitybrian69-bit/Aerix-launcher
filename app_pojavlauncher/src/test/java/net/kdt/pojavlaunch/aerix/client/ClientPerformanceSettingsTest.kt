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
}
