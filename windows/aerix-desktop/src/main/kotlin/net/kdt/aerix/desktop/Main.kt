package net.kdt.aerix.desktop

import net.kdt.aerix.desktop.app.AppEnv
import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Scheduler
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.auth.accounts.Accounts
import net.kdt.aerix.desktop.core.instance.Instances
import net.kdt.aerix.desktop.ui.shell.ScreenshotHarness
import net.kdt.aerix.desktop.ui.shell.AerixWindow
import net.kdt.aerix.desktop.ui.shell.Page
import net.kdt.aerix.desktop.ui.shell.SelfTest
import net.kdt.aerix.desktop.ui.shell.Shell
import net.kdt.aerix.desktop.ui.theme.AerixTheme
import net.kdt.aerix.desktop.ui.theme.UiScale
import javax.swing.SwingUtilities
import javax.swing.UIManager
import java.awt.GraphicsEnvironment

/**
 * Entry point.
 *
 * Modes, all handled here because the launcher is one binary:
 *   `--help`        usage
 *   `--version`     print the version (used by the installer and the updater)
 *   `--selftest`    headless check of data, auth sealing, network metadata and every page; writes
 *                   screenshots to `build/screenshots/` and exits non-zero on failure
 *   `--screenshot`  render every page to PNG without checking anything (design review)
 *   `--data <dir>`  override `%APPDATA%\Aerix Launcher` (portable mode passes the app folder)
 *   `--game-dir <dir>`  override the game directory for this run
 *   no arguments    the window
 */
fun main(args: Array<String>) {
    var mode = "gui"
    var dataOverride: String? = null
    var gameOverride: String? = null
    var i = 0
    while (i < args.size) {
        when (val a = args[i]) {
            "--help", "-h", "/?" -> { printUsage(); return }
            "--version", "-v" -> {
                System.out.println("Aerix Launcher ${readVersion()}")
                return
            }
            "--selftest" -> mode = "selftest"
            "--screenshot" -> mode = "screenshot"
            "--list-pages" -> mode = "list-pages"
            "--data" -> { dataOverride = args.getOrNull(++i); if (dataOverride == null) fail("--data needs a folder") }
            "--game-dir" -> { gameOverride = args.getOrNull(++i); if (gameOverride == null) fail("--game-dir needs a folder") }
            "--safe-mode" -> System.setProperty("aerix.safeMode", "true")
            else -> {
                if (a.startsWith("-")) { System.err.println("Unknown option: $a"); printUsage(); kotlin.system.exitProcess(2) }
                // A dropped file (the installer passes it) opens the import flow.
                System.setProperty("aerix.openPath", a)
            }
        }
        i++
    }
    if (dataOverride != null) System.setProperty("aerix.dataDir", dataOverride)
    if (gameOverride != null) System.setProperty("aerix.gameDir", gameOverride)

    AppEnv.ensureDirs()
    val level = if (mode == "gui") Log.Level.INFO else Log.Level.DEBUG
    Log.init(AppEnv.logsDir, level)
    AppEnv.version = readVersion()
    AppEnv.buildChannel = if (AppEnv.isWindows) "windows-x64" else "dev"

    Log.i("main", "Aerix Launcher ${AppEnv.version} starting (mode=$mode, java=${System.getProperty("java.version")}, os=${System.getProperty("os.name")})")

    when (mode) {
        "list-pages" -> {
            Page.entries.forEach { println("${it.name.lowercase()}\t${it.label}\t${it.icon}") }
            return
        }
        "selftest" -> {
            val ok = SelfTest.run()
            Log.shutdown()
            kotlin.system.exitProcess(if (ok) 0 else 1)
        }
        "screenshot" -> {
            ScreenshotHarness.run()
            Log.shutdown()
            return
        }
    }

    if (GraphicsEnvironment.isHeadless()) {
        System.err.println("This machine has no display. Use --selftest or --screenshot.")
        kotlin.system.exitProcess(3)
    }
    launchGui()
}

private fun launchGui() {
    System.setProperty("awt.useSystemAAFontSettings", "on")
    System.setProperty("swing.aatext", "true")
    System.setProperty("sun.java2d.uiScale.enabled", "true")
    runCatching {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    }

    val startup = longArrayOf(System.currentTimeMillis())
    SwingUtilities.invokeLater {
        runCatching {
            applyDpi()
            AerixTheme.refresh()
            Settings.load(AppEnv.appData.resolve("settings.json"))
            Accounts.load()
            Instances.load()
        }.onFailure {
            Log.e("main", "startup state failed: ${it.message}", it)
            javax.swing.JOptionPane.showMessageDialog(
                null,
                "The launcher could not read its settings:\n\n${it.message}\n\nA backup of the broken file was kept.",
                "Aerix Launcher", javax.swing.JOptionPane.ERROR_MESSAGE
            )
        }
        val window = AerixWindow()
        installTray(window)
        window.isVisible = true
        window.toFront()
        Log.i("main", "window shown in ${System.currentTimeMillis() - startup[0]} ms")
        Settings.set("lastLaunch", System.currentTimeMillis())

        // A file passed on the command line (drag onto the exe, or "Open with"): import it.
        System.getProperty("aerix.openPath")?.let { path ->
            net.kdt.aerix.desktop.app.Scheduler.later(600) {
                net.kdt.aerix.desktop.ui.screens.DropImport.importFile(window.shell, java.nio.file.Path.of(path))
            }
        }
        // Updates are checked quietly, once a day, and never block the window.
        if (Settings.bool("checkForUpdates", true)) {
            net.kdt.aerix.desktop.app.Scheduler.io("update-check") {
                net.kdt.aerix.desktop.app.Updates.checkInBackground()
            }
        }
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { Settings.saveNow() }
            runCatching { Log.flushNow() }
        })
    }
}

private fun applyDpi() {
    val gc = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    runCatching {
        // Java 9+ already scales; this only feeds UiScale so the design px match the OS px.
        UiScale.update((96 * gc.defaultTransform.scaleX).toInt())
    }.onFailure { UiScale.update(96) }
    Log.i("main", "ui scale ${UiScale.factor}")
}

private fun installTray(window: AerixWindow) {
    if (!java.awt.SystemTray.isSupported()) return
    runCatching {
        val tray = java.awt.SystemTray.getSystemTray()
        val image = runCatching {
            java.awt.Toolkit.getDefaultToolkit().getImage(
                AerixWindow::class.java.getResource("/aerix/brand/icon_32.png")
            ).getScaledInstance(16, 16, java.awt.Image.SCALE_SMOOTH)
        }.getOrNull() ?: return
        val popup = java.awt.PopupMenu("Aerix Launcher")
        val open = java.awt.MenuItem("Open Aerix Launcher")
        open.addActionListener { SwingUtilities.invokeLater { window.isVisible = true; window.toFront() } }
        popup.add(open)
        popup.addSeparator()
        val playing = net.kdt.aerix.desktop.core.game.GameLauncher.runningGames()
        if (playing.isNotEmpty()) {
            playing.forEach { game ->
                val stop = java.awt.MenuItem("Stop ${game.instance.name}")
                stop.addActionListener { net.kdt.aerix.desktop.core.game.GameLauncher.stop(game) }
                popup.add(stop)
            }
            popup.addSeparator()
        }
        val quit = java.awt.MenuItem("Quit")
        quit.addActionListener {
            if (Settings.bool("closeGameWithLauncher", false)) net.kdt.aerix.desktop.core.game.GameLauncher.stopAll()
            Settings.saveNow()
            System.exit(0)
        }
        popup.add(quit)
        val icon = java.awt.TrayIcon(image, "Aerix Launcher", popup)
        icon.addActionListener { window.isVisible = true; window.toFront() }
        icon.isImageAutoSize = true
        tray.add(icon)
        window.onHideToTray = {
            window.isVisible = false
            runCatching { icon.displayMessage("Aerix Launcher", "Still running in the tray. Click to reopen.", java.awt.TrayIcon.MessageType.INFO) }
        }
    }.onFailure { Log.w("tray", "could not install the tray icon: ${it.message}") }
}

private fun readVersion(): String {
    val props = runCatching {
        val stream = MainKtMarker::class.java.getResourceAsStream("/aerix/version.txt") ?: return@runCatching null
        stream.use { it.readBytes().toString(Charsets.UTF_8).trim() }
    }.getOrNull()
    return props?.ifBlank { null } ?: "dev"
}

private object MainKtMarker

private fun printUsage() {
    System.out.println(
        """
        Aerix Launcher ${readVersion()}

        Usage: aerix-launcher.exe [options] [file]

          --help, -h            show this text
          --version             print the version
          --selftest            run the headless self test and exit (0 = pass)
          --screenshot          render every page to build/screenshots
          --list-pages          print the navigation pages
          --data <dir>          use <dir> instead of %APPDATA%\Aerix Launcher
          --game-dir <dir>      use <dir> for instances and game files
          --safe-mode           start without the wallpaper and without animations
          <file>                import a .mrpack / mod / zip on start
        """.trimIndent()
    )
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    kotlin.system.exitProcess(2)
}
