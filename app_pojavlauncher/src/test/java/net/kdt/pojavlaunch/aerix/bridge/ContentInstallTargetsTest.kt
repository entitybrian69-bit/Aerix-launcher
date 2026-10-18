package net.kdt.pojavlaunch.aerix.bridge

import com.google.gson.Gson
import net.kdt.pojavlaunch.instances.Instance
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ContentInstallTargetsTest {
    private fun instance(name: String): Instance = Gson().fromJson(
        """{"name":"$name","versionId":"fabric-loader-0.16.10-1.16.5"}""", Instance::class.java)

    @Test fun fullInstancesBecomeSelectableDestinations() {
        val targets = contentInstallTargets(listOf(instance("Fabric")), File("fabric")) {
            GameDownloadTarget(it.name, "1.16.5", "fabric", File("fabric"))
        }
        assertEquals(1, targets.size)
        assertEquals("Fabric", targets.single().name)
        assertEquals("1.16.5", targets.single().mcVersion)
        assertEquals("fabric", targets.single().loader)
        assertEquals(File("fabric"), targets.single().gameDirectory)
        assertTrue(targets.single().detected)
    }

    @Test fun selectedDestinationComesFirstAndOthersRemainAvailable() {
        val targets = contentInstallTargets(listOf(instance("Other"), instance("Selected")), File("Selected")) {
            GameDownloadTarget(it.name, "1.16.5", "fabric", File(it.name))
        }
        assertEquals(listOf("Selected", "Other"), targets.map { it.name })
        assertFalse(targets.last().detected)
    }

    @Test fun brokenDestinationDoesNotHideHealthyInstances() {
        val targets = contentInstallTargets(listOf(instance("Broken"), instance("Healthy")), null) {
            if (it.name == "Broken") error("Unreadable profile")
            GameDownloadTarget(it.name, "1.16.5", "fabric", File(it.name))
        }
        assertEquals("Healthy", targets.single().name)
    }

    @Test fun missingDirectoriesAreNotOfferedAsInstallDestinations() {
        assertTrue(contentInstallTargets(listOf(instance("Missing")), null) {
            GameDownloadTarget(it.name, "1.16.5", "fabric", null)
        }.isEmpty())
        assertTrue(contentInstallTargets(emptyList(), null).isEmpty())
    }
}
