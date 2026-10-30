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

data class ClientModEntry(
    val slug: String,
    val title: String,
    val category: String
)

object AerixClientInstaller {

    const val TARGET_MC_VERSION = "1.16.5"
    private const val USER_AGENT =
        "entitybrian69-bit/Aerix-launcher/1.16.5-client (https://github.com/entitybrian69-bit/Aerix-launcher)"

    var isInstalling by mutableStateOf(false)
        private set
    var progressFraction by mutableFloatStateOf(0f)
        private set
    var statusHeadline by mutableStateOf("Ready to deploy Aerix Client 1.16.5")
        private set
    var statusDetail by mutableStateOf("Fabric 1.16.5 + FPS Mod Stack + Liquid Crystal Pack")
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    fun findInstalledClientInstance(): Instance? = runCatching {
        val list = Instances.loadAllInstances()
        list.firstOrNull { it.isAerixClient }
            ?: list.firstOrNull { it.name?.equals(AerixClientStore.CLIENT_INSTANCE_NAME, ignoreCase = true) == true }
    }.getOrNull()

    fun buildDesiredModList(): List<ClientModEntry> {
        val out = ArrayList<ClientModEntry>()
        if (AerixClientStore.installFpsStack) {
            out += ClientModEntry("fabric-api", "Fabric API", "Core")
            out += ClientModEntry("sodium", "Sodium", "FPS Engine")
            out += ClientModEntry("lithium", "Lithium", "Physics Tick")
            out += ClientModEntry("ferrite-core", "FerriteCore", "-45% RAM")
            out += ClientModEntry("lazydfu", "LazyDFU", "2x Faster Boot")
            out += ClientModEntry("starlight", "Starlight", "Chunk Lighting")
            out += ClientModEntry("entityculling", "EntityCulling", "Entity FPS")
            out += ClientModEntry("modmenu", "Mod Menu", "Client UI")
        }
        if (AerixClientStore.installShadersAddon) {
            out += ClientModEntry("iris", "Iris Shaders", "Shaders")
        }
        if (AerixClientStore.installSurvivalPolishAddon) {
            out += ClientModEntry("appleskin", "AppleSkin", "Saturation HUD")
            out += ClientModEntry("lambdynamiclights", "LambDynamicLights", "Dynamic Torch")
        }
        if (AerixClientStore.installZoomAddon) {
            out += ClientModEntry("logical-zoom", "Logical Zoom", "OptiFine Zoom")
        }
        return out
    }

    /**
     * Full 1-Click Automated Installer / Syncer for **Aerix Client 1.16.5** ONLY:
     * 1. Resolves latest stable Fabric Loader for `1.16.5` and writes the Fabric version profile JSON.
     * 2. Creates (or updates) an isolated `Aerix Client 1.16.5` instance (`isAerixClient = true`, `sharedData = false`).
     * 3. Writes the custom Liquid Crystal emblem icon to `instance.ownIconLocation`.
     * 4. Queries Modrinth v2 for exact `1.16.5` Fabric `.jar` releases and downloads missing jars into `mods/`.
     * 5. Generates `Aerix-Client-Pack.zip` (`pack_format: 6`) + pre-configures `options.txt` (`monochromeLogo:true`, Fullbright, pack active).
     * 6. Selects `Aerix Client 1.16.5` as the active launcher instance!
     */
    suspend fun installOrSyncClient1165(context: Context): Result<Instance> = withContext(Dispatchers.IO) {
        if (isInstalling) {
            return@withContext Result.failure(IllegalStateException("Install already in progress"))
        }
        isInstalling = true
        lastError = null
        progressFraction = 0.04f
        statusHeadline = "Connecting to Fabric Meta (1.16.5)..."
        statusDetail = "Resolving stable Fabric Loader for Minecraft $TARGET_MC_VERSION"

        runCatching {
            AerixClientStore.init(context)

            // Step 1: Download & install Fabric 1.16.5 profile
            val loaders = FabriclikeUtils.FABRIC_UTILS.downloadLoaderVersions(TARGET_MC_VERSION)
                ?: error("Could not reach Fabric Meta server. Check your internet connection.")
            if (loaders.isEmpty()) error("No Fabric loaders returned for $TARGET_MC_VERSION")
            val pickedLoader = loaders.firstOrNull { it.stable } ?: loaders.first()

            progressFraction = 0.14f
            statusHeadline = "Installing Fabric ${pickedLoader.version} for $TARGET_MC_VERSION..."
            statusDetail = "Writing version JSON into versions/ directory"
            val fabricVersionId = FabriclikeUtils.FABRIC_UTILS.install(TARGET_MC_VERSION, pickedLoader.version)
                ?: error("Failed to download Fabric $TARGET_MC_VERSION profile metadata.")

            // Step 2: Create or reuse isolated Aerix Client 1.16.5 Instance
            progressFraction = 0.24f
            statusHeadline = "Configuring isolated Aerix Client 1.16.5 workspace..."
            statusDetail = "Setting isAerixClient = true, G1GC flags & crystal emblem"

            val existing = findInstalledClientInstance()
            val clientInstance: Instance = if (existing != null) {
                existing.versionId = fabricVersionId
                existing.isAerixClient = true
                existing.sharedData = false
                if (existing.name.isNullOrBlank()) {
                    existing.name = AerixClientStore.CLIENT_INSTANCE_NAME
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
                        inst.name = AerixClientStore.CLIENT_INSTANCE_NAME
                        inst.versionId = fabricVersionId
                        inst.icon = "fabric"
                        inst.sharedData = false
                        inst.isAerixClient = true
                        inst.argsMode = Instance.ARGS_MODE_MERGE_DEFAULT_FIRST
                        inst.jvmArgs = "-XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:MaxGCPauseMillis=50"
                    },
                    "aerix_client_1165"
                )
            }

            // Stamp custom Liquid Crystal Client icon onto the instance
            writeCustomClientInstanceIcon(context, clientInstance)
            val gameDir = clientInstance.gameDirectory.apply { mkdirs() }
            val modsDir = File(gameDir, "mods").apply { mkdirs() }

            // Step 3: Download 1.16.5 Fabric Performance & Client Mods from Modrinth v2
            val modPlan = buildDesiredModList()
            modPlan.forEachIndexed { idx, mod ->
                val stepBase = 0.28f + (0.54f * (idx.toFloat() / modPlan.size.coerceAtLeast(1)))
                progressFraction = stepBase
                statusHeadline = "Fetching ${mod.title} (${idx + 1}/${modPlan.size})..."
                statusDetail = "Modrinth 1.16.5 Fabric · ${mod.category}"
                runCatching {
                    ensureModrinthJar1165(modsDir, mod.slug)
                }
            }

            // Step 4: Generate Aerix-Client-Pack.zip (pack_format: 6) + options.txt
            progressFraction = 0.88f
            statusHeadline = "Synthesizing Liquid Crystal 1.16.5 Resource Pack..."
            statusDetail = "Building transparent GUI, skybox, PvP tweaks & options.txt"
            if (AerixClientStore.installClientResourcePack) {
                AerixClientPackGenerator.buildAndApplyToInstance(context, gameDir)
            } else {
                AerixClientPackGenerator.applyClientOptionsTxt(gameDir)
            }

            // Step 5: Select instance & award Client XP
            Instances.setSelectedInstance(clientInstance)
            AerixClientStore.installsCompleted += 1
            AerixClientStore.awardXp(80)

            progressFraction = 1.0f
            statusHeadline = "Aerix Client 1.16.5 Ready to Launch!"
            statusDetail = "Fabric ${pickedLoader.version} · ${modsDir.listFiles()?.count { it.extension == "jar" } ?: 0} Client Mods · Crystal Pack Active"
            clientInstance
        }.onFailure { err ->
            lastError = err.message ?: "Installation failed"
            statusHeadline = "Setup paused"
            statusDetail = lastError.orEmpty()
        }.also {
            isInstalling = false
        }
    }

    /**
     * Rebuilds `Aerix-Client-Pack.zip` & `options.txt` in <300ms whenever the user toggles
     * Resource Pack Studio tweaks on an already-installed Client instance!
     */
    suspend fun rebuildPackOnlyIfInstalled(context: Context): Boolean = withContext(Dispatchers.IO) {
        val inst = findInstalledClientInstance() ?: return@withContext false
        runCatching {
            AerixClientPackGenerator.buildAndApplyToInstance(context, inst.gameDirectory)
            true
        }.getOrDefault(false)
    }

    private fun ensureModrinthJar1165(modsDir: File, slug: String) {
        // Skip re-downloading if a jar matching this slug prefix is already present
        val normalizedKey = slug.replace("-", "").lowercase()
        val alreadyExists = modsDir.listFiles()?.any { f ->
            f.isFile && f.extension.equals("jar", true) &&
                f.name.replace("-", "").replace("_", "").lowercase().contains(normalizedKey)
        } == true
        if (alreadyExists) return

        val loadersParam = URLEncoder.encode("[\"fabric\"]", "UTF-8")
        val gvParam = URLEncoder.encode("[\"$TARGET_MC_VERSION\"]", "UTF-8")
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
                    intArrayOf(0xFF1A475E.toInt(), 0xFF091A26.toInt()),
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
                color = 0xFFC8FAFF.toInt()
            }
            c.drawRoundRect(5.5f, 5.5f, 122.5f, 122.5f, 28f, 28f, rim)
            instance.encodeNewIcon(bmp)
            InstanceIconProvider.dropIcon(instance)
        }
    }
}
