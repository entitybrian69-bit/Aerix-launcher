package net.kdt.pojavlaunch.aerix.bridge

import java.io.File
import kotlinx.coroutines.CancellationException

/** Do not reorder a row merely because it was disabled. */
internal object ContentListPolicy {
    val order: Comparator<ContentEntry> = compareBy<ContentEntry> { it.displayTitle.lowercase(java.util.Locale.ROOT) }
        .thenBy { it.name }.thenBy { it.file.absolutePath }

    /** .disabled renames keep identity; coexisting enabled/disabled copies must still be unique. */
    fun rowKeys(entries: List<ContentEntry>): Map<String, String> {
        val bases = entries.groupBy { File(it.file.parentFile, it.name).absolutePath }
        return entries.associate { entry ->
            val base = File(entry.file.parentFile, entry.name).absolutePath
            entry.file.absolutePath to if (bases.getValue(base).size == 1) base else entry.file.absolutePath
        }
    }

    fun toggle(selected: Set<String>, path: String): Set<String> =
        if (path in selected) selected - path else selected + path
}

internal enum class ContentBulkAction { ENABLE, DISABLE, DELETE }
internal data class ContentBatchResult(val succeeded: Set<String>, val failed: Set<String>)

/** Snapshot the targets, serialize operations and report partial failures rather than hiding them. */
internal suspend fun applyContentBatch(
    kind: ContentKind,
    targets: List<ContentEntry>,
    action: ContentBulkAction,
    perform: suspend (ContentEntry, ContentBulkAction) -> Boolean = { entry, operation ->
        when (operation) {
            ContentBulkAction.DELETE -> PojavContentBridge.delete(entry)
            ContentBulkAction.ENABLE -> PojavContentBridge.setEnabled(entry, true)
            ContentBulkAction.DISABLE -> PojavContentBridge.setEnabled(entry, false)
        }
    }
): ContentBatchResult {
    require(action == ContentBulkAction.DELETE || kind.toggleable) { "This content cannot be toggled" }
    val succeeded = linkedSetOf<String>()
    val failed = linkedSetOf<String>()
    for (entry in targets.distinctBy { it.file.absolutePath }) {
        val ok = try { perform(entry, action) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
        (if (ok) succeeded else failed).add(entry.file.absolutePath)
    }
    return ContentBatchResult(succeeded, failed)
}
