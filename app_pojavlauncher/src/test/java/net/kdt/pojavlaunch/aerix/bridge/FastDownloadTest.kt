package net.kdt.pojavlaunch.aerix.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FastDownloadTest {
    private val megabyte = 1024L * 1024L

    @Test fun partsCoverTheWholeFileWithoutGapsOrOverlap() {
        val size = 115 * megabyte
        val parts = FastDownload.plan(size)
        assertEquals(FastDownload.MAX_CONNECTIONS, parts.size)
        assertEquals(0L, parts.first().start)
        assertEquals(size - 1, parts.last().endInclusive)
        parts.zipWithNext().forEach { (left, right) ->
            assertEquals(left.endInclusive + 1, right.start)
        }
        assertEquals(size, parts.sumOf { it.length })
    }

    @Test fun aLargeFileIsSplitOverEveryConnection() {
        assertEquals(6, FastDownload.plan(100 * megabyte).size)
    }

    @Test fun smallFilesAreNotSplit() {
        assertEquals(1, FastDownload.plan(3 * megabyte).size)
        assertEquals(1, FastDownload.plan(1).size)
    }

    @Test fun theSplitGrowsWithTheFile() {
        assertEquals(2, FastDownload.plan(10 * megabyte).size)
        assertEquals(3, FastDownload.plan(13 * megabyte).size)
    }

    @Test fun anOddSizeIsSpreadOverTheParts() {
        val size = 13 * megabyte + 1
        val parts = FastDownload.plan(size)
        assertEquals(3, parts.size)
        assertEquals(size, parts.sumOf { it.length })
        // The remainder goes to the first parts, one byte each, never past the end of the file.
        assertEquals(parts[0].length, parts[1].length)
        assertEquals(parts[0].length, parts[2].length + 1)
        assertEquals(size - 1, parts.last().endInclusive)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nothingToDownloadIsRejected() {
        FastDownload.plan(0)
    }

    @Test fun totalIsReadFromTheContentRangeHeader() {
        assertEquals(121_248_012L, FastDownload.totalFromContentRange("bytes 0-0/121248012"))
        assertNull(FastDownload.totalFromContentRange("bytes 0-0/*"))
        assertNull(FastDownload.totalFromContentRange(null))
        assertNull(FastDownload.totalFromContentRange("nonsense"))
    }
}
