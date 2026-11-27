package net.kdt.aerix.desktop.ui.screens

import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Scheduler
import net.kdt.aerix.desktop.core.content.ContentInstaller
import net.kdt.aerix.desktop.core.instance.Instance
import net.kdt.aerix.desktop.core.instance.Instances
import net.kdt.aerix.desktop.core.json.Json
import net.kdt.aerix.desktop.ui.shell.Shell
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

/**
 * One entry point for "a file arrived": drag and drop, the Open-with handler, or the file dialog.
 *
 * The type is decided by looking inside the archive rather than trusting the extension, because
 * players rename modpacks and mod jars constantly. A `.mrpack` has `modrinth.index.json`, a
 * CurseForge pack has `manifest.json`, a mod jar has `fabric.mod.json`/`META-INF/mods.toml`/
 * `META-INF/MANIFEST.MF`, a resource pack has `pack.mcmeta` at the root, and a world folder has
 * `level.dat`.
 */
object DropImport {

    fun importFile(shell: Shell, path: Path) {
        if (!Files.exists(path)) { shell.toast("That file does not exist"); return }
        if (Files.isDirectory(path)) { importWorld(shell, path); return }
        val name = path.fileName.toString().lowercase()
        Scheduler.io("drop-import") {
            val kind = detect(path)
            Log.i("drop", "importing ${path.fileName} as $kind")
            SwingUtilities.invokeLater {
                when (kind) {
                    Kind.MRPACK, Kind.CURSE_PACK -> {
                        shell.navigate(net.kdt.aerix.desktop.ui.shell.Page.MODPACKS)
                        shell.toast("Dropped ${path.fileName} — pick a name to import it")
                        // Reuse the modpacks page's own flow rather than a second implementation.
                        ModpackDrop.install(shell, path)
                    }
                    Kind.MOD -> installInto(shell, path, net.kdt.aerix.desktop.core.content.Content.Kind.MOD)
                    Kind.RESOURCE_PACK -> installInto(shell, path, net.kdt.aerix.desktop.core.content.Content.Kind.RESOURCE_PACK)
                    Kind.SHADER -> installInto(shell, path, net.kdt.aerix.desktop.core.content.Content.Kind.SHADER)
                    Kind.SKIN -> importSkin(shell, path)
                    Kind.WORLD -> importWorld(shell, path)
                    Kind.UNKNOWN -> JOptionPane.showMessageDialog(
                        shell,
                        "Aerix does not recognise ${path.fileName}.\n\nRecognised: .mrpack, CurseForge modpack zips, mod jars,\n" +
                                "resource pack zips, shader zips, a 64×64 skin PNG, or a world folder.",
                        "Unknown file", JOptionPane.WARNING_MESSAGE
                    )
                }
            }
        }
    }

    private enum class Kind { MRPACK, CURSE_PACK, MOD, RESOURCE_PACK, SHADER, SKIN, WORLD, UNKNOWN }

    private fun detect(path: Path): Kind {
        val name = path.fileName.toString().lowercase()
        if (name.endsWith(".png")) return Kind.SKIN
        if (name.endsWith(".mrpack")) return Kind.MRPACK
        if (!name.endsWith(".zip") && !name.endsWith(".jar")) return Kind.UNKNOWN
        val entries = runCatching {
            val names = HashSet<String>()
            ZipInputStream(Files.newInputStream(path)).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    names.add(e.name)
                    e = zip.nextEntry
                }
            }
            names
        }.getOrDefault(emptySet())
        return when {
            "modrinth.index.json" in entries -> Kind.MRPACK
            "manifest.json" in entries -> Kind.CURSE_PACK
            "fabric.mod.json" in entries || "META-INF/mods.toml" in entries || "META-INF/neoforge.mods.toml" in entries -> Kind.MOD
            "pack.mcmeta" in entries && entries.any { it.startsWith("assets/") } ->
                if (entries.any { it.startsWith("shaders/") }) Kind.SHADER else Kind.RESOURCE_PACK
            entries.any { it.startsWith("shaders/") } -> Kind.SHADER
            name.endsWith(".jar") -> Kind.MOD
            else -> Kind.UNKNOWN
        }
    }

    private fun chooseInstance(shell: Shell): Instance? {
        val instances = Instances.list()
        if (instances.isEmpty()) {
            shell.toast("Create an instance before importing content")
            shell.navigate(net.kdt.aerix.desktop.ui.shell.Page.CREATE)
            return null
        }
        val options = instances.map { "${it.name}  ·  ${it.subtitle()}" }.toTypedArray()
        val pick = JOptionPane.showInputDialog(shell, "Install ${if (options.size > 1) "into" else "into"} which instance?",
            "Choose instance", JOptionPane.QUESTION_MESSAGE, null, options, options.first()) as? String ?: return null
        return instances[options.indexOf(pick)]
    }

    private fun installInto(shell: Shell, path: Path, kind: net.kdt.aerix.desktop.core.content.Content.Kind) {
        val target = chooseInstance(shell) ?: return
        Scheduler.io("drop-copy") {
            runCatching {
                val dir = ContentInstaller.folderFor(target, kind)
                Files.createDirectories(dir)
                val dest = dir.resolve(path.fileName.toString())
                Files.copy(path, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                dest
            }.onSuccess { dest ->
                SwingUtilities.invokeLater {
                    shell.toast("Installed ${dest.fileName} into ${target.name}")
                    shell.refreshPages()
                }
            }.onFailure {
                Log.e("drop", "install failed: ${it.message}", it)
                SwingUtilities.invokeLater { shell.toast("Could not install: ${it.message}") }
            }
        }
    }

    private fun importSkin(shell: Shell, path: Path) {
        val accounts = net.kdt.aerix.desktop.core.auth.accounts.Accounts.all()
        if (accounts.isEmpty()) { shell.toast("Add an account first"); return }
        val options = accounts.map { it.username }.toTypedArray()
        val pick = JOptionPane.showInputDialog(shell, "Apply this skin to which profile?", "Import skin",
            JOptionPane.QUESTION_MESSAGE, null, options, options.first()) as? String ?: return
        val account = accounts[options.indexOf(pick)]
        if (net.kdt.aerix.desktop.ui.components.SkinFace.importSkin(account, path.toFile())) {
            shell.toast("Skin applied to ${account.username}")
        } else {
            JOptionPane.showMessageDialog(shell, "That PNG is not a 64×64 or 64×32 Minecraft skin.", "Skin", JOptionPane.WARNING_MESSAGE)
        }
    }

    /** A dropped folder: import it as a world if it has a level.dat, else copy its jar/zip children. */
    private fun importWorld(shell: Shell, dir: Path) {
        val hasLevel = Files.isRegularFile(dir.resolve("level.dat"))
        if (hasLevel) {
            val target = chooseInstance(shell) ?: return
            Scheduler.io("drop-world") {
                runCatching {
                    val saves = target.gameDir.resolve("saves")
                    Files.createDirectories(saves)
                    val dest = saves.resolve(dir.fileName.toString())
                    if (!Files.exists(dest)) copyDirectory(dir, dest) else throw IllegalStateException("A world with that name already exists")
                }.onSuccess {
                    SwingUtilities.invokeLater { shell.toast("Imported world ${dir.fileName} into ${target.name}") }
                }.onFailure {
                    SwingUtilities.invokeLater { shell.toast("World import failed: ${it.message}") }
                }
            }
            return
        }
        // Not a world: import every archive inside it.
        runCatching {
            Files.list(dir).use { s -> s.toList() }.filter { Files.isRegularFile(it) }
        }.getOrDefault(emptyList()).forEach { importFile(shell, it) }
    }
}

/** The modpack half of DropImport, kept next to the screen it uses. */
object ModpackDrop {
    fun install(shell: Shell, archive: Path) {
        val name = JOptionPane.showInputDialog(
            shell, "Name for the new instance", archive.fileName.toString().substringBeforeLast('.')
        ) ?: return
        if (name.isBlank()) return
        val dialog = ProgressDialog(shell, "Importing modpack")
        dialog.isVisible = true
        Scheduler.io("drop-pack") {
            try {
                var mcVersion = ""
                var loader = "vanilla"
                runCatching {
                    ZipInputStream(Files.newInputStream(archive)).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (entry.name == "modrinth.index.json") {
                                val doc = Json.parseObject(String(zip.readBytes()))
                                val deps = doc.obj("dependencies")
                                mcVersion = deps?.str("minecraft") ?: ""
                                loader = when {
                                    deps?.has("fabric-loader") == true -> "fabric"
                                    deps?.has("quilt-loader") == true -> "quilt"
                                    deps?.has("forge") == true -> "forge"
                                    deps?.has("neoforge") == true -> "neoforge"
                                    else -> "vanilla"
                                }
                            }
                            entry = zip.nextEntry
                        }
                    }
                }
                val inst = Instances.create(name.trim(), mcVersion, mcVersion, loader)
                val result = ContentInstaller.installPack(inst, archive) { p -> dialog.update(p.phase, p.fraction) }
                SwingUtilities.invokeLater {
                    dialog.dispose()
                    shell.toast("Imported ${result.installedFiles} files into ${inst.name}")
                    shell.navigate(net.kdt.aerix.desktop.ui.shell.Page.LIBRARY)
                }
            } catch (e: Exception) {
                Log.e("drop", "pack import failed: ${e.message}", e)
                SwingUtilities.invokeLater {
                    dialog.dispose()
                    JOptionPane.showMessageDialog(shell, "Could not import the pack:\n\n${e.message}", "Import failed", JOptionPane.ERROR_MESSAGE)
                }
            }
        }
    }
}
