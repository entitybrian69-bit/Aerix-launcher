package net.kdt.pojavlaunch.aerix.bridge

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ContentListPolicyTest {
    private fun entry(name: String, enabled: Boolean = true, folder: String = "/instance/mods", title: String = name) =
        ContentEntry(File(folder, name + if (enabled) "" else ".disabled"), name, title,
            emptyList(), null, enabled, false, 1, 0)

    @Test fun disablingKeepsRowIdentityAndAlphabeticPosition() {
        val before = listOf(entry("a.jar"), entry("b.jar"), entry("c.jar"))
        val after = listOf(before[0], entry("b.jar", false), before[2]).sortedWith(ContentListPolicy.order)
        assertEquals(before.map { it.name }, after.map { it.name })
        assertEquals(ContentListPolicy.rowKeys(before)[before[1].file.absolutePath],
            ContentListPolicy.rowKeys(after)[after[1].file.absolutePath])
    }
    @Test fun neighboringRowsKeepKeysAfterDeletion() {
        val before = (0..100).map { entry("item-$it.jar") }
        val after = before.filterIndexed { index, _ -> index !in setOf(2, 20, 41) }
        val keys = ContentListPolicy.rowKeys(before)
        val updated = ContentListPolicy.rowKeys(after)
        after.forEach { assertEquals(keys[it.file.absolutePath], updated[it.file.absolutePath]) }
        assertEquals(keys[before[50].file.absolutePath], updated[before[50].file.absolutePath])
    }
    @Test fun coexistingEnabledAndDisabledCopiesNeverHaveDuplicateKeys() {
        val entries = listOf(entry("same.jar"), entry("same.jar", false))
        assertEquals(2, ContentListPolicy.rowKeys(entries).values.toSet().size)
    }
    @Test fun identicalNamesInDifferentInstancesStayDistinct() {
        val entries = listOf(entry("same.jar", folder = "/one/mods"), entry("same.jar", folder = "/two/mods"))
        assertEquals(2, ContentListPolicy.rowKeys(entries).values.toSet().size)
    }
    @Test fun rowSelectionTogglesWithoutLosingOtherSelections() {
        assertEquals(setOf("a", "b"), ContentListPolicy.toggle(setOf("a"), "b"))
        assertEquals(setOf("b"), ContentListPolicy.toggle(setOf("a", "b"), "a"))
    }
    @Test fun batchTouchesOnlyTheSelectedSnapshotAndOnlyOnce() = runBlocking {
        val a = entry("a.jar"); val b = entry("b.jar"); val touched = mutableListOf<String>()
        val result = applyContentBatch(ContentKind.MODS, listOf(a, b, a), ContentBulkAction.DISABLE) { e, action ->
            assertEquals(ContentBulkAction.DISABLE, action); touched.add(e.name); true
        }
        assertEquals(listOf("a.jar", "b.jar"), touched)
        assertEquals(setOf(a.file.absolutePath, b.file.absolutePath), result.succeeded)
        assertTrue(result.failed.isEmpty())
    }
    @Test fun everyCategorySupportsBulkDelete() = runBlocking {
        for (kind in ContentKind.entries) {
            val result = applyContentBatch(kind, listOf(entry("one"), entry("two")), ContentBulkAction.DELETE) { _, _ -> true }
            assertEquals(2, result.succeeded.size)
        }
    }
    @Test fun partialFailureIsReportedAndDoesNotSkipRemainingTargets() = runBlocking {
        val all = listOf(entry("a"), entry("b"), entry("c"))
        val result = applyContentBatch(ContentKind.MODS, all, ContentBulkAction.ENABLE) { e, _ ->
            if (e.name == "b") throw java.io.IOException("Read-only file")
            true
        }
        assertEquals(setOf(all[0].file.absolutePath, all[2].file.absolutePath), result.succeeded)
        assertEquals(setOf(all[1].file.absolutePath), result.failed)
    }
    @Test fun cancellationIsNotSwallowedAsAFileFailure() {
        var cancelled = false
        try { runBlocking { applyContentBatch(ContentKind.MODS, listOf(entry("a")), ContentBulkAction.DELETE) { _, _ -> throw CancellationException() } } }
        catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }
    @Test fun worldsAndPacksAreNotSilentlyRenamedToDisableThem() = runBlocking {
        for (kind in ContentKind.entries.filterNot { it.toggleable }) {
            var rejected = false
            try { applyContentBatch(kind, listOf(entry("world")), ContentBulkAction.DISABLE) { _, _ -> fail("Must not touch files"); true } }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
    }
}
