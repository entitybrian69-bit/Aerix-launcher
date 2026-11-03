package net.kdt.pojavlaunch.aerix.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class GameDownloadTargetVersionTest {
    @Test fun preservesModernVersionAndSnapshotInsteadOfLoaderVersion() {
        assertEquals("26.3", GameDownloadTarget.minecraftVersionIn("fabric-loader-0.19.5-26.3"))
        assertEquals("26.4-snapshot-6", GameDownloadTarget.minecraftVersionIn("fabric-loader-0.19.5-26.4-snapshot-6"))
        assertEquals("26.4-snapshot-5", GameDownloadTarget.minecraftVersionIn("26.4-snapshot-5"))
        assertEquals("1.21.11-pre-1", GameDownloadTarget.minecraftVersionIn("fabric-loader-0.19.5-1.21.11-pre-1"))
        assertEquals("1.20.1", GameDownloadTarget.minecraftVersionIn("fabric-loader-0.19.5-1.20.1"))
    }
}
