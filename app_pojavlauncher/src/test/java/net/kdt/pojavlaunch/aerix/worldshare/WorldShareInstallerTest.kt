package net.kdt.pojavlaunch.aerix.worldshare

import net.kdt.pojavlaunch.aerix.bridge.GameDownloadTarget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = android.app.Application::class)
class WorldShareInstallerTest {
    @get:Rule val temp = TemporaryFolder()

    private fun mod(file: File, name: String, content: String) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray())
            zip.closeEntry()
        }
    }

    @Test fun detectsActualModIdentityNotJustFileName() {
        val dir = temp.newFolder()
        val mods = File(dir, "mods").apply { mkdir() }
        val target = GameDownloadTarget("Test", "1.20.1", "fabric", dir)
        mod(File(mods, "e4all-lookalike.jar"), "fabric.mod.json", """{"id":"another-mod"}""")
        assertFalse(WorldShareInstaller.installed(target))
        mod(File(mods, "different-name.jar"), "fabric.mod.json", """{"id":"e4all"}""")
        assertTrue(WorldShareInstaller.installed(target))
    }

    @Test fun detectsForgeAndNeoForgeMetadata() {
        val dir = temp.newFolder()
        val mods = File(dir, "mods").apply { mkdir() }
        val target = GameDownloadTarget("Test", "1.20.6", "forge", dir)
        mod(File(mods, "forge.jar"), "META-INF/mods.toml", """modId="e4all"""")
        assertTrue(WorldShareInstaller.installed(target))
    }
}
