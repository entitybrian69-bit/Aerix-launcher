package net.kdt.pojavlaunch.aerix.bridge

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class RetiredRendererCleanupTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun onlyRetiredSelectionsAreInvalidated() {
        assertTrue(RetiredRendererCleanup.isRetiredTag("plugin:net.kdt.pojavlaunch.renderer.rustgl"))
        assertTrue(RetiredRendererCleanup.isRetiredTag("plugin:dev.rustrenderer.plugin"))
        assertFalse(RetiredRendererCleanup.isRetiredTag(null))
        assertFalse(RetiredRendererCleanup.isRetiredTag("mobileglues"))
        assertFalse(RetiredRendererCleanup.isRetiredTag("plugin:example.other"))
    }

    @Test fun recursiveCleanupDoesNotTouchSiblingDataOrFollowSymlinks() {
        val root = temporary.newFolder()
        val cache = File(root, "retired").apply { mkdir() }
        File(cache, "version/abi").mkdirs()
        File(cache, "version/abi/payload.so").writeText("old private payload")
        val world = File(root, "world").apply { mkdir() }
        val saved = File(world, "level.dat").apply { writeText("preserve") }
        Files.createSymbolicLink(File(cache, "outside").toPath(), world.toPath())
        assertTrue(RetiredRendererCleanup.deleteTree(cache))
        assertFalse(cache.exists())
        assertEquals("preserve", saved.readText())
        assertTrue(RetiredRendererCleanup.deleteTree(cache))
    }
}
