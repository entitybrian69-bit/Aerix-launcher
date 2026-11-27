package net.kdt.aerix.desktop.core.instance

import net.kdt.aerix.desktop.app.AppEnv
import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.json.Json
import net.kdt.aerix.desktop.core.json.JsonObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * One Minecraft installation ("instance"), the desktop counterpart of
 * `net.kdt.pojavlaunch.instances.Instance`.
 *
 * The on-disk layout intentionally matches the Android launcher's so an exported Aerix instance
 * folder can be dropped in and read: `<instance>/instance.json` holds the metadata, the game itself
 * lives in `<instance>/.minecraft` (or the shared directory when [sharedData] is set), and
 * `<instance>/icon.png` is the artwork. Fields that only make sense on Android (`renderer`,
 * `controlLayout`, `selectedRuntime`) are parsed and kept in [extraFields] but are never used to
 * launch on Windows, and never dropped when the file is rewritten.
 */
class Instance {
    var name: String = "Untitled"
        set(value) { field = value.ifBlank { "Untitled" } }

    /** Folder name on disk; sanitised against Windows reserved names. */
    lateinit var id: String
    lateinit var root: Path

    /** The Minecraft version id this instance runs (a loader profile id for modded instances). */
    var versionId: String = ""
    var loader: String = Loader.VANILLA
    var loaderVersion: String? = null
    var mcVersion: String = ""

    var iconFile: String? = null
    var color: Int = DEFAULT_COLORS[0]

    /** Share one `.minecraft` folder between instances (Android `sharedData`). */
    var sharedData: Boolean = true   // install once, play many: see core/instance/InstanceLinks.kt

    var jvmArgs: String = ""
    var argsMode: Int = 0
    var memoryMb: Int = 0            // 0 = follow the global setting
    var javaPath: String = ""        // "" = follow the global setting / auto
    var javaMajor: Int = 0           // requirement resolved from the version JSON

    var group: String = ""
    var favorite: Boolean = false
    var pinned: Boolean = false
    var notes: String = ""

    var created: Long = System.currentTimeMillis()
    var lastPlayed: Long = 0
    var playSeconds: Long = 0
    var launchCount: Int = 0

    var modpackProjectId: String? = null
    var modpackSource: String? = null
    var modpackIconUrl: String? = null
    var aerixPackId: String? = null

    /** Field order/values we did not model; written back verbatim to stay lossless. */
    val extraFields = LinkedHashMap<String, net.kdt.aerix.desktop.core.json.JsonValue>()

    val gameDir: Path
        get() = if (sharedData) sharedRoot() else root.resolve(".minecraft")

    fun sharedRoot(): Path = Instances.sharedDataDir()

    val iconPath: Path? get() = iconFile?.let { root.resolve(it) }

    fun effectiveMemoryMb(): Int = if (memoryMb > 0) memoryMb else Settings.int("memoryAllocationMb", defaultMemoryMb())

    fun effectiveJavaArgs(): String {
        val globalArgs = Settings.string("jvmArgs", defaultJvmArgs())
        if (jvmArgs.isBlank()) return globalArgs
        return when (argsMode) {
            ARGS_MODE_REPLACE -> jvmArgs
            ARGS_MODE_MERGE_INSTANCE_FIRST -> "$jvmArgs $globalArgs"
            else -> "$globalArgs $jvmArgs"
        }
    }

    fun subtitle(): String = buildString {
        append(mcVersion.ifBlank { versionId })
        if (loader != Loader.VANILLA) {
            append(" · ")
            append(Loader.displayName(loader))
            loaderVersion?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
        }
    }

    // ---- persistence -----------------------------------------------------------------------

    fun toJson(): JsonObject {
        val doc = JsonObject()
        doc.put("formatVersion", 1)
        doc.put("instanceName", name)
        doc.put("versionId", versionId)
        doc.put("mcVersion", mcVersion)
        doc.put("loader", loader)
        doc.put("loaderVersion", loaderVersion)
        doc.put("iconFile", iconFile)
        doc.put("color", color.toLong())
        doc.put("sharedData", sharedData)
        doc.put("jvmArgs", jvmArgs)
        doc.put("argsMode", argsMode.toLong())
        doc.put("memoryMb", memoryMb.toLong())
        doc.put("javaPath", javaPath)
        doc.put("javaMajor", javaMajor.toLong())
        doc.put("group", group)
        doc.put("favorite", favorite)
        doc.put("pinned", pinned)
        doc.put("notes", notes)
        doc.put("created", created)
        doc.put("lastPlayed", lastPlayed)
        doc.put("playSeconds", playSeconds)
        doc.put("launchCount", launchCount.toLong())
        doc.put("modpackProjectId", modpackProjectId)
        doc.put("modpackSource", modpackSource)
        doc.put("modpackIconUrl", modpackIconUrl)
        doc.put("aerixPackId", aerixPackId)
        // keep unknown keys from an imported Android/desktop instance
        extraFields.forEach { (k, v) -> if (!doc.has(k)) doc.put(k, v) }
        return doc
    }

    fun save() {
        try {
            val doc = toJson()
            val tmp = root.resolve("instance.json.tmp")
            Files.writeString(tmp, Json.pretty(doc), StandardCharsets.UTF_8)
            Files.move(tmp, root.resolve("instance.json"), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            Log.e("instance", "could not save ${name}: ${e.message}")
        }
    }

    fun touchLaunch() {
        lastPlayed = System.currentTimeMillis()
        launchCount++
        save()
    }

    fun addPlayTime(seconds: Long) {
        if (seconds <= 0) return
        playSeconds += seconds
        save()
    }

    companion object {
        const val ARGS_MODE_REPLACE = 0
        const val ARGS_MODE_MERGE_DEFAULT_FIRST = 1
        const val ARGS_MODE_MERGE_INSTANCE_FIRST = 2

        val DEFAULT_COLORS = intArrayOf(
            0xFF8DEFE0.toInt(), 0xFF91E9FF.toInt(), 0xFFB7A9FF.toInt(), 0xFFF3A9D9.toInt(),
            0xFF73E5B0.toInt(), 0xFFFFC777.toInt(), 0xFF37DCEC.toInt(), 0xFF7A71F5.toInt()
        )

        fun defaultMemoryMb(): Int {
            val total = AppEnv.totalMemoryMb
            return ((total / 4) / 512 * 512).coerceIn(1024, maxOf(1024, total - 2048)).let {
                if (it < 1024) 1024 else it
            }
        }

        fun defaultJvmArgs(): String = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 " +
                "-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch " +
                "-XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M " +
                "-XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 " +
                "-XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 " +
                "-XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem " +
                "-XX:MaxTenuringThreshold=1"

        fun fromJson(root: Path, doc: JsonObject): Instance {
            val inst = Instance()
            inst.root = root
            inst.id = root.fileName.toString()
            inst.name = doc.str("instanceName") ?: doc.str("name") ?: inst.id
            inst.versionId = doc.str("versionId") ?: ""
            inst.mcVersion = doc.str("mcVersion") ?: guessMcVersion(inst.versionId)
            inst.loader = Loader.normalise(doc.str("loader"))
            inst.loaderVersion = doc.str("loaderVersion")
            inst.iconFile = doc.str("iconFile")
            inst.color = doc.num("color")?.toInt() ?: DEFAULT_COLORS[0]
            inst.sharedData = doc.bool("sharedData") ?: false
            inst.jvmArgs = doc.str("jvmArgs") ?: ""
            inst.argsMode = doc.num("argsMode")?.toInt() ?: 0
            inst.memoryMb = doc.num("memoryMb")?.toInt() ?: 0
            inst.javaPath = doc.str("javaPath") ?: ""
            inst.javaMajor = doc.num("javaMajor")?.toInt() ?: 0
            inst.group = doc.str("group") ?: ""
            inst.favorite = doc.bool("favorite") ?: false
            inst.pinned = doc.bool("pinned") ?: false
            inst.notes = doc.str("notes") ?: ""
            inst.created = doc.num("created") ?: System.currentTimeMillis()
            inst.lastPlayed = doc.num("lastPlayed") ?: 0
            inst.playSeconds = doc.num("playSeconds") ?: 0
            inst.launchCount = doc.num("launchCount")?.toInt() ?: 0
            inst.modpackProjectId = doc.str("modpackProjectId")
            inst.modpackSource = doc.str("modpackSource")
            inst.modpackIconUrl = doc.str("modpackIconUrl")
            inst.aerixPackId = doc.str("aerixPackId")
            val known = setOf(
                "formatVersion", "instanceName", "name", "versionId", "mcVersion", "loader", "loaderVersion",
                "iconFile", "color", "sharedData", "jvmArgs", "argsMode", "memoryMb", "javaPath", "javaMajor",
                "group", "favorite", "pinned", "notes", "created", "lastPlayed", "playSeconds", "launchCount",
                "modpackProjectId", "modpackSource", "modpackIconUrl", "aerixPackId"
            )
            doc.fields.forEach { (k, v) -> if (k !in known) inst.extraFields[k] = v }
            return inst
        }

        /** Android instance files do not always carry `mcVersion`; derive it from the version id. */
        fun guessMcVersion(versionId: String): String {
            val m = Regex("(\\d+\\.\\d+(?:\\.\\d+)?)").find(versionId) ?: return ""
            return m.groupValues[1]
        }
    }
}

/** Mod loader families. */
object Loader {
    const val VANILLA = "vanilla"
    const val FABRIC = "fabric"
    const val QUILT = "quilt"
    const val FORGE = "forge"
    const val NEOFORGE = "neoforge"
    const val OPTIFINE = "optifine"

    /**
     * Better than Adventure is not a Minecraft mod loader — it is a total conversion with its own
     * client jar that inherits from `b1.7.3`. It still installs the same way (a profile JSON plus a
     * library), so it lives in this list rather than a separate concept.
     */
    const val BTA = "bta"

    val all = listOf(VANILLA, FABRIC, QUILT, FORGE, NEOFORGE, OPTIFINE, BTA)

    fun displayName(id: String): String = when (id.lowercase()) {
        VANILLA -> "Vanilla"
        FABRIC -> "Fabric"
        QUILT -> "Quilt"
        FORGE -> "Forge"
        NEOFORGE -> "NeoForge"
        OPTIFINE -> "OptiFine"
        BTA -> "Better than Adventure"
        else -> id.replaceFirstChar { it.uppercase() }
    }

    fun normalise(raw: String?): String {
        val v = raw?.lowercase()?.trim().orEmpty()
        return when {
            v.startsWith("fabric") -> FABRIC
            v.startsWith("quilt") -> QUILT
            v.startsWith("neoforge") -> NEOFORGE
            v.startsWith("forge") -> FORGE
            v.startsWith("optifine") -> OPTIFINE
            v == "bta" || v.startsWith("better than adventure") -> BTA
            v.isEmpty() -> VANILLA
            else -> VANILLA
        }
    }

    /** Fabric/Quilt use the same metadata API shape; Forge/NeoForge need an installer. */
    fun isFabricLike(id: String) = id == FABRIC || id == QUILT
}

/**
 * The instance store: scans, creates, duplicates and trashes instances.
 *
 * Deleting never destroys data outright. An instance is moved into `instances/.trash/<name>-<time>`
 * first, and the Trash screen can restore it or empty it explicitly — the Android app's equivalent
 * promise ("never silently delete worlds").
 */
object Instances {
    private val instances = ArrayList<Instance>()
    // Copy-on-write, like Log and GameLauncher: listeners are registered as screens are built, but
    // notifyChanged runs on whatever thread performed the change, which is usually an IO thread.
    // With a plain ArrayList, `toList()` copies while another thread is appending and dies with
    // ArrayIndexOutOfBoundsException - it did exactly that on the Windows runner, inside
    // ArrayList.add, one frame below HomeScreen's constructor.
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun rootDir(): Path = Settings.string("gameDirectory", AppEnv.defaultGameDir.toString()).let { Path.of(it) }
        .resolve("instances")

    fun sharedDataDir(): Path = rootDir().parent.resolve("shared")

    fun list(): List<Instance> = instances

    fun groups(): List<String> = instances.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()

    fun onChange(listener: () -> Unit) { listeners.add(listener) }

    private fun notifyChanged() = listeners.forEach { runCatching { it() } }

    fun load() {
        instances.clear()
        val dir = rootDir()
        runCatching { Files.createDirectories(dir) }
        Files.list(dir).use { stream ->
            stream.filter { Files.isDirectory(it) }.filter { it.fileName.toString() != ".trash" }.forEach { d ->
                val meta = d.resolve("instance.json")
                if (Files.isRegularFile(meta)) {
                    runCatching {
                        val doc = Json.parseObject(Files.readString(meta, StandardCharsets.UTF_8))
                        instances.add(Instance.fromJson(d, doc))
                    }.onFailure { Log.w("instances", "could not read $meta: ${it.message}") }
                }
            }
        }
        // A folder that has a .minecraft but no metadata (an imported Aerix/vanilla install) is adopted.
        Files.list(dir).use { stream ->
            stream.filter { Files.isDirectory(it) }.filter { it.fileName.toString() != ".trash" }.forEach { d ->
                if (Files.isDirectory(d.resolve(".minecraft")) && Files.notExists(d.resolve("instance.json"))) {
                    val inst = Instance()
                    inst.root = d
                    inst.id = d.fileName.toString()
                    inst.name = d.fileName.toString()
                    inst.created = runCatching { Files.getLastModifiedTime(d).toMillis() }.getOrDefault(System.currentTimeMillis())
                    inst.save()
                    instances.add(inst)
                }
            }
        }
        instances.sortWith(compareByDescending<Instance> { it.lastPlayed }.thenBy { it.name.lowercase() })
        notifyChanged()
    }

    fun uniqueName(base: String): String {
        var candidate = sanitiseName(base)
        var i = 2
        while (instances.any { it.name.equals(candidate, true) }) {
            candidate = "${sanitiseName(base)} ($i)"
            i++
        }
        return candidate
    }

    /** Windows-safe folder/file name: reserved characters and device names are replaced. */
    fun sanitiseName(raw: String): String {
        // `&`, `%`, `^` and `;` are legal in a Windows folder name but are shell and cmd.exe
        // metacharacters. Nothing should hand these paths to a shell (see InstanceLinks and
        // WinLinks), and this is defence in depth: the name is also what the game's
        // `--gameDir` argument carries, where `%` can be expanded by launcher scripts.
        var s = raw.trim().replace(Regex("[<>:\"/\\\\|?*&%^;`\\$\\u0000-\\u001F]"), "-")
        s = s.trimEnd('.', ' ')
        if (s.isEmpty()) s = "Untitled"
        val reserved = setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        )
        if (s.uppercase().substringBefore('.') in reserved) s = "_$s"
        if (s.length > 64) s = s.substring(0, 64)
        return s
    }

    fun create(name: String, versionId: String, mcVersion: String, loader: String, loaderVersion: String? = null): Instance {
        val clean = uniqueName(name)
        val dir = rootDir().resolve(sanitiseName(clean)).also { Files.createDirectories(it) }
        val inst = Instance()
        inst.root = dir
        inst.id = dir.fileName.toString()
        inst.name = clean
        inst.versionId = versionId
        inst.mcVersion = mcVersion
        inst.loader = loader
        inst.loaderVersion = loaderVersion
        inst.color = Instance.DEFAULT_COLORS[instances.size % Instance.DEFAULT_COLORS.size]
        inst.created = System.currentTimeMillis()
        Files.createDirectories(inst.root.resolve(".minecraft"))
        // A shared-data instance plays from the shared install root, which must exist before the
        // launcher writes links or downloads into it.
        runCatching { Files.createDirectories(inst.gameDir) }
        inst.save()
        instances.add(inst)
        notifyChanged()
        return inst
    }

    fun duplicate(source: Instance): Instance {
        val clean = uniqueName("${source.name} (copy)")
        val dir = rootDir().resolve(sanitiseName(clean))
        Files.createDirectories(dir)
        copyTree(source.root, dir, exclude = setOf(".minecraft", "instance.json.tmp"))
        val inst = Instance.fromJson(dir, source.toJson())
        inst.root = dir
        inst.id = dir.fileName.toString()
        inst.name = clean
        inst.created = System.currentTimeMillis()
        inst.lastPlayed = 0
        inst.launchCount = 0
        inst.playSeconds = 0
        inst.save()
        instances.add(inst)
        instances.sortWith(compareByDescending<Instance> { it.lastPlayed }.thenBy { it.name.lowercase() })
        notifyChanged()
        return inst
    }

    private fun copyTree(from: Path, to: Path, exclude: Set<String>) {
        Files.walk(from).use { walk ->
            walk.forEach { p ->
                val rel = from.relativize(p)
                if (rel.toString().isEmpty()) return@forEach
                if (rel.getName(0).toString() in exclude) return@forEach
                val target = to.resolve(rel)
                if (Files.isDirectory(p)) Files.createDirectories(target)
                else runCatching { Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES) }
            }
        }
    }

    /** Moves an instance into `.trash`; the game data stays recoverable. */
    fun trash(inst: Instance): Boolean {
        val trashDir = rootDir().resolve(".trash")
        Files.createDirectories(trashDir)
        val target = trashDir.resolve("${inst.id}-${System.currentTimeMillis()}")
        return runCatching {
            moveDirectory(inst.root, target)
            instances.remove(inst)
            notifyChanged()
            true
        }.onFailure { Log.e("instances", "could not trash ${inst.name}: ${it.message}", it) }.getOrDefault(false)
    }

    class TrashedInstance(val path: Path, val originalName: String, val deletedAt: Long)

    fun trashed(): List<TrashedInstance> {
        val trashDir = rootDir().resolve(".trash")
        if (!Files.isDirectory(trashDir)) return emptyList()
        return Files.list(trashDir).use { s ->
            s.filter { Files.isDirectory(it) }.toList()
        }.map { p ->
            val n = p.fileName.toString()
            val idx = n.lastIndexOf('-')
            val name = if (idx > 0) n.substring(0, idx) else n
            val at = if (idx > 0) n.substring(idx + 1).toLongOrNull() ?: 0 else 0
            TrashedInstance(p, name, at)
        }.sortedByDescending { it.deletedAt }
    }

    fun restore(trashed: TrashedInstance): Boolean = runCatching {
        var name = uniqueName(trashed.originalName)
        var target = rootDir().resolve(sanitiseName(name))
        moveDirectory(trashed.path, target)
        val inst = Instance()
        inst.root = target
        inst.id = target.fileName.toString()
        inst.name = name
        if (Files.isRegularFile(target.resolve("instance.json"))) {
            val loaded = Instance.fromJson(target, Json.parseObject(Files.readString(target.resolve("instance.json"))))
            loaded.root = target; loaded.id = target.fileName.toString(); loaded.name = name
            loaded.save()
            instances.add(loaded)
        } else {
            inst.save(); instances.add(inst)
        }
        instances.sortWith(compareByDescending<Instance> { it.lastPlayed }.thenBy { it.name.lowercase() })
        notifyChanged()
        true
    }.getOrElse { Log.e("instances", "restore failed: ${it.message}", it); false }

    fun emptyTrash() {
        val trashDir = rootDir().resolve(".trash")
        if (!Files.isDirectory(trashDir)) return
        Files.list(trashDir).use { s -> s.toList() }.forEach { p -> runCatching { deleteTree(p) } }
        notifyChanged()
    }

    fun deletePermanently(inst: Instance) { runCatching { deleteTree(inst.root) }; instances.remove(inst); notifyChanged() }

    fun deleteTree(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { walk ->
            walk.sorted(Comparator.reverseOrder()).forEach { p -> runCatching { Files.deleteIfExists(p) } }
        }
    }

    private fun moveDirectory(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            copyTree(from, to, emptySet())
            deleteTree(from)
        }
    }

    fun find(name: String): Instance? = instances.firstOrNull { it.name == name || it.id == name }

    fun byVersion(versionId: String): Instance? = instances.firstOrNull { it.versionId == versionId }

    fun reload() = load()

    /** Offline UUID for an instance's world identification. */
    fun stableUuid(inst: Instance): UUID = UUID.nameUUIDFromBytes(("aerix-instance:" + inst.id).toByteArray())
}
