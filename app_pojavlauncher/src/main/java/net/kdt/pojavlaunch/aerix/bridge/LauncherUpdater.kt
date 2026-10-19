package net.kdt.pojavlaunch.aerix.bridge

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import net.kdt.pojavlaunch.BuildConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal enum class LauncherUpdatePhase { CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, ERROR }

/** Process-lifetime download: closing/minimizing the dialog does not cancel an accepted update. */
internal object LauncherUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var connection: HttpURLConnection? = null
    var phase by mutableStateOf(LauncherUpdatePhase.CHECKING); private set
    var release by mutableStateOf<LauncherRelease?>(null); private set
    var received by mutableLongStateOf(0L); private set
    var error by mutableStateOf(""); private set
    var awaitingPermission by mutableStateOf(false); private set
    var installing by mutableStateOf(false); private set
    private var apk: File? = null

    fun check() {
        if (job?.isActive == true || (phase == LauncherUpdatePhase.READY && apk?.isFile == true)) return
        phase = LauncherUpdatePhase.CHECKING; error = ""
        job = scope.launch {
            try {
                val response = open("https://api.github.com/repos/entitybrian69-bit/Aerix-launcher/releases/latest")
                val result = try {
                    if (response.responseCode == 404) null else {
                        require(response.responseCode == 200) { "GitHub returned HTTP ${response.responseCode}. Please try again later." }
                        val bytes = response.inputStream.use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(4096)
                            while (out.size() <= 256 * 1024) {
                                val count = input.read(buffer)
                                if (count == -1) break
                                out.write(buffer, 0, count)
                            }
                            out.toByteArray()
                        }
                        require(bytes.size <= 256 * 1024) { "Release response is too large" }
                        LauncherUpdatePolicy.release(bytes.toString(Charsets.UTF_8), BuildConfig.FLAVOR == "noruntime")
                    }
                } finally { response.disconnect(); connection = null }
                withContext(Dispatchers.Main) {
                    release = result
                    val installed = LauncherVersion.parse(BuildConfig.VERSION_NAME)
                    require(installed != null) { "This build does not have a stable release version" }
                    phase = if (result != null && result.version > installed) LauncherUpdatePhase.AVAILABLE else LauncherUpdatePhase.CURRENT
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                fail(e)
            }
        }
    }
    fun download(context: Context) {
        if (phase == LauncherUpdatePhase.DOWNLOADING || phase == LauncherUpdatePhase.CHECKING) return
        val update = release ?: return
        val app = context.applicationContext
        phase = LauncherUpdatePhase.DOWNLOADING; error = ""; received = 0
        job = scope.launch {
            val directory = File(app.cacheDir, "updates")
            val target = File(directory, update.asset.name)
            val partial = File(directory, update.asset.name + ".part")
            try {
                if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create update cache")
                val digest = MessageDigest.getInstance("SHA-256")
                val response = open(update.asset.url)
                try {
                    require(response.responseCode == 200) { "Download returned HTTP ${response.responseCode}" }
                    response.inputStream.use { input -> partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytes = 0L
                        var lastReport = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count == -1) break
                            bytes += count
                            require(bytes <= update.asset.size && bytes <= LauncherUpdatePolicy.MAX_APK_BYTES) { "Update download size does not match the release" }
                            output.write(buffer, 0, count); digest.update(buffer, 0, count)
                            val now = System.nanoTime()
                            if (now - lastReport > 100_000_000) {
                                withContext(Dispatchers.Main) { received = bytes }
                                lastReport = now
                            }
                        }
                        require(bytes == update.asset.size) { "The update download is incomplete. Please retry." }
                        withContext(Dispatchers.Main) { received = bytes }
                    } }
                } finally { response.disconnect(); connection = null }
                val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                require(update.asset.sha256 == null || hash == update.asset.sha256) { "Update checksum verification failed" }
                require(partial.renameTo(target)) { "Cannot save downloaded update" }
                validate(app, target, update)
                withContext(Dispatchers.Main) { apk = target; phase = LauncherUpdatePhase.READY }
            } catch (e: Exception) {
                target.delete()
                if (e is CancellationException || !currentCoroutineContext().isActive) {
                    withContext(NonCancellable + Dispatchers.Main) { phase = LauncherUpdatePhase.AVAILABLE; received = 0 }
                } else fail(e)
            } finally { partial.delete() }
        }
    }
    fun cancelDownload() {
        job?.cancel()
        val active = connection
        scope.launch { runCatching { active?.disconnect() } }
    }
    private fun open(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "AerixLauncher/${BuildConfig.VERSION_NAME}")
            instanceFollowRedirects = true
            connection = this
        }
    }
    private suspend fun fail(e: Exception) = withContext(Dispatchers.Main) {
        error = e.message ?: "The update could not be completed. Please try again."
        phase = LauncherUpdatePhase.ERROR
    }
    @Suppress("DEPRECATION")
    private fun validate(context: Context, file: File, update: LauncherRelease) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pm = context.packageManager
        val next = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: error("Downloaded file is not a valid APK")
        val current = pm.getPackageInfo(context.packageName, flags)
        require(next.packageName == context.packageName) { "This APK is for a different launcher edition" }
        val code = if (Build.VERSION.SDK_INT >= 28) next.longVersionCode else next.versionCode.toLong()
        val currentCode = if (Build.VERSION.SDK_INT >= 28) current.longVersionCode else current.versionCode.toLong()
        val expectedVersion = update.tag.removePrefix("v") + if (BuildConfig.FLAVOR == "noruntime") "-noruntime" else ""
        require(code > currentCode && next.versionName == expectedVersion) { "APK version or runtime edition does not match a newer release" }
        if (Build.VERSION.SDK_INT >= 24) require((next.applicationInfo?.minSdkVersion ?: 23) <= Build.VERSION.SDK_INT) { "This update requires a newer Android version" }
        val installedSignatures = if (Build.VERSION.SDK_INT >= 28) current.signingInfo?.apkContentsSigners else current.signatures
        val newSignatures = if (Build.VERSION.SDK_INT >= 28) next.signingInfo?.apkContentsSigners else next.signatures
        require(!installedSignatures.isNullOrEmpty() && !newSignatures.isNullOrEmpty() &&
            installedSignatures.map { it.toCharsString() }.toSet() == newSignatures.map { it.toCharsString() }.toSet()) { "Update signing certificate does not match the installed launcher" }
    }
    fun install(context: Context) {
        if (installing) return
        val file = apk ?: return
        val update = release ?: return
        installing = true
        scope.launch {
            try {
                validate(context.applicationContext, file, update)
                withContext(Dispatchers.Main) {
                    if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                        awaitingPermission = true
                        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                    } else {
                        awaitingPermission = false
                        error = ""
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            clipData = ClipData.newRawUri("Aerix update", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    awaitingPermission = false
                    error = e.message ?: "Android could not open the installer"
                }
            } finally { withContext(Dispatchers.Main) { installing = false } }
        }
    }
    fun permissionReturned(context: Context) {
        if (!awaitingPermission) return
        awaitingPermission = false
        if (Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()) install(context)
        else error = "Allow Aerix to install app updates, then tap Install again."
    }
}
