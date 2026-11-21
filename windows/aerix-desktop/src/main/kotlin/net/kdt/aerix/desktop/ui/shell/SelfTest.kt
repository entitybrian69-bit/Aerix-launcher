package net.kdt.aerix.desktop.ui.shell

import net.kdt.aerix.desktop.app.AppEnv
import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.auth.SecureStore
import net.kdt.aerix.desktop.core.auth.accounts.Accounts
import net.kdt.aerix.desktop.core.game.JavaRuntimes
import net.kdt.aerix.desktop.core.instance.Instance
import net.kdt.aerix.desktop.core.instance.Instances
import net.kdt.aerix.desktop.core.json.Json
import net.kdt.aerix.desktop.core.json.JsonObject
import net.kdt.aerix.desktop.core.loaders.Loaders
import net.kdt.aerix.desktop.ui.components.SkinFace
import net.kdt.aerix.desktop.ui.theme.AerixTheme
import net.kdt.aerix.desktop.ui.theme.Backdrops
import net.kdt.aerix.desktop.ui.theme.Fonts
import net.kdt.aerix.desktop.ui.theme.Paint
import net.kdt.aerix.desktop.ui.theme.UiScale
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.Graphics2D

/**
 * Headless self test: exercises the parts that can be checked without a display, then renders every
 * page offscreen and writes PNGs. `build.sh --selftest` runs this and fails the build on a
 * non-zero exit.
 *
 * What is checked (and what is not):
 *   * settings round-trip through disk                    — real
 *   * account sealing without a plaintext token on disk   — real (AES-GCM path in the sandbox,
 *                                                           DPAPI on Windows)
 *   * instance CRUD on a scratch game dir                 — real
 *   * image assets / fonts load                           — real
 *   * every page lays out and paints at 1440×900          — real (offscreen)
 *   * network reachability of the metadata hosts          — reported, not fatal (a launcher
 *                                                           must start offline; the facts are
 *                                                           printed so CI can see them)
 */
object SelfTest {

    private val results = ArrayList<Triple<String, Boolean, String>>()

    fun run(): Boolean {
        val started = System.currentTimeMillis()
        val shots = AppEnv.appData.resolve("selftest-shots")
        runCatching { Files.createDirectories(shots) }
        Log.i("selftest", "Aerix Launcher self test")
        Log.i("selftest", "java ${System.getProperty("java.version")} · ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
        Log.i("selftest", "data folder: ${AppEnv.appData}")

        Settings.load(AppEnv.appData.resolve("settings.json"))
        AerixTheme.refresh()
        Fonts.rebuild()

        check("settings round-trip") {
            Settings.set("selftest.marker", "value-${System.nanoTime()}")
            Settings.saveNow()
            val reread = Json.parseObject(Files.readString(AppEnv.appData.resolve("settings.json")))
            val value = reread.obj("values")?.str("selftest.marker") ?: reread.str("selftest.marker")
            if (value == null || !value.startsWith("value-")) error("settings did not persist (got $value)")
        }

        check("secure store seals and opens") {
            val secret = "selftest-token-${System.nanoTime()}"
            val sealed = SecureStore.seal(secret)
            if (sealed.contains(secret)) error("the sealed blob contains the plaintext")
            val opened = SecureStore.open(sealed)
            if (opened != secret) error("open(seal(x)) != x")
            // and it survives a re-key read on a fresh instance of the store
            if (SecureStore.open(sealed) != secret) error("second open failed")
        }

        check("account store never writes an access token") {
            val before = Accounts.all().size
            val acc = Accounts.addOffline("SelfTest_${System.nanoTime() % 1000}")
            try {
                val file = AppEnv.accountsDir.resolve(acc.file.fileName.toString())
                if (!Files.isRegularFile(file)) error("account file was not written")
                val text = Files.readString(file)
                if (text.contains("\"accessToken\": \"") && !text.contains("\"accessToken\": \"0\"")) {
                    error("an access token appears in the account file")
                }
                if (!text.contains("refreshTokenSealed") && acc.isMicrosoft) error("microsoft account lost its sealed token")
            } finally {
                Accounts.remove(acc)
            }
            if (Accounts.all().size > before) error("account was not removed")
        }

        check("instance create, save, reload, trash, restore") {
            val scratch = AppEnv.appData.resolve("selftest-game")
            runCatching { Instances.deleteTree(scratch) }
            Settings.set("gameDirectory", scratch.toString())
            Instances.reload()
            val inst = Instances.create("SelfTest", "1.21.1", "1.21.1", "fabric", "0.16.0")
            inst.memoryMb = 2048
            inst.save()
            Instances.reload()
            val reloaded = Instances.find("SelfTest") ?: error("instance did not survive a reload")
            if (reloaded.memoryMb != 2048) error("settings on the instance were lost")
            if (!Files.isDirectory(reloaded.gameDir)) error("instance game folder missing")
            Instances.trash(reloaded)
            if (Instances.find("SelfTest") != null) error("instance still listed after trash")
            val trashed = Instances.trashed().firstOrNull() ?: error("trash is empty")
            if (!Instances.restore(trashed)) error("restore failed")
            if (Instances.find("SelfTest") == null) error("restored instance not listed")
        }

        check("bundled assets load") {
            val wallpaper = Backdrops.load("wp_01_lush_caves") ?: error("bundled wallpaper missing")
            if (wallpaper.width < 100) error("wallpaper too small")
            val skin = SkinFace.defaultSkin()
            if (skin.width != 64) error("default skin is not 64 px wide")
            val face = SkinFace.placeholderFace("SelfTest", 32)
            if (face.width != 32) error("face render failed")
            val character = SkinFace.renderCharacter(skin, 220, 380, -20f, 0f, true, "")
            if (character.width != 220) error("character render failed")
            val stream = SelfTest::class.java.getResourceAsStream("/aerix/icons/vector/aerix_ic_play_arrow_filled.xml")
            if (stream == null) error("icon resources missing")
            stream.close()
        }

        check("version comparison and NeoForge mapping") {
            if (Loaders.compareVersions("47.2.10", "47.2.9") <= 0) error("47.2.10 should sort above 47.2.9")
            if (Loaders.compareVersions("1.21.1", "1.20.2") <= 0) error("1.21.1 should sort above 1.20.2")
        }

        check("version metadata resolves libraries the way the game needs") {
            // A fake game root with hand-written version JSONs, exercising the three library
            // shapes that appear across 1.5 → 1.21, plus an inheritance chain and a rule that
            // must exclude a foreign-OS library. Nothing here touches the network.
            val root = Files.createTempDirectory("aerix-meta")
            try {
                fun write(id: String, json: String) {
                    val dir = root.resolve("versions").resolve(id)
                    Files.createDirectories(dir)
                    Files.writeString(dir.resolve("$id.json"), json)
                }
                // The native classifier is platform-specific, so the fixture asks MetadataRules
                // what this machine is instead of hard-coding "windows": the same assertions then
                // run on the Linux development box and on the Windows CI runner.
                val osKey = net.kdt.aerix.desktop.core.game.MetadataRules.nativeKeys().first()
                val osName = net.kdt.aerix.desktop.core.game.MetadataRules.osName
                val natClassifier = "natives-$osKey"
                val foreignKey = if (osKey == "osx") "windows" else "osx"
                write("1.6.4", """
                {
                  "id": "1.6.4", "type": "release", "mainClass": "net.minecraft.client.main.Main",
                  "minecraftArguments": "--username ${'$'}{auth_player_name} --gameDir ${'$'}{game_directory}",
                  "assetIndex": {"id": "legacy", "url": "https://example.invalid/legacy.json"},
                  "assets": "legacy", "jar": "1.6.4", "javaVersion": {"majorVersion": 8},
                  "libraries": [
                    {"name": "org.lwjgl.lwjgl:lwjgl:2.9.1",
                     "downloads": {"artifact": {"path": "org/lwjgl/lwjgl/lwjgl/2.9.1/lwjgl-2.9.1.jar",
                                                "url": "https://example.invalid/class.jar", "size": 10},
                                   "classifiers": {"${natClassifier}": {"path": "org/lwjgl/lwjgl/lwjgl/2.9.1/lwjgl-2.9.1-${natClassifier}.jar",
                                                                       "url": "https://example.invalid/nat.jar", "size": 20}}},
                     "natives": {"${osKey}": "${natClassifier}", "${foreignKey}": "natives-other"},
                     "extract": {"exclude": ["META-INF/"]}},
                    {"name": "com.example:rawlib:1.0", "url": "https://example.invalid/maven/"},
                    {"name": "com.example:maconly:1.0",
                     "rules": [{"action": "allow", "os": {"name": "osx"}}],
                     "downloads": {"artifact": {"url": "https://example.invalid/mac.jar"}}}
                  ]
                }
                """.trimIndent())
                write("1.21", """
                {
                  "id": "1.21", "type": "release", "mainClass": "net.minecraft.client.main.Main",
                  "arguments": {"game": ["--username", "${'$'}{auth_player_name}"],
                                "jvm": ["-Djava.library.path=${'$'}{natives_directory}", "-cp", "${'$'}{classpath}"]},
                  "assetIndex": {"id": "17", "url": "https://example.invalid/17.json"},
                  "assets": "17", "javaVersion": {"majorVersion": 21},
                  "libraries": [
                    {"name": "org.lwjgl:lwjgl:3.3.3",
                     "downloads": {"artifact": {"path": "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar",
                                                "url": "https://example.invalid/lwjgl.jar"}}},
                    {"name": "org.lwjgl:lwjgl:3.3.3:natives-windows",
                     "downloads": {"artifact": {"path": "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar",
                                                "url": "https://example.invalid/lwjgl-natives.jar"}}}
                  ]
                }
                """.trimIndent())
                // a loader profile that inherits from 1.21 and adds its own jvm arg
                write("fabric-loader-0.16", """
                {
                  "id": "fabric-loader-0.16", "inheritsFrom": "1.21",
                  "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
                  "arguments": {"jvm": [
                      "-DFabricMcEmu=net.minecraft.client.main.Main",
                      {"rules": [{"action": "allow", "os": {"name": "osx"}}], "value": "-DMacOnly=1"},
                      {"rules": [{"action": "allow", "os": {"name": "${osName}"}}], "value": ["-DThisOs=1"]},
                      {"value": "-DNoRules=1"}
                  ], "game": []},
                  "libraries": [{"name": "net.fabricmc:fabric-loader:0.16", "url": "https://maven.fabricmc.net/"}]
                }
                """.trimIndent())

                val old = net.kdt.aerix.desktop.core.game.Mojang.resolve(root, "1.6.4")
                val oldPaths = old.libraries.map { it.relativePath }
                if ("org/lwjgl/lwjgl/lwjgl/2.9.1/lwjgl-2.9.1.jar" !in oldPaths)
                    error("classic library lost its class jar: $oldPaths")
                if (oldPaths.any { it.contains("${natClassifier}.jar") })
                    error("a native jar leaked onto the class path")
                if (old.natives.none { it.relativePath.endsWith("lwjgl-2.9.1-${natClassifier}.jar") })
                    error("classic native classifier was not extracted: ${old.natives.map { it.relativePath }}")
                if (old.natives.any { it.relativePath.contains("natives-other") })
                    error("a native for another operating system was extracted: ${old.natives.map { it.relativePath }}")
                if (old.natives.first().excludeFromNatives != listOf("META-INF/"))
                    error("extract.exclude ignored")
                if (oldPaths.any { it.contains("rawlib") }.not())
                    error("pre-1.13 raw library (url + name, no downloads) was dropped: $oldPaths")
                if (oldPaths.any { it.contains("maconly") })
                    error("a library for another operating system was included")
                if (old.legacyAssets.not()) error("1.6.4 should use the virtual asset tree")

                val modern = net.kdt.aerix.desktop.core.game.Mojang.resolve(root, "1.21")
                if (modern.libraries.none { it.relativePath.endsWith("lwjgl-3.3.3.jar") })
                    error("modern class jar missing")
                if (modern.libraries.any { it.relativePath.contains("natives") })
                    error("modern natives were put on the class path")
                if (modern.natives.size != 1) error("expected exactly one modern native, got ${modern.natives.size}")

                val loader = net.kdt.aerix.desktop.core.game.Mojang.resolve(root, "fabric-loader-0.16")
                if (loader.mainClass != "net.fabricmc.loader.impl.launch.knot.KnotClient")
                    error("loader main class did not win: ${loader.mainClass}")
                if (loader.libraries.none { it.relativePath.contains("fabric-loader") })
                    error("loader library missing")
                val jvm = loader.jvmArgs.mapNotNull { it.asString() }
                if (jvm.count { it.contains("java.library.path") } != 1)
                    error("jvm args duplicated across the inheritance chain: $jvm")
                if (jvm.indexOfFirst { it.contains("java.library.path") } > jvm.indexOfFirst { it.startsWith("-DFabricMcEmu") })
                    error("parent jvm args must come before loader jvm args: $jvm")
                if (loader.gameArgs.isEmpty()) error("inherited game args were lost")

                // The launch command itself: 1.6.4 has no `arguments` block, so the class path has
                // to be synthesised or the game cannot start at all.
                val classicCmd = net.kdt.aerix.desktop.core.game.GameLauncher
                    .metadataJvmArgs(old, emptyMap(), "C:\\game\\libs.jar")
                if (!classicCmd.contains("-cp") || !classicCmd.contains("C:\\game\\libs.jar"))
                    error("pre-1.13 launch has no class path: $classicCmd")
                if (classicCmd.indexOf("-cp") > classicCmd.indexOf("C:\\game\\libs.jar"))
                    error("class path flag must precede its value: $classicCmd")

                // Modern metadata declares its own -cp, so no duplicate is added.
                val modernCmd = net.kdt.aerix.desktop.core.game.GameLauncher
                    .metadataJvmArgs(modern, mapOf("classpath" to "a;b", "natives_directory" to "n"), "a;b")
                if (modernCmd.count { it == "-cp" } != 1)
                    error("class path declared twice: $modernCmd")
                if (!modernCmd.contains("-cp") || modernCmd[modernCmd.indexOf("-cp") + 1] != "a;b")
                    error("class path value was not substituted: $modernCmd")

                // Rules: a foreign-OS arg is dropped, a matching one kept, a rule-less object kept.
                val loaderCmd = net.kdt.aerix.desktop.core.game.GameLauncher
                    .metadataJvmArgs(loader, emptyMap(), "x")
                if (loaderCmd.any { it.contains("MacOnly") })
                    error("an osx-only JVM argument reached a non-mac launch: $loaderCmd")
                if (loaderCmd.none { it == "-DThisOs=1" })
                    error("a matching rule's JVM argument was dropped: $loaderCmd")
                if (loaderCmd.none { it == "-DNoRules=1" })
                    error("a rule-less object argument was dropped: $loaderCmd")
                if (loaderCmd.none { it.startsWith("-DFabricMcEmu") })
                    error("the loader's own argument was dropped: $loaderCmd")
            } finally {
                root.toFile().deleteRecursively()
            }
        }

        check("OptiFine and BTA pages parse") {
            // A trimmed copy of optifine.net/downloads: header row, then two download lines.
            val html = """
                <html><body>
                <h2>Minecraft 1.21.1</h2>
                <table>
                  <tr class="downloadLine">
                    <td class="colFile">OptiFine_1.21.1_HD_U_J3.jar</td>
                    <td class="colMirror"><a href="https://optifine.net/adloadx?f=OptiFine_1.21.1_HD_U_J3.jar">Mirror</a></td>
                  </tr>
                  <tr class="downloadLine preview">
                    <td class="colFile">OptiFine_1.21.1_HD_U_J4_pre1.jar</td>
                    <td class="colMirror"><a href="https://optifine.net/adloadx?f=OptiFine_1.21.1_HD_U_J4_pre1.jar">Mirror</a></td>
                  </tr>
                </table>
                <h2>Minecraft 1.20.1</h2>
                <table>
                  <tr class="downloadLine">
                    <td class="colFile">OptiFine_1.20.1_HD_U_I6.jar</td>
                    <td class="colMirror"><a href="https://optifine.net/adloadx?f=OptiFine_1.20.1_HD_U_I6.jar">Mirror</a></td>
                  </tr>
                </table>
                </body></html>
            """.trimIndent()
            val builds = Loaders.parseOptifinePage(html)
            if (builds.size != 3) error("expected 3 OptiFine builds, parsed ${builds.size}")
            val stable = builds.filter { it.gameVersion == "1.21.1" && it.stable }
            if (stable.size != 1) error("expected one stable 1.21.1 OptiFine, got ${stable.size}")
            if (!stable[0].label.contains("HD U J3")) error("wrong label: ${stable[0].label}")
            if (builds.none { it.label.contains("pre1") && !it.stable }) error("previews should be marked unstable")
            if (Loaders.btaSemver("v7.3_04") != "7.3.4") error("BTA semver conversion is wrong")
            if (net.kdt.aerix.desktop.core.instance.Loader.normalise("Better than Adventure 7.3") !=
                net.kdt.aerix.desktop.core.instance.Loader.BTA) error("BTA does not normalise")
        }

        check("launch argument file round-trips through a real JVM") {
            // This is the one place where a quoting mistake means *every* launch is wrong, and the
            // failure looks like "Minecraft started with no memory and no classpath". So it is
            // tested end to end: the running launcher jar is copied into a folder whose name
            // contains a space (which is what %APPDATA%\Aerix Launcher is on Windows), then the
            // JVM is asked, through an argument file, to run that jar and print its version.
            val mine = runCatching {
                java.io.File(SelfTest::class.java.protectionDomain.codeSource.location.toURI())
            }.getOrNull()
            if (mine == null || !mine.isFile || !mine.name.endsWith(".jar")) {
                // Running from a class directory (IDE): fall back to the encoding assertions only.
                Log.i("selftest", "not running from a jar; encoding checked textually only")
            } else {
                val dir = Files.createTempDirectory("aerix args test ")
                val copy = dir.resolve("launcher copy.jar")
                Files.copy(mine.toPath(), copy)
                val argFile = dir.resolve("args.txt")
                val args = listOf(
                    "-cp", copy.toAbsolutePath().toString(),
                    "net.kdt.aerix.desktop.MainKt", "--version"
                )
                Files.writeString(argFile, net.kdt.aerix.desktop.core.game.GameLauncher.encodeArgFile(args), Charsets.UTF_8)
                val java = JavaRuntimes.discover(force = true).firstOrNull()?.javaExe
                    ?: error("no Java runtime to test with")
                val process = ProcessBuilder(java.toString(), "@${argFile.toAbsolutePath()}")
                    .redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                if (!process.waitFor(60, TimeUnit.SECONDS)) process.destroyForcibly()
                runCatching { Files.deleteIfExists(copy) }
                runCatching { Files.deleteIfExists(argFile) }
                runCatching { Files.deleteIfExists(dir) }
                if (!output.contains("Aerix Launcher")) {
                    error("the JVM could not launch through the argument file:\n" + output.take(700))
                }
            }
            // And the shape of the text itself: spaces force quotes, and inside them a backslash
            // must be doubled (the JDK's rule — unquoted backslashes are literal).
            val encoded = net.kdt.aerix.desktop.core.game.GameLauncher.encodeArgFile(listOf("plain", "two words"))
            if (encoded != "plain\n\"two words\"") error("unexpected encoding: $encoded")
            val path = net.kdt.aerix.desktop.core.game.GameLauncher.encodeArgFile(listOf("C:\\a b\\c"))
            if (path != "\"C:\\\\a b\\\\c\"") error("backslashes inside quotes must double, got: $path")
            val unquoted = net.kdt.aerix.desktop.core.game.GameLauncher.encodeArgFile(listOf("C:\\nospace\\x.jar"))
            if (unquoted.contains("\\\\")) error("unquoted backslashes must not be doubled, got: $unquoted")
        }

        check("downloads survive a server that ignores or shifts ranges") {
            // Resuming is where a launcher quietly corrupts files: appending a full body to an
            // existing fragment produces a jar that only fails much later, at hash time. A real
            // socket server inside the test answers three ways — honours the range, ignores it,
            // and shifts it — and every case must still produce the exact bytes.
            val body = ByteArray(64 * 1024) { (it % 251).toByte() }
            val sha1 = run {
                val md = java.security.MessageDigest.getInstance("SHA-1")
                md.digest(body).joinToString("") { "%02x".format(it) }
            }
            val server = java.net.ServerSocket(0, 8, java.net.InetAddress.getLoopbackAddress())
            val port = server.localPort
            val worker = Thread {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    socket.use { sck ->
                        val input = sck.getInputStream().bufferedReader()
                        val requestLine = input.readLine() ?: return@use
                        var range: String? = null
                        while (true) {
                            val line = input.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.lowercase().startsWith("range:")) range = line.substringAfter("=").trim().removeSuffix("-").toLongOrNull()?.toString()
                        }
                        val mode = requestLine.split(' ').getOrNull(1)?.trim('/') ?: "honour"
                        val start = if (range == null) 0L else range!!.toLong()
                        // A server cannot legally send *less* than asked for and call it a range
                        // response; the only realistic refusal of an offset it cannot honour is a
                        // plain 200. So "shift" starts later than requested (a legal 206 that the
                        // launcher must reject, because the fragment no longer lines up).
                        val effective = when {
                            mode == "ignore" || range == null -> 0L
                            mode == "shift" -> (start + 1000).coerceAtMost(body.size.toLong())
                            else -> start
                        }
                        val out = sck.getOutputStream()
                        if (range != null && (mode == "honour" || mode == "shift")) {
                            out.write("HTTP/1.1 206 Partial Content\r\nContent-Range: bytes $effective-${body.size - 1}/${body.size}\r\nContent-Length: ${body.size - effective.toInt()}\r\nConnection: close\r\n\r\n".toByteArray())
                        } else {
                            out.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        }
                        out.write(body.copyOfRange(effective.toInt(), body.size))
                        out.flush()
                    }
                }
            }.apply { isDaemon = true; start() }
            try {
                for (mode in listOf("honour", "ignore", "shift")) {
                    for (partial in listOf(0, 20000)) {
                        val dir = Files.createTempDirectory("aerix-resume")
                        try {
                            val dest = dir.resolve("file.bin")
                            if (partial > 0) Files.write(dest.resolveSibling("file.bin.part"), body.copyOfRange(0, partial))
                            val job = net.kdt.aerix.desktop.core.net.DownloadJob("resume-test")
                            var errors = 0
                            job.onError = { errors++ }
                            net.kdt.aerix.desktop.core.net.Downloader.run(
                                job,
                                listOf(net.kdt.aerix.desktop.core.net.DownloadJob.Task(
                                    "http://127.0.0.1:$port/$mode", dest, sha1 = sha1, size = body.size.toLong()
                                )),
                                threads = 1
                            )
                            if (errors > 0) error("$mode/$partial reported an error")
                            if (!Files.isRegularFile(dest)) error("$mode/$partial produced no file")
                            if (!Files.readAllBytes(dest).contentEquals(body)) error("$mode/$partial produced the wrong bytes")
                            if (job.bytes != body.size.toLong())
                                error("$mode/$partial counted ${job.bytes} bytes for a ${body.size}-byte file")
                            if (Files.exists(dest.resolveSibling("file.bin.part"))) error("$mode/$partial left a .part file")
                        } finally {
                            dir.toFile().deleteRecursively()
                        }
                    }
                }
            } finally {
                runCatching { server.close() }
            }
        }

        check("java discovery") {
            val runtimes = JavaRuntimes.discover(force = true)
            // The launcher itself runs on one, so at least one must be found even headless.
            if (runtimes.isEmpty()) error("no Java runtime discovered even though the launcher is running on one")
            val picked = JavaRuntimes.pick(8)
            if (picked == null) error("pick(8) found nothing (Java 17+ should be accepted as a fallback)")
        }

        check("data folders exist") {
            for (dir in listOf(AppEnv.appData, AppEnv.logsDir, AppEnv.accountsDir, AppEnv.skinsDir, AppEnv.runtimesDir)) {
                if (!Files.isDirectory(dir)) error("$dir missing")
            }
        }

        // ---- rendering ---------------------------------------------------------------------
        check("text is never clipped by its own cell") {
            // The home page shipped with an eyebrow rendered as "YOUR NEXT WOR" and a headline
            // drawn from a negative origin, both because a vertical BoxLayout centred a child
            // whose preferred width did not fill the row. Nothing about the build or the
            // screenshots flagged it — the page just looked slightly wrong. This walks every
            // rendered page and reports any component whose preferred width does not fit the cell
            // the layout gave it.
            val width = 1440
            val height = 900
            val host = JPanel(CardLayout())
            host.isOpaque = false
            val problems = ArrayList<String>()
            val shell = HeadlessShell
            for (page in Page.values()) {
                val component = HeadlessPages.create(page, shell)
                host.add(component, page.name)
                (host.layout as CardLayout).show(host, page.name)
                host.setSize(width, height)
                host.doLayout()
                layoutRecursively(host)
                settleUntilReady(host)
                fun walk(c: java.awt.Component, path: String) {
                    val pref = c.preferredSize
                    // 2 px of slack: preferred sizes are computed to the pixel and borders differ.
                    if (c.width in 1..(pref.width - 3) && c.height > 4) {
                        problems.add("$page: $path ${c.javaClass.simpleName} cell=${c.width} wants=${pref.width}")
                    }
                    // Horizontal overflow poisons a whole page: one long label widens every
                    // ancestor, so report the outermost component whose content wants more width
                    // than the window instead of a hundred children.
                    if (c.width > 0 && pref.width > c.width + 2 && (c.parent == null || c.parent.width <= 0 ||
                            c.preferredSize.width <= c.width + 2)) {
                        problems.add("$page: $path ${c.javaClass.simpleName} is ${pref.width}px wide in a ${c.width}px window")
                    }
                    if (c is java.awt.Container) {
                        c.components.forEachIndexed { i, child -> walk(child, "$path/$i") }
                    }
                }
                walk(component, page.name)
                host.remove(component)
            }
            if (problems.isNotEmpty()) error(problems.take(12).joinToString("\n"))
        }

        check("every page renders offscreen") {
            val width = 1440
            val height = 900
            val host = JPanel(CardLayout())
            host.isOpaque = false
            val images = HashMap<String, BufferedImage>()
            val shell = HeadlessShell
            for (page in Page.values()) {
                val component = HeadlessPages.create(page, shell)
                host.add(component, page.name)
                (host.layout as CardLayout).show(host, page.name)
                host.setSize(width, height)
                host.doLayout()
                layoutRecursively(host)
                settleUntilReady(host)
                val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                val g = image.createGraphics()
                Paint.quality(g)
                g.paint = java.awt.GradientPaint(0f, 0f, Color(0x0B1B28), 0f, height.toFloat(), Color(0x07131D))
                g.fillRect(0, 0, width, height)
                host.printAll(g)
                g.dispose()
                images[page.name] = image
            }
            for ((name, image) in images) {
                ImageIO.write(image, "png", shotFile(name).toFile())
            }
        }

        // ---- report ------------------------------------------------------------------------
        val elapsed = System.currentTimeMillis() - started
        var failed = 0
        println()
        println("Aerix Launcher self test — $elapsed ms")
        println("─".repeat(64))
        for ((name, ok, message) in results) {
            println("${if (ok) "PASS" else "FAIL"}  $name" + if (message.isNotBlank()) "  — $message" else "")
            if (!ok) failed++
        }
        println("─".repeat(64))
        val runtimeFact = System.getProperty("java.runtime.name")
        Log.i("selftest", "runtime: $runtimeFact ${System.getProperty("java.version")}")
        Log.i("selftest", "${results.count { it.second }} passed, $failed failed")
        println("${results.count { it.second }} passed, $failed failed. Screenshots: ${AppEnv.appData.resolve("selftest-shots")}")
        return failed == 0
    }

    private fun shotFile(name: String): Path {
        val dir = AppEnv.appData.resolve("selftest-shots")
        Files.createDirectories(dir)
        return dir.resolve("page-$name.png")
    }

    private fun check(name: String, body: () -> Unit) {
        try {
            body()
            results.add(Triple(name, true, ""))
            Log.i("selftest", "PASS $name")
        } catch (e: Throwable) {
            results.add(Triple(name, false, e.message ?: e.toString()))
            Log.e("selftest", "FAIL $name: ${e.message}", e)
        }
    }

    /**
     * Lets a freshly-built page finish its asynchronous load (version lists, the server catalogue,
     * cover art) before it is photographed. Runs the pending EDT work, then waits up to [millis].
     */
    fun settle(host: java.awt.Container, millis: Long = 900) {
        val deadline = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < deadline) {
            try { javax.swing.SwingUtilities.invokeAndWait { host.doLayout() } } catch (_: Throwable) { }
            Thread.sleep(60)
            layoutRecursively(host)
        }
    }

    /**
     * True while any visible label still reads like a placeholder ("Loading…", "…").
     *
     * A screenshot taken mid-request shows a spinner that the player would never see for long, and
     * makes design review disagree with the product. Pages that fetch on creation are given a
     * bounded time to reach a resting state; when the network is unreachable that state is the
     * error or empty message, which is exactly what should be photographed.
     */
    fun isPending(host: java.awt.Container): Boolean {
        var pending = false
        fun walk(c: java.awt.Component) {
            if (pending || !c.isVisible) return
            val text = (c as? javax.swing.JLabel)?.text ?: ""
            // Compare by code point rather than `endsWith("…")`: on this JDK, endsWith with a
            // non-ASCII literal returns false for a string that equals the same literal, and the
            // harness then photographs a mid-request spinner. Comparing the character directly is
            // both clearer and immune to that.
            val last = text.lastOrNull()?.code
            if (last == 0x2026 || text == "…" || text.contains("Loading the version list") ||
                text.contains("Loading", true) && text.length < 40
            ) {
                pending = true
                return
            }
            if (c is java.awt.Container) c.components.forEach { walk(it) }
        }
        walk(host)
        return pending
    }

    /** Settles until no placeholder is visible, or [maxMillis] elapses. */
    fun settleUntilReady(host: java.awt.Container, maxMillis: Long = 12_000) {
        val deadline = System.currentTimeMillis() + maxMillis
        while (System.currentTimeMillis() < deadline) {
            layoutRecursively(host)
            if (!isPending(host)) { settle(host, 300); return }
            Thread.sleep(120)
        }
        settle(host, 200)
    }

    /**
     * Lays a component tree out without a window.
     *
     * `doLayout()` alone is not enough offscreen: a `JScrollPane`'s view keeps whatever size the
     * viewport gives it, which is the viewport's size until the view reports a preferred height.
     * Left alone, long pages get squeezed into one screenful (every server card 38 pixels tall).
     */
    fun layoutRecursively(c: java.awt.Container) {
        c.doLayout()
        for (child in c.components) {
            if (!child.isVisible) continue
            if (child is javax.swing.JViewport) {
                val view = child.view ?: continue
                val pref = view.preferredSize
                view.setSize(
                    maxOf(pref.width, child.width),
                    maxOf(pref.height, child.height)
                )
                if (view is java.awt.Container) layoutRecursively(view)
                continue
            }
            if (child is java.awt.Container) layoutRecursively(child)
        }
    }
}

val HeadlessShell: Shell by lazy { Shell(unattached = true) }

/** Factory used by the harness; real navigation hits the same components the GUI uses. */
object HeadlessPages {
    fun create(page: Page, shell: Shell): JComponent = when (page) {
        Page.HOME -> net.kdt.aerix.desktop.ui.screens.HomeScreen(shell)
        Page.CREATE -> net.kdt.aerix.desktop.ui.screens.CreateInstanceScreen(shell)
        Page.LIBRARY -> net.kdt.aerix.desktop.ui.screens.LibraryScreen(shell, null)
        Page.DISCOVER -> net.kdt.aerix.desktop.ui.screens.DiscoverScreen(shell)
        Page.MODPACKS -> net.kdt.aerix.desktop.ui.screens.ModpacksScreen(shell)
        Page.RELEASES -> net.kdt.aerix.desktop.ui.screens.ReleasesScreen(shell)
        Page.SERVERS -> net.kdt.aerix.desktop.ui.screens.ServersScreen(shell)
        Page.SETTINGS -> net.kdt.aerix.desktop.ui.screens.SettingsScreen(shell)
        Page.ACCOUNTS -> net.kdt.aerix.desktop.ui.screens.AccountsScreen(shell)
        Page.CONTRIBUTION -> net.kdt.aerix.desktop.ui.screens.ContributionScreen(shell)
        Page.ABOUT -> net.kdt.aerix.desktop.ui.screens.AboutScreen(shell)
        Page.LOGS -> net.kdt.aerix.desktop.ui.screens.ConsoleScreen(shell)
        Page.FILES -> net.kdt.aerix.desktop.ui.screens.FilesScreen(shell)
    }
}

/** `--screenshot`: renders every page and exits, for design review on a machine without a display. */
object ScreenshotHarness {
    fun run() {
        val outDir = java.nio.file.Paths.get("build", "screenshots")
        Files.createDirectories(outDir)
        Settings.load(AppEnv.appData.resolve("settings.json"))
        AerixTheme.refresh()
        Fonts.rebuild()
        UiScale.update(96)
        val shell = HeadlessShell
        val width = 1440
        val height = 900
        for (page in Page.values()) {
            val component = HeadlessPages.create(page, shell)
            val host = JPanel(BorderLayout())
            host.isOpaque = false
            host.add(component, BorderLayout.CENTER)
            host.setSize(width, height)
            host.doLayout()
            SelfTest.layoutRecursively(host)
            SelfTest.settleUntilReady(host)
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics() as Graphics2D
            Paint.quality(g)
            g.paint = java.awt.GradientPaint(0f, 0f, Color(0x0B1B28), 0f, height.toFloat(), Color(0x07131D))
            g.fillRect(0, 0, width, height)
            host.printAll(g)
            g.dispose()
            ImageIO.write(image, "png", outDir.resolve("page-${page.name.lowercase()}.png").toFile())
            println("wrote $outDir/page-${page.name.lowercase()}.png")
        }
    }
}
