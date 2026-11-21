package net.kdt.aerix.desktop.ui.screens

import net.kdt.aerix.desktop.app.Scheduler
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.instance.Instance
import net.kdt.aerix.desktop.core.instance.InstanceLinks
import net.kdt.aerix.desktop.core.instance.Instances
import net.kdt.aerix.desktop.core.instance.Loader
import net.kdt.aerix.desktop.ui.components.AerixButton
import net.kdt.aerix.desktop.ui.components.AerixField
import net.kdt.aerix.desktop.ui.components.AerixSlider
import net.kdt.aerix.desktop.ui.components.AerixSwitch
import net.kdt.aerix.desktop.ui.components.ButtonStyle
import net.kdt.aerix.desktop.ui.components.ClearPanel
import net.kdt.aerix.desktop.ui.components.label
import net.kdt.aerix.desktop.ui.components.vspace
import net.kdt.aerix.desktop.ui.shell.Page
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
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.geom.RoundRectangle2D
import java.nio.file.Files
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.filechooser.FileNameExtensionFilter

/** The right-click menu for an instance, matching the Android overflow menu's actions. */
object InstanceMenu {

    fun build(shell: Shell, instance: Instance): javax.swing.JPopupMenu {
        val menu = DarkMenu()
        fun item(text: String, icon: String? = null, danger: Boolean = false, action: () -> Unit) {
            menu.add(DarkMenuItem(text, icon, danger) {
                menu.isVisible = false
                action()
            })
        }
        item("Play", "play") { PlayAction.launch(shell, instance) }
        item(if (GameRunning(instance)) "Stop" else "Open", if (GameRunning(instance)) "pause" else "library") {
            if (GameRunning(instance)) net.kdt.aerix.desktop.core.game.GameLauncher.runningGames()
                .filter { it.instance.id == instance.id }.forEach(net.kdt.aerix.desktop.core.game.GameLauncher::stop)
            else shell.openInstance(instance)
        }
        menu.addSeparator()
        item("Manage mods & content", "archive") { shell.openInstance(instance) }
        item(if (instance.pinned) "Unpin" else "Pin to top", "favorite") {
            instance.pinned = !instance.pinned; instance.save(); Instances.reload()
        }
        item("Rename", "edit") { rename(shell, instance) }
        item("Duplicate", "copy") {
            Scheduler.io("duplicate") {
                val copy = Instances.duplicate(instance)
                Scheduler.ui { shell.toast("Duplicated as ${copy.name}") }
            }
        }
        item("Change icon", "image") { chooseIcon(shell, instance) }
        item("Export modpack", "share") { shell.navigate(Page.MODPACKS); shell.toast("Pick a modpack format to export into") }
        menu.addSeparator()
        item("Open instance folder", "folder") { openFolder(instance.root.toFile()) }
        item("Open game folder", "folder") { Files.createDirectories(instance.gameDir); openFolder(instance.gameDir.toFile()) }
        item("Reinstall / repair files", "download") { PlayAction.reinstall(shell, instance) }
        menu.addSeparator()
        item("Delete…", "delete", danger = true) { confirmTrash(shell, instance) }
        return menu
    }

    private fun GameRunning(instance: Instance) =
        net.kdt.aerix.desktop.core.game.GameLauncher.isRunning(instance)

    fun confirmTrash(shell: Shell, instance: Instance) {
        val ok = JOptionPane.showConfirmDialog(
            shell,
            "Move “${instance.name}” to the Trash?\n\nThe instance folder, including its worlds, is kept. You can restore it from the Trash in Settings.",
            "Delete instance",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE
        )
        if (ok != JOptionPane.OK_OPTION) return
        InstanceLinks.detach(instance)
        if (Instances.trash(instance)) shell.toast("${instance.name} moved to Trash") else shell.toast("Could not move the instance")
        shell.refreshPages()
    }

    fun rename(shell: Shell, instance: Instance) {
        val newName = JOptionPane.showInputDialog(shell, "New name for “${instance.name}”", instance.name)
        if (newName.isNullOrBlank()) return
        val clean = Instances.sanitiseName(newName)
        if (clean != instance.name) {
            val target = Instances.rootDir().resolve(clean)
            if (Files.exists(target)) { shell.toast("An instance called “$clean” already exists"); return }
            runCatching {
                Files.move(instance.root, target)
                instance.root = target
                instance.id = target.fileName.toString()
            }.onFailure {
                shell.toast("Could not rename: ${it.message}")
                return
            }
        }
        instance.name = newName.trim()
        instance.save()
        Instances.reload()
        shell.toast("Renamed to ${instance.name}")
    }

    fun chooseIcon(shell: Shell, instance: Instance) {
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose instance icon"
            fileFilter = FileNameExtensionFilter("Images (PNG, JPG)", "png", "jpg", "jpeg")
        }
        if (chooser.showOpenDialog(shell) != JFileChooser.APPROVE_OPTION) return
        runCatching {
            val target = instance.root.resolve("icon.png")
            Files.copy(chooser.selectedFile.toPath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            instance.iconFile = "icon.png"
            instance.save()
            Instances.reload()
            shell.toast("Icon updated")
        }.onFailure { shell.toast("Could not use that image: ${it.message}") }
    }
}

/** Popup menu painted in the launcher's palette rather than the platform's. */
class DarkMenu : javax.swing.JPopupMenu() {
    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(UiScale.px(6), UiScale.px(6), UiScale.px(6), UiScale.px(6))
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        Paint.glass(g2, 0, 0, width, height, UiScale.px(Radii.cardSmall),
            fill = withAlpha(AerixColors.canvas, 246), shadow = false, highlight = false)
        g2.dispose()
    }
    override fun paintBorder(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        g2.color = withAlpha(AerixColors.border, 0x66)
        g2.stroke = java.awt.BasicStroke(UiScale.px(1f))
        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f,
            UiScale.px(Radii.cardSmall) * 2f, UiScale.px(Radii.cardSmall) * 2f))
        g2.dispose()
    }
}

class DarkMenuItem(text: String, private val iconName: String?, private val danger: Boolean, onClick: () -> Unit) : javax.swing.JMenuItem(text) {
    private var hover = false
    init {
        isOpaque = false
        font = Fonts.body(12.5f)
        foreground = if (danger) AerixColors.danger else AerixColors.textPrimary
        border = BorderFactory.createEmptyBorder(UiScale.px(7), UiScale.px(10), UiScale.px(7), UiScale.px(18))
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseEntered(e: java.awt.event.MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: java.awt.event.MouseEvent) { hover = false; repaint() }
        })
        addActionListener { onClick() }
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        if (hover) {
            g2.color = if (danger) withAlpha(AerixColors.danger, 34) else withAlpha(AerixColors.textPrimary, 20)
            g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), UiScale.px(8) * 2f, UiScale.px(8) * 2f))
        }
        if (iconName != null) {
            net.kdt.aerix.desktop.ui.icons.Icons.draw(g2, iconName, UiScale.px(6), (height - UiScale.px(14)) / 2, UiScale.px(14),
                if (danger) AerixColors.danger else AerixColors.textSecondary)
        }
        g2.font = font
        g2.color = if (danger) AerixColors.danger else AerixColors.textPrimary
        val fm = g2.fontMetrics
        g2.drawString(text, UiScale.px(28), (height - fm.height) / 2 + fm.ascent)
        g2.dispose()
    }
    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(font)
        return Dimension(fm.stringWidth(text) + UiScale.px(56), UiScale.px(30))
    }
}

/** The instance editor dialog (rename, icon, colour, group, Java, memory, arguments). */
class InstanceEditorDialog(owner: Shell, private val instance: Instance, private val onSaved: () -> Unit) :
    javax.swing.JDialog(javax.swing.SwingUtilities.getWindowAncestor(owner), "Edit ${instance.name}", ModalityType.APPLICATION_MODAL) {

    private val nameField = AerixField(placeholder = "Instance name", text0 = instance.name).apply {
        preferredSize = Dimension(UiScale.px(320), UiScale.px(42))
    }
    private val groupField = AerixField(placeholder = "Group (optional)", text0 = instance.group, icon = "group").apply {
        preferredSize = Dimension(UiScale.px(320), UiScale.px(42))
    }
    private val javaField = AerixField(placeholder = "Auto", text0 = instance.javaPath).apply {
        preferredSize = Dimension(UiScale.px(240), UiScale.px(42))
    }
    private val argsField = AerixField(placeholder = "Extra JVM arguments", text0 = instance.jvmArgs).apply {
        preferredSize = Dimension(UiScale.px(320), UiScale.px(42))
    }
    private val memory = AerixSlider(
        min = 512, max = maxOf(2048, net.kdt.aerix.desktop.app.AppEnv.totalMemoryMb - 1024),
        value = if (instance.memoryMb > 0) instance.memoryMb else instance.effectiveMemoryMb(), step = 256, suffix = " MB"
    )
    private val sharedSwitch = AerixSwitch(instance.sharedData)
    private var chosenColor = instance.color

    init {
        isUndecorated = true
        rootPane.border = BorderFactory.createEmptyBorder()
        val panel = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                Paint.quality(g2)
                Paint.glass(g2, 0, 0, width, height, UiScale.px(Radii.dialog),
                    fill = withAlpha(AerixColors.canvas, 250), shadow = false, highlight = false)
                g2.dispose()
            }
        }
        panel.isOpaque = false
        panel.border = BorderFactory.createEmptyBorder(UiScale.px(22), UiScale.px(24), UiScale.px(18), UiScale.px(24))

        val head = ClearPanel(BorderLayout())
        head.add(label("Edit instance", Fonts.headline(19f), AerixColors.textPrimary), BorderLayout.WEST)
        head.add(label(instance.subtitle(), Fonts.label(11.5f), AerixColors.textMuted), BorderLayout.EAST)
        panel.add(head, BorderLayout.NORTH)

        val body = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        body.border = BorderFactory.createEmptyBorder(UiScale.px(16), 0, 0, 0)
        fun field(rowLabel: String, comp: JComponent) {
            val row = ClearPanel(BorderLayout())
            row.border = BorderFactory.createEmptyBorder(0, 0, UiScale.px(10), 0)
            row.add(label(rowLabel, Fonts.labelStrong(11f), AerixColors.textSecondary), BorderLayout.WEST)
            val wrap = ClearPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
            wrap.add(comp)
            row.add(wrap, BorderLayout.EAST)
            body.add(row)
        }
        field("Name", nameField)
        field("Group", groupField)
        field("Memory", memory)
        field("Java path", javaField)
        field("JVM arguments", argsField)

        val sharedRow = ClearPanel(BorderLayout())
        sharedRow.border = BorderFactory.createEmptyBorder(0, 0, UiScale.px(10), 0)
        val sharedText = ClearPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(label("Share the game install", Fonts.labelStrong(11f), AerixColors.textSecondary).apply { alignmentX = 0f })
            add(label("Link versions, libraries and assets from the shared folder. Worlds and mods stay here.",
                Fonts.label(10.5f), AerixColors.textMuted).apply { alignmentX = 0f })
        }
        sharedRow.add(sharedText, BorderLayout.CENTER)
        sharedRow.add(sharedSwitch, BorderLayout.EAST)
        body.add(sharedRow)

        val colorRow = ClearPanel(BorderLayout())
        colorRow.add(label("Colour", Fonts.labelStrong(11f), AerixColors.textSecondary), BorderLayout.WEST)
        val swatches = ClearPanel(FlowLayout(FlowLayout.RIGHT, UiScale.px(6), 0))
        Instance.DEFAULT_COLORS.forEach { c ->
            swatches.add(Swatch(c, c == chosenColor) { chosenColor = it; swatches.repaint() })
        }
        colorRow.add(swatches, BorderLayout.EAST)
        body.add(colorRow)

        // icon action
        val iconRow = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(8), 0))
        iconRow.border = BorderFactory.createEmptyBorder(UiScale.px(6), 0, 0, 0)
        iconRow.add(AerixButton("Choose icon…", ButtonStyle.GHOST, "image").apply {
            compact = true
            addActionListener {
                val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("Images", "png", "jpg", "jpeg") }
                if (chooser.showOpenDialog(this@InstanceEditorDialog) == JFileChooser.APPROVE_OPTION) {
                    runCatching {
                        Files.copy(chooser.selectedFile.toPath(), instance.root.resolve("icon.png"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        instance.iconFile = "icon.png"
                        JOptionPane.showMessageDialog(this@InstanceEditorDialog, "Icon saved. It shows after saving.")
                    }.onFailure { JOptionPane.showMessageDialog(this@InstanceEditorDialog, it.message) }
                }
            }
        })
        iconRow.add(AerixButton("Clear icon", ButtonStyle.GHOST).apply {
            compact = true
            addActionListener { instance.iconFile = null; instance.save(); onSaved() }
        })
        body.add(iconRow)
        panel.add(body, BorderLayout.CENTER)

        val footer = ClearPanel(FlowLayout(FlowLayout.RIGHT, UiScale.px(8), 0))
        footer.add(AerixButton("Cancel", ButtonStyle.GHOST).apply { addActionListener { dispose() } })
        footer.add(AerixButton("Save changes", ButtonStyle.PRIMARY, "check").apply { addActionListener { save() } })
        panel.add(footer, BorderLayout.SOUTH)

        contentPane = panel
        defaultCloseOperation = DISPOSE_ON_CLOSE
        pack()
        setSize(Dimension(UiScale.px(560), height.coerceAtLeast(UiScale.px(600))))
        setLocationRelativeTo(javax.swing.SwingUtilities.getWindowAncestor(owner))
    }

    private fun save() {
        val newName = nameField.text.trim()
        if (newName.isNotEmpty() && newName != instance.name) {
            val clean = Instances.sanitiseName(newName)
            val target = Instances.rootDir().resolve(clean)
            if (!Files.exists(target)) {
                runCatching {
                    Files.move(instance.root, target)
                    instance.root = target
                    instance.id = target.fileName.toString()
                }.onFailure {
                    JOptionPane.showMessageDialog(this, "Could not rename the folder: ${it.message}")
                    return
                }
            } else {
                JOptionPane.showMessageDialog(this, "An instance called “$clean” already exists.")
                return
            }
            instance.name = newName
        }
        instance.group = groupField.text.trim()
        instance.memoryMb = memory.value
        instance.javaPath = javaField.text.trim()
        instance.jvmArgs = argsField.text.trim()
        instance.color = chosenColor
        val wasShared = instance.sharedData
        instance.sharedData = sharedSwitch.on
        instance.save()
        if (instance.sharedData && !wasShared) InstanceLinks.ensure(instance)
        Instances.reload()
        onSaved()
        dispose()
    }
}

private class Swatch(private val color: Int, private val selectedNow: Boolean, private val onPick: (Int) -> Unit) : JComponent() {
    init {
        preferredSize = Dimension(UiScale.px(24), UiScale.px(24))
        java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR).let { cursor = it }
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) { onPick(color) }
        })
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        g2.color = Color(color)
        g2.fillOval(2, 2, width - 4, height - 4)
        if (selectedNow) {
            g2.color = Color.WHITE
            g2.stroke = java.awt.BasicStroke(UiScale.px(1.6f))
            g2.drawOval(1, 1, width - 2, height - 2)
        }
        g2.dispose()
    }
}
