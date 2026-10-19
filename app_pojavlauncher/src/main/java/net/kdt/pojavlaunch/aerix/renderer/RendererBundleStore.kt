package net.kdt.pojavlaunch.aerix.renderer

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

internal data class BundleFile(val name: String, val bytes: Long, val sha256: String)
internal data class BundleRelease(
    val version: String, val revision: String, val abi: String, val files: List<BundleFile>
) {
    fun validate() {
        require(version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
        require(revision.matches(Regex("[a-f0-9]{40}")))
        require(abi in setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"))
        require(files.size in 1..32 && files.map { it.name }.distinct().size == files.size)
        require(files.any { it.name == "librust_gl.so" })
        require(files.all {
            (it.name.matches(Regex("lib[A-Za-z0-9_+.-]+\\.so")) || it.name == "NOTICE.txt") &&
                it.bytes in 1..(128L * 1024 * 1024) && it.sha256.matches(Regex("[a-f0-9]{64}"))
        })
        require(files.sumOf { it.bytes } <= 256L * 1024 * 1024)
    }
    val id: String get() {
        validate()
        val description = listOf(version, revision, abi).joinToString("\n") + "\n" +
            files.sortedBy { it.name }.joinToString("\n") { "${it.name}:${it.bytes}:${it.sha256}" }
        return "$version-$abi-${sha256(description.toByteArray()).take(24)}"
    }
}

internal fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Only APK-authenticated manifests may be supplied. No network or instance-directory inputs. */
internal class RendererBundleStore(root: File) {
    private val root = root.canonicalFile.also { require(it.mkdirs() || it.isDirectory) }

    private fun child(name: String): File {
        require(name.matches(Regex("[A-Za-z0-9_.+-]+")) && name != "." && name != "..")
        val file = File(root, name)
        require(!Files.isSymbolicLink(file.toPath()) && file.canonicalFile.parentFile == root)
        return file
    }
    private fun pointer(name: String): String? = child(name).takeIf { it.isFile }?.readText()?.trim()
    private fun atomic(name: String, value: String) {
        val temp = child("$name.tmp")
        FileOutputStream(temp).use { it.write(value.toByteArray()); it.fd.sync() }
        Files.move(temp.toPath(), child(name).toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    }
    private fun <T> locked(action: () -> T): T = synchronized(lock) {
        java.io.RandomAccessFile(child("lock"), "rw").channel.use { channel ->
            channel.lock().use { action() }
        }
    }
    private fun valid(release: BundleRelease): File? = runCatching {
        val directory = child(release.id)
        require(directory.isDirectory)
        require(directory.list()?.toSet() == release.files.map { it.name }.toSet())
        release.files.forEach { entry ->
            val file = File(directory, entry.name)
            require(!Files.isSymbolicLink(file.toPath()) && file.canonicalFile.parentFile == directory)
            require(file.isFile && file.length() == entry.bytes)
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
            }
            require(hash.digest().joinToString("") { "%02x".format(it) } == entry.sha256)
        }
        directory
    }.getOrNull()

    fun verify(release: BundleRelease): File? = locked { valid(release) }

    fun install(release: BundleRelease, open: (String) -> InputStream): File = locked {
        release.validate()
        valid(release)?.let { return@locked it }
        val destination = child(release.id)
        require(!destination.exists()) { "Existing generation failed verification; refusing to replace loaded code" }
        val staging = Files.createTempDirectory(root.toPath(), "stage-").toFile()
        try {
            release.files.forEach { entry ->
                val file = File(staging, entry.name)
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                open(entry.name).use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            size += n
                            require(size <= entry.bytes) { "Oversized bundle file" }
                            digest.update(buffer, 0, n); output.write(buffer, 0, n)
                        }
                        output.fd.sync()
                    }
                }
                require(size == entry.bytes && digest.digest().joinToString("") { "%02x".format(it) } == entry.sha256) {
                    "Bundle checksum mismatch: ${entry.name}"
                }
                require(file.setReadOnly())
            }
            Files.move(staging.toPath(), destination.toPath(), ATOMIC_MOVE)
            requireNotNull(valid(release))
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    /** Keep previous code; activation never overwrites a generation used by another process. */
    fun activate(release: BundleRelease) = locked {
        requireNotNull(valid(release)) { "Unverified release" }
        val old = pointer("active")
        if (old != release.id) {
            if (old != null) atomic("previous", old)
            atomic("active", release.id)
        }
    }

    fun active(approved: List<BundleRelease>): BundleRelease? = locked {
        approved.firstOrNull { it.id == pointer("active") && valid(it) != null && !child("bad-${it.id}").exists() }
    }

    class InitAttempt(private val complete: (Boolean) -> Unit) {
        fun finish(success: Boolean) = complete(success)
    }

    fun beginInit(release: BundleRelease): InitAttempt = locked {
        requireNotNull(valid(release))
        val channel = java.io.RandomAccessFile(child("init-lock"), "rw").channel
        try {
            val lease = requireNotNull(channel.tryLock()) { "Renderer initialization is already running" }
            try { atomic("pending", release.id) } catch (error: Exception) { lease.release(); throw error }
            var finished = false
            InitAttempt { success ->
                locked {
                    if (!finished) {
                        try {
                            if (pointer("pending") == release.id) {
                                if (!success) atomic("bad-${release.id}", "init failed")
                                child("pending").delete()
                            }
                        } finally { finished = true; lease.release(); channel.close() }
                    }
                }
            }
        } catch (error: Exception) { channel.close(); throw error }
    }
    fun rejected(release: BundleRelease): Boolean = locked { child("bad-${release.id}").exists() }

    /** A killed native init is quarantined on the next scan, without loading it again. */
    fun recover(approved: List<BundleRelease>) = locked {
        java.io.RandomAccessFile(child("init-lock"), "rw").channel.use { channel ->
            val lease = try { channel.tryLock() } catch (_: java.nio.channels.OverlappingFileLockException) { null }
            if (lease != null) lease.use {
                pointer("pending")?.let { pending ->
                    approved.firstOrNull { it.id == pending }?.let { atomic("bad-${it.id}", "init interrupted") }
                    child("pending").delete()
                }
            }
        }
        val active = pointer("active")
        if (approved.none { it.id == active && valid(it) != null && !child("bad-${it.id}").exists() }) {
            val previous = pointer("previous")
            val rollback = approved.firstOrNull { it.id == previous && valid(it) != null && !child("bad-${it.id}").exists() }
            if (rollback != null) atomic("active", rollback.id) else child("active").delete()
        }
    }

    companion object { private val lock = Any() }
}
