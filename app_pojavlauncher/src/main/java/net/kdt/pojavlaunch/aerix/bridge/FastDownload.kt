/*
 * Aerix Launcher bridge for PojavLauncher.
 * Fast file download: a queue of byte ranges handed to many connections at once.
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Every connection one download has open, so cancelling closes all of them at once.
 */
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
        val open = synchronized(live) { ArrayList(live) }
        for (connection in open) {
            runCatching { connection.disconnect() }
        }
        live.clear()
    }
}

/** What a finished download produced: the byte count and the file's SHA-256. */
internal class DownloadResult(val bytes: Long, val sha256: String)

/**
 * Downloads one file over many connections at once.
 *
 * A single stream to GitHub's file host is shaped to roughly one megabyte a second no matter how
 * fast the phone's link is, and one stalled connection stops everything behind it. So the file is
 * cut into fixed size chunks held in a queue, and a pool of connections pulls chunks off that
 * queue: whoever finishes first takes the next one, and the pool grows while the measured speed per
 * connection is still low. Nothing is buffered in memory - each chunk is written straight into its
 * own offset of the file - and a chunk that drops resumes from the byte it reached.
 *
 * A server that ignores range requests still works: the download falls back to a single stream.
 */
internal object FastDownload {
    /** Connections a download may grow to. */
    const val MAX_CONNECTIONS = 16

    /** Connections a large download starts with, before the speed is known. */
    const val INITIAL_CONNECTIONS = 6

    /** Range size. Big enough that connection setup is noise, small enough to keep the queue busy. */
    const val MAX_CHUNK_BYTES = 8L * 1024 * 1024
    const val MIN_CHUNK_BYTES = 2L * 1024 * 1024

    /** Below this per-connection speed, another connection is worth opening. */
    private const val PER_CONNECTION_FLOOR = 700_000.0
    private const val BUFFER_BYTES = 256 * 1024
    private const val MAX_ATTEMPTS = 5
    private const val SAMPLE_READ_TIMEOUT_MS = 8_000

    /** A download that takes no bytes at all for this long is reported instead of waited on. */
    private const val STALL_TIMEOUT_MS = 45_000L
    private const val REPORT_INTERVAL_NS = 100_000_000L

    /** The range size used for a file of [size]. */
    fun chunkBytesFor(size: Long): Long =
        (size / (MAX_CONNECTIONS * 2L)).coerceIn(MIN_CHUNK_BYTES, MAX_CHUNK_BYTES)

    /** How many ranges a file of [size] is cut into. */
    fun chunkCount(size: Long): Int {
        require(size > 0) { "Nothing to download" }
        val chunk = chunkBytesFor(size)
        return ceil(size.toDouble() / chunk).toInt()
    }

    /** First byte of range [index]. */
    fun chunkStart(size: Long, index: Int): Long = index * chunkBytesFor(size)

    /** Last byte of range [index], inclusive. */
    fun chunkEnd(size: Long, index: Int): Long =
        min(size - 1, chunkStart(size, index) + chunkBytesFor(size) - 1)

    /**
     * Downloads [url] into [target] and returns its checksum.
     *
     * [expectedSize] is the size the release announced; the download never grows past it.
     * [onProgress] is called from background threads at most ten times a second with the number
     * of bytes written so far.
     */
    suspend fun download(
        url: String,
        target: File,
        expectedSize: Long,
        userAgent: String,
        connections: DownloadConnections,
        maxConnections: Int = MAX_CONNECTIONS,
        onProgress: (Long) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        require(expectedSize > 0 && expectedSize <= LauncherUpdatePolicy.MAX_APK_BYTES) {
            "Invalid update size"
        }
        val directory = target.parentFile
        if (directory != null && !directory.isDirectory) {
            require(directory.mkdirs() || directory.isDirectory) { "Cannot create the update cache" }
        }
        target.delete()

        // Follow GitHub's redirect down to the file host first. A request that is redirected can
        // lose its Range header on the way, and then every chunk would come back as the whole file.
        val direct = resolve(url, userAgent, connections)
        val total = probe(direct, userAgent, connections)
        val written = if (total != null && total == expectedSize) {
            downloadChunks(direct, target, expectedSize, userAgent, connections, maxConnections, onProgress)
        } else {
            downloadSingle(direct, target, expectedSize, userAgent, connections, onProgress)
        }
        require(written == expectedSize && target.length() == expectedSize) {
            "The update download is incomplete. Please retry."
        }
        DownloadResult(written, sha256(target))
    }

    /**
     * How many bytes per second one connection ([parallel] = 1) or a pool of them reaches, measured
     * on a slice of the file. Used by the debug speed test to show which side is the limit.
     *
     * It is capped by [timeoutMs] and reads with a short timeout, retrying twice at most: a shaped
     * or throttled connection must produce a number in seconds, never leave the screen waiting.
     * Returns null when nothing could be read inside the cap.
     */
    suspend fun measureRate(
        url: String,
        userAgent: String,
        connections: DownloadConnections,
        parallel: Int,
        sampleBytes: Long = 4L * 1024 * 1024,
        timeoutMs: Long = 20_000,
        onBytes: (Long) -> Unit = {}
    ): Double? = withTimeoutOrNull(timeoutMs) {
        withContext(Dispatchers.IO) {
            val direct = resolve(url, userAgent, connections)
            val total = probe(direct, userAgent, connections) ?: return@withContext null
            val lanes = max(1, parallel)
            val perLane = max(1L, min(sampleBytes / lanes, total))
            val started = System.nanoTime()
            val written = AtomicLong(0L)
            val reporter = ProgressReporter(written) { bytes -> onBytes(bytes) }
            coroutineScope {
                repeat(lanes) { lane ->
                    launch(Dispatchers.IO) {
                        val start = min(total - 1, lane * (total / lanes))
                        val end = min(total - 1, start + perLane - 1)
                        if (start <= end) {
                            // A sample is throwaway: two attempts and a short read timeout are
                            // enough to learn the speed, and a dead lane must not hold the rest.
                            try {
                                readRange(
                                    direct, start, end, userAgent, connections, null, written, reporter,
                                    attempts = 2, readTimeoutMs = SAMPLE_READ_TIMEOUT_MS
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // One dead lane must not cancel the sample the others are producing.
                            }
                        }
                    }
                }
            }
            if (written.get() <= 0L) return@withContext null
            val seconds = (System.nanoTime() - started) / 1_000_000_000.0
            if (seconds <= 0.0) null else written.get() / seconds
        }
    }

    /** The URL the redirect chain ends at, so every chunk asks the file host directly. */
    private fun resolve(url: String, userAgent: String, connections: DownloadConnections): String {
        var current = url
        repeat(6) {
            val connection = open(current, userAgent, null, false) ?: return current
            connections.attach(connection)
            try {
                val code = connection.responseCode
                if (code < 300 || code > 399) return current
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
     * Asks for a single byte and reads the file's total size out of the answer. Returns the size
     * when the server honours ranges, null when it does not.
     */
    private fun probe(url: String, userAgent: String, connections: DownloadConnections): Long? {
        val connection = open(url, userAgent, "bytes=0-0", true) ?: return null
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

    /** The total size out of a `bytes 0-0/12345` header. */
    fun totalFromContentRange(header: String?): Long? {
        if (header == null) return null
        val total = header.substringAfter('/', "").trim()
        if (total.isEmpty() || total == "*") return null
        return total.toLongOrNull()
    }

    private suspend fun downloadChunks(
        url: String,
        target: File,
        size: Long,
        userAgent: String,
        connections: DownloadConnections,
        maxConnections: Int,
        onProgress: (Long) -> Unit
    ): Long {
        val ranges = chunkCount(size)
        val next = AtomicInteger(0)
        val written = AtomicLong(0L)
        val reporter = ProgressReporter(written, onProgress)
        val spawned = AtomicInteger(0)
        val ceiling = max(1, maxConnections)
        RandomAccessFile(target, "rw").use { it.setLength(size) }
        try {
            coroutineScope {
                val spawn = {
                    if (spawned.getAndIncrement() < ceiling) {
                        launch(Dispatchers.IO) {
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val index = next.getAndIncrement()
                                if (index >= ranges) break
                                readRange(
                                    url, chunkStart(size, index), chunkEnd(size, index),
                                    userAgent, connections, target, written, reporter
                                )
                            }
                        }
                    }
                }
                // Start with a small pool, then let the watcher decide whether more helps.
                repeat(min(INITIAL_CONNECTIONS, ceiling)) { spawn() }
                launch {
                    var lastBytes = 0L
                    var lastAt = System.nanoTime()
                    var lastProgressAt = lastAt
                    // Ends when every range has been taken and every byte has landed; the scope
                    // would otherwise wait for a watcher that never stops watching.
                    while (isActive && (next.get() < ranges || written.get() < size)) {
                        delay(1_000)
                        val now = System.nanoTime()
                        val bytes = written.get()
                        if (bytes > lastBytes) lastProgressAt = now
                        // Waiting forever looks exactly like a frozen app: say so instead, and let
                        // the caller offer a retry.
                        else if (now - lastProgressAt > STALL_TIMEOUT_MS * 1_000_000) {
                            throw IOException("The update stalled with no data for 45 seconds. Please retry.")
                        }
                        val seconds = (now - lastAt) / 1_000_000_000.0
                        val lanes = spawned.get().coerceAtLeast(1)
                        val perConnection = if (seconds > 0) (bytes - lastBytes) / seconds / lanes else 0.0
                        lastBytes = bytes
                        lastAt = now
                        // A shaped connection plateaus well below the floor; add lanes until the
                        // per-connection speed rises, and stop at the ceiling either way.
                        if (perConnection < PER_CONNECTION_FLOOR && spawned.get() < ceiling) {
                            spawn()
                            if (perConnection < PER_CONNECTION_FLOOR / 2 && spawned.get() < ceiling) spawn()
                        }
                    }
                }
            }
        } finally {
            reporter.flush()
        }
        return written.get()
    }

    /**
     * Reads one byte range into its place in the file, resuming from wherever it stopped rather
     * than from the beginning. [reporter] may be null, for speed sampling.
     */
    private suspend fun readRange(
        url: String,
        start: Long,
        endInclusive: Long,
        userAgent: String,
        connections: DownloadConnections,
        target: File?,
        written: AtomicLong,
        reporter: ProgressReporter?,
        attempts: Int = MAX_ATTEMPTS,
        readTimeoutMs: Int = 30_000
    ) {
        var position = start
        var attempt = 0
        val buffer = ByteArray(BUFFER_BYTES)
        while (position <= endInclusive) {
            currentCoroutineContext().ensureActive()
            val connection = open(url, userAgent, "bytes=$position-$endInclusive", true, readTimeoutMs)
                ?: throw IOException("Cannot open the update download")
            connections.attach(connection)
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    throw IOException("The server refused a range request (HTTP ${connection.responseCode})")
                }
                // Writing at the wrong offset would corrupt the file, so the answer has to begin
                // exactly where the request asked.
                val answeredStart = contentRangeStart(connection.getHeaderField("Content-Range"))
                if (answeredStart != null && answeredStart != position) {
                    throw IOException("The server answered a different byte range than was asked for")
                }
                if (target != null) {
                    RandomAccessFile(target, "rw").use { file ->
                        file.seek(position)
                        position = copyRange(connection, file, position, endInclusive, buffer, written, reporter)
                    }
                } else {
                    // Sampling: read and discard, only the byte count matters.
                    position = copyRange(connection, null, position, endInclusive, buffer, written, reporter)
                }
                if (position <= endInclusive) {
                    attempt++
                    if (attempt >= attempts) throw IOException("The update connection kept closing early")
                    delay(250L * attempt)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!currentCoroutineContext().isActive) throw e
                attempt++
                if (attempt >= attempts) throw e
                delay(250L * attempt)
            } finally {
                connections.detach(connection)
                runCatching { connection.disconnect() }
            }
        }
        require(position == endInclusive + 1) { "A part of the update is incomplete" }
    }

    private fun copyRange(
        connection: HttpURLConnection,
        file: RandomAccessFile?,
        from: Long,
        endInclusive: Long,
        buffer: ByteArray,
        written: AtomicLong,
        reporter: ProgressReporter?
    ): Long {
        var position = from
        BufferedInputStream(connection.inputStream, BUFFER_BYTES).use { input ->
            while (position <= endInclusive) {
                // Stopping here is what lets a timed out sample or a cancelled download walk away
                // from a connection that is still dribbling bytes.
                if (Thread.currentThread().isInterrupted) break
                val count = input.read(buffer)
                if (count == -1) break
                require(position + count - 1 <= endInclusive) {
                    "The server sent more bytes than were asked for"
                }
                file?.write(buffer, 0, count)
                position += count
                written.addAndGet(count.toLong())
                reporter?.add(count.toLong())
            }
        }
        return position
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
        val connection = open(url, userAgent, null, true)
            ?: throw IOException("Cannot open the update download")
        connections.attach(connection)
        val written = AtomicLong(0L)
        val reporter = ProgressReporter(written, onProgress)
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Download returned HTTP ${connection.responseCode}"
            }
            val buffer = ByteArray(BUFFER_BYTES)
            BufferedInputStream(connection.inputStream, BUFFER_BYTES).use { input ->
                target.outputStream().use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        require(written.get() + count <= size) { "Update download size does not match the release" }
                        output.write(buffer, 0, count)
                        written.addAndGet(count.toLong())
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

    private fun contentRangeStart(header: String?): Long? {
        if (header == null) return null
        return header.substringAfter(' ', "").substringBefore('-').trim().toLongOrNull()
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
        followRedirects: Boolean,
        readTimeoutMs: Int = 30_000
    ): HttpURLConnection? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = readTimeoutMs
            instanceFollowRedirects = followRedirects
            useCaches = false
            // The update is a zip that is already compressed: do not let anything re-encode it.
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", userAgent)
            if (range != null) setRequestProperty("Range", range)
        }
    }.getOrNull()
}

/**
 * Counts bytes and hands the running total to the screen at most ten times a second, so the
 * download never waits on the UI.
 */
internal class ProgressReporter(
    private val written: AtomicLong,
    private val onProgress: (Long) -> Unit
) {
    private val lastReport = AtomicLong(0L)

    fun add(count: Long) {
        val total = written.get()
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
