package net.kdt.pojavlaunch.aerix.worldshare

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.aerix.bridge.GameDownloadTarget
import net.kdt.pojavlaunch.aerix.social.SocialRealtime
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.zip.ZipFile

/** On-demand, checksum-pinned upstream Android builds. Nothing is bundled or changed silently. */
object WorldShareInstaller {
    private const val RELEASE = "https://github.com/Synquox/e4all/releases/download/v2.3.0/"
    private val digests = mapOf(
        WorldSharePolicy.FABRIC to "bc20a9de9b4075b5426743eff3ce51c59241db8ec50c22fa5a8bf8b5bba860cd",
        WorldSharePolicy.FABRIC_MODERN to "8c7941446ac2c48046963c606bb68dae63aad945b35ce05d0781c7e7cef10e9c",
        WorldSharePolicy.FORGE to "1fc5a6f703b3095660d5508596530378799c91b8a4bc69fa1c6ab6e85b2dca4b",
        WorldSharePolicy.NEOFORGE to "0bd6c7a48bda072400f57dec6be8f36b5e8432f0bec34eae43d0ba80d7b79ace"
    )

    fun supportsArm64(): Boolean = android.os.Process.is64Bit() && Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")

    private fun ids(file: File): Set<String> = try {
        ZipFile(file).use { zip ->
            val result = mutableSetOf<String>()
            zip.getEntry("fabric.mod.json")?.let { entry ->
                if (entry.size in 1L..262144L) {
                    val obj = JSONObject(zip.getInputStream(entry).bufferedReader().use { it.readText() })
                    obj.optString("id").takeIf { it.isNotBlank() }?.let { result.add(it) }
                    val provides = obj.optJSONArray("provides")
                    if (provides != null) {
                        for (i in 0 until provides.length()) {
                            result.add(provides.optString(i))
                        }
                    }
                }
            }
            listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml").forEach { name ->
                zip.getEntry(name)?.let { entry ->
                    if (entry.size in 1L..262144L) {
                        val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        Regex("""(?m)^\s*modId\s*=\s*["']([^"']+)["']""").findAll(text).forEach { result.add(it.groupValues[1]) }
                    }
                }
            }
            result
        }
    } catch (_: Exception) { emptySet() }

    private fun jars(dir: File): List<File> = dir.listFiles()?.filter { it.isFile && it.extension.equals("jar", true) }.orEmpty()
    fun installed(target: GameDownloadTarget): Boolean = target.gameDirectory?.let { dir ->
        jars(File(dir, "mods")).any { "e4all" in ids(it) }
    } == true

    private fun readMetadata(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "AerixLauncher/2.0 WorldSharing").build()
        SocialRealtime.restClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Could not fetch Fabric API information (HTTP ${response.code}).")
            val body = response.body ?: throw IOException("Fabric API information is empty.")
            if (body.contentLength() > 2_000_000) throw IOException("Fabric API information is too large.")
            return body.string().also { if (it.length > 2_000_000) throw IOException("Fabric API information is too large.") }
        }
    }

    private data class Download(val url: String, val name: String, val algorithm: String, val hash: String)
    private fun fabricApi(version: String): Download {
        val loader = URLEncoder.encode("[\"fabric\"]", "UTF-8")
        val game = URLEncoder.encode("[\"$version\"]", "UTF-8")
        val versions = JSONArray(readMetadata("https://api.modrinth.com/v2/project/fabric-api/version?loaders=$loader&game_versions=$game"))
        if (versions.length() == 0) throw IOException("No Fabric API release is available for $version.")
        val files = versions.getJSONObject(0).getJSONArray("files")
        val item = (0 until files.length()).map { files.getJSONObject(it) }.firstOrNull { it.optBoolean("primary") }
            ?: throw IOException("Fabric API download is missing.")
        val url = item.optString("url")
        val name = item.optString("filename")
        val hash = item.optJSONObject("hashes")?.optString("sha512").orEmpty()
        if (!url.startsWith("https://cdn.modrinth.com/") || name != File(name).name || !name.endsWith(".jar") || !hash.matches(Regex("[a-fA-F0-9]{128}")))
            throw IOException("Fabric API download information is invalid.")
        return Download(url, name, "SHA-512", hash)
    }

    private fun download(download: Download, temp: File) {
        val digest = MessageDigest.getInstance(download.algorithm)
        val req = Request.Builder().url(download.url).header("User-Agent", "AerixLauncher/2.0 WorldSharing").build()
        SocialRealtime.restClient.newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code}).")
            val body = response.body ?: throw IOException("Empty download.")
            if (body.contentLength() > 40_000_000) throw IOException("Download is too large.")
            body.byteStream().use { input -> temp.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 40_000_000) throw IOException("Download is too large.")
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            } }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        if (!actual.equals(download.hash, ignoreCase = true)) throw IOException("Download checksum did not match; nothing was installed.")
        ZipFile(temp).use { if (it.size() == 0) throw IOException("Downloaded file is not a valid mod.") }
    }

    /** Installs for the captured game directory. Existing mods are never removed or replaced. */
    suspend fun install(target: GameDownloadTarget): String = withContext(Dispatchers.IO) {
        if (!supportsArm64()) throw IOException("e4all's Android build requires a 64-bit ARM game runtime.")
        val asset = WorldSharePolicy.asset(target.loader, target.mcVersion)
            ?: throw IOException("No supported Android e4all release for ${target.versionLabel}.")
        val gameDir = target.gameDirectory ?: throw IOException("The running instance directory is unavailable.")
        val mods = File(gameDir, "mods")
        if (!mods.isDirectory && !mods.mkdirs()) throw IOException("Cannot create the instance mods folder.")
        val existing = jars(mods)
        val allIds = existing.flatMap { ids(it) }.toSet()
        if ("e4all" in allIds) throw IOException("e4all is already installed. Restart Minecraft if it was just added.")
        if ("e4mc" in allIds || "e4mc_minecraft" in allIds) throw IOException("e4mc is already installed. Remove the conflict manually before adding e4all.")
        val downloads = mutableListOf<Download>()
        if (target.loader == "fabric" && "fabric-api" !in allIds) downloads += fabricApi(target.mcVersion!!)
        downloads += Download(RELEASE + asset, asset, "SHA-256", digests.getValue(asset))
        val temps = mutableListOf<File>()
        val placed = mutableListOf<File>()
        try {
            for (item in downloads) {
                val dst = File(mods, item.name)
                if (dst.exists()) throw IOException("${item.name} already exists; no files were replaced.")
                val tmp = File.createTempFile(".aerix-worldshare-", ".part", mods)
                temps += tmp
                download(item, tmp)
            }
            downloads.forEachIndexed { index, item ->
                val dst = File(mods, item.name)
                // Reserve the name exclusively; never intentionally replace another mod.
                if (!dst.createNewFile()) throw IOException("${item.name} already exists; no existing files were replaced.")
                placed += dst
                if (!temps[index].renameTo(dst)) throw IOException("Could not install ${item.name}; no existing files were replaced.")
            }
            "Installed e4all. Restart Minecraft, then open a world to LAN."
        } catch (error: Exception) {
            placed.forEach { it.delete() }
            throw error
        } finally {
            temps.forEach { it.delete() }
        }
    }
}
