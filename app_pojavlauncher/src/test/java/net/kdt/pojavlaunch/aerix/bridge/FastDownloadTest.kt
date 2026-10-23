package net.kdt.pojavlaunch.aerix.bridge

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedOutputStream
import java.io.File
import java.net.ServerSocket
import java.security.MessageDigest
import kotlin.concurrent.thread

class FastDownloadTest {
    @Test fun chunksCoverTheFileWithoutGapsOrOverlap() {
        for (size in longArrayOf(131_090_806L, 400_000_000L, 9_000_000L, 2_500_000L)) {
            val count = FastDownload.chunkCount(size)
            assertEquals(0L, FastDownload.chunkStart(size, 0))
            assertEquals(size - 1, FastDownload.chunkEnd(size, count - 1))
            for (index in 0 until count - 1) {
                assertEquals(FastDownload.chunkEnd(size, index) + 1, FastDownload.chunkStart(size, index + 1))
            }
            assertEquals(size, (0 until count).sumOf { FastDownload.chunkEnd(size, it) - FastDownload.chunkStart(size, it) + 1 })
            // Enough ranges that every connection keeps busy, but no more than is worth setting up.
            assertTrue(count >= FastDownload.MAX_CONNECTIONS)
        }
    }

    @Test fun chunkSizeStaysInsideItsBounds() {
        assertEquals(FastDownload.MIN_CHUNK_BYTES, FastDownload.chunkBytesFor(1_000_000L))
        assertEquals(FastDownload.MAX_CHUNK_BYTES, FastDownload.chunkBytesFor(4_000_000_000L))
    }

    @Test fun contentRangeTotalIsParsed() {
        assertEquals(12345L, FastDownload.totalFromContentRange("bytes 0-0/12345"))
        assertNull(FastDownload.totalFromContentRange("bytes 0-0/*"))
        assertNull(FastDownload.totalFromContentRange("bytes 0-0/"))
        assertNull(FastDownload.totalFromContentRange(null))
    }

    @Test fun downloadsOverRangesAndChecksumsTheResult() = runBlocking {
        val payload = ByteArray(700_000) { (it % 251).toByte() }
        val server = TestFileServer(payload, honoursRanges = true)
        val target = File.createTempFile("fastdownload", ".bin")
        try {
            val result = FastDownload.download(
                url = "http://127.0.0.1:${server.port}/asset",
                target = target,
                expectedSize = payload.size.toLong(),
                userAgent = "FastDownloadTest",
                connections = DownloadConnections(),
                onProgress = {}
            )
            assertEquals(payload.size.toLong(), result.bytes)
            assertArrayEquals(payload, target.readBytes())
            assertEquals(sha256(payload), result.sha256)
        } finally {
            server.close()
            target.delete()
        }
    }

    @Test fun fallsBackToASingleStreamWhenRangesAreIgnored() = runBlocking {
        val payload = ByteArray(300_000) { (it % 97).toByte() }
        val server = TestFileServer(payload, honoursRanges = false)
        val target = File.createTempFile("fastdownload", ".bin")
        try {
            val result = FastDownload.download(
                url = "http://127.0.0.1:${server.port}/asset",
                target = target,
                expectedSize = payload.size.toLong(),
                userAgent = "FastDownloadTest",
                connections = DownloadConnections(),
                onProgress = {}
            )
            assertEquals(payload.size.toLong(), result.bytes)
            assertArrayEquals(payload, target.readBytes())
        } finally {
            server.close()
            target.delete()
        }
    }

    @Test fun aShortAnswerIsReportedAsAnIncompleteDownload() = runBlocking {
        val payload = ByteArray(200_000) { 1 }
        val server = TestFileServer(payload, honoursRanges = false)
        val target = File.createTempFile("fastdownload", ".bin")
        try {
            var failed = false
            try {
                FastDownload.download(
                    url = "http://127.0.0.1:${server.port}/asset",
                    target = target,
                    // The release claims more bytes than the server sends: it must not pass.
                    expectedSize = payload.size.toLong() + 1024,
                    userAgent = "FastDownloadTest",
                    connections = DownloadConnections(),
                    onProgress = {}
                )
            } catch (e: Exception) {
                failed = true
            }
            assertTrue(failed)
        } finally {
            server.close()
            target.delete()
        }
    }

    /**
     * A one-file HTTP server that answers byte ranges, so the downloader is tested against real
     * sockets instead of a mock. It serves [payload] at every path and closes each connection.
     */
    private class TestFileServer(private val payload: ByteArray, private val honoursRanges: Boolean) {
        private val socket = ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))
        val port: Int get() = socket.localPort
        @Volatile private var running = true

        init {
            thread(isDaemon = true, name = "FastDownloadTest-server") {
                while (running) {
                    val client = try { socket.accept() } catch (e: Exception) { break }
                    try {
                        client.use { serve(it) }
                    } catch (e: Exception) {
                        // A part that was cancelled closes its end; the test does not care.
                    }
                }
            }
        }

        private fun serve(client: java.net.Socket) {
            val input = client.getInputStream().bufferedReader()
            var range: String? = null
            var line = input.readLine() ?: return
            while (line.isNotEmpty()) {
                if (line.startsWith("Range:", ignoreCase = true)) range = line.substringAfter(':').trim()
                line = input.readLine() ?: break
            }
            val output = BufferedOutputStream(client.getOutputStream())
            val spec = if (honoursRanges && range != null && range.startsWith("bytes=")) {
                range.removePrefix("bytes=").split("-")
            } else null
            if (spec != null) {
                val start = spec[0].toLong()
                val end = spec.getOrNull(1)?.takeIf { it.isNotEmpty() }?.toLong() ?: (payload.size - 1).toLong()
                val length = (end - start + 1).toInt()
                output.write(
                    ("HTTP/1.1 206 Partial Content\r\n" +
                        "Content-Range: bytes $start-$end/${payload.size}\r\n" +
                        "Content-Length: $length\r\n" +
                        "Accept-Ranges: bytes\r\n" +
                        "Connection: close\r\n\r\n").toByteArray()
                )
                output.write(payload, start.toInt(), length)
            } else {
                output.write(
                    ("HTTP/1.1 200 OK\r\n" +
                        "Content-Length: ${payload.size}\r\n" +
                        "Connection: close\r\n\r\n").toByteArray()
                )
                output.write(payload)
            }
            output.flush()
        }

        fun close() {
            running = false
            runCatching { socket.close() }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
