package net.kdt.pojavlaunch.aerix.tools

import android.app.ActivityManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.instances.Instance
import net.kdt.pojavlaunch.instances.Instances
import net.kdt.pojavlaunch.services.GameService
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String,
    val instanceId: String,
    val notes: String = ""
)

internal data class WorldCard(
    val instanceId: String, val instanceName: String, val directory: File,
    val name: String, val version: String?, val lastPlayed: Long, val bytes: Long,
    val icon: ImageBitmap?, val lastBackup: File?
)

internal object AerixToolsBridge {
    @Suppress("DEPRECATION")
    fun gameRunning(context: Context): Boolean = try {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == GameService::class.java.name }
    } catch (_: Exception) { true }

    private fun profilesFile(context: Context) = File(context.filesDir, "aerix-server-profiles.json")

    suspend fun servers(context: Context): List<ServerProfile> = withContext(Dispatchers.IO) {
        val file = profilesFile(context)
        if (!file.exists()) emptyList() else AtomicFile(file).openRead().bufferedReader().use {
            Tools.GLOBAL_GSON.fromJson<List<ServerProfile>>(it, object : TypeToken<List<ServerProfile>>() {}.type) ?: emptyList()
        }
    }

    suspend fun saveServers(context: Context, profiles: List<ServerProfile>) = withContext(Dispatchers.IO) {
        require(profiles.size <= 100) { "At most 100 server profiles are supported" }
        val file = AtomicFile(profilesFile(context))
        val stream = file.startWrite()
        try {
            stream.write(Tools.GLOBAL_GSON.toJson(profiles).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) { file.failWrite(stream); throw e }
    }

    fun validateAddress(address: String): String {
        val clean = address.trim()
        require(clean.length in 1..253 && clean.none { it.isWhitespace() || it in "/\\?#@" }) { "Enter a host or host:port, without a URL scheme" }
        val uri = URI("minecraft://$clean")
        require(!uri.host.isNullOrBlank()) { "Invalid server hostname or IPv6 address" }
        require(uri.port == -1 || uri.port in 1..65535) { "Port must be between 1 and 65535" }
        return clean
    }

    suspend fun instance(id: String): Instance? = withContext(Dispatchers.IO) {
        Instances.loadAllInstances().firstOrNull { it.directoryId == id }
    }

    private fun backupDirectory(context: Context, world: File): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(world.canonicalPath.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "world-backups/$hash")
    }

    suspend fun worlds(context: Context): List<WorldCard> = withContext(Dispatchers.IO) {
        val worlds = ArrayList<WorldCard>()
        val visited = HashSet<String>()
        for (instance in Instances.loadAllInstances()) {
            val saves = File(instance.gameDirectory, "saves")
            if (!visited.add(saves.canonicalPath)) continue
            for (world in saves.listFiles().orEmpty()) {
                if (!world.isDirectory || world.canonicalFile.parentFile != saves.canonicalFile || !File(world, "level.dat").isFile) continue
                val metadata = runCatching { WorldMetadata.read(File(world, "level.dat")) }.getOrNull()
                val lastBackup = backupDirectory(context, world).listFiles { f -> f.extension == "zip" }
                    ?.maxByOrNull { it.lastModified() }
                val iconFile = File(world, "icon.png")
                val icon = runCatching {
                    if (iconFile.length() !in 1..1_048_576) return@runCatching null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(iconFile.path, bounds)
                    if (bounds.outWidth !in 1..512 || bounds.outHeight !in 1..512) return@runCatching null
                    val options = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 64) }
                    BitmapFactory.decodeFile(iconFile.path, options)?.asImageBitmap()
                }.getOrNull()
                val bytes = runCatching { safeTree(world.canonicalFile, false).filter { it.isFile }.sumOf { it.length() } }.getOrDefault(-1L)
                worlds.add(WorldCard(instance.directoryId, instance.name ?: instance.versionId ?: "Instance", world,
                    metadata?.name ?: world.name, metadata?.version, metadata?.lastPlayed ?: 0, bytes, icon, lastBackup))
            }
        }
        worlds.sortedByDescending { it.lastPlayed }
    }

    private fun safeTree(root: File, strict: Boolean): Sequence<File> = sequence {
        val seen = HashSet<String>()
        var count = 0
        val tree = root.walkTopDown().onEnter { directory ->
            val real = directory.canonicalFile
            val safe = real == directory.absoluteFile && seen.add(real.path) &&
                (real == root || real.path.startsWith(root.path + File.separator)) &&
                real.relativeTo(root).invariantSeparatorsPath.count { it == '/' } < 64
            if (strict) require(safe) { "World contains a link, cycle or overly deep directory" }
            safe
        }
        for (file in tree) {
            check(++count <= 200_000) { "Too many entries in world" }
            val real = file.canonicalFile
            val safe = real == file.absoluteFile && (real == root || real.path.startsWith(root.path + File.separator))
            if (strict) require(safe) { "World contains a link" }
            if (safe) yield(file)
        }
    }

    suspend fun backup(context: Context, world: WorldCard): File = withContext(Dispatchers.IO) {
        WorldTaskGate.beginWrite(context)
        try {
            val root = world.directory.canonicalFile
            require(File(root, "level.dat").isFile) { "World no longer exists" }
            java.io.RandomAccessFile(File(root, "session.lock"), "rw").use { lockFile ->
                val lock = lockFile.channel.tryLock() ?: error("This world is in use")
                try {
                    val directory = backupDirectory(context, root).apply { mkdirs() }
                    val target = File(directory, "${System.currentTimeMillis()}.zip")
                    val partial = File(directory, target.name + ".partial")
                    try {
                        ZipOutputStream(partial.outputStream().buffered()).use { zip ->
                            safeTree(root, true).forEach { file ->
                                if (file.isFile && file.name != "session.lock") {
                                    zip.putNextEntry(ZipEntry(file.relativeTo(root).invariantSeparatorsPath).apply { time = file.lastModified() })
                                    file.inputStream().use { it.copyTo(zip) }
                                    zip.closeEntry()
                                }
                            }
                        }
                        check(!gameRunning(context)) { "Minecraft started during backup; backup was discarded" }
                        check(partial.renameTo(target)) { "Could not finish backup" }
                        directory.listFiles { file -> file.extension == "zip" }?.sortedByDescending { it.lastModified() }
                            ?.drop(5)?.forEach { it.delete() }
                        target
                    } finally { partial.delete() }
                } finally { lock.release() }
            }
        } finally { WorldTaskGate.endWrite() }
    }

    suspend fun installCompanion(context: Context, instanceId: String) = withContext(Dispatchers.IO) {
        WorldTaskGate.beginWrite(context)
        try {
        val selected = instance(instanceId) ?: error("Instance no longer exists")
        val version = selected.versionId.orEmpty()
        require(version.contains("fabric", ignoreCase = true) && Regex("(^|[^0-9])1\\.20\\.1([^0-9]|$)").containsMatchIn(version)) { "The companion supports Fabric 1.20.1 only" }
        val mods = File(selected.gameDirectory, "mods").apply { mkdirs() }
        val destination = File(mods, "aerix-companion-fabric-1.20.1.jar")
        val temporary = File(mods, destination.name + ".partial")
        try {
            context.assets.open("components/aerix-companion/aerix-companion.jar").use { source ->
                temporary.outputStream().use { source.copyTo(it) }
            }
            check(temporary.renameTo(destination)) { "Unable to install companion" }
        } finally { temporary.delete() }
        val config = AtomicFile(File(selected.gameDirectory, "config/aerix-capes.json"))
        config.baseFile.parentFile?.mkdirs()
        val json = com.google.gson.JsonObject().apply { addProperty("endpoint", capeEndpoint(context)) }
        val stream = config.startWrite()
        try { stream.write(json.toString().toByteArray()); config.finishWrite(stream) }
        catch (e: Exception) { config.failWrite(stream); throw e }
        } finally { WorldTaskGate.endWrite() }
    }

    fun capeEndpoint(context: Context): String = context.getSharedPreferences("aerix-tools", 0).getString("cape-endpoint", "").orEmpty()

    fun saveCapeEndpoint(context: Context, endpoint: String) {
        val uri = URI(endpoint.trim().trimEnd('/'))
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "Use an HTTPS service URL without credentials or query parameters" }
        context.getSharedPreferences("aerix-tools", 0).edit().putString("cape-endpoint", uri.toString()).apply()
    }

    suspend fun uploadCape(context: Context, uuid: String, uploadKey: String, png: Uri) = withContext(Dispatchers.IO) {
        val endpoint = capeEndpoint(context)
        require(endpoint.isNotBlank()) { "Configure your hosted cape service first" }
        require(uploadKey.length in 16..256 && uploadKey.none { it == '\r' || it == '\n' }) { "Enter the cape service upload key assigned to this UUID" }
        val profile = UUID.fromString(uuid).toString()
        val bytes = context.contentResolver.openInputStream(png)?.use { input ->
            val data = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                check(data.size() + n <= 1_048_576) { "Cape PNG must be smaller than 1 MB" }
                data.write(buffer, 0, n)
            }
            data.toByteArray()
        } ?: throw IOException("Unable to read cape")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outMimeType == "image/png" && bounds.outWidth == 64 && bounds.outHeight == 32) { "Use a 64 × 32 PNG cape" }
        val connection = URL("$endpoint/v1/capes/$profile").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", "Bearer $uploadKey")
            connection.setRequestProperty("Content-Type", "image/png")
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            check(connection.responseCode in 200..299) { "Cape service rejected upload (HTTP ${connection.responseCode})" }
        } finally { connection.disconnect() }
    }
}
