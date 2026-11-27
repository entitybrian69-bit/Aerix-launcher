package net.kdt.aerix.desktop.ui.screens

import net.kdt.aerix.desktop.app.AppEnv
import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Scheduler
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.game.GameLauncher
import net.kdt.aerix.desktop.core.game.JavaRuntimes
import net.kdt.aerix.desktop.core.instance.Instance
import net.kdt.aerix.desktop.core.instance.Instances
import net.kdt.aerix.desktop.core.instance.Loader
import net.kdt.aerix.desktop.ui.components.AerixButton
import net.kdt.aerix.desktop.ui.components.AerixField
import net.kdt.aerix.desktop.ui.components.AerixSlider
import net.kdt.aerix.desktop.ui.components.AerixSwitch
import net.kdt.aerix.desktop.ui.components.ButtonStyle
import net.kdt.aerix.desktop.ui.components.ClearPanel
import net.kdt.aerix.desktop.ui.components.EmptyState
import net.kdt.aerix.desktop.ui.components.IconButton
import net.kdt.aerix.desktop.ui.components.PillTab
import net.kdt.aerix.desktop.ui.components.SectionCard
import net.kdt.aerix.desktop.ui.components.SettingRow
import net.kdt.aerix.desktop.ui.components.aerixScroll
import net.kdt.aerix.desktop.ui.components.heading
import net.kdt.aerix.desktop.ui.components.label
import net.kdt.aerix.desktop.ui.components.vspace
import net.kdt.aerix.desktop.ui.icons.Icons
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
import net.kdt.aerix.desktop.ui.theme.mix
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
import java.nio.file.Files
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

/**
 * Instance management, laid out like the Android page for a wide window: the header
 * (title + count + folder + install), the search/sort row, then a two-column body where the
 * instance cards run down the left and the selected instance's detail pane fills the rest.
 *
 * The detail pane keeps the Android tab set — Overview, Content, Worlds, Settings — and the same
 * footer action: a full-width PLAY button that never scrolls away.
 */
class LibraryScreen(private val shell: Shell, initial: Instance?) : JPanel(BorderLayout()), Refreshable {
    private var search = ""
    private var sort = 0
    private var selected: Instance? = initial
    private var tab = 1 // 0 overview, 1 content, 2 worlds, 3 settings
    private var contentType = "mods"

    private val listHost = ClearPanel().apply { layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS) }
    private val detailHost = ClearPanel(BorderLayout())
    private val countPill = CountPill()
    private val searchField = AerixField(placeholder = "Search instances…", icon = "search").apply {
        preferredSize = Dimension(UiScale.px(260), UiScale.px(38))
        addCaretListener { }
    }
    private val sortButton = IconButton("sort", size = 36, tooltip = "Sort instances")
    private val installButton = AerixButton("Install", ButtonStyle.PRIMARY, "add").apply { compact = true; horizontalPadding = 16 }
    private val folderButton = IconButton("folder", size = 36, tooltip = "Open the instances folder")

    init {
        isOpaque = false
        add(buildChrome(), BorderLayout.NORTH)
        add(buildBody(), BorderLayout.CENTER)
        searchField.addActionListener { search = searchField.text; rebuild() }
        searchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { search = searchField.text; rebuild() }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { search = searchField.text; rebuild() }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {}
        })
        sortButton.addActionListener { showSortMenu() }
        installButton.addActionListener { shell.navigate(Page.CREATE) }
        folderButton.addActionListener { openFolder(Instances.rootDir().toFile()) }
        Instances.onChange { Scheduler.ui { rebuild() } }
        if (selected == null) selected = Instances.find(Settings.string("currentInstance", "")) ?: Instances.list().firstOrNull()
        rebuild()
    }

    fun select(instance: Instance?) {
        selected = instance ?: selected
        tab = 1
        rebuild()
    }

    override fun refresh() = rebuild()

    // ---- chrome ------------------------------------------------------------------------------

    private fun buildChrome(): JPanel {
        val chrome = ClearPanel(BorderLayout())
        chrome.border = BorderFactory.createEmptyBorder(
            UiScale.px(Spacing.lg), UiScale.px(Spacing.xl), UiScale.px(Spacing.sm), UiScale.px(Spacing.xl)
        )
        val titles = ClearPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(heading("Instance Management", 20f).apply { alignmentX = 0f })
            add(label("Select an instance to manage", Fonts.label(12f), AerixColors.textMuted).apply { alignmentX = 0f })
        }
        chrome.add(titles, BorderLayout.WEST)

        val controls = ClearPanel(FlowLayout(FlowLayout.RIGHT, UiScale.px(8), 0))
        controls.add(countPill)
        controls.add(searchField)
        controls.add(sortButton)
        controls.add(folderButton)
        controls.add(installButton)
        chrome.add(controls, BorderLayout.EAST)
        return chrome
    }

    private fun buildBody(): JPanel {
        val body = ClearPanel(BorderLayout())
        body.border = BorderFactory.createEmptyBorder(0, UiScale.px(Spacing.xl), UiScale.px(Spacing.xl), UiScale.px(Spacing.xl))
        val listScroll = aerixScroll(listHost)
        listScroll.preferredSize = Dimension(UiScale.px(330), 100)
        listScroll.minimumSize = Dimension(UiScale.px(280), 100)
        listScroll.border = BorderFactory.createEmptyBorder()
        body.add(listScroll, BorderLayout.WEST)
        detailHost.isOpaque = false
        detailHost.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        body.add(detailHost, BorderLayout.CENTER)
        return body
    }

    private fun showSortMenu() {
        val menu = javax.swing.JPopupMenu()
        listOf("Pinned & active", "Name (A–Z)", "Game version", "Last played").forEachIndexed { i, text ->
            val item = javax.swing.JMenuItem(if (sort == i) "$text  ✓" else text)
            item.addActionListener { sort = i; rebuild() }
            menu.add(item)
        }
        menu.show(sortButton, 0, sortButton.height)
    }

    // ---- list --------------------------------------------------------------------------------

    private fun rebuild() {
        val all = Instances.list()
        val filtered = all.filter { inst ->
            search.isBlank() || inst.name.contains(search, true) || inst.subtitle().contains(search, true) ||
                    inst.group.contains(search, true)
        }
        val sorted = when (sort) {
            0 -> filtered.sortedWith(compareByDescending<Instance> { it.pinned }.thenByDescending { it.lastPlayed })
            1 -> filtered.sortedBy { it.name.lowercase() }
            2 -> filtered.sortedByDescending { it.mcVersion }
            else -> filtered.sortedByDescending { it.lastPlayed }
        }
        countPill.count = sorted.size
        listHost.removeAll()
        listHost.border = BorderFactory.createEmptyBorder(0, 0, 0, UiScale.px(6))
        if (sorted.isEmpty()) {
            listHost.add(Box.createVerticalStrut(UiScale.px(20)))
            val empty = label(
                if (all.isEmpty()) "No instances yet — press Install." else "Nothing matches “$search”.",
                Fonts.body(12.5f), AerixColors.textMuted
            )
            empty.alignmentX = 0.5f
            listHost.add(empty)
        } else {
            sorted.forEach { inst ->
                listHost.add(LibraryRow(shell, inst, inst === selected) { select(inst) })
                listHost.add(Box.createVerticalStrut(UiScale.px(8)))
            }
        }
        if (selected != null && Instances.list().none { it === selected }) selected = Instances.list().firstOrNull()
        rebuildDetail()
        listHost.revalidate(); listHost.repaint()
    }

    private fun rebuildDetail() {
        detailHost.removeAll()
        val inst = selected
        if (inst == null) {
            detailHost.add(EmptyState("library", "No instance yet", "Create one from the Create page or install a version."), BorderLayout.CENTER)
            detailHost.revalidate(); detailHost.repaint()
            return
        }
        val card = ClearPanel(BorderLayout())
        card.border = BorderFactory.createEmptyBorder(0, UiScale.px(18), 0, 0)

        val head = DetailHeader(inst, shell, this)
        card.add(head, BorderLayout.NORTH)

        val tabs = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(6), 0))
        tabs.border = BorderFactory.createEmptyBorder(UiScale.px(14), 0, UiScale.px(10), 0)
        val names = listOf("Overview", "Content", "Worlds", "Settings")
        names.forEachIndexed { i, name ->
            val pill = PillTab(name, i == tab).apply {
                addActionListener { tab = i; rebuildDetail() }
            }
            tabs.add(pill)
        }
        card.add(tabs, BorderLayout.CENTER)

        val bodyHost = ClearPanel(BorderLayout())
        val scroll = aerixScroll(when (tab) {
            0 -> overviewTab(inst)
            1 -> contentTab(inst)
            2 -> worldsTab(inst)
            else -> settingsTab(inst)
        })
        scroll.border = BorderFactory.createEmptyBorder()
        scroll.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        bodyHost.add(scroll, BorderLayout.CENTER)
        card.add(bodyHost, BorderLayout.SOUTH)

        // restructure: header N, tabs C? — put tabs north of body instead
        card.removeAll()
        val north = ClearPanel(BorderLayout())
        north.add(head, BorderLayout.NORTH)
        north.add(tabs, BorderLayout.SOUTH)
        card.add(north, BorderLayout.NORTH)
        card.add(bodyHost, BorderLayout.CENTER)

        // footer PLAY
        val footer = ClearPanel(BorderLayout())
        footer.border = BorderFactory.createEmptyBorder(UiScale.px(12), 0, 0, 0)
        val play = AerixButton(if (GameLauncher.isRunning(inst)) "STOP" else "PLAY", ButtonStyle.PRIMARY, "play")
        play.preferredSize = Dimension(UiScale.px(400), UiScale.px(52))
        play.addActionListener {
            if (GameLauncher.isRunning(inst)) {
                GameLauncher.runningGames().filter { it.instance.id == inst.id }.forEach(GameLauncher::stop)
                shell.toast("Stopping ${inst.name}…")
                Scheduler.ui { refresh() }
            } else PlayAction.launch(shell, inst)
        }
        footer.add(play, BorderLayout.CENTER)
        card.add(footer, BorderLayout.SOUTH)

        detailHost.add(card, BorderLayout.CENTER)
        detailHost.revalidate(); detailHost.repaint()
    }

    // ---- tabs --------------------------------------------------------------------------------

    private fun overviewTab(inst: Instance): JPanel {
        val col = ClearPanel().apply { layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS) }
        col.border = BorderFactory.createEmptyBorder(0, 0, 0, UiScale.px(10))
        val info = SectionCard("Instance details", "Everything this instance launches with")
        val grid = ClearPanel(GridLayout(0, 2, UiScale.px(10), UiScale.px(6)))
        fun kv(k: String, v: String) {
            val cell = ClearPanel().apply { layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS) }
            cell.add(label(k.uppercase(), Fonts.labelStrong(9.5f), AerixColors.textMuted).apply { alignmentX = 0f })
            cell.add(label(v.ifBlank { "—" }, Fonts.bodyStrong(13f)).apply { alignmentX = 0f })
            grid.add(cell)
        }
        kv("Name", inst.name)
        kv("Minecraft", inst.mcVersion.ifBlank { Instance.guessMcVersion(inst.versionId).ifBlank { "—" } })
        kv("Loader", Loader.displayName(inst.loader) + (inst.loaderVersion?.let { " $it" } ?: ""))
        kv("Version id", inst.versionId.ifBlank { "—" })
        kv("Memory", "${inst.effectiveMemoryMb()} MB")
        kv("Group", inst.group.ifBlank { "Ungrouped" })
        kv("Play time", formatPlayTime(inst.playSeconds))
        kv("Launches", inst.launchCount.toString())
        kv("Last played", if (inst.lastPlayed == 0L) "Never" else java.time.Instant.ofEpochMilli(inst.lastPlayed).let { java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(java.time.ZoneId.systemDefault()).format(it) })
        kv("Game folder", inst.gameDir.toString())
        info.body.add(grid)
        info.body.add(vspace(12))

        val actions = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(8), 0))
        actions.add(smallButton("Edit", "edit") { InstanceEditorDialog(shell, inst) { rebuild() }.isVisible = true })
        actions.add(smallButton("Duplicate", "copy") {
            val copy = Instances.duplicate(inst)
            shell.toast("Duplicated as ${copy.name}")
            select(copy)
        })
        actions.add(smallButton("Open folder", "folder") { openFolder(inst.root.toFile()) })
        actions.add(smallButton("Reinstall files", "download") { PlayAction.reinstall(shell, inst) })
        actions.add(smallButton("Delete", "delete") {
            if (confirmDelete(inst)) { Instances.trash(inst); selected = null; shell.toast("${inst.name} moved to Trash") }
        })
        info.body.add(actions)
        col.add(info)

        if (inst.notes.isNotBlank()) {
            col.add(vspace(12))
            val notes = SectionCard("Notes")
            notes.body.add(label(inst.notes, Fonts.body(12.5f), AerixColors.textSecondary))
            col.add(notes)
        }
        return col
    }

    private fun smallButton(text: String, icon: String, action: () -> Unit) = AerixButton(text, ButtonStyle.SECONDARY, icon).apply {
        compact = true; horizontalPadding = 14
        addActionListener { action() }
    }

    private fun contentTab(inst: Instance): JPanel {
        val col = ClearPanel().apply { layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS) }
        col.border = BorderFactory.createEmptyBorder(0, 0, 0, UiScale.px(10))
        val types = linkedMapOf(
            "mods" to "Mods", "resourcepacks" to "Resource packs", "shaderpacks" to "Shader packs",
            "datapacks" to "Datapacks", "saves" to "Worlds"
        )
        val tabs = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(6), 0))
        types.forEach { (key, name) ->
            tabs.add(PillTab(name, key == contentType).apply { addActionListener { contentType = key; rebuildDetail() } })
        }
        col.add(tabs)
        col.add(vspace(10))

        val dir = when (contentType) {
            "saves" -> inst.gameDir.resolve("saves")
            "datapacks" -> inst.gameDir.resolve("datapacks")
            else -> inst.gameDir.resolve(contentType)
        }
        val card = SectionCard(types[contentType] ?: "Content", dir.toString())
        val bar = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(8), 0))
        bar.add(smallButton("Import files…", "upload") { importContent(inst, dir) })
        bar.add(smallButton("Open folder", "folder") { Files.createDirectories(dir); openFolder(dir.toFile()) })
        card.body.add(bar)
        card.body.add(vspace(8))

        val entries = runCatching { Files.list(dir).use { s -> s.toList() } }.getOrDefault(emptyList())
            .filter { !it.fileName.toString().startsWith(".") }
            .sortedBy { it.fileName.toString().lowercase() }
        if (entries.isEmpty()) {
            card.body.add(label("Nothing here yet.", Fonts.body(12.5f), AerixColors.textMuted))
        } else {
            entries.forEach { path ->
                card.body.add(WorldlessContentRow(path) { rebuildDetail() })
            }
        }
        col.add(card)
        return col
    }

    private fun importContent(inst: Instance, dir: java.nio.file.Path) {
        val chooser = JFileChooser().apply {
            isMultiSelectionEnabled = true
            dialogTitle = "Import into ${dir.fileName}"
            fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        Scheduler.io("import-content") {
            var imported = 0
            chooser.selectedFiles.forEach { file ->
                runCatching {
                    Files.createDirectories(dir)
                    val target = dir.resolve(file.name)
                    if (file.isDirectory) {
                        copyDirectory(file.toPath(), target)
                    } else Files.copy(file.toPath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    imported++
                }.onFailure { Log.w("library", "import ${file.name} failed: ${it.message}") }
            }
            Scheduler.ui {
                shell.toast("Imported $imported item(s)")
                rebuildDetail()
            }
        }
    }

    private fun worldsTab(inst: Instance): JPanel {
        val col = ClearPanel().apply { layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS) }
        col.border = BorderFactory.createEmptyBorder(0, 0, 0, UiScale.px(10))
        val saves = inst.gameDir.resolve("saves")
        val card = SectionCard("Worlds", "Your worlds are never touched by the launcher")
        val bar = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(8), 0))
        bar.add(smallButton("Open saves folder", "folder") { Files.createDirectories(saves); openFolder(saves.toFile()) })
        bar.add(smallButton("Import world…", "import") { importContent(inst, saves) })
        card.body.add(bar)
        card.body.add(vspace(8))
        val worlds = GameLauncher.worldDetails(inst)
        if (worlds.isEmpty()) {
            card.body.add(label("No worlds yet — start the game and create one.", Fonts.body(12.5f), AerixColors.textMuted))
        } else {
            worlds.forEach { w ->
                card.body.add(WorldRow(w.path, w.levelName, w.sizeBytes))
            }
        }
        col.add(card)
        return col
    }

    private fun settingsTab(inst: Instance): JPanel {
        val col = ClearPanel().apply { layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS) }
        col.border = BorderFactory.createEmptyBorder(0, 0, 0, UiScale.px(10))

        val card = SectionCard("Launch settings", "Overrides for this instance only")
        val memory = AerixSlider(
            min = 512, max = maxOf(2048, AppEnv.totalMemoryMb - 1024),
            value = if (inst.memoryMb > 0) inst.memoryMb else inst.effectiveMemoryMb(),
            step = 256, suffix = " MB"
        )
        memory.addChangeListener { inst.memoryMb = memory.value; inst.save(); shell.toast("Memory set to ${memory.value} MB") }
        card.body.add(SettingRow("Memory allocation", "Leave at 0 in the field to follow the global setting (${Settings.int("memoryAllocationMb", 4096)} MB).", memory))

        val groupField = AerixField(placeholder = "e.g. Modded", text0 = inst.group, icon = "group").apply { preferredSize = Dimension(UiScale.px(220), UiScale.px(38)) }
        groupField.addActionListener { inst.group = groupField.text.trim(); inst.save(); rebuild() }
        groupField.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusLost(e: java.awt.event.FocusEvent) { inst.group = groupField.text.trim(); inst.save(); rebuild() }
        })
        card.body.add(SettingRow("Group", "Groups order the library and the rail's quick list.", groupField))

        val argsField = AerixField(placeholder = "-XX:+UseG1GC …", text0 = inst.jvmArgs).apply { preferredSize = Dimension(UiScale.px(300), UiScale.px(38)) }
        argsField.addActionListener { inst.jvmArgs = argsField.text; inst.save(); shell.toast("Saved JVM arguments") }
        argsField.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusLost(e: java.awt.event.FocusEvent) { inst.jvmArgs = argsField.text; inst.save() }
        })
        card.body.add(SettingRow("Extra JVM arguments", "Appended after the launcher's tuned defaults.", argsField))

        val javaPicker = AerixButton(
            JavaRuntimes.pick(inst.javaMajor, inst.javaPath)?.label ?: "Auto (recommended)", ButtonStyle.GHOST
        ).apply { compact = true }
        javaPicker.addActionListener {
            val runtimes = JavaRuntimes.discover()
            if (runtimes.isEmpty()) { shell.toast("No Java runtimes found — install one from Settings"); return@addActionListener }
            val options = arrayOf("Auto (recommended)") + runtimes.map { it.label + " — " + it.path }
            val choice = JOptionPane.showInputDialog(this, "Java runtime for ${inst.name}", "Java", JOptionPane.QUESTION_MESSAGE, null, options, options[0])
            if (choice != null) {
                inst.javaPath = if (choice == options[0]) "" else runtimes[options.indexOf(choice) - 1].javaExe.toString()
                inst.save(); rebuildDetail()
            }
        }
        card.body.add(SettingRow("Java runtime", "Minecraft needs Java 8 for old versions and Java 21 for 1.20.5+.", javaPicker))

        val favorite = AerixSwitch(inst.favorite).apply {
            addChangeListener { inst.favorite = on; inst.save(); rebuild() }
        }
        card.body.add(SettingRow("Favorite", "Shows a star in the library and the home page.", favorite))

        val pinned = AerixSwitch(inst.pinned).apply {
            addChangeListener { inst.pinned = on; inst.save(); rebuild() }
        }
        card.body.add(SettingRow("Pin to top", "Sorted above everything else.", pinned))
        col.add(card)

        col.add(vspace(12))
        val danger = SectionCard("Danger zone", "A trashed instance is kept until the Trash is emptied")
        val del = AerixButton("Move instance to Trash", ButtonStyle.DANGER, "delete").apply {
            compact = true
            addActionListener {
                if (confirmDelete(inst)) { Instances.trash(inst); selected = null; shell.toast("${inst.name} moved to Trash") }
            }
        }
        danger.body.add(del)
        col.add(danger)
        return col
    }

    private fun confirmDelete(inst: Instance): Boolean {
        val result = JOptionPane.showConfirmDialog(
            this@LibraryScreen,
            "Move “${inst.name}” to the Trash?\n\nWorlds, mods and settings are kept until you empty the Trash.",
            "Delete instance", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE
        )
        return result == JOptionPane.OK_OPTION
    }

    override fun addNotify() {
        super.addNotify()
        javax.swing.SwingUtilities.invokeLater {
            Settings.onChange { key ->
                if (key == "gameDirectory") Scheduler.ui { rebuild() }
            }
        }
    }
}

// ---- rows -------------------------------------------------------------------------------------

private class LibraryRow(val shell: Shell, val instance: Instance, val active: Boolean, onClick: () -> Unit) : JComponent() {
    private var hover = false
    private var icon: java.awt.image.BufferedImage? = null
    private var iconKey = ""

    init {
        preferredSize = Dimension(UiScale.px(320), UiScale.px(78))
        maximumSize = Dimension(Int.MAX_VALUE, UiScale.px(78))
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: MouseEvent) { hover = false; repaint() }
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    InstanceMenu.build(shell, instance).show(this@LibraryRow, e.x, e.y)
                } else onClick()
            }
        })
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val r = UiScale.px(Radii.cardSmall)
        val fill = when {
            active -> withAlpha(AerixTheme.accent, 30)
            hover -> AerixTheme.panelRaised()
            else -> AerixTheme.panel()
        }
        g2.color = fill
        g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), r * 2f, r * 2f))
        g2.color = if (active) withAlpha(AerixTheme.accent, 0x99) else AerixColors.borderSoft
        g2.stroke = java.awt.BasicStroke(UiScale.px(1.1f))
        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, r * 2f, r * 2f))

        val pad = UiScale.px(11)
        val iconSize = UiScale.px(46)
        val key = "${instance.iconPath}:$iconSize"
        if (key != iconKey) {
            icon = IconTile.instanceTile(instance, iconSize)
            iconKey = key
        }
        icon?.let { g2.drawImage(it, pad, (height - iconSize) / 2, null) }

        val textX = pad + iconSize + UiScale.px(11)
        g2.font = Fonts.titleMedium(13.5f)
        g2.color = AerixColors.textPrimary
        g2.drawString(fit(instance.name, g2.fontMetrics, width - textX - pad), textX, height / 2 - UiScale.px(2))
        g2.font = Fonts.label(11f)
        g2.color = if (active) AerixTheme.accent else AerixColors.textMuted
        g2.drawString(fit(instance.subtitle(), g2.fontMetrics, width - textX - pad), textX, height / 2 + UiScale.px(14))
        if (instance.pinned) Icons.draw(g2, "favorite", width - pad - UiScale.px(14), pad, UiScale.px(14), AerixColors.warning)
        g2.dispose()
    }

    private fun fit(text: String, fm: java.awt.FontMetrics, maxWidth: Int): String {
        if (fm.stringWidth(text) <= maxWidth) return text
        var t = text
        while (t.length > 3 && fm.stringWidth(t + "…") > maxWidth) t = t.dropLast(1)
        return t + "…"
    }
}

private class CountPill : JComponent() {
    var count: Int = 0
        set(value) { field = value; repaint() }
    init { preferredSize = Dimension(UiScale.px(30), UiScale.px(30)) }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        Paint.pill(g2, 0, 0, width, height, AerixTheme.panelRaised(), AerixColors.borderSoft)
        g2.font = Fonts.labelStrong(11f)
        g2.color = AerixColors.textPrimary
        val text = count.toString()
        val fm = g2.fontMetrics
        g2.drawString(text, (width - fm.stringWidth(text)) / 2, (height - fm.height) / 2 + fm.ascent)
        g2.dispose()
    }
}

private class DetailHeader(private val instance: Instance, private val shell: Shell, private val screen: LibraryScreen) : JComponent() {
    private var icon: java.awt.image.BufferedImage? = null
    init { preferredSize = Dimension(100, UiScale.px(64)) }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val size = UiScale.px(52)
        icon = IconTile.instanceTile(instance, size)
        g2.drawImage(icon, 0, 0, null)
        g2.font = Fonts.headline(20f)
        g2.color = AerixColors.textPrimary
        g2.drawString(instance.name, size + UiScale.px(14), UiScale.px(22))
        g2.font = Fonts.label(11.5f)
        g2.color = AerixColors.textMuted
        g2.drawString(instance.subtitle(), size + UiScale.px(14), UiScale.px(40))
        g2.dispose()
    }
}

private class WorldlessContentRow(private val path: java.nio.file.Path, private val onChange: () -> Unit) : JComponent() {
    private var hover = false
    private val name = path.fileName.toString()
    private val enabled = !name.endsWith(".disabled") && !name.endsWith(".jar.disabled")
    private val isDir = Files.isDirectory(path)
    private val iconName = when {
        isDir -> "folder"
        name.endsWith(".zip") || name.endsWith(".mrpack") -> "zip"
        name.endsWith(".jar") || name.endsWith(".jar.disabled") -> "archive"
        else -> "description"
    }

    init {
        preferredSize = Dimension(100, UiScale.px(40))
        maximumSize = Dimension(Int.MAX_VALUE, UiScale.px(40))
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: MouseEvent) { hover = false; repaint() }
        })
        toolTipText = path.toString()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        if (hover) {
            g2.color = withAlpha(Color.WHITE, 12)
            g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), UiScale.px(8) * 2f, UiScale.px(8) * 2f))
        }
        Icons.draw(g2, iconName, UiScale.px(6), (height - UiScale.px(16)) / 2, UiScale.px(16),
            if (enabled) AerixTheme.accent else AerixColors.textMuted)
        g2.font = Fonts.body(12.5f)
        g2.color = if (enabled) AerixColors.textPrimary else AerixColors.textMuted
        val labelText = name.removeSuffix(".disabled") + if (!enabled) "  (disabled)" else ""
        g2.drawString(fit(labelText, g2.fontMetrics, width - UiScale.px(150)), UiScale.px(30), height / 2 + g2.fontMetrics.ascent / 2 - 2)
        g2.dispose()

        // action buttons are real components so they get focus and tooltips
        if (componentCount == 0) {
            val toggle = IconButton(if (enabled) "visibility" else "eye_off", size = 28, tooltip = if (enabled) "Disable" else "Enable").apply {
                addActionListener { toggleEnabled() }
            }
            val trash = IconButton("delete", size = 28, tooltip = "Delete").apply {
                addActionListener { delete() }
            }
            add(toggle); add(trash)
            toggle.setBounds(width - UiScale.px(70), (height - UiScale.px(28)) / 2, UiScale.px(28), UiScale.px(28))
            trash.setBounds(width - UiScale.px(38), (height - UiScale.px(28)) / 2, UiScale.px(28), UiScale.px(28))
        }
    }

    private fun toggleEnabled() {
        runCatching {
            val target = if (enabled) path.resolveSibling(name + ".disabled") else path.resolveSibling(name.removeSuffix(".disabled"))
            Files.move(path, target)
            onChange()
        }.onFailure { JOptionPane.showMessageDialog(this, "Could not rename: ${it.message}") }
    }

    private fun delete() {
        val ok = JOptionPane.showConfirmDialog(this, "Delete “$name”?\nThis cannot be undone.", "Delete", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
        if (ok != JOptionPane.OK_OPTION) return
        runCatching { if (isDir) Instances.deleteTree(path) else Files.deleteIfExists(path); onChange() }
            .onFailure { JOptionPane.showMessageDialog(this, "Could not delete: ${it.message}") }
    }

    private fun fit(text: String, fm: java.awt.FontMetrics, maxWidth: Int): String {
        if (fm.stringWidth(text) <= maxWidth) return text
        var t = text
        while (t.length > 3 && fm.stringWidth(t + "…") > maxWidth) t = t.dropLast(1)
        return t + "…"
    }
}

private class WorldRow(private val dir: java.nio.file.Path, private val levelName: String?, private val sizeBytes: Long) : JComponent() {
    private var hover = false
    init {
        preferredSize = Dimension(100, UiScale.px(52))
        maximumSize = Dimension(Int.MAX_VALUE, UiScale.px(52))
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: MouseEvent) { hover = false; repaint() }
            override fun mouseClicked(e: MouseEvent) { openFolder(dir.toFile()) }
        })
        toolTipText = dir.toString()
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        if (hover) {
            g2.color = withAlpha(Color.WHITE, 12)
            g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), UiScale.px(8) * 2f, UiScale.px(8) * 2f))
        }
        Icons.draw(g2, "public", UiScale.px(6), UiScale.px(8), UiScale.px(18), AerixTheme.accent)
        g2.font = Fonts.bodyStrong(12.5f)
        g2.color = AerixColors.textPrimary
        g2.drawString(levelName ?: dir.fileName.toString(), UiScale.px(32), UiScale.px(20))
        g2.font = Fonts.label(10.5f)
        g2.color = AerixColors.textMuted
        val mod = runCatching { Files.getLastModifiedTime(dir) }.getOrNull()?.toMillis() ?: 0
        val whenText = if (mod == 0L) "" else java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy HH:mm")
            .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(mod))
        g2.drawString("${humanSize(sizeBytes)}  ·  $whenText", UiScale.px(32), UiScale.px(36))
        g2.dispose()
    }
}

// ---- shared helpers ---------------------------------------------------------------------------

private fun JComponent.settingChecked() {}

fun openFolder(file: java.io.File) {
    try {
        if (!java.awt.Desktop.isDesktopSupported()) {
            throw IllegalStateException("Desktop API not available")
        }
        java.awt.Desktop.getDesktop().open(file)
    } catch (e: Exception) {
        Log.w("files", "could not open ${file.path}: ${e.message}")
        // Explorer is always there on Windows; the Desktop API can fail inside a stripped JRE.
        if (AppEnv.isWindows) {
            runCatching { ProcessBuilder("explorer.exe", file.absolutePath).start() }
        }
    }
}

fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

fun formatPlayTime(seconds: Long): String {
    if (seconds <= 0) return "—"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "${seconds}s"
    }
}

fun copyDirectory(from: java.nio.file.Path, to: java.nio.file.Path) {
    Files.walk(from).use { walk ->
        walk.forEach { p ->
            val rel = from.relativize(p)
            val target = to.resolve(rel)
            if (Files.isDirectory(p)) Files.createDirectories(target)
            else {
                Files.createDirectories(target.parent)
                Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
