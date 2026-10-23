/*
 * Aerix Launcher bridge for PojavLauncher.
 * Fast file download: one HTTP range request per connection, several at once, each writing its
 * own slice of the file. Used for launcher updates, where a single stream was slow and stalled.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.bridge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/** Every connection the current download has open, so cancelling can close all of them at once. */
internal class DownloadConnections {
    private val live = Collections.synchronizedSet(HashSet<HttpURLConnection>())

    fun attach(connection: HttpURLConnection) {
        live.add(connection)
    }

    fun detach(connection: HttpURLConnection) {
        live.remove(connection)
    }

    /** Closes every open connection; the reading coroutines then fail and unwind. */
    fun disconnectAll() {
        val open = synchronized(live) { live.toList() }
        open.forEach { runCatching { it.disconnect() } }
        live.clear()
    }
}

/** The checksum of a finished download, and how many bytes were written. */
internal class DownloadResult(val bytes: Long, val sha256: String)

/**
 * Downloads a file over several connections at once.
 *
 * A single stream spends most of its time waiting: the phone's link stalls, the CDN throttles one
 * connection, a retransmission holds everything behind it. GitHub serves release assets with byte
 * ranges, so the file is cut into equal parts and each part is fetched on its own connection. The
 * parts are written straight into their own offset of the file, so nothing is held in memory and
 * the file is complete the moment the last part lands.
 *
 * A server that does not honour range requests still works: the download falls back to a single
 * buffered stream.
 */
internal object FastDownload {
    /** Connections used at once. Six fills a phone's link without starving it. */
    const val MAX_CONNECTIONS = 6

    /** Files smaller than this are not worth splitting. */
    const val MIN_PART_BYTES = 4L * 1024 * 1024L

    private const val BUFFER_BYTES = 256 * 1024
    private const val MAX_ATTEMPTS = 4
    private const val REPORT_INTERVAL_NS = 100_000_000L

    /** One byte range of the file, inclusive at both ends. */
    data class Part(val index: Int, val start: Long, val endInclusive: Long) {
        val length: Long get() = endInclusive - start + 1
    }

    /**
     * Cut [size] into equal parts. Small files stay whole, and the count never exceeds
     * [maxConnections], so a hundred-megabyte APK is fetched over six connections and a two
     * megabyte one over a single connection.
     */
    fun plan(
        size: Long,
        maxConnections: Int = MAX_CONNECTIONS,
        minPartBytes: Long = MIN_PART_BYTES
    ): List<Part> {
        require(size > 0) { "Nothing to download" }
        val safeMin = minPartBytes.coerceAtLeast(1L)
        val count = min(maxConnections.coerceAtLeast(1).toLong(), max(1L, size / safeMin)).toInt()
        val base = size / count
        val remainder = size % count
        var start = 0L
        return (0 until count).map { index ->
            val length = base + if (index < remainder) 1L else 0L
            val part = Part(index = index, start = start, endInclusive = start + length - 1)
            start += length
            part
        }
    }

    /**
     * Downloads [url] into [target] and returns its checksum. [expectedSize] is the size the
     * release page announced; the download never grows past it.
     *
     * [onProgress] is called from background threads, at most ten times a second, with the number
     * of bytes written so far.
     */
    suspend fun download(
        url: String,
        target: File,
        expectedSize: Long,
        userAgent: String,
        connections: DownloadConnections,
        onProgress: (Long) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        require(expectedSize in 1..LauncherUpdatePolicy.MAX_APK_BYTES) { "Invalid update size" }
        val directory = target.parentFile
        if (directory != null && !directory.isDirectory) {
            require(directory.mkdirs() || directory.isDirectory) { "Cannot create the update cache" }
        }
        target.delete()

        // Follow GitHub's redirect to the file host by hand: a request that is redirected can lose
        // its Range header on the way, and then every part would come back as the whole file.
        val direct = resolve(url, userAgent, connections)
        val ranged = probe(direct, userAgent, connections)
        val written = if (ranged != null && ranged == expectedSize) {
            downloadParts(direct, target, expectedSize, userAgent, connections, onProgress)
        } else {
            downloadSingle(direct, target, expectedSize, userAgent, connections, onProgress)
        }
        require(written == expectedSize && target.length() == expectedSize) {
            "The update download is incomplete. Please retry."
        }
        DownloadResult(written, sha256(target))
    }

    /** The URL the redirect chain ends at, so every part asks the file host directly. */
    private fun resolve(url: String, userAgent: String, connections: DownloadConnections): String {
        var current = url
        repeat(6) {
            val connection = open(current, userAgent, range = null, followRedirects = false) ?: return current
            connections.attach(connection)
            try {
                val code = connection.responseCode
                if (code !in 300..399) return current
                val location = connection.getHeaderField("Location") ?: return current
                current = URL(URL(current), location).toString()
            } catch (e: Exception) {
                return current
            } finally {
                connections.detach(connection)
                runCatching { connection.disconnect() }
            }
        }
        return current
    }

    /**
     * Asks for one byte and reads the total out of the answer. Returns the size when the server
     * honours ranges, null when it does not.
     */
    private fun probe(url: String, userAgent: String, connections: DownloadConnections): Long? {
        val connection = open(url, userAgent, range = "bytes=0-0") ?: return null
        connections.attach(connection)
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_PARTIAL) return null
            totalFromContentRange(connection.getHeaderField("Content-Range"))
        } catch (e: Exception) {
            null
        } finally {
            connections.detach(connection)
            runCatching { connection.disconnect() }
        }
    }

    /** Total size out of a "bytes 0-0/12345" header. */
    internal fun totalFromContentRange(header: String?): Long? =
        header?.substringAfter('/', "")?.trim()?.takeIf { it.isNotEmpty() && it != "*" }?.toLongOrNull()

    private suspend fun downloadParts(
        url: String,
        target: File,
        size: Long,
        userAgent: String,
        connections: DownloadConnections,
        onProgress: (Long) -> Unit
    ): Long {
        val parts = plan(size)
        val written = AtomicLong(0L)
        val reporter = ProgressReporter(written, onProgress)
        RandomAccessFile(target, "rw").use { it.setLength(size) }
        try {
            coroutineScope {
                parts.map { part ->
                    async(Dispatchers.IO) {
                        fetchPart(url, target, part, userAgent, connections, written, reporter)
                    }
                }.awaitAll()
            }
        } finally {
            reporter.flush()
        }
        return written.get()
    }

    /** One part, retried from wherever it stopped rather than from the beginning. */
    private suspend fun fetchPart(
        url: String,
        target: File,
        part: Part,
        userAgent: String,
        connections: DownloadConnections,
        written: AtomicLong,
        reporter: ProgressReporter
    ) {
        var position = part.start
        var attempt = 0
        val buffer = ByteArray(BUFFER_BYTES)
        while (position <= part.endInclusive) {
            currentCoroutineContext().ensureActive()
            val connection = open(url, userAgent, range = "bytes=$position-${part.endInclusive}")
                ?: throw IOException("Range requests are not supported by the server")
            connections.attach(connection)
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    throw IOException("The server refused a range request (HTTP ${connection.responseCode})")
                }
                // Each part writes at its own offset, so the parts never touch each other's bytes.
                RandomAccessFile(target, "rw").use { file ->
                    file.seek(position)
                    BufferedInputStream(connection.inputStream, BUFFER_BYTES).use { input ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count == -1) break
                            require(position + count - 1 <= part.endInclusive) { "The server sent more bytes than asked for" }
                            file.write(buffer, 0, count)
                            position += count
                            reporter.add(count)
                            if (position > part.endInclusive) break
                        }
                    }
                }
                // A server that closes early is retried from where it stopped, not from zero.
                if (position <= part.endInclusive) {
                    attempt++
                    if (attempt >= MAX_ATTEMPTS) throw IOException("The update connection closed early")
                    delay(250L * attempt)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                attempt++
                if (attempt >= MAX_ATTEMPTS) throw e
                delay(250L * attempt)
            } finally {
                connections.detach(connection)
                runCatching { connection.disconnect() }
            }
        }
        require(position == part.endInclusive + 1) { "A part of the update is incomplete" }
    }

    /** One connection, for servers that ignore range requests. */
    private suspend fun downloadSingle(
        url: String,
        target: File,
        size: Long,
        userAgent: String,
        connections: DownloadConnections,
        onProgress: (Long) -> Unit
    ): Long {
        val connection = open(url, userAgent, range = null)
            ?: throw IOException("Cannot open the update download")
        connections.attach(connection)
        val written = AtomicLong(0L)
        val reporter = ProgressReporter(written, onProgress)
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Download returned HTTP ${connection.responseCode}"
            }
            val buffer = ByteArray(BUFFER_BYTES)
            target.outputStream().use { output ->
                BufferedInputStream(connection.inputStream, BUFFER_BYTES).use { input ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        require(written.get() + count <= size) { "Update download size does not match the release" }
                        output.write(buffer, 0, count)
                        reporter.add(count.toLong())
                    }
                }
            }
        } finally {
            reporter.flush()
            connections.detach(connection)
            runCatching { connection.disconnect() }
        }
        return written.get()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun open(
        url: String,
        userAgent: String,
        range: String?,
        followRedirects: Boolean = true
    ): HttpURLConnection? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = followRedirects
            useCaches = false
            // The update is a zip: never let a proxy re-compress it, and never ask for gzip.
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", userAgent)
            if (range != null) setRequestProperty("Range", range)
        }
    }.getOrNull()
}

/**
 * Counts bytes and hands the running total to the screen at most ten times a second, so the
 * download never waits on the UI thread.
 */
internal class ProgressReporter(
    private val written: AtomicLong,
    private val onProgress: (Long) -> Unit
) {
    private val lastReport = AtomicLong(0L)

    fun add(count: Long) {
        val total = written.addAndGet(count)
        val now = System.nanoTime()
        val previous = lastReport.get()
        if (now - previous > 100_000_000L && lastReport.compareAndSet(previous, now)) {
            onProgress(total)
        }
    }

    fun flush() {
        onProgress(written.get())
    }
}
