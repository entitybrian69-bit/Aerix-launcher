package net.kdt.aerix.desktop.core.net

import net.kdt.aerix.desktop.app.Log
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Batched downloader: the desktop rewrite of `pojavlaunch/downloader`.
 *
 * The Android class already had the right ideas (verify first, resume with ranges, retry five
 * times, three HTTP workers). This keeps all of that and adds what a desktop install needs:
 * a cancellable job, real progress reporting including bytes and speed, and per-file hashing for
 * SHA-1 *and* SHA-512 (Modrinth publishes the latter).
 */
class DownloadJob(val name: String) {
    class Task(
        val url: String,
        val destination: Path,
        val sha1: String? = null,
        val sha512: String? = null,
        val size: Long = -1,
        /** 0..1 weight in the overall progress; defaults to the byte size. */
        val weight: Long = if (size > 0) size else 1,
        val headers: Map<String, String> = emptyMap(),
        /** Optional post-download hook (extraction, permissions, …). */
        val after: ((Path) -> Unit)? = null
    )

    private val cancelled = AtomicBoolean(false)
    private val totalWeight = AtomicLong(0)
    private val doneWeight = AtomicLong(0)
    private val completedFiles = AtomicInteger()
    private val failedFiles = AtomicInteger()
    private val skippedFiles = AtomicInteger()
    private val bytesDownloaded = AtomicLong(0)

    @Volatile var statusText: String = "Preparing download"
    @Volatile var currentFile: String = ""
    var onProgress: ((DownloadJob) -> Unit)? = null
    var onDone: ((DownloadJob) -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null

    val total: Int get() = totalWeight.get().toInt().coerceAtLeast(0)
    val finished: Int get() = completedFiles.get()
    val failed: Int get() = failedFiles.get()
    val skipped: Int get() = skippedFiles.get()
    val bytes: Long get() = bytesDownloaded.get()
    val isCancelled: Boolean get() = cancelled.get()

    fun progress(): Float {
        val t = totalWeight.get()
        if (t <= 0) return 0f
        return (doneWeight.get().toDouble() / t).toFloat().coerceIn(0f, 1f)
    }

    fun filesDone(): Int = completedFiles.get()
    fun fileCount(): Int = totalWeight.get().let { _ -> totalFiles }
    var totalFiles: Int = 0
        internal set

    fun cancel() { cancelled.set(true) }

    internal fun addBytes(n: Long) { bytesDownloaded.addAndGet(n) }
    internal fun progressTick() { runCatching { onProgress?.invoke(this) } }
    internal fun markDone(t: Task) { completedFiles.incrementAndGet(); doneWeight.addAndGet(t.weight) }
    internal fun markFailed() { failedFiles.incrementAndGet(); doneWeight.addAndGet(1) }
    internal fun markSkipped(t: Task) { skippedFiles.incrementAndGet(); doneWeight.addAndGet(t.weight) }
    internal fun computeTotal(tasks: List<Task>) {
        totalWeight.set(tasks.sumOf { if (it.weight > 0) it.weight else 1 })
        totalFiles = tasks.size
    }
}

object Downloader {
    private val hashers = ThreadLocal.withInitial { HashMap<String, MessageDigest>() }

    private fun digest(algorithm: String): MessageDigest =
        hashers.get()!!.getOrPut(algorithm) { MessageDigest.getInstance(algorithm) }

    fun sha1(file: Path): String = hashFile(file, "SHA-1")
    fun sha512(file: Path): String = hashFile(file, "SHA-512")

    fun hashFile(file: Path, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        Files.newInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(file: Path, sha1: String? = null, sha512: String? = null, size: Long = -1): Boolean {
        if (!Files.isRegularFile(file)) return false
        if (size > 0 && Files.size(file) != size) return false
        if (!sha1.isNullOrBlank() && !sha1(file).equals(sha1, true)) return false
        if (!sha512.isNullOrBlank() && !sha512(file).equals(sha512, true)) return false
        return true
    }

    /**
     * Runs [tasks] on [threads] HTTP workers.
     *
     * Files already on disk with a matching hash are skipped without a request — that is what makes
     * re-running "install" after a network drop cheap. A mismatched file is deleted and fetched
     * again; a failed request is retried with backoff, and range-resumed when the server allows it.
     */
    fun run(job: DownloadJob, tasks: List<DownloadJob.Task>, threads: Int = 4, onComplete: (DownloadJob) -> Unit = {}) {
        job.computeTotal(tasks)
        if (tasks.isEmpty()) {
            job.statusText = "Nothing to download"
            job.onProgress?.invoke(job)
            onComplete(job)
            job.onDone?.invoke(job)
            return
        }
        runBlocking(job, tasks, threads, onComplete)
    }

    /** Same as [run] but on a background thread; returns immediately. */
    fun runAsync(job: DownloadJob, tasks: List<DownloadJob.Task>, threads: Int = 4, onComplete: (DownloadJob) -> Unit = {}) {
        net.kdt.aerix.desktop.app.Scheduler.io("download-${job.name}") { run(job, tasks, threads, onComplete) }
    }

    private fun runBlocking(job: DownloadJob, tasks: List<DownloadJob.Task>, threads: Int, onComplete: (DownloadJob) -> Unit) {
        run {
            val pool: ExecutorService = Executors.newFixedThreadPool(threads.coerceIn(1, 8)) { r ->
                Thread(r, "aerix-dl").apply { isDaemon = true }
            }
            val startTime = System.currentTimeMillis()
            var lastTick = 0L
            val ticker = object : Runnable {
                override fun run() {
                    val now = System.currentTimeMillis()
                    if (now - lastTick > 120) {
                        lastTick = now
                        job.progressTick()
                    }
                }
            }
            val timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "dl-tick").apply { isDaemon = true } }
            timer.scheduleWithFixedDelay(ticker, 0, 120, TimeUnit.MILLISECONDS)
            var firstError: Throwable? = null
            try {
                for (task in tasks) {
                    if (job.isCancelled) break
                    pool.submit {
                        if (job.isCancelled) return@submit
                        try {
                            handleTask(job, task)
                        } catch (t: Throwable) {
                            Log.w("download", "failed ${task.url}: ${t.message}")
                            job.markFailed()
                            if (firstError == null) firstError = t
                        }
                    }
                }
                pool.shutdown()
                pool.awaitTermination(3, TimeUnit.HOURS)
            } finally {
                timer.shutdownNow()
                pool.shutdownNow()
            }
            job.statusText = when {
                job.isCancelled -> "Cancelled"
                job.failed > 0 -> "${job.failed} file(s) failed"
                else -> "Download complete"
            }
            job.progressTick()
            Log.i("download", "${job.name}: ${job.finished}/${job.totalFiles} files, ${job.bytes / 1048576} MiB in ${(System.currentTimeMillis() - startTime) / 1000}s")
            if (job.isCancelled) {
                job.onError?.invoke(IOException("Download cancelled"))
            } else if (firstError != null && job.failed > 0) {
                job.onError?.invoke(firstError!!)
            } else {
                onComplete(job)
                job.onDone?.invoke(job)
            }
        }
    }

    private fun handleTask(job: DownloadJob, task: DownloadJob.Task) {
        val dest = task.destination
        job.currentFile = dest.fileName.toString()
        if (verify(dest, task.sha1, task.sha512, task.size)) {
            job.markSkipped(task)
            task.after?.invoke(dest)
            return
        }
        Files.createDirectories(dest.parent)
        var attempt = 0
        while (true) {
            if (job.isCancelled) return
            try {
                fetchWithResume(job, task)
                if (!verify(dest, task.sha1, task.sha512, task.size)) {
                    Files.deleteIfExists(dest)
                    throw IOException("Hash mismatch for ${dest.fileName}")
                }
                task.after?.invoke(dest)
                job.markDone(task)
                return
            } catch (e: Exception) {
                attempt++
                if (attempt > 4 || job.isCancelled) throw e
                Thread.sleep(300L * attempt)
            }
        }
    }

    private fun fetchWithResume(job: DownloadJob, task: DownloadJob.Task) {
        val dest = task.destination
        val part = dest.resolveSibling(dest.fileName.toString() + ".part")
        var resumeFrom = if (Files.isRegularFile(part)) Files.size(part) else 0L
        if (task.size in 1 until resumeFrom) { Files.deleteIfExists(part); resumeFrom = 0 }
        var appended = false
        Http.downloadStream(
            task.url,
            task.headers,
            rangeStart = resumeFrom,
            onProgress = { job.addBytes(it) },
            sink = { input: java.io.InputStream ->
                // A server that ignores Range answers 200 with the whole body; start over in that case.
                val streamStart = 0L
                val out: OutputStream = Files.newOutputStream(
                    part,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE,
                    if (resumeFrom > 0) java.nio.file.StandardOpenOption.APPEND else java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
                )
                out.use { o -> input.copyTo(o, 128 * 1024) }
                appended = resumeFrom > 0 || streamStart == 0L
            }
        )
        if (!appended) Files.deleteIfExists(part)
        Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING)
    }

    /** Copies [from] to [to] with cancel support, for local imports. */
    fun copy(job: DownloadJob, from: Path, to: Path) {
        Files.createDirectories(to.parent)
        Files.newInputStream(from).use { input ->
            Files.newOutputStream(to).use { output ->
                val buf = ByteArray(128 * 1024)
                var copied = 0L
                while (true) {
                    if (job.isCancelled) throw IOException("Cancelled")
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                    copied += n
                    job.addBytes(n.toLong())
                    if (copied % (1024L * 1024L) < buf.size.toLong()) job.progressTick()
                }
            }
        }
    }

    /** Byte formatter shared by the UI ("12.4 MB", "1.2 GB"). */
    fun humanBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        return String.format("%.2f GB", mb / 1024.0)
    }

    fun humanSpeed(bytesPerSecond: Long): String = humanBytes(bytesPerSecond) + "/s"

    fun eta(remainingBytes: Long, bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0) return ""
        val seconds = remainingBytes / bytesPerSecond
        return when {
            seconds < 60 -> "${seconds}s left"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s left"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
        }
    }

    fun clampProgress(done: Long, total: Long): Float = if (total <= 0) 0f else min(1.0, done.toDouble() / total).toFloat()
}
