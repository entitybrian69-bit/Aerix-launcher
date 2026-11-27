package net.kdt.aerix.desktop.ui.screens

import net.kdt.aerix.desktop.app.AppEnv
import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Scheduler
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.game.GameLauncher
import net.kdt.aerix.desktop.core.instance.Instance
import net.kdt.aerix.desktop.core.instance.Instances
import net.kdt.aerix.desktop.ui.components.AerixButton
import net.kdt.aerix.desktop.ui.components.AerixField
import net.kdt.aerix.desktop.ui.components.ButtonStyle
import net.kdt.aerix.desktop.ui.components.ClearPanel
import net.kdt.aerix.desktop.ui.components.IconButton
import net.kdt.aerix.desktop.ui.components.PillTab
import net.kdt.aerix.desktop.ui.components.aerixScroll
import net.kdt.aerix.desktop.ui.components.heading
import net.kdt.aerix.desktop.ui.components.label
import net.kdt.aerix.desktop.ui.shell.Page
import net.kdt.aerix.desktop.ui.shell.Refreshable
import net.kdt.aerix.desktop.ui.shell.Shell
import net.kdt.aerix.desktop.ui.theme.AerixColors
import net.kdt.aerix.desktop.ui.theme.AerixTheme
import net.kdt.aerix.desktop.ui.theme.Fonts
import net.kdt.aerix.desktop.ui.theme.Paint
import net.kdt.aerix.desktop.ui.theme.Radii
import net.kdt.aerix.desktop.ui.theme.Spacing
import net.kdt.aerix.desktop.ui.theme.UiScale
import net.kdt.aerix.desktop.ui.theme.withAlpha
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseWheelEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

/**
 * The live console: launcher logs and game output in one stream, filterable and searchable.
 *
 * The Android LogScreen shows the same tabs (Launcher / Game) with a level filter and a copy button;
 * this keeps that and adds a search box and a live "command" bar for the running game, which writes
 * to the process' stdin the way the desktop launchers do.
 */
class ConsoleScreen(private val shell: Shell) : JPanel(BorderLayout()), Refreshable {
    private var mode = 0 // 0 = launcher, 1 = game
    private var level = -1
    private var filter = ""
    private var autoscroll = true
    private val output = ConsoleOutput()
    private val scroll = JScrollPane(output)
    private val tabs = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(6), 0))
    private var activeInstance: Instance? = null
    private val commandField = JTextField().apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(UiScale.px(8), UiScale.px(12), UiScale.px(8), UiScale.px(12))
        font = Fonts.mono(12f)
        foreground = AerixColors.textPrimary
        caretColor = AerixTheme.accent
        background = Color(0x0B1A25)
        toolTipText = "Send a command to the running game (without the leading /)"
        addActionListener { sendCommand() }
    }
    private val statusLine = ClearPanel(BorderLayout())

    init {
        isOpaque = false
        add(buildHeader(), BorderLayout.NORTH)

        val body = ClearPanel(BorderLayout())
        body.border = BorderFactory.createEmptyBorder(0, UiScale.px(Spacing.xl), UiScale.px(12), UiScale.px(Spacing.xl))
        output.isOpaque = true
        output.background = Color(0x061019)
        scroll.isOpaque = false
        scroll.border = BorderFactory.createLineBorder(withAlpha(AerixColors.borderSoft, 0x88), 1)
        scroll.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        scroll.verticalScrollBar.isOpaque = false
        scroll.viewport.isOpaque = false
        body.add(scroll, BorderLayout.CENTER)

        val commandBar = ClearPanel(BorderLayout())
        commandBar.border = BorderFactory.createEmptyBorder(UiScale.px(8), 0, 0, 0)
        commandBar.isOpaque = true
        commandBar.background = Color(0x0B1A25)
        commandField.background = Color(0x0B1A25)
        commandBar.add(commandField, BorderLayout.CENTER)
        commandBar.add(AerixButton("Send", ButtonStyle.SECONDARY, "send").apply {
            compact = true
            addActionListener { sendCommand() }
        }, BorderLayout.EAST)
        body.add(commandBar, BorderLayout.SOUTH)
        body.add(statusLine, BorderLayout.NORTH)
        add(body, BorderLayout.CENTER)

        Log.addListener { line -> SwingUtilities.invokeLater { appendLine(line) } }
        // fill from history
        Log.history().forEach { appendLine(it, repaintNow = false) }
        output.repaint()

        GameLauncher.onStarted { game ->
            Scheduler.ui {
                activeInstance = game.instance
                updateStatus()
            }
        }
        Scheduler.every(1500L) { SwingUtilities.invokeLater { updateStatus() } }
        updateStatus()
    }

    override fun refresh() = updateStatus()

    private fun buildHeader(): JPanel {
        val header = ClearPanel(BorderLayout())
        header.border = BorderFactory.createEmptyBorder(
            UiScale.px(Spacing.lg), UiScale.px(Spacing.xl), UiScale.px(Spacing.sm), UiScale.px(Spacing.xl)
        )
        val titles = ClearPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(heading("Console", 20f).apply { alignmentX = 0f })
            add(label("Everything the launcher and the game print, live", Fonts.label(12f), AerixColors.textMuted).apply { alignmentX = 0f })
        }
        header.add(titles, BorderLayout.WEST)

        tabs.add(PillTab("Launcher", true).apply { addActionListener { mode = 0; rebuildTabs(); rebuildAll() } })
        tabs.add(PillTab("Game", false).apply { addActionListener { mode = 1; rebuildTabs(); rebuildAll() } })

        val right = ClearPanel(FlowLayout(FlowLayout.RIGHT, UiScale.px(8), 0))
        right.add(tabs)
        val search = AerixField(placeholder = "Filter…", icon = "search").apply {
            preferredSize = java.awt.Dimension(UiScale.px(200), UiScale.px(34))
            addCaretListener { filter = text; rebuildAll() }
        }
        right.add(search)
        right.add(IconButton("delete", size = 34, tooltip = "Clear").apply {
            addActionListener { Log.clearHistory(); output.lines.clear(); output.repaint() }
        })
        right.add(IconButton("copy", size = 34, tooltip = "Copy visible log").apply {
            addActionListener {
                val text = output.lines.joinToString("\n") { it.first }
                java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
                shell.toast("Copied ${output.lines.size} lines")
            }
        })
        right.add(IconButton("folder", size = 34, tooltip = "Open the log folder").apply {
            addActionListener { openFolder(AppEnv.logsDir.resolve("games").parent.toFile()) }
        })
        right.add(IconButton("upload", size = 34, tooltip = "Attach a log to a report").apply {
            addActionListener {
                shell.toast("Logs are never uploaded automatically. Attach ${Log.logFile()?.fileName} to a GitHub issue.")
            }
        })
        header.add(right, BorderLayout.EAST)
        return header
    }

    private fun rebuildTabs() {
        val pills = tabs.components.filterIsInstance<PillTab>()
        pills.forEachIndexed { i, p -> p.checkedState = i == mode; p.repaint() }
    }

    private fun appendLine(line: Log.Line, repaintNow: Boolean = true) {
        if (!matches(line)) return
        output.append(line)
        if (autoscroll) {
            val bar = scroll.verticalScrollBar
            bar.value = bar.maximum
        }
        if (repaintNow) output.repaint()
    }

    private fun matches(line: Log.Line): Boolean {
        if (mode == 0 && line.game) return false
        if (mode == 1 && !line.game) return false
        if (filter.isNotBlank() && !line.rendered.contains(filter, true)) return false
        return true
    }

    private fun rebuildAll() {
        output.lines.clear()
        Log.history().filter { matches(it) }.forEach { output.append(it, false) }
        output.revalidate(); output.repaint()
    }

    private fun updateStatus() {
        statusLine.removeAll()
        statusLine.isOpaque = true
        statusLine.background = Color(0x08131C)
        statusLine.border = BorderFactory.createEmptyBorder(UiScale.px(6), UiScale.px(10), UiScale.px(6), UiScale.px(10))
        val running = GameLauncher.runningGames()
        val text = if (running.isEmpty()) {
            "No game running  ·  ${Instances.list().size} instance(s)  ·  log: ${Log.logFile()?.fileName ?: "—"}"
        } else {
            running.joinToString("   ") { "${it.instance.name} (pid ${it.process.pid()})" }
        }
        val labelText = label(text, Fonts.label(11f), if (running.isEmpty()) AerixColors.textMuted else AerixColors.success)
        statusLine.add(labelText, BorderLayout.WEST)
        val right = ClearPanel(FlowLayout(FlowLayout.RIGHT, UiScale.px(6), 0))
        right.add(label(if (autoscroll) "Auto-scroll on" else "Auto-scroll off", Fonts.label(10.5f), AerixColors.textMuted))
        right.add(IconButton(if (autoscroll) "check" else "close", size = 24, tooltip = "Toggle auto-scroll").apply {
            addActionListener { autoscroll = !autoscroll; updateStatus() }
        })
        statusLine.add(right, BorderLayout.EAST)
        statusLine.revalidate(); statusLine.repaint()
    }

    private fun sendCommand() {
        val text = commandField.text.trim()
        if (text.isEmpty()) return
        val game = GameLauncher.runningGames().firstOrNull()
        if (game == null) { shell.toast("Start the game first"); return }
        runCatching {
            game.process.outputStream.write((text + "\n").toByteArray())
            game.process.outputStream.flush()
            Log.i("console", "> $text")
            commandField.text = ""
        }.onFailure { shell.toast("Could not send: ${it.message}") }
    }
}

/** Renders the log lines itself: one component, thousands of lines, no layout cost. */
private class ConsoleOutput : JComponent() {
    val lines = ArrayList<Pair<String, Log.Level>>()
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss")

    init {
        font = Fonts.mono(11.5f)
        isFocusable = true
    }

    fun append(line: Log.Line, repaint: Boolean = true) {
        val stamp = time.format(Instant.ofEpochMilli(line.time).atZone(ZoneId.systemDefault()))
        lines.add("$stamp  ${line.level.name.first()}  ${line.message}" to line.level)
        while (lines.size > 6000) lines.removeAt(0)
        if (repaint) {
            revalidate()
            repaint()
        }
    }

    override fun getPreferredSize(): java.awt.Dimension {
        val fm = getFontMetrics(font)
        return java.awt.Dimension(fm.charWidth('M') * 110, maxOf(1, lines.size) * (fm.height + 1) + 12)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        g2.color = Color(0x061019)
        g2.fillRect(0, 0, width, height)
        g2.font = font
        val fm = g2.fontMetrics
        val lineHeight = fm.height + 1
        val start = 0
        var y = fm.ascent + 6
        for ((text, level) in lines) {
            if (y > height + lineHeight) break
            g2.color = when (level) {
                Log.Level.ERROR -> Color(0xFF, 0x8B, 0x9E)
                Log.Level.WARN -> Color(0xFF, 0xD1, 0x8A)
                Log.Level.DEBUG -> Color(0x7D, 0x90, 0x9D)
                else -> Color(0xCB, 0xD8, 0xE2)
            }
            g2.drawString(text.take(400), 8, y)
            y += lineHeight
        }
        // bar at the left indicating level colours above is enough; no gutter needed
        g2.dispose()
    }
}
