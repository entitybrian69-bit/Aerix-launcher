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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.zip.ZipInputStream

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

enum class TrinkiesDeploymentStatus { INSTALLED, CURRENT, CONFLICT, FAILED }

data class TrinkiesDeploymentResult(
    val status: TrinkiesDeploymentStatus,
    val detail: String,
    val sha256: String? = null
)

object AerixClientInstaller {

    private const val TRINKIES_ARTIFACT_NAME = "trinkies-aerix.jar"
    private const val TRINKIES_MANIFEST_NAME = "trinkies-aerix.manifest.json"
    private const val TRINKIES_MANIFEST_FORMAT = "aerix.trinkies.install-manifest"

    private const val USER_AGENT =
        "entitybrian69-bit/Aerix-launcher/aerix-client (https://github.com/entitybrian69-bit/Aerix-launcher)"

    // JVM defaults adapt to the actual heap and runtime. Do not force large fixed G1 regions
    // or blanket GC flags on mobile; preserve custom flags on existing instances.
    private const val TUNED_CLIENT_JVM_ARGS = ""

    val availableVersions: List<ClientVersionSpec> = ClientVersions.RELEASES.map { version ->
        ClientVersionSpec(version, "Aerix Client $version", if (ClientVersions.modern(version))
            "Experimental · Java 25 · cape animation needs device testing · time/weather overrides unavailable" else "Fabric · version-matched optimization mods")
    }

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

    fun findInstalledClientInstance(mcVersion: String? = null): Instance? =
        findInstalledClientInstance(mcVersion, runCatching { Instances.loadAllInstances() }.getOrDefault(emptyList()))

    fun findInstalledClientInstance(mcVersion: String?, list: List<Instance>): Instance? = runCatching {
        if (mcVersion.isNullOrBlank()) {
            list.firstOrNull { it.isAerixClient }
                ?: list.firstOrNull { it.name?.startsWith("Aerix Client", ignoreCase = true) == true }
        } else {
            val expectedName = instanceNameFor(mcVersion)
            list.firstOrNull { inst ->
                inst.isAerixClient && (
                    inst.name.equals(expectedName, ignoreCase = true) ||
                        ClientVersions.resolve(inst.versionId, "") == mcVersion
                )
            } ?: list.firstOrNull { inst ->
                inst.name.equals(expectedName, ignoreCase = true)
            }
        }
    }.getOrNull()

    /**
     * Pure Competitive FPS + 3D Waving Capes stack (Iris removed so mobile GPUs run at full speed!).
     */
    fun buildModListForVersion(mcVersion: String): List<ClientModEntry> {
        val out = ArrayList<ClientModEntry>()
        out += ClientModEntry("fabric-api", "Fabric API", "Core")
        out += ClientModEntry("sodium", "Sodium", "Chunk rendering")
        if (ClientVersions.supportsPodium(mcVersion)) {
            out += ClientModEntry("podium", "Podium", "Sodium / Android compatibility")
        }
        out += ClientModEntry("lithium", "Lithium", "Tick optimization")
        out += ClientModEntry("ferrite-core", "FerriteCore", "Memory optimization")
        out += ClientModEntry("immediatelyfast", "ImmediatelyFast", "Entity & HUD Batching")
        out += ClientModEntry("entityculling", "EntityCulling", "Wall Occlusion FPS")
        out += ClientModEntry("modernfix", "ModernFix", "Loading & memory")
        out += ClientModEntry("krypton", "Krypton", "Fast Netty Stack")
        if (mcVersion == "1.16.5" || mcVersion == "1.18.2" || mcVersion == "1.19.4") {
            out += ClientModEntry("lazydfu", "LazyDFU", "Startup optimization")
            out += ClientModEntry("starlight", "Starlight", "Lighting Engine")
        }
        out += ClientModEntry("wavey-capes", "WaveyCapes", "3D Waving Capes")
        out += ClientModEntry("modmenu", "Mod Menu", "Client UI")
        out += ClientModEntry("appleskin", "AppleSkin", "Saturation")
        out += ClientModEntry("logical-zoom", "Logical Zoom", "Zoom")
        return out
    }

    /**
     * Installs the Java-8 Trinkies bridge without touching unowned files. Updates are allowed only
     * when the visible sidecar manifest proves that the existing artifact is the exact prior build.
     */
    @Synchronized
    fun deployBundledTrinkies(context: Context, modsDir: File): TrinkiesDeploymentResult {
        var artifactTemp: File? = null
        try {
            if (!modsDir.isDirectory && !modsDir.mkdirs()) {
                return TrinkiesDeploymentResult(TrinkiesDeploymentStatus.FAILED, "Could not create the instance mods folder")
            }
            val bundled = context.assets.open("components/aerix_client/aerix_client_mod.jar").use { it.readBytes() }
            val bundledMetadata = readFabricModMetadata(bundled)
                ?: return TrinkiesDeploymentResult(TrinkiesDeploymentStatus.FAILED, "The bundled Trinkies JAR has no valid Fabric descriptor")
            if (bundledMetadata.optString("id") != "trinkies" || bundledMetadata.optString("environment") != "client") {
                return TrinkiesDeploymentResult(TrinkiesDeploymentStatus.FAILED, "The bundled JAR is not a client-only Trinkies mod")
            }
            val bundledHash = sha256(bundled)
            val target = File(modsDir, TRINKIES_ARTIFACT_NAME)
            val manifest = File(modsDir, TRINKIES_MANIFEST_NAME)
            val manifestJson = if (manifest.exists()) runCatching {
                org.json.JSONObject(manifest.readText(Charsets.UTF_8))
            }.getOrNull() else null
            val manifestOwned = manifestJson?.let { isOurTrinkiesManifest(it) } == true
            if (manifest.exists() && !manifestOwned) {
                return TrinkiesDeploymentResult(
                    TrinkiesDeploymentStatus.CONFLICT,
                    "${manifest.name} already exists and is not a valid Aerix install manifest; it was left untouched"
                )
            }

            val legacyAnimatorJar = File(modsDir, "aerix-client-animator.jar")
            if (legacyAnimatorJar.exists()) {
                return TrinkiesDeploymentResult(
                    TrinkiesDeploymentStatus.CONFLICT,
                    "${legacyAnimatorJar.name} is present; it was left untouched. Review it before installing the renamed Trinkies mod"
                )
            }
            val otherTrinkies = findFabricModJar(modsDir, "trinkies", target)
            if (otherTrinkies != null) {
                return TrinkiesDeploymentResult(
                    TrinkiesDeploymentStatus.CONFLICT,
                    "${otherTrinkies.name} already provides Trinkies; no mod was replaced or removed"
                )
            }

            val existingHash = if (target.isFile) sha256(target) else null
            val isCurrent = existingHash == bundledHash
            val priorHashMatches = manifestOwned && existingHash != null &&
                manifestJson?.optString("sha256") == existingHash
            if (existingHash != null && !isCurrent && !priorHashMatches) {
                return TrinkiesDeploymentResult(
                    TrinkiesDeploymentStatus.CONFLICT,
                    "${target.name} exists without a matching Aerix hash manifest; it was left untouched"
                )
            }

            if (isCurrent) {
                if (!manifestOwned || manifestJson?.optString("sha256") != bundledHash) {
                    if (!writeTrinkiesManifest(manifest, bundledMetadata.optString("version"), bundledHash, manifestOwned)) {
                        return TrinkiesDeploymentResult(
                            TrinkiesDeploymentStatus.FAILED,
                            "Trinkies is current, but its install manifest could not be written",
                            bundledHash
                        )
                    }
                }
                return TrinkiesDeploymentResult(TrinkiesDeploymentStatus.CURRENT, "Trinkies is already current", bundledHash)
            }

            artifactTemp = File.createTempFile(".trinkies-aerix-", ".tmp", modsDir)
            FileOutputStream(artifactTemp).use { output ->
                output.write(bundled)
                output.fd.sync()
            }
            if (sha256(artifactTemp) != bundledHash) {
                return TrinkiesDeploymentResult(TrinkiesDeploymentStatus.FAILED, "Temporary Trinkies JAR failed SHA-256 verification")
            }
            if (!replaceManagedFile(artifactTemp, target, mayReplace = existingHash != null && priorHashMatches)) {
                return TrinkiesDeploymentResult(TrinkiesDeploymentStatus.FAILED, "Could not atomically install ${target.name}")
            }
            artifactTemp = null
            if (!writeTrinkiesManifest(manifest, bundledMetadata.optString("version"), bundledHash, manifestOwned)) {
                return TrinkiesDeploymentResult(
                    TrinkiesDeploymentStatus.FAILED,
                    "The mod JAR was written, but the install manifest could not be updated; it will be rechecked next launch",
                    bundledHash
                )
            }
            return TrinkiesDeploymentResult(TrinkiesDeploymentStatus.INSTALLED, "Trinkies installed and SHA-256 verified", bundledHash)
        } catch (error: Exception) {
            android.util.Log.w("AerixClient", "Could not install bundled Trinkies", error)
            return TrinkiesDeploymentResult(TrinkiesDeploymentStatus.FAILED, error.message ?: "Trinkies installation failed")
        } finally {
            artifactTemp?.delete()
        }
    }

    private fun isOurTrinkiesManifest(json: org.json.JSONObject): Boolean =
        json.optString("format") == TRINKIES_MANIFEST_FORMAT &&
            json.optInt("schema", -1) == 1 &&
            json.optString("modId") == "trinkies" &&
            json.optString("artifact") == TRINKIES_ARTIFACT_NAME &&
            json.optString("sha256").matches(Regex("[0-9a-f]{64}"))

    private fun writeTrinkiesManifest(
        manifest: File,
        modVersion: String,
        sha256: String,
        mayReplace: Boolean
    ): Boolean {
        val json = org.json.JSONObject()
            .put("format", TRINKIES_MANIFEST_FORMAT)
            .put("schema", 1)
            .put("modId", "trinkies")
            .put("artifact", TRINKIES_ARTIFACT_NAME)
            .put("version", modVersion)
            .put("sha256", sha256)
        val temporary = runCatching { File.createTempFile(".trinkies-manifest-", ".tmp", manifest.parentFile) }
            .getOrNull() ?: return false
        return try {
            FileOutputStream(temporary).use { output ->
                output.write(json.toString(2).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            replaceManagedFile(temporary, manifest, mayReplace).also { replaced ->
                if (replaced) temporary.delete()
            }
        } catch (_: Exception) {
            false
        } finally {
            temporary.delete()
        }
    }

    /** Renames over an existing destination only after its ownership hash was checked. */
    private fun replaceManagedFile(temporary: File, target: File, mayReplace: Boolean): Boolean {
        if (!target.exists()) return temporary.renameTo(target)
        if (!mayReplace) return false
        if (temporary.renameTo(target)) return true
        val backup = File(target.parentFile, ".${target.name}.${java.util.UUID.randomUUID()}.bak")
        if (!target.renameTo(backup)) return false
        if (temporary.renameTo(target)) {
            backup.delete()
            return true
        }
        backup.renameTo(target)
        return false
    }

    private fun readFabricModMetadata(bytes: ByteArray): org.json.JSONObject? = runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return@use null
                if (entry.name == "fabric.mod.json") {
                    val descriptor = zip.readBytes()
                    if (descriptor.size > 65_536) return@use null
                    return@use org.json.JSONObject(String(descriptor, Charsets.UTF_8))
                }
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
    }.getOrNull()

    private fun findFabricModJar(modsDir: File, id: String, except: File): File? =
        modsDir.listFiles()?.firstOrNull { file ->
            file.isFile && file.extension.equals("jar", true) && file.canonicalFile != except.canonicalFile &&
                readFabricModMetadata(file)?.optString("id") == id
        }

    private fun readFabricModMetadata(file: File): org.json.JSONObject? = runCatching {
        java.util.zip.ZipFile(file).use { zip ->
            val entry = zip.getEntry("fabric.mod.json") ?: return@use null
            if (entry.size < 0 || entry.size > 65_536) return@use null
            zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { org.json.JSONObject(it.readText()) }
        }
    }.getOrNull()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /**
     * Upgrades an already-installed Client instance right before Launch so users immediately get
     * `wavey-capes`, `immediatelyfast`, the managed Trinkies artifact, `iris` removal, `AERIX CLIENT`
     * title logo, `images.jpeg` forest background, and genuine vanilla animated gear!
     */
    suspend fun syncExistingInstanceForLaunch(context: Context, instance: Instance, mcVersion: String) =
        withContext(Dispatchers.IO) {
            runCatching {
                val gameDir = instance.gameDirectory.apply { mkdirs() }
                val modsDir = File(gameDir, "mods").apply { mkdirs() }
                migrateLegacyClientIcon(context, instance)
                if (instance.jvmArgs.isNullOrBlank()) {
                    instance.argsMode = Instance.ARGS_MODE_MERGE_DEFAULT_FIRST
                    instance.jvmArgs = TUNED_CLIENT_JVM_ARGS
                    instance.write()
                }
                val trinkiesResult = deployBundledTrinkies(context, modsDir)
                if (trinkiesResult.status == TrinkiesDeploymentStatus.CONFLICT || trinkiesResult.status == TrinkiesDeploymentStatus.FAILED) {
                    android.util.Log.w("AerixClient", "Trinkies not changed: ${trinkiesResult.detail}")
                }
                // Ensure essential new upgrade jars exist even on older installs
                for (slug in listOf("wavey-capes", "immediatelyfast", "entityculling")) {
                    runCatching { ensureModrinthJar(modsDir, slug, mcVersion, depth = 0) }
                }
                if (ClientVersions.supportsPodium(mcVersion)) {
                    runCatching { ensurePodiumAndSodium(modsDir, mcVersion) }.onFailure { error ->
                        android.util.Log.w("AerixClient", "Podium unavailable", error)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(context, "Podium / Sodium unavailable for $mcVersion: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
                ensureWaveyCapes(modsDir, mcVersion)
                AerixClientPackGenerator.ensurePackUpToDate(context, gameDir, mcVersion)
            }
        }

    /**
     * Fast synchronous local-only sync safe to call inside `GameActivity.onCreate` (`attachForInstance`)
     * right after Pojav's `MinecraftDownloader` has verified `<mcVersion>.jar` on disk!
     */
    @JvmStatic
    fun syncLocalAssetsAtGameStart(context: Context, instance: Instance) {
        runCatching {
            val gameDir = instance.gameDirectory?.apply { mkdirs() } ?: return
            val modsDir = File(gameDir, "mods").apply { mkdirs() }
            migrateLegacyClientIcon(context, instance)
            val mcVer = AerixClientPackGenerator.resolveMcVersionFromInstance(
                instance.versionId ?: instance.name,
                AerixClientStore.selectedClientVersion
            )
            val trinkiesResult = deployBundledTrinkies(context, modsDir)
            if (trinkiesResult.status == TrinkiesDeploymentStatus.CONFLICT || trinkiesResult.status == TrinkiesDeploymentStatus.FAILED) {
                android.util.Log.w("AerixClient", "Trinkies not changed: ${trinkiesResult.detail}")
            }
            // Launches from Home bypass the Client tab's network sync; repair missing cape mod here too.
            runCatching { ensureWaveyCapes(modsDir, mcVer) }.onFailure { error ->
                android.util.Log.w("AerixClient", "Waving cape mod unavailable", error)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Waving capes unavailable: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            if (ClientVersions.supportsPodium(mcVer)) {
                runCatching { ensurePodiumAndSodium(modsDir, mcVer) }.onFailure { error ->
                    android.util.Log.w("AerixClient", "Podium unavailable", error)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(context, "Podium / Sodium unavailable for $mcVer: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
            AerixClientPackGenerator.ensurePackUpToDate(context, gameDir, mcVer)
            ClientVisualBridge.sync(gameDir)
            runCatching { ClientAdaptiveBridge.restoreAtLaunch(gameDir) }.onFailure { error ->
                android.util.Log.w("AerixClient", "Could not restore adaptive render distance", error)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Adaptive distance recovery failed: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            ClientPerformanceSettings.init(context)
            ClientPerformanceSettings.applyAtLaunch(gameDir, mcVer)
            runCatching { ClientInstanceOptions.applyAtLaunch(gameDir) }.onFailure { error ->
                android.util.Log.w("AerixClient", "Queued options could not be applied", error)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Queued Client options were not applied: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
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
                statusDetail = "Configuring high-FPS client directory"

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
                        existing.jvmArgs = TUNED_CLIENT_JVM_ARGS
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
                            inst.jvmArgs = TUNED_CLIENT_JVM_ARGS
                        },
                        folderSlug
                    )
                }

                writeCustomClientInstanceIcon(context, clientInstance)
                val gameDir = clientInstance.gameDirectory.apply { mkdirs() }
                val modsDir = File(gameDir, "mods").apply { mkdirs() }

                val trinkiesResult = deployBundledTrinkies(context, modsDir)
                check(trinkiesResult.status == TrinkiesDeploymentStatus.INSTALLED ||
                    trinkiesResult.status == TrinkiesDeploymentStatus.CURRENT) { trinkiesResult.detail }

                val modPlan = buildModListForVersion(mcVersion)
                val missingMods = mutableListOf<String>()
                modPlan.forEachIndexed { idx, mod ->
                    progressFraction = 0.30f + (0.55f * (idx.toFloat() / modPlan.size.coerceAtLeast(1)))
                    statusHeadline = "Downloading ${mod.title} (${idx + 1}/${modPlan.size})..."
                    statusDetail = "Minecraft $mcVersion · ${mod.category}"
                    val installed = runCatching {
                        if (mod.slug == "podium") {
                            ensurePodiumAndSodium(modsDir, mcVersion)
                            true
                        } else ensureModrinthJar(modsDir, mod.slug, mcVersion, depth = 0)
                    }.getOrDefault(false)
                    if (!installed && mod.slug != "wavey-capes") missingMods += mod.title
                }

                val capeWarning = runCatching { ensureWaveyCapes(modsDir, mcVersion) }.exceptionOrNull()
                progressFraction = 0.90f
                statusHeadline = "Applying Aerix Client Pack & FPS Engine..."
                statusDetail = "Syncing AERIX CLIENT title, capes & Sodium"
                AerixClientPackGenerator.buildAndApplyToInstance(context, gameDir, mcVersion)

                Instances.setSelectedInstance(clientInstance)
                AerixClientStore.installsCompleted += 1
                AerixClientStore.persist()

                progressFraction = 1.0f
                statusHeadline = "$targetName ready!"
                if (capeWarning != null) missingMods += "WaveyCapes"
                statusDetail = if (missingMods.isEmpty()) "Ready to launch" else "Unavailable: ${missingMods.joinToString()}"
                if (missingMods.isNotEmpty()) android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Not installed for $mcVersion: ${missingMods.joinToString()}", android.widget.Toast.LENGTH_LONG).show()
                }
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

    /** WaveyCapes is essential to the waving option. Do not silently claim success if absent. */
    private fun ensureWaveyCapes(modsDir: File, mcVersion: String) {
        fun present(): Boolean = modsDir.listFiles()?.any {
            it.isFile && it.extension.equals("jar", true) &&
                it.name.replace("-", "").replace("_", "").contains("waveycapes", true)
        } == true
        if (present()) return
        runCatching { ensureModrinthJar(modsDir, "wavey-capes", mcVersion, 0) }
        if (present()) return
        // Official version-specific release fallback; keep any existing user-installed copy intact.
        val supported = mcVersion in setOf("1.16.5", "1.18.2", "1.19.4", "1.20.1", "1.20.2", "1.20.4", "1.20.6", "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.8", "1.21.10", "1.21.11", "26.1", "26.2", "26.3")
        if (!supported) error("WaveyCapes is not available for Minecraft $mcVersion")
        val filename = "waveycapes-fabric-1.11.3-mc$mcVersion.jar"
        val target = File(modsDir, filename)
        val temp = File(modsDir, "$filename.part")
        try {
            val request = Request.Builder().url("https://github.com/tr7zw/WaveyCapes/releases/download/1.11.3/$filename")
                .header("User-Agent", USER_AGENT).build()
            SocialRealtime.restClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("WaveyCapes download failed (HTTP ${response.code})")
                val body = response.body ?: error("WaveyCapes download was empty")
                FileOutputStream(temp).use { output -> body.byteStream().use { it.copyTo(output) } }
            }
            java.util.zip.ZipFile(temp).use { zip ->
                require(zip.getEntry("fabric.mod.json") != null) { "Invalid WaveyCapes download" }
            }
            check(temp.renameTo(target)) { "Could not save WaveyCapes" }
        } finally {
            temp.delete()
        }
    }

    /** Read Fabric metadata so renamed jars and unrelated Sodium add-ons do not cause duplicates. */
    private fun fabricModMetadata(modsDir: File, id: String): org.json.JSONObject? {
        modsDir.listFiles()?.forEach { file ->
            if (!file.isFile || !file.extension.equals("jar", true)) return@forEach
            val metadata = runCatching {
                java.util.zip.ZipFile(file).use { zip ->
                    val entry = zip.getEntry("fabric.mod.json") ?: return@use null
                    if (entry.size < 0 || entry.size > 65536) return@use null
                    val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    org.json.JSONObject(text).takeIf { it.optString("id") == id }
                }
            }.getOrNull()
            if (metadata != null) return metadata
        }
        return null
    }

    private fun hasFabricMod(modsDir: File, id: String) = fabricModMetadata(modsDir, id) != null

    /** Podium requires Sodium; do not install a dependency-breaking Podium jar by itself. */
    private fun ensurePodiumAndSodium(modsDir: File, mcVersion: String) {
        if (!hasFabricMod(modsDir, "sodium")) {
            check(ensureModrinthJar(modsDir, "sodium", mcVersion, 0) && hasFabricMod(modsDir, "sodium")) {
                "compatible Sodium not found"
            }
        }
        val sodiumVersion = fabricModMetadata(modsDir, "sodium")?.optString("version").orEmpty()
        check(ClientVersions.supportsPodiumSodiumVersion(sodiumVersion)) {
            "Sodium $sodiumVersion is too old for Podium (requires 0.5.13 or newer)"
        }
        if (!hasFabricMod(modsDir, "podium")) {
            check(ensureModrinthJar(modsDir, "podium", mcVersion, 0) && hasFabricMod(modsDir, "podium")) {
                "compatible Podium not found"
            }
        }
    }

    private fun ensureModrinthJar(modsDir: File, projectSlugOrId: String, mcVersion: String, depth: Int): Boolean {
        val fabricId = when (projectSlugOrId) {
            "sodium", "AANobbMI" -> "sodium"
            "podium", "fW8woQj4" -> "podium"
            else -> null
        }
        if (fabricId != null && hasFabricMod(modsDir, fabricId)) return true
        val normalizedKey = projectSlugOrId.replace("-", "").lowercase()
        val alreadyExists = fabricId == null && modsDir.listFiles()?.any { f ->
            f.isFile && f.extension.equals("jar", true) &&
                f.name.replace("-", "").replace("_", "").lowercase().contains(normalizedKey)
        } == true
        if (alreadyExists) return true

        val loadersParam = URLEncoder.encode("[\"fabric\"]", "UTF-8")
        val gvParam = URLEncoder.encode("[\"$mcVersion\"]", "UTF-8")
        val url = "https://api.modrinth.com/v2/project/$projectSlugOrId/version?loaders=$loadersParam&game_versions=$gvParam"

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        val bodyStr = SocialRealtime.restClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return false
            resp.body?.string().orEmpty()
        }
        if (bodyStr.isBlank()) return false
        val versions = JSONArray(bodyStr)
        if (versions.length() == 0) return false

        val verObj = versions.getJSONObject(0)

        // Resolve required transitive Modrinth dependencies (max depth 1)
        if (depth < 1) {
            val deps = verObj.optJSONArray("dependencies")
            if (deps != null) {
                for (d in 0 until deps.length()) {
                    val dep = deps.getJSONObject(d)
                    if (dep.optString("dependency_type") == "required") {
                        val depProjId = dep.optString("project_id")
                        if (depProjId.isNotBlank() && depProjId != "P7dR8mSH" &&
                            !(depProjId == "AANobbMI" && hasFabricMod(modsDir, "sodium"))) {
                            runCatching { ensureModrinthJar(modsDir, depProjId, mcVersion, depth + 1) }
                        }
                    }
                }
            }
        }

        val files = verObj.optJSONArray("files") ?: return false
        if (files.length() == 0) return false
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
        if (dlUrl.isBlank() || fileName.isBlank() || !fileName.endsWith(".jar", true) ||
            fileName != File(fileName).name || fileName.contains('\\')) return false

        val targetFile = File(modsDir, fileName)
        if (targetFile.isFile && targetFile.length() > 1024L) return true

        val tmpFile = File(modsDir, "$fileName.part")
        val dlReq = Request.Builder()
            .url(dlUrl)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        try {
            SocialRealtime.restClient.newCall(dlReq).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val stream = resp.body?.byteStream() ?: return false
                FileOutputStream(tmpFile).use { out -> stream.copyTo(out) }
            }
            if (tmpFile.length() <= 512L) return false
            val sha512 = chosen.optJSONObject("hashes")?.optString("sha512").orEmpty()
            if (sha512.isNotBlank()) {
                val digest = java.security.MessageDigest.getInstance("SHA-512")
                tmpFile.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                if (!actual.equals(sha512, ignoreCase = true)) return false
            }
            java.util.zip.ZipFile(tmpFile).use { zip ->
                if (zip.getEntry("fabric.mod.json") == null) return false
            }
            return tmpFile.renameTo(targetFile)
        } finally { tmpFile.delete() }
    }

    /** Refresh only the icon generated by older Aerix builds, preserving custom icons. */
    private fun migrateLegacyClientIcon(context: Context, instance: Instance) {
        runCatching {
            val path = instance.ownIconLocation ?: return
            if (!path.isFile) return
            val old = BitmapFactory.decodeFile(path.absolutePath) ?: return
            try {
                if (old.width != 128 || old.height != 128) return
                val samples = IntArray(ClientLogoMigration.sampleCount()) { index ->
                    old.getPixel(ClientLogoMigration.sampleX(index), ClientLogoMigration.sampleY(index))
                }
                if (ClientLogoMigration.matchesLegacy(old.width, old.height, samples)) {
                    writeCustomClientInstanceIcon(context, instance)
                }
            } finally {
                old.recycle()
            }
        }.onFailure { android.util.Log.w("AerixClient", "Could not update the Client icon", it) }
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
            val logo = BitmapFactory.decodeResource(context.resources, R.drawable.ic_aerix_mark)
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
