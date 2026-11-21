package net.kdt.aerix.desktop.ui.screens

import net.kdt.aerix.desktop.app.AppEnv
import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Scheduler
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.game.JavaRuntimes
import net.kdt.aerix.desktop.core.instance.Instances
import net.kdt.aerix.desktop.ui.components.AerixButton
import net.kdt.aerix.desktop.ui.components.AerixField
import net.kdt.aerix.desktop.ui.components.AerixSlider
import net.kdt.aerix.desktop.ui.components.AerixSwitch
import net.kdt.aerix.desktop.ui.components.ButtonStyle
import net.kdt.aerix.desktop.ui.components.ClearPanel
import net.kdt.aerix.desktop.ui.components.IconButton
import net.kdt.aerix.desktop.ui.components.PillTab
import net.kdt.aerix.desktop.ui.components.SectionCard
import net.kdt.aerix.desktop.ui.components.SettingRow
import net.kdt.aerix.desktop.ui.components.aerixScroll
import net.kdt.aerix.desktop.ui.components.heading
import net.kdt.aerix.desktop.ui.components.label
import net.kdt.aerix.desktop.ui.components.vspace
import net.kdt.aerix.desktop.ui.shell.Page
import net.kdt.aerix.desktop.ui.shell.Refreshable
import net.kdt.aerix.desktop.ui.shell.Shell
import net.kdt.aerix.desktop.ui.theme.AerixColors
import net.kdt.aerix.desktop.ui.theme.AerixTheme
import net.kdt.aerix.desktop.ui.theme.Backdrops
import net.kdt.aerix.desktop.ui.theme.Fonts
import net.kdt.aerix.desktop.ui.theme.Paint
import net.kdt.aerix.desktop.ui.theme.Radii
import net.kdt.aerix.desktop.ui.theme.Spacing
import net.kdt.aerix.desktop.ui.theme.UiScale
import net.kdt.aerix.desktop.ui.theme.withAlpha
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JPanel

/**
 * Settings, on the Android page's tab model: Appearance, Game, Java, Wallpapers, Launcher, About.
 *
 * The Android renderer/controls/gamepad tabs are device-only; the desktop replacements are
 * "Game" (memory, arguments, game directory) and "Launcher" (window, updates, tray, behaviour).
 * Every control writes the same preference key the Android app uses where one exists, so a
 * settings file carried over keeps its meaning.
 */
class SettingsScreen(private val shell: Shell) : JPanel(BorderLayout()), Refreshable {
    private var tab = 0
    private val tabs = listOf("Appearance", "Game", "Java", "Wallpapers", "Launcher", "About")
    private val tabRow = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(6), 0))
    private val bodyHost = ClearPanel(BorderLayout())

    init {
        isOpaque = false
        add(buildHeader(), BorderLayout.NORTH)
        val body = ClearPanel(BorderLayout())
        body.border = BorderFactory.createEmptyBorder(0, UiScale.px(Spacing.xl), UiScale.px(Spacing.xl), UiScale.px(Spacing.xl))
        bodyHost.isOpaque = false
        val scroll = aerixScroll(bodyHost)
        scroll.border = BorderFactory.createEmptyBorder()
        body.add(scroll, BorderLayout.CENTER)
        add(body, BorderLayout.CENTER)
        rebuildTabs()
        rebuildBody()
    }

    override fun refresh() = rebuildBody()

    private fun buildHeader(): JPanel {
        val header = ClearPanel(BorderLayout())
        header.border = BorderFactory.createEmptyBorder(
            UiScale.px(Spacing.lg), UiScale.px(Spacing.xl), UiScale.px(Spacing.md), UiScale.px(Spacing.xl)
        )
        val titles = ClearPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(heading("Settings", 20f).apply { alignmentX = 0f })
            add(label("Launcher behaviour, Java, appearance and game defaults", Fonts.label(12f), AerixColors.textMuted).apply { alignmentX = 0f })
        }
        header.add(titles, BorderLayout.NORTH)
        header.add(tabRow, BorderLayout.SOUTH)
        return header
    }

    private fun rebuildTabs() {
        tabRow.removeAll()
        tabs.forEachIndexed { i, name ->
            tabRow.add(PillTab(name, i == tab).apply {
                addActionListener { tab = i; rebuildTabs(); rebuildBody() }
            })
        }
        tabRow.revalidate(); tabRow.repaint()
    }

    private fun rebuildBody() {
        bodyHost.removeAll()
        val content = when (tab) {
            0 -> appearanceTab()
            1 -> gameTab()
            2 -> javaTab()
            3 -> wallpapersTab()
            4 -> launcherTab()
            else -> aboutTab()
        }
        bodyHost.add(content, BorderLayout.CENTER)
        bodyHost.revalidate(); bodyHost.repaint()
    }

    // ---- appearance --------------------------------------------------------------------------

    private fun appearanceTab(): JPanel {
        val col = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

        val theme = SectionCard("Colour theme", "The accent colour of every control in the launcher")
        val swatchRow = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(8), UiScale.px(6)))
        AerixTheme.accentValues.forEach { (name, color) ->
            swatchRow.add(AccentSwatch(name, color) {
                Settings.set("launcherColorTheme", "AERIX")
                Settings.set("launcherCustomColor", color.rgb)
                AerixTheme.refresh()
                shell.repaint()
                rebuildBody()
            })
        }
        // custom accent
        swatchRow.add(AccentSwatch("Custom", Color(Settings.int("launcherCustomColor", AerixColors.accentDefault.rgb))) {
            val c = javax.swing.JColorChooser.showDialog(this, "Custom accent colour",
                Color(Settings.int("launcherCustomColor", AerixColors.accentDefault.rgb)))
            if (c != null) {
                Settings.set("launcherColorTheme", "CUSTOM")
                Settings.set("launcherCustomColor", c.rgb)
                AerixTheme.refresh()
                shell.repaint()
                rebuildBody()
            }
        })
        theme.body.add(swatchRow)
        col.add(theme)
        col.add(vspace(12))

        val glass = SectionCard("Transparency", "How strongly the panels tint the wallpaper")
        val layoutSlider = AerixSlider(
            min = 25, max = 100, value = Settings.int("layoutTransparency", 100), step = 1, suffix = " %"
        )
        layoutSlider.addChangeListener { Settings.set("layoutTransparency", layoutSlider.value); shell.repaint() }
        glass.body.add(SettingRow("Layout transparency", "100 matches the design; lower values let more of the wallpaper through.", layoutSlider))

        val bgSlider = AerixSlider(
            min = 20, max = 100, value = Settings.int("launcherBackgroundOpacity", 60), step = 1, suffix = " %"
        )
        bgSlider.addChangeListener {
            Settings.set("launcherBackgroundOpacity", bgSlider.value)
            shell.window?.invalidateBackdrop()
            shell.repaint()
        }
        glass.body.add(SettingRow("Wallpaper opacity", "The Android default is 60%.", bgSlider))

        val blur = AerixSwitch(Settings.bool("backgroundBlur", false)).apply {
            addChangeListener {
                Settings.set("backgroundBlur", on)
                shell.window?.invalidateBackdrop()
                shell.repaint()
            }
        }
        glass.body.add(SettingRow("Blur the wallpaper", "Softens the backdrop so text stays readable.", blur))
        col.add(glass)
        col.add(vspace(12))

        val font = SectionCard("Font", "Poppins ships with the launcher")
        val choices = listOf("Poppins", "System") + net.kdt.aerix.desktop.ui.theme.SystemFonts.families().take(24)
        val fontButton = AerixButton(Settings.string("launcherFont", "Poppins"), ButtonStyle.GHOST).apply {
            compact = true
            addActionListener {
                val picked = JOptionPane.showInputDialog(this@SettingsScreen, "Launcher font", "Font",
                    JOptionPane.QUESTION_MESSAGE, null, choices.toTypedArray(), Settings.string("launcherFont", "Poppins")) as? String
                if (picked != null) {
                    Settings.set("launcherFont", picked)
                    net.kdt.aerix.desktop.ui.theme.Fonts.rebuild()
                    shell.window?.let { w ->
                        javax.swing.SwingUtilities.updateComponentTreeUI(w)
                        w.repaint()
                    }
                    rebuildBody()
                }
            }
        }
        font.body.add(SettingRow("Launcher font", "Applies to menus, labels and headings.", fontButton))
        col.add(font)
        return col
    }

    // ---- game --------------------------------------------------------------------------------

    private fun gameTab(): JPanel {
        val col = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val memory = SectionCard("Memory", "Applies to instances that do not override it")
        val ram = AerixSlider(
            min = 512, max = maxOf(2048, AppEnv.totalMemoryMb - 512),
            value = Settings.int("memoryAllocationMb", defaultMemory()), step = 256, suffix = " MB"
        )
        ram.addChangeListener { Settings.set("memoryAllocationMb", ram.value) }
        memory.body.add(SettingRow("RAM allocation", "This machine has ${AppEnv.totalMemoryMb} MB. Leave headroom for Windows.", ram))
        col.add(memory); col.add(vspace(12))

        val dirs = SectionCard("Folders", "Where the launcher keeps instances and downloads")
        val gameDir = AerixField(placeholder = "Game directory", text0 = Instances.rootDir().parent.toString(), icon = "folder").apply {
            preferredSize = Dimension(UiScale.px(340), UiScale.px(40))
        }
        val browse = AerixButton("Browse…", ButtonStyle.GHOST).apply {
            compact = true
            addActionListener {
                val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
                if (chooser.showOpenDialog(this@SettingsScreen) == JFileChooser.APPROVE_OPTION) {
                    Settings.set("gameDirectory", chooser.selectedFile.absolutePath)
                    Instances.reload()
                    rebuildBody()
                    shell.toast("Game directory changed — existing instances stay where they are until reopened")
                }
            }
        }
        val dirRow = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(6), 0))
        dirRow.add(gameDir); dirRow.add(browse)
        dirs.body.add(SettingRow("Game directory", "Instances live in <dir>/instances; shared data in <dir>/shared.", dirRow))

        val dataRow = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(6), 0))
        dataRow.add(AerixButton("Open launcher data", ButtonStyle.GHOST, "folder").apply {
            compact = true; addActionListener { openFolder(AppEnv.appData.toFile()) }
        })
        dataRow.add(AerixButton("Open logs", ButtonStyle.GHOST, "logs").apply {
            compact = true; addActionListener { openFolder(AppEnv.logsDir.toFile()) }
        })
        dirs.body.add(SettingRow("Launcher data", "%APPDATA%\\Aerix Launcher — settings, accounts, runtimes, logs.", dataRow))
        col.add(dirs); col.add(vspace(12))

        val args = SectionCard("Java arguments", "Default JVM flags for every instance")
        val argsField = AerixField(placeholder = "JVM arguments", text0 = Settings.string("jvmArgs", net.kdt.aerix.desktop.core.instance.Instance.defaultJvmArgs())).apply {
            preferredSize = Dimension(UiScale.px(420), UiScale.px(40))
        }
        val saveArgs = AerixButton("Save", ButtonStyle.SECONDARY).apply {
            compact = true
            addActionListener { Settings.set("jvmArgs", argsField.text); shell.toast("JVM arguments saved") }
        }
        val argsRow = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(6), 0))
        argsRow.add(argsField); argsRow.add(saveArgs)
        args.body.add(SettingRow("Default arguments", "Includes the G1GC tuning from the Android build.", argsRow))
        val reset = AerixButton("Reset to defaults", ButtonStyle.GHOST).apply {
            compact = true
            addActionListener {
                Settings.set("jvmArgs", net.kdt.aerix.desktop.core.instance.Instance.defaultJvmArgs())
                argsField.text = net.kdt.aerix.desktop.core.instance.Instance.defaultJvmArgs()
                shell.toast("Reset")
            }
        }
        args.body.add(reset)
        col.add(args)
        return col
    }

    private fun defaultMemory(): Int {
        val total = AppEnv.totalMemoryMb
        return ((total / 4) / 512 * 512).coerceIn(1024, maxOf(1024, total - 2048))
    }

    // ---- java --------------------------------------------------------------------------------

    private fun javaTab(): JPanel {
        val col = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val card = SectionCard("Installed runtimes", "Found on PATH, JAVA_HOME, the launcher's own runtimes folder, and Program Files")

        val runtimes = runCatching { JavaRuntimes.discover() }.getOrDefault(emptyList())
        if (runtimes.isEmpty()) {
            card.body.add(label("No Java runtime found yet.", Fonts.body(12.5f), AerixColors.warning))
        } else {
            runtimes.forEach { rt ->
                card.body.add(JavaRow(rt.label + if (rt.managed) "  ·  managed" else "", rt.detail))
            }
        }
        val actions = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(8), UiScale.px(8)))
        actions.add(AerixButton("Rescan", ButtonStyle.SECONDARY, "refresh").apply {
            compact = true
            addActionListener { JavaRuntimes.discover(force = true); rebuildBody() }
        })
        actions.add(AerixButton("Add runtime…", ButtonStyle.GHOST, "folder").apply {
            compact = true
            addActionListener {
                val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
                if (chooser.showOpenDialog(this@SettingsScreen) == JFileChooser.APPROVE_OPTION) {
                    val dir = chooser.selectedFile.toPath()
                    val probe = JavaRuntimes.probe(dir)
                    if (probe != null) {
                        Settings.set("javaHomeOverride", dir.toString())
                        shell.toast("Using ${probe.label}")
                        rebuildBody()
                    } else shell.toast("That folder does not look like a Java home (no bin/java)")
                }
            }
        })
        card.body.add(actions)
        col.add(card); col.add(vspace(12))

        val download = SectionCard("Download a runtime", "Temurin builds arrive pre-verified with SHA-256")
        val row = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(8), UiScale.px(4)))
        listOf(8, 17, 21, 25).forEach { major ->
            row.add(AerixButton("Java $major", ButtonStyle.GHOST, "download").apply {
                compact = true
                addActionListener {
                    val dialog = ProgressDialog(shell, "Downloading Java $major")
                    dialog.isVisible = true
                    Scheduler.io("jre-$major") {
                        val result = runCatching {
                            JavaRuntimes.install(major) { text, fraction -> dialog.update(text, fraction) }
                        }
                        Scheduler.ui {
                            dialog.dispose()
                            result.onSuccess { rt ->
                                if (rt != null) { shell.toast("Java $major installed"); rebuildBody() }
                                else shell.toast("No Temurin build for Java $major on this platform")
                            }.onFailure { JOptionPane.showMessageDialog(this@SettingsScreen, it.message, "Download failed", JOptionPane.ERROR_MESSAGE) }
                        }
                    }
                }
            })
        }
        download.body.add(row)
        col.add(download)
        return col
    }

    // ---- wallpapers --------------------------------------------------------------------------

    private fun wallpapersTab(): JPanel {
        val col = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val card = SectionCard("Wallpapers", "The 25 wallpapers from the Android launcher, plus your own")
        val grid = ClearPanel(GridLayout(0, 4, UiScale.px(10), UiScale.px(10)))
        val current = Settings.string("selected_wallpaper_id", "wp_01_lush_caves")
        Backdrops.presets().forEach { preset ->
            grid.add(WallpaperTile(preset.id, preset.label, preset.id == current) {
                Settings.set("selected_wallpaper_id", preset.id)
                shell.window?.invalidateBackdrop()
                shell.repaint()
                rebuildBody()
            })
        }
        // custom wallpapers
        runCatching { Backdrops.customDir().listFiles() }.getOrNull()?.filter { it.isFile }?.forEach { file ->
            grid.add(WallpaperTile("custom:" + file.name, file.nameWithoutExtension, "custom:" + file.name == current) {
                Settings.set("selected_wallpaper_id", "custom:" + file.name)
                shell.window?.invalidateBackdrop()
                shell.repaint(); rebuildBody()
            })
        }
        card.body.add(grid)
        card.body.add(vspace(10))
        card.body.add(AerixButton("Add your own wallpaper…", ButtonStyle.SECONDARY, "image").apply {
            compact = true
            addActionListener {
                val chooser = JFileChooser().apply {
                    fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "webp")
                }
                if (chooser.showOpenDialog(this@SettingsScreen) == JFileChooser.APPROVE_OPTION) {
                    runCatching {
                        java.nio.file.Files.createDirectories(Backdrops.customDir().toPath())
                        java.nio.file.Files.copy(chooser.selectedFile.toPath(),
                            Backdrops.customDir().toPath().resolve(chooser.selectedFile.name),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        Backdrops.invalidate()
                        rebuildBody()
                    }.onFailure { shell.toast("Could not add that image: ${it.message}") }
                }
            }
        })
        col.add(card)
        return col
    }

    // ---- launcher ----------------------------------------------------------------------------

    private fun launcherTab(): JPanel {
        val col = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val behaviour = SectionCard("Behaviour", "How the launcher opens and closes")
        val tray = AerixSwitch(Settings.bool("minimizeToTrayOnClose", false)).apply {
            addChangeListener { Settings.set("minimizeToTrayOnClose", on) }
        }
        behaviour.body.add(SettingRow("Keep running in the tray on close", "Games continue; the launcher stays one click away.", tray))

        val closeGame = AerixSwitch(Settings.bool("closeGameWithLauncher", false)).apply {
            addChangeListener { Settings.set("closeGameWithLauncher", on) }
        }
        behaviour.body.add(SettingRow("Close the game with the launcher", "Otherwise the game window stays open, like the Android app.", closeGame))

        val checkUpdates = AerixSwitch(Settings.bool("checkForUpdates", true)).apply {
            addChangeListener { Settings.set("checkForUpdates", on) }
        }
        behaviour.body.add(SettingRow("Check for launcher updates", "Quietly, once a day, when the launcher starts.", checkUpdates))
        col.add(behaviour); col.add(vspace(12))

        val storage = SectionCard("Storage", "Instances and their trash")
        val trashCount = Instances.trashed().size
        val trashRow = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(8), 0))
        trashRow.add(label("$trashCount item(s) in the Trash", Fonts.body(12.5f), AerixColors.textSecondary))
        trashRow.add(AerixButton("Restore latest", ButtonStyle.GHOST, "restore").apply {
            compact = true
            addActionListener {
                val latest = Instances.trashed().firstOrNull()
                if (latest == null) shell.toast("The Trash is empty")
                else { Instances.restore(latest); shell.toast("Restored ${latest.originalName}"); rebuildBody() }
            }
        })
        trashRow.add(AerixButton("Empty Trash", ButtonStyle.DANGER, "delete").apply {
            compact = true
            addActionListener {
                val ok = JOptionPane.showConfirmDialog(this@SettingsScreen,
                    "Permanently delete $trashCount item(s) from the Trash?\nWorlds inside them cannot be recovered.",
                    "Empty Trash", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
                if (ok == JOptionPane.OK_OPTION) { Instances.emptyTrash(); shell.toast("Trash emptied"); rebuildBody() }
            }
        })
        storage.body.add(SettingRow("Instance trash", "Deleted instances wait here so nothing is lost by accident.", trashRow))
        col.add(storage)
        return col
    }

    // ---- about -------------------------------------------------------------------------------

    private fun aboutTab(): JPanel {
        val col = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val card = SectionCard("Aerix Launcher", "Windows desktop build")
        card.body.add(label("Version ${AppEnv.version} (${AppEnv.buildChannel})", Fonts.bodyStrong(13f)))
        card.body.add(label("A fork of PojavLauncher, licensed under GPL-3.0.", Fonts.body(12.5f), AerixColors.textSecondary))
        card.body.add(vspace(6))
        card.body.add(label("Java runtime: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})", Fonts.label(11.5f), AerixColors.textMuted))
        card.body.add(label("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} · ${System.getProperty("os.arch")}", Fonts.label(11.5f), AerixColors.textMuted))
        card.body.add(label("Memory: ${AppEnv.totalMemoryMb} MB total, ${AppEnv.freeMemoryMb()} MB free", Fonts.label(11.5f), AerixColors.textMuted))
        card.body.add(vspace(10))
        card.body.add(AerixButton("Open About page", ButtonStyle.GHOST, "info").apply {
            compact = true; addActionListener { shell.navigate(Page.ABOUT) }
        })
        card.body.add(vspace(6))
        card.body.add(AerixButton("Copy system info", ButtonStyle.GHOST, "copy").apply {
            compact = true
            addActionListener {
                val text = buildString {
                    appendLine("Aerix Launcher ${AppEnv.version}")
                    appendLine("Java ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
                    appendLine("${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
                    appendLine("Memory: ${AppEnv.totalMemoryMb} MB")
                    appendLine("Game dir: ${Instances.rootDir().parent}")
                }
                java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
                shell.toast("System info copied")
            }
        })
        col.add(card)
        return col
    }
}

// ---- small parts -------------------------------------------------------------------------------

private class AccentSwatch(private val name: String, private val color: Color, private val onClick: () -> Unit) : JComponent() {
    private var hover = false
    init {
        preferredSize = Dimension(UiScale.px(84), UiScale.px(64))
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = name
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: MouseEvent) { hover = false; repaint() }
            override fun mouseClicked(e: MouseEvent) = onClick()
        })
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val selected = AerixTheme.accent.rgb == color.rgb
        if (hover || selected) {
            g2.color = withAlpha(if (selected) AerixTheme.accent else Color.WHITE, if (selected) 30 else 16)
            g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), UiScale.px(14) * 2f, UiScale.px(14).toFloat() * 2f))
        }
        g2.color = color
        g2.fillOval((width - UiScale.px(30)) / 2, UiScale.px(8), UiScale.px(30), UiScale.px(30))
        if (selected) {
            g2.color = AerixTheme.onAccent
            net.kdt.aerix.desktop.ui.icons.Icons.draw(g2, "check", width / 2 - UiScale.px(7), UiScale.px(8) + UiScale.px(8), UiScale.px(14), AerixTheme.onAccent)
        }
        g2.font = Fonts.label(9.5f)
        g2.color = AerixColors.textMuted
        val w = g2.fontMetrics.stringWidth(name)
        g2.drawString(name, (width - w) / 2, height - UiScale.px(6))
        g2.dispose()
    }
}

private class WallpaperTile(private val id: String, private val name: String, private val selected: Boolean, private val onClick: () -> Unit) : JComponent() {
    private var hover = false
    private var thumb: java.awt.image.BufferedImage? = null
    init {
        preferredSize = Dimension(UiScale.px(180), UiScale.px(104))
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: MouseEvent) { hover = false; repaint() }
            override fun mouseClicked(e: MouseEvent) = onClick()
        })
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        if (thumb == null) {
            thumb = Backdrops.load(id)?.let { Backdrops.cover(it, width.coerceAtLeast(1), height.coerceAtLeast(1)) }
        }
        val r = UiScale.px(12)
        g2.clip = RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), r * 2f, r * 2f)
        val img = thumb
        if (img != null) g2.drawImage(img, 0, 0, width, height, null)
        else { g2.color = Color(0x14293A); g2.fillRect(0, 0, width, height) }
        g2.clip = null
        // label strip
        g2.color = Color(0, 8, 14, 160)
        g2.fill(RoundRectangle2D.Float(0f, height - UiScale.px(24).toFloat(), width.toFloat(), UiScale.px(24).toFloat(), 0f, 0f))
        g2.font = Fonts.label(10f)
        g2.color = Color.WHITE
        g2.drawString(name.take(24), UiScale.px(8), height - UiScale.px(8))
        if (selected || hover) {
            g2.color = if (selected) AerixTheme.accent else Color.WHITE
            g2.stroke = java.awt.BasicStroke(UiScale.px(if (selected) 2f else 1f))
            g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, r * 2f, r * 2f))
        }
        g2.dispose()
    }
}

private class JavaRow(private val title: String, private val detail: String) : JComponent() {
    init {
        preferredSize = Dimension(100, UiScale.px(44))
        maximumSize = Dimension(Int.MAX_VALUE, UiScale.px(44))
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        g2.color = withAlpha(Color.WHITE, 5)
        g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), UiScale.px(9) * 2f, UiScale.px(9) * 2f))
        net.kdt.aerix.desktop.ui.icons.Icons.draw(g2, "java", UiScale.px(8), (height - UiScale.px(16)) / 2, UiScale.px(16), AerixTheme.accent)
        g2.font = Fonts.bodyStrong(12.5f)
        g2.color = AerixColors.textPrimary
        g2.drawString(title, UiScale.px(32), UiScale.px(19))
        g2.font = Fonts.label(10.5f)
        g2.color = AerixColors.textMuted
        g2.drawString(detail.take(100), UiScale.px(32), UiScale.px(34))
        g2.dispose()
    }
}
