package net.kdt.aerix.desktop.ui.screens

import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Scheduler
import net.kdt.aerix.desktop.core.instance.Instance
import net.kdt.aerix.desktop.core.instance.Instances
import net.kdt.aerix.desktop.ui.components.AerixButton
import net.kdt.aerix.desktop.ui.components.AerixField
import net.kdt.aerix.desktop.ui.components.ButtonStyle
import net.kdt.aerix.desktop.ui.components.ClearPanel
import net.kdt.aerix.desktop.ui.components.EmptyState
import net.kdt.aerix.desktop.ui.components.IconButton
import net.kdt.aerix.desktop.ui.components.aerixScroll
import net.kdt.aerix.desktop.ui.components.heading
import net.kdt.aerix.desktop.ui.components.label
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
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * A file manager for the selected instance, because the Android app's FileManagerScreen exists for
 * the same reason: players need to get at `mods/`, `saves/` and `config/` without leaving the
 * launcher. Folders open in place, files show their size and can be deleted or revealed in
 * Explorer.
 */
class FilesScreen(private val shell: Shell) : JPanel(BorderLayout()), Refreshable {
    private var instance: Instance? = null
    private var current: Path? = null
    private val listHost = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val pathField = AerixField(placeholder = "Path", icon = "folder").apply {
        preferredSize = Dimension(UiScale.px(380), UiScale.px(34))
        isEditable = false
    }
    private val crumb = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(4), 0))
    private val instanceRow = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(6), 0))

    init {
        isOpaque = false
        add(buildHeader(), BorderLayout.NORTH)
        val body = ClearPanel(BorderLayout())
        body.border = BorderFactory.createEmptyBorder(0, UiScale.px(Spacing.xl), UiScale.px(Spacing.xl), UiScale.px(Spacing.xl))
        val scroll = aerixScroll(listHost)
        scroll.border = BorderFactory.createEmptyBorder()
        body.add(scroll, BorderLayout.CENTER)
        add(body, BorderLayout.CENTER)
        instance = Instances.list().firstOrNull()
        current = instance?.gameDir
        rebuildInstanceRow()
        refresh()
    }

    override fun refresh() {
        rebuildInstanceRow()
        reload()
    }

    private fun buildHeader(): JPanel {
        val header = ClearPanel(BorderLayout())
        header.border = BorderFactory.createEmptyBorder(
            UiScale.px(Spacing.lg), UiScale.px(Spacing.xl), UiScale.px(Spacing.md), UiScale.px(Spacing.xl)
        )
        val titles = ClearPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(heading("Files", 20f).apply { alignmentX = 0f })
            add(label("Browse, import and clean up an instance's folder", Fonts.label(12f), AerixColors.textMuted).apply { alignmentX = 0f })
        }
        header.add(titles, BorderLayout.NORTH)

        val bar = ClearPanel(BorderLayout())
        bar.border = BorderFactory.createEmptyBorder(UiScale.px(10), 0, 0, 0)
        val left = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(6), 0))
        left.add(instanceRow)
        bar.add(left, BorderLayout.WEST)
        val right = ClearPanel(FlowLayout(FlowLayout.RIGHT, UiScale.px(6), 0))
        right.add(pathField)
        right.add(IconButton("arrow_back", size = 34, tooltip = "Up one folder").apply {
            addActionListener { current?.parent?.let { p -> if (instance != null && p.startsWith(instance!!.gameDir)) { current = p; reload() } } }
        })
        right.add(IconButton("folder", size = 34, tooltip = "Reveal in Explorer").apply {
            addActionListener { current?.let { openFolder(it.toFile()) } }
        })
        right.add(IconButton("refresh", size = 34, tooltip = "Refresh").apply { addActionListener { reload() } })
        right.add(AerixButton("Import…", ButtonStyle.SECONDARY, "upload").apply {
            compact = true
            addActionListener { importFiles() }
        })
        bar.add(right, BorderLayout.EAST)
        header.add(bar, BorderLayout.SOUTH)
        return header
    }

    private fun rebuildInstanceRow() {
        instanceRow.removeAll()
        Instances.list().forEach { inst ->
            val pill = net.kdt.aerix.desktop.ui.components.PillTab(inst.name, inst === instance).apply {
                addActionListener {
                    instance = inst
                    current = inst.gameDir
                    rebuildInstanceRow()
                    reload()
                }
            }
            instanceRow.add(pill)
        }
        instanceRow.revalidate(); instanceRow.repaint()
    }

    private fun reload() {
        listHost.removeAll()
        listHost.border = BorderFactory.createEmptyBorder(0, 0, 0, UiScale.px(8))
        val inst = instance
        val dir = current
        if (inst == null || dir == null) {
            listHost.add(EmptyState("folder", "No instance", "Create an instance to browse its files."))
            listHost.revalidate(); listHost.repaint()
            return
        }
        pathField.text = dir.toString()
        Scheduler.io("files") {
            val entries: List<Path> = runCatching {
                if (!Files.isDirectory(dir)) emptyList<Path>()
                else Files.list(dir).use { s ->
                    s.toList().filter { !it.fileName.toString().startsWith(".") }
                        .sortedWith(compareByDescending<Path> { Files.isDirectory(it) }.thenBy { it.fileName.toString().lowercase() })
                }
            }.getOrDefault(emptyList())
            Scheduler.ui {
                listHost.removeAll()
                if (entries.isEmpty()) {
                    listHost.add(EmptyState("folder", "Empty folder", "Drop files here or use Import."))
                } else {
                    entries.forEach { p -> listHost.add(FileRow(p) { open(p) }) }
                }
                val bar = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(6), UiScale.px(10)))
                bar.add(label("${entries.size} item(s)  ·  ${dir.fileName}", Fonts.label(11f), AerixColors.textMuted))
                bar.add(AerixButton("Delete this folder's contents…", ButtonStyle.GHOST).apply {
                    compact = true
                    addActionListener { deleteContents(dir) }
                })
                listHost.add(bar)
                listHost.revalidate(); listHost.repaint()
            }
        }
    }

    private fun open(p: Path) {
        if (Files.isDirectory(p)) {
            current = p
            reload()
        } else {
            openFolder(p.toFile())
        }
    }

    private fun importFiles() {
        val inst = instance ?: return
        val target = current ?: inst.gameDir
        val chooser = JFileChooser().apply { isMultiSelectionEnabled = true }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        Scheduler.io("files-import") {
            chooser.selectedFiles.forEach { f ->
                runCatching { Files.copy(f.toPath(), target.resolve(f.name), java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
                    .onFailure { Log.w("files", "import ${f.name}: ${it.message}") }
            }
            Scheduler.ui { reload() }
        }
    }

    private fun deleteContents(dir: Path) {
        val ok = JOptionPane.showConfirmDialog(this,
            "Delete everything inside ${dir.fileName}?\nThis cannot be undone.",
            "Delete contents", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
        if (ok != JOptionPane.OK_OPTION) return
        Scheduler.io("files-delete") {
            runCatching { Files.list(dir).use { it.toList() }.forEach { p -> Instances.deleteTree(p) } }
            Scheduler.ui { reload() }
        }
    }
}

private class FileRow(private val path: Path, private val onClick: () -> Unit) : JComponent() {
    private var hover = false
    private val isDir = Files.isDirectory(path)
    private val name = path.fileName.toString()
    private val size = runCatching { if (isDir) 0L else Files.size(path) }.getOrDefault(0L)
    private val modified = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)

    init {
        preferredSize = Dimension(100, UiScale.px(44))
        maximumSize = Dimension(Int.MAX_VALUE, UiScale.px(44))
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: MouseEvent) { hover = false; repaint() }
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val menu = javax.swing.JPopupMenu()
                    menu.add(javax.swing.JMenuItem("Open").apply { addActionListener { onClick() } })
                    menu.add(javax.swing.JMenuItem("Reveal in Explorer").apply { addActionListener { openFolder(if (isDir) path.toFile() else path.parent.toFile()) } })
                    menu.add(javax.swing.JMenuItem("Delete").apply {
                        addActionListener {
                            if (JOptionPane.showConfirmDialog(null, "Delete “$name”?", "Delete", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                                Instances.deleteTree(path)

                            }
                        }
                    })
                    menu.show(this@FileRow, e.x, e.y)
                } else onClick()
            }
        })
        toolTipText = path.toString()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        if (hover) {
            g2.color = withAlpha(Color.WHITE, 12)
            g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), UiScale.px(9) * 2f, UiScale.px(9) * 2f))
        }
        val icon = when {
            isDir -> "folder"
            name.endsWith(".jar") -> "archive"
            name.endsWith(".zip") || name.endsWith(".mrpack") -> "zip"
            name.endsWith(".json") || name.endsWith(".toml") || name.endsWith(".cfg") -> "description"
            name.endsWith(".png") || name.endsWith(".jpg") -> "image"
            name.endsWith(".log") || name.endsWith(".txt") -> "text"
            else -> "description"
        }
        net.kdt.aerix.desktop.ui.icons.Icons.draw(g2, icon, UiScale.px(6), (height - UiScale.px(18)) / 2, UiScale.px(18),
            if (isDir) AerixTheme.accent else AerixColors.textSecondary)
        g2.font = Fonts.body(12.5f)
        g2.color = AerixColors.textPrimary
        g2.drawString(name, UiScale.px(32), height / 2 + g2.fontMetrics.ascent / 2 - 2)
        g2.font = Fonts.label(10.5f)
        g2.color = AerixColors.textMuted
        val info = buildString {
            if (!isDir) append(humanSize(size))
            if (modified > 0) {
                if (isNotEmpty()) append("  ·  ")
                append(java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm")
                    .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(modified)))
            }
        }
        val w = g2.fontMetrics.stringWidth(info)
        g2.drawString(info, width - w - UiScale.px(12), height / 2 + g2.fontMetrics.ascent / 2 - 2)
        g2.dispose()
    }
}
