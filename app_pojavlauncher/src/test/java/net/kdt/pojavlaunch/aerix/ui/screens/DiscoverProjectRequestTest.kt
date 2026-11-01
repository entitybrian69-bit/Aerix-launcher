package net.kdt.pojavlaunch.aerix.ui.screens

import net.kdt.pojavlaunch.aerix.bridge.*
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class DiscoverProjectRequestTest {
    @After fun clear() { AerixDownloadRequest.consumeProject(); AerixDownloadRequest.type = null }
    @Test fun cataloguePinsOnlyTheSuppliedFabricProject() {
        val entry = teamReleases.single()
        assertEquals("1749327", entry.projectId)
        assertEquals("aetherium-fabric", entry.slug)
        assertEquals(AssetType.MOD, entry.type)
        assertTrue(entry.warning.contains("OptiFine"))
    }
    @Test fun exactProjectIsSentToDiscoverNotAnExternalDownload() {
        val project = AssetProject(AssetPlatform.CURSEFORGE, AssetType.MOD, "123", "Fixture", "", "", null, 0)
        var destination: LauncherSection? = null
        AerixDownloadRequest.openProject(project) { destination = it }
        assertEquals(LauncherSection.DISCOVER, destination)
        assertSame(project, AerixDownloadRequest.project)
        assertNull(AerixDownloadRequest.type)
        AerixDownloadRequest.consumeProject()
        assertNull(AerixDownloadRequest.project)
    }
    @Test fun categoryRequestReplacesAnOldProjectRequest() {
        AerixDownloadRequest.openProject(AssetProject(AssetPlatform.CURSEFORGE, AssetType.MOD, "123", "", "", "", null, 0)) {}
        AerixDownloadRequest.open(AssetType.RESOURCE_PACK) {}
        assertNull(AerixDownloadRequest.project)
        assertEquals(AssetType.RESOURCE_PACK, AerixDownloadRequest.type)
    }
}
