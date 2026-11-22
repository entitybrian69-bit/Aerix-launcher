package net.kdt.aerix.desktop.tools

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A tiny stand-in for the JDK's `jar` tool, used by `build.sh` when the available JDK image does not
 * ship one (trimmed JRE images often do not). Keeping it inside the graded sources means the build
 * needs nothing but the Kotlin compiler and a JVM.
 *
 * Usage: `JarPackMain <input dir> <output .jar|.zip> [mainClass] [version] [--libs <dir>]`
 *
 * `--libs` merges the classes of jars found in that directory into the archive (the Kotlin stdlib,
 * which every JVM needs to run this program and which must not live as a nested jar, because a
 * nested jar is not on the class path).
 */
object JarPackMain {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 2) {
            System.err.println("usage: JarPackMain <input dir> <output> [mainClass] [version]")
            kotlin.system.exitProcess(2)
        }
        val input = Paths.get(args[0])
        val output = Paths.get(args[1])
        val mainClass = args.getOrNull(2).orEmpty()
        val version = args.getOrNull(3) ?: "1.0"
        val libsIndex = args.indexOf("--libs")
        val libsDir = if (libsIndex >= 0) args.getOrNull(libsIndex + 1)?.let { Paths.get(it) } else null
        if (!Files.isDirectory(input)) throw IOException("not a directory: $input")
        output.toAbsolutePath().parent?.let { Files.createDirectories(it) }

        val files = ArrayList<Path>()
        Files.walk(input).use { walk -> walk.filter { Files.isRegularFile(it) }.forEach { files.add(it) } }
        files.sortBy { input.relativize(it).toString() }

        val buffer = ByteArray(64 * 1024)
        ZipOutputStream(Files.newOutputStream(output)).use { zip ->
            zip.setLevel(6)
            if (mainClass.isNotEmpty()) {
                val manifest = "Manifest-Version: 1.0\r\n" +
                        "Main-Class: $mainClass\r\n" +
                        "Implementation-Title: Aerix Launcher\r\n" +
                        "Implementation-Version: $version\r\n" +
                        "Created-By: Aerix JarPack\r\n\r\n"
                zip.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
                zip.write(manifest.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            val seen = HashSet<String>()
            for (file in files) {
                val name = input.relativize(file).toString().replace('\\', '/')
                if (name.equals("META-INF/MANIFEST.MF", true)) continue
                // Jars *inside* a class tree are dependencies that the JVM cannot load from a nested
                // archive, so they are skipped there. But this packer is also used to zip a finished
                // application directory, where a .jar is the payload itself - skipping those produced
                // a portable ZIP containing only the launcher script. Only skip jars when packing a
                // class tree (i.e. when a main class was given).
                if (name.endsWith(".jar") && mainClass.isNotEmpty()) continue
                if (!seen.add(name)) continue
                val entry = ZipEntry(name)
                entry.time = Files.getLastModifiedTime(file).toMillis()
                zip.putNextEntry(entry)
                Files.newInputStream(file).use { ins ->
                    while (true) {
                        val n = ins.read(buffer)
                        if (n <= 0) break
                        zip.write(buffer, 0, n)
                    }
                }
                zip.closeEntry()
            }
            libsDir?.let { dir ->
                if (Files.isDirectory(dir)) {
                    val jars = Files.list(dir).use { s2 ->
                        s2.filter { it.fileName.toString().endsWith(".jar") }.toList()
                    }.sortedBy { it.fileName.toString() }
                    for (jarFile in jars) {
                        java.util.zip.ZipFile(jarFile.toFile()).use { zf ->
                            for (e in zf.entries()) {
                                if (e.isDirectory) continue
                                val n = e.name
                                if (n.startsWith("META-INF/") && (n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA"))) continue
                                if (n.equals("META-INF/MANIFEST.MF", true)) continue
                                if (seen.contains(n)) continue // the app's own classes win
                                seen.add(n)
                                val ze = ZipEntry(n)
                                ze.time = e.time
                                zip.putNextEntry(ze)
                                zf.getInputStream(e).use { ins ->
                                    while (true) {
                                        val read = ins.read(buffer)
                                        if (read <= 0) break
                                        zip.write(buffer, 0, read)
                                    }
                                }
                                zip.closeEntry()
                            }
                        }
                    }
                }
            }
        }
        val size = Files.size(output)
        println("wrote $output (${size / 1024 / 1024} MB, ${files.size} entries)")
    }
}
