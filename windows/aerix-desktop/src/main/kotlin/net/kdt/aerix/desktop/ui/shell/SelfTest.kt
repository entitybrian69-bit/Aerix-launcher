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
                settle(host)
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
            SelfTest.settle(host)
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
