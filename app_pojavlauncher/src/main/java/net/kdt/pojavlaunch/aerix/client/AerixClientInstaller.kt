package net.kdt.pojavlaunch.aerix.client

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.SocialRealtime
import net.kdt.pojavlaunch.instances.Instance
import net.kdt.pojavlaunch.instances.InstanceIconProvider
import net.kdt.pojavlaunch.instances.Instances
import net.kdt.pojavlaunch.modloaders.FabriclikeUtils
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

data class ClientVersionSpec(
    val mcVersion: String,
    val label: String,
    val summary: String
)

data class ClientModEntry(
    val slug: String,
    val title: String,
    val category: String
)

object AerixClientInstaller {

    private const val USER_AGENT =
        "entitybrian69-bit/Aerix-launcher/aerix-client (https://github.com/entitybrian69-bit/Aerix-launcher)"

    val availableVersions: List<ClientVersionSpec> = listOf(
        ClientVersionSpec("1.21.4", "Aerix Client 1.21.4", "Minecraft 1.21.4 · Fabric + FPS Engine"),
        ClientVersionSpec("1.21.1", "Aerix Client 1.21.1", "Minecraft 1.21.1 · Fabric + FPS Engine"),
        ClientVersionSpec("1.20.4", "Aerix Client 1.20.4", "Minecraft 1.20.4 · Fabric + FPS Engine"),
        ClientVersionSpec("1.20.1", "Aerix Client 1.20.1", "Minecraft 1.20.1 · Fabric + FPS Engine"),
        ClientVersionSpec("1.19.4", "Aerix Client 1.19.4", "Minecraft 1.19.4 · Fabric + FPS Engine"),
        ClientVersionSpec("1.18.2", "Aerix Client 1.18.2", "Minecraft 1.18.2 · Fabric + FPS Engine"),
        ClientVersionSpec("1.16.5", "Aerix Client 1.16.5", "Minecraft 1.16.5 · Fabric + FPS Engine")
    )

    var isInstalling by mutableStateOf(false)
        private set
    var installingVersion by mutableStateOf<String?>(null)
        private set
    var progressFraction by mutableFloatStateOf(0f)
        private set
    var statusHeadline by mutableStateOf("")
        private set
    var statusDetail by mutableStateOf("")
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    fun instanceNameFor(mcVersion: String): String = "Aerix Client $mcVersion"

    fun findInstalledClientInstance(mcVersion: String? = null): Instance? = runCatching {
        val list = Instances.loadAllInstances()
        if (mcVersion.isNullOrBlank()) {
            list.firstOrNull { it.isAerixClient }
                ?: list.firstOrNull { it.name?.startsWith("Aerix Client", ignoreCase = true) == true }
        } else {
            val expectedName = instanceNameFor(mcVersion)
            list.firstOrNull { inst ->
                inst.isAerixClient && (
                    inst.name.equals(expectedName, ignoreCase = true) ||
                        inst.versionId?.contains(mcVersion) == true
                )
            } ?: list.firstOrNull { inst ->
                inst.name.equals(expectedName, ignoreCase = true)
            }
        }
    }.getOrNull()

    fun buildModListForVersion(mcVersion: String): List<ClientModEntry> {
        val out = ArrayList<ClientModEntry>()
        out += ClientModEntry("fabric-api", "Fabric API", "Core")
        out += ClientModEntry("sodium", "Sodium", "FPS Engine")
        out += ClientModEntry("lithium", "Lithium", "Physics Tick")
        out += ClientModEntry("ferrite-core", "FerriteCore", "RAM Saver")
        if (mcVersion == "1.16.5" || mcVersion == "1.18.2" || mcVersion == "1.19.4") {
            out += ClientModEntry("lazydfu", "LazyDFU", "Fast Boot")
            out += ClientModEntry("starlight", "Starlight", "Lighting")
        }
        out += ClientModEntry("entityculling", "EntityCulling", "Entity FPS")
        out += ClientModEntry("modmenu", "Mod Menu", "Client UI")
        out += ClientModEntry("iris", "Iris Shaders", "Shaders")
        out += ClientModEntry("appleskin", "AppleSkin", "HUD")
        out += ClientModEntry("logical-zoom", "Logical Zoom", "Zoom")
        return out
    }

    suspend fun installClientVersion(context: Context, mcVersion: String): Result<Instance> =
        withContext(Dispatchers.IO) {
            if (isInstalling) {
                return@withContext Result.failure(IllegalStateException("Download already in progress"))
            }
            isInstalling = true
            installingVersion = mcVersion
            lastError = null
            progressFraction = 0.05f
            statusHeadline = "Resolving Fabric Loader ($mcVersion)..."
            statusDetail = "Connecting to Fabric Meta"

            runCatching {
                AerixClientStore.init(context)
                AerixClientStore.selectedClientVersion = mcVersion
                AerixClientStore.persist()

                val loaders = FabriclikeUtils.FABRIC_UTILS.downloadLoaderVersions(mcVersion)
                    ?: error("Could not reach Fabric Meta server. Check your internet connection.")
                if (loaders.isEmpty()) error("No Fabric loaders returned for $mcVersion")
                val pickedLoader = loaders.firstOrNull { it.stable } ?: loaders.first()

                progressFraction = 0.16f
                statusHeadline = "Downloading Fabric ${pickedLoader.version} ($mcVersion)..."
                statusDetail = "Writing version profile"
                val fabricVersionId = FabriclikeUtils.FABRIC_UTILS.install(mcVersion, pickedLoader.version)
                    ?: error("Failed to download Fabric $mcVersion profile.")

                progressFraction = 0.26f
                statusHeadline = "Preparing ${instanceNameFor(mcVersion)}..."
                statusDetail = "Configuring isolated client directory"

                val existing = findInstalledClientInstance(mcVersion)
                val targetName = instanceNameFor(mcVersion)
                val folderSlug = "aerix_client_${mcVersion.replace('.', '_')}"
                val clientInstance: Instance = if (existing != null) {
                    existing.versionId = fabricVersionId
                    existing.isAerixClient = true
                    existing.sharedData = false
                    if (existing.name.isNullOrBlank()) {
                        existing.name = targetName
                    }
                    if (existing.jvmArgs.isNullOrBlank()) {
                        existing.argsMode = Instance.ARGS_MODE_MERGE_DEFAULT_FIRST
                        existing.jvmArgs = "-XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:MaxGCPauseMillis=50"
                    }
                    existing.write()
                    existing
                } else {
                    Instances.createInstance(
                        { inst ->
                            inst.name = targetName
                            inst.versionId = fabricVersionId
                            inst.icon = "fabric"
                            inst.sharedData = false
                            inst.isAerixClient = true
                            inst.argsMode = Instance.ARGS_MODE_MERGE_DEFAULT_FIRST
                            inst.jvmArgs = "-XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:MaxGCPauseMillis=50"
                        },
                        folderSlug
                    )
                }

                writeCustomClientInstanceIcon(context, clientInstance)
                val gameDir = clientInstance.gameDirectory.apply { mkdirs() }
                val modsDir = File(gameDir, "mods").apply { mkdirs() }

                val modPlan = buildModListForVersion(mcVersion)
                modPlan.forEachIndexed { idx, mod ->
                    progressFraction = 0.30f + (0.55f * (idx.toFloat() / modPlan.size.coerceAtLeast(1)))
                    statusHeadline = "Downloading ${mod.title} (${idx + 1}/${modPlan.size})..."
                    statusDetail = "Minecraft $mcVersion · ${mod.category}"
                    runCatching {
                        ensureModrinthJar(modsDir, mod.slug, mcVersion)
                    }
                }

                progressFraction = 0.90f
                statusHeadline = "Applying Aerix Client Pack..."
                statusDetail = "Configuring resource pack & options"
                AerixClientPackGenerator.buildAndApplyToInstance(context, gameDir, mcVersion)
                AerixClientPackGenerator.applyClientOptionsTxt(gameDir)

                Instances.setSelectedInstance(clientInstance)
                AerixClientStore.installsCompleted += 1
                AerixClientStore.persist()

                progressFraction = 1.0f
                statusHeadline = "$targetName downloaded!"
                statusDetail = "Ready to launch"
                clientInstance
            }.onFailure { err ->
                lastError = err.message ?: "Download failed"
                statusHeadline = "Download failed"
                statusDetail = lastError.orEmpty()
            }.also {
                isInstalling = false
                installingVersion = null
            }
        }

    suspend fun installOrSyncClient1165(context: Context): Result<Instance> =
        installClientVersion(context, "1.16.5")

    private fun ensureModrinthJar(modsDir: File, slug: String, mcVersion: String) {
        val normalizedKey = slug.replace("-", "").lowercase()
        val alreadyExists = modsDir.listFiles()?.any { f ->
            f.isFile && f.extension.equals("jar", true) &&
                f.name.replace("-", "").replace("_", "").lowercase().contains(normalizedKey)
        } == true
        if (alreadyExists) return

        val loadersParam = URLEncoder.encode("[\"fabric\"]", "UTF-8")
        val gvParam = URLEncoder.encode("[\"$mcVersion\"]", "UTF-8")
        val url = "https://api.modrinth.com/v2/project/$slug/version?loaders=$loadersParam&game_versions=$gvParam"

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        val bodyStr = SocialRealtime.restClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return
            resp.body?.string().orEmpty()
        }
        if (bodyStr.isBlank()) return
        val versions = JSONArray(bodyStr)
        if (versions.length() == 0) return

        val files = versions.getJSONObject(0).optJSONArray("files") ?: return
        if (files.length() == 0) return
        var chosen = files.getJSONObject(0)
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            if (f.optBoolean("primary", false)) {
                chosen = f
                break
            }
        }
        val dlUrl = chosen.optString("url")
        val fileName = chosen.optString("filename")
        if (dlUrl.isBlank() || fileName.isBlank() || !fileName.endsWith(".jar", true)) return

        val targetFile = File(modsDir, fileName)
        if (targetFile.isFile && targetFile.length() > 1024L) return

        val tmpFile = File(modsDir, "$fileName.part")
        val dlReq = Request.Builder()
            .url(dlUrl)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        SocialRealtime.restClient.newCall(dlReq).execute().use { resp ->
            if (!resp.isSuccessful) return
            val stream = resp.body?.byteStream() ?: return
            FileOutputStream(tmpFile).use { out -> stream.copyTo(out) }
        }
        if (tmpFile.length() > 512L) {
            tmpFile.renameTo(targetFile)
        } else {
            tmpFile.delete()
        }
    }

    private fun writeCustomClientInstanceIcon(context: Context, instance: Instance) {
        runCatching {
            val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 128f, 128f,
                    intArrayOf(0xFF2B151B.toInt(), 0xFF141519.toInt()),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            c.drawRoundRect(4f, 4f, 124f, 124f, 30f, 30f, bg)
            val logo = BitmapFactory.decodeResource(context.resources, R.drawable.aerix_logo)
            if (logo != null) {
                c.drawBitmap(Bitmap.createScaledBitmap(logo, 88, 88, true), 20f, 20f, null)
            }
            val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3.5f
                color = 0xFFDC1E3A.toInt()
            }
            c.drawRoundRect(5.5f, 5.5f, 122.5f, 122.5f, 28f, 28f, rim)
            instance.encodeNewIcon(bmp)
            InstanceIconProvider.dropIcon(instance)
        }
    }
}
