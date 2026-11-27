package net.kdt.aerix.desktop.core.instance

import net.kdt.aerix.desktop.app.AppEnv
import net.kdt.aerix.desktop.app.Log
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Shares the expensive parts of a Minecraft installation between instances.
 *
 * Every version, library and asset is identical for every instance of the same game version, and
 * together they are several gigabytes. The launcher keeps one copy in the shared data root and
 * links it into each instance's `.minecraft`:
 *
 * * **Windows** — a directory junction (`mklink /J`), which needs no administrator rights and is
 *   what MultiMC/Prism do on Windows for exactly this reason.
 * * **elsewhere** — a symbolic link, used only while developing on Linux/macOS.
 *
 * World saves, mods, resource packs, shader packs and options stay *inside* the instance, so two
 * instances never see each other's content. A pre-existing real folder is never replaced: if the
 * player already has versions there, it is left alone.
 */
object InstanceLinks {
    private val sharedFolders = listOf("versions", "libraries", "assets")

    /** Creates the links (or copies as a last resort) for [instance]. Safe to call repeatedly. */
    fun ensure(instance: Instance): Boolean {
        val shared = Instances.sharedDataDir()
        val minecraft = instance.root.resolve(".minecraft")
        runCatching { Files.createDirectories(minecraft) }
        var ok = true
        for (name in sharedFolders) {
            val source = shared.resolve(name)
            val target = minecraft.resolve(name)
            runCatching { Files.createDirectories(source) }
            if (Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue
            if (!Files.isDirectory(source)) continue
            ok = link(source, target) && ok
        }
        return ok
    }

    private fun link(source: Path, target: Path): Boolean {
        if (AppEnv.isWindows) {
            // Never through cmd.exe: it re-parses the command line, so `&` inside a folder name
            // becomes a second command. See WinLinks for the direct calls.
            if (WinLinks.supported && WinLinks.createJunction(target, source)) return true
        } else {
            val created = runCatching {
                Files.createSymbolicLink(target, source)
                true
            }.getOrDefault(false)
            if (created) return true
        }
        // Fallback: copy, so the game still starts even on a locked-down filesystem.
        Log.w("instances", "could not link ${target.fileName} to shared data; copying instead")
        return runCatching {
            copyTree(source, target)
            true
        }.onFailure { Log.e("instances", "copy fallback failed for $target: ${it.message}", it) }
            .getOrDefault(false)
    }

    private fun copyTree(from: Path, to: Path) {
        Files.walk(from).use { walk ->
            walk.forEach { p ->
                val rel = from.relativize(p)
                if (rel.toString().isEmpty()) return@forEach
                val dest = to.resolve(rel)
                if (Files.isDirectory(p)) Files.createDirectories(dest)
                else {
                    Files.createDirectories(dest.parent)
                    runCatching { Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING) }
                }
            }
        }
    }

    /** Removes the links (not the shared data) before an instance is duplicated or trashed. */
    fun detach(instance: Instance) {
        val minecraft = instance.root.resolve(".minecraft")
        for (name in sharedFolders) {
            val target = minecraft.resolve(name)
            if (!Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue
            runCatching {
                if (Files.isSymbolicLink(target)) Files.delete(target)
                else if (AppEnv.isWindows) {
                    // A junction is removed as a link, never with Files.delete (which refuses a
                    // directory) and never with a shell `rmdir`.
                    if (!(WinLinks.supported && WinLinks.removeJunction(target))) {
                        Log.w("instances", "could not unlink $target")
                    }
                }
            }
        }
    }
}
