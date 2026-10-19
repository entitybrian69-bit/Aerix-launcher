package net.kdt.pojavlaunch.aerix.renderer

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class RendererBundleStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val data = "synthetic unit-test bytes, not an executable renderer".toByteArray()
    private fun release(version: String = "0.3.0") = BundleRelease(version, "a".repeat(40), "arm64-v8a",
        listOf(BundleFile("librust_gl.so", data.size.toLong(), sha256(data))))
    private fun install(store: RendererBundleStore, release: BundleRelease) = store.install(release) { ByteArrayInputStream(data) }

    @Test fun checkedInstallAndActivation() {
        val store = RendererBundleStore(temp.newFolder())
        val release = release(); val dir = install(store, release)
        assertArrayEquals(data, File(dir, "librust_gl.so").readBytes())
        assertNull(store.active(listOf(release)))
        store.activate(release)
        assertEquals(release, store.active(listOf(release)))
    }
    @Test fun checksumMismatchDoesNotActivateAndRemovesStaging() {
        val root = temp.newFolder(); val store = RendererBundleStore(root)
        assertThrows(IllegalArgumentException::class.java) {
            store.install(release()) { ByteArrayInputStream(ByteArray(data.size)) }
        }
        assertNull(store.active(listOf(release())))
        assertFalse(root.list()!!.any { it.startsWith("stage-") })
    }
    @Test fun oversizedInputRejected() {
        val store = RendererBundleStore(temp.newFolder())
        assertThrows(IllegalArgumentException::class.java) { store.install(release()) { ByteArrayInputStream(data + data) } }
    }
    @Test fun shortInputRejected() {
        val store = RendererBundleStore(temp.newFolder())
        assertThrows(IllegalArgumentException::class.java) { store.install(release()) { ByteArrayInputStream(byteArrayOf(1)) } }
    }
    @Test fun traversalAndDuplicateNamesRejected() {
        val normal = release()
        for (files in listOf(listOf(normal.files[0].copy(name = "../librust_gl.so")), normal.files + normal.files)) {
            assertThrows(IllegalArgumentException::class.java) { normal.copy(files = files).validate() }
        }
    }
    @Test fun invalidAbiRevisionVersionAndSizesRejected() {
        val release = release()
        for (bad in listOf(release.copy(abi = "../../arm"), release.copy(version = "../0"), release.copy(revision = "main"),
            release.copy(files = listOf(release.files[0].copy(bytes = Long.MAX_VALUE))))) {
            assertThrows(IllegalArgumentException::class.java) { bad.validate() }
        }
    }
    @Test fun failedInitRollsBackAndQuarantines() {
        val store = RendererBundleStore(temp.newFolder()); val a = release(); val b = release("0.3.1")
        install(store, a); store.activate(a); install(store, b); store.activate(b)
        store.beginInit(b).finish(false)
        store.recover(listOf(b, a))
        assertEquals(a, store.active(listOf(b, a)))
        assertTrue(store.rejected(b))
    }
    @Test fun healthyInitKeepsNewRelease() {
        val store = RendererBundleStore(temp.newFolder()); val release = release()
        install(store, release); store.activate(release); store.beginInit(release).finish(true)
        store.recover(listOf(release)); assertFalse(store.rejected(release))
        assertEquals(release, store.active(listOf(release)))
    }
    @Test fun runningInitNotMistakenForCrash() {
        val store = RendererBundleStore(temp.newFolder()); val release = release()
        install(store, release); store.activate(release)
        val attempt = store.beginInit(release)
        store.recover(listOf(release)); assertFalse(store.rejected(release))
        attempt.finish(true)
    }
    @Test fun interruptedInitIsQuarantined() {
        val root = temp.newFolder(); val store = RendererBundleStore(root); val release = release()
        install(store, release); store.activate(release)
        File(root, "pending").writeText(release.id)
        store.recover(listOf(release))
        assertTrue(store.rejected(release)); assertNull(store.active(listOf(release)))
    }
    @Test fun unapprovedRollbackCannotBeSelected() {
        val store = RendererBundleStore(temp.newFolder()); val a = release(); val b = release("0.3.1")
        install(store, a); store.activate(a); install(store, b); store.activate(b)
        store.beginInit(b).finish(false); store.recover(listOf(b))
        assertNull(store.active(listOf(b)))
    }
    @Test fun extraFilesAndTamperingInvalidateGeneration() {
        val store = RendererBundleStore(temp.newFolder()); val release = release(); val dir = install(store, release)
        File(dir, "injected.so").writeText("bad"); assertNull(store.verify(release))
        File(dir, "injected.so").delete(); val lib = File(dir, "librust_gl.so")
        lib.setWritable(true); lib.writeBytes(ByteArray(data.size)); assertNull(store.verify(release))
        assertThrows(IllegalArgumentException::class.java) { install(store, release) }
    }
    @Test fun symlinkedGenerationRejected() {
        val root = temp.newFolder(); val store = RendererBundleStore(root); val release = release()
        Files.createSymbolicLink(File(root, release.id).toPath(), temp.newFolder().toPath())
        assertNull(store.verify(release))
        assertThrows(IllegalArgumentException::class.java) { install(store, release) }
    }
    @Test fun idChangesWhenPayloadChanges() {
        val a = release(); val b = a.copy(files = listOf(a.files[0].copy(sha256 = "b".repeat(64))))
        assertNotEquals(a.id, b.id)
    }
}
