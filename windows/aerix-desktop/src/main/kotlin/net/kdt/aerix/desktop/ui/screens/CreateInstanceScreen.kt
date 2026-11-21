package net.kdt.aerix.desktop.ui.screens

import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Scheduler
import net.kdt.aerix.desktop.core.game.Installer
import net.kdt.aerix.desktop.core.game.Mojang
import net.kdt.aerix.desktop.core.game.VersionSummary
import net.kdt.aerix.desktop.core.instance.Instance
import net.kdt.aerix.desktop.core.instance.InstanceLinks
import net.kdt.aerix.desktop.core.instance.Instances
import net.kdt.aerix.desktop.core.instance.Loader
import net.kdt.aerix.desktop.core.loaders.Loaders
import net.kdt.aerix.desktop.ui.components.AerixButton
import net.kdt.aerix.desktop.ui.components.AerixField
import net.kdt.aerix.desktop.ui.components.AerixSwitch
import net.kdt.aerix.desktop.ui.components.ButtonStyle
import net.kdt.aerix.desktop.ui.components.ClearPanel
import net.kdt.aerix.desktop.ui.components.IconButton
import net.kdt.aerix.desktop.ui.components.PillTab
import net.kdt.aerix.desktop.ui.components.aerixScroll
import net.kdt.aerix.desktop.ui.components.label
import net.kdt.aerix.desktop.ui.components.heading
import net.kdt.aerix.desktop.ui.components.label
import net.kdt.aerix.desktop.ui.components.vspace
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
import net.kdt.aerix.desktop.ui.icons.Icons
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
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * The Create page: the Android screen's two panes side by side.
 *
 *   left  — the Minecraft version list (Release / Snapshot / Old / Installed tabs + filter)
 *   right — name, icon, loader grid, and the loader's build list for the chosen version
 *
 * Installing is explicit: pressing "Create & Install" writes the loader profile, downloads the
 * version, then drops the player back into the library with the new instance selected. Nothing is
 * created if the install fails, which is what the Android flow promises.
 */
class CreateInstanceScreen(private val shell: Shell) : JPanel(BorderLayout()), Refreshable {
    private var versions: List<VersionSummary> = emptyList()
    private var versionsLoading = false
    private var versionsError: String? = null
    private var tab = 0
    private var filter = ""

    private var selectedVersion: String? = null
    private var loader = Loader.FABRIC
    private var builds: List<Loaders.Build> = emptyList()
    private var build: Loaders.Build? = null
    private var buildsLoading = false
    private var buildsError: String? = null
    private var stableOnly = true
    private var nameEdited = false
    private var installing = false
    private var progressText = ""
    private var progressFraction = 0f

    private val nameField = AerixField("My new world").apply {
        preferredSize = Dimension(UiScale.px(300), UiScale.px(46))
    }
    private val filterField = AerixField(placeholder = "Search versions", icon = "search").apply {
        preferredSize = Dimension(UiScale.px(240), UiScale.px(38))
    }
    private val installButton = AerixButton("CREATE & INSTALL", ButtonStyle.PRIMARY, "download").apply {
        addActionListener { startInstall() }
    }
    private val importButton = AerixButton("Import modpack", ButtonStyle.GHOST, "import").apply {
        addActionListener { shell.navigate(Page.MODPACKS) }
    }
    private val versionList = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val loaderPane = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val buildList = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val stableSwitch = AerixSwitch(true)
    private val versionTabs = ClearPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
    private var versionTabButtons: List<PillTab> = emptyList()
    private var headerPill: MetaPill? = null

    init {
        isOpaque = false
        add(buildHeader(), BorderLayout.NORTH)
        add(buildBody(), BorderLayout.CENTER)
        nameField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) {
                nameEdited = nameField.text.isNotEmpty()
                updateInstallButton()
            }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) {
                nameEdited = nameField.text.isNotEmpty()
                updateInstallButton()
            }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {}
        })
        filterField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { filter = filterField.text; rebuildVersionList() }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { filter = filterField.text; rebuildVersionList() }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {}
        })
        stableSwitch.addChangeListener { stableOnly = stableSwitch.on; loadBuilds() }
        rebuildLoaderPane()
        loadVersions()
    }

    override fun refresh() {
        if (versions.isEmpty() && !versionsLoading) loadVersions()
        updateInstallButton()
    }

    private fun buildHeader(): JPanel {
        val header = ClearPanel(BorderLayout())
        header.border = BorderFactory.createEmptyBorder(
            UiScale.px(Spacing.lg), UiScale.px(Spacing.xl), UiScale.px(Spacing.md), UiScale.px(Spacing.xl)
        )
        val left = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(10), 0))
        left.add(IconButton("back", size = 36, fillColor = AerixTheme.panel(), tooltip = "Close").apply {
            addActionListener { if (!shell.goBack()) shell.navigate(Page.HOME) }
        })
        left.add(heading("Create instance", 20f))
        headerPill = MetaPill("${selectedVersion ?: "…"}  •  ${Loader.displayName(loader)}", accented = true)
        left.add(headerPill!!)
        header.add(left, BorderLayout.WEST)

        val right = ClearPanel(FlowLayout(FlowLayout.RIGHT, UiScale.px(8), 0))
        right.add(importButton)
        right.add(installButton)
        header.add(right, BorderLayout.EAST)
        return header
    }

    private fun buildBody(): JPanel {
        val body = ClearPanel(GridLayout(1, 2, UiScale.px(14), 0)).apply { grow = true }
        body.border = BorderFactory.createEmptyBorder(0, UiScale.px(Spacing.xl), UiScale.px(Spacing.xl), UiScale.px(Spacing.xl))

        // ---- left: versions
        val leftCard = GlassPane()
        leftCard.layout = BorderLayout()
        leftCard.border = BorderFactory.createEmptyBorder(UiScale.px(12), UiScale.px(12), UiScale.px(12), UiScale.px(12))
        listOf("Release", "Snapshot", "Old", "Installed").forEachIndexed { i, name ->
            val pill = PillTab(name, i == tab)
            pill.addActionListener { tab = i; syncTabs(); rebuildVersionList() }
            versionTabs.add(pill)
        }
        versionTabButtons = versionTabs.components.filterIsInstance<PillTab>()
        leftCard.add(versionTabs, BorderLayout.NORTH)
        val leftBody = ClearPanel(BorderLayout())
        leftBody.border = BorderFactory.createEmptyBorder(UiScale.px(10), 0, 0, 0)
        val filterRow = ClearPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        filterField.preferredSize = Dimension(UiScale.px(260), UiScale.px(38))
        filterRow.add(filterField)
        leftBody.add(filterRow, BorderLayout.NORTH)
        val listScroll = aerixScroll(versionList)
        listScroll.border = BorderFactory.createEmptyBorder()
        leftBody.add(listScroll, BorderLayout.CENTER)
        leftCard.add(leftBody, BorderLayout.CENTER)
        body.add(leftCard)

        // ---- right: details
        val rightCard = GlassPane()
        rightCard.layout = BorderLayout()
        rightCard.border = BorderFactory.createEmptyBorder(UiScale.px(14), UiScale.px(14), UiScale.px(14), UiScale.px(14))

        // name row
        val nameRow = ClearPanel(BorderLayout()).apply { grow = true }
        nameRow.add(Eyebrow("INSTANCE INFO"), BorderLayout.NORTH)
        val nameInner = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(10), UiScale.px(6))).apply { grow = true }
        nameInner.add(IconTilePreview {  })
        nameField.preferredSize = Dimension(UiScale.px(240), UiScale.px(46))
        nameInner.add(nameField)
        nameRow.add(nameInner, BorderLayout.CENTER)
        rightCard.add(nameRow, BorderLayout.NORTH)

        val detailsScroll = aerixScroll(loaderPane)
        detailsScroll.border = BorderFactory.createEmptyBorder()
        rightCard.add(detailsScroll, BorderLayout.CENTER)
        body.add(rightCard)
        return body
    }

    // ---- data --------------------------------------------------------------------------------

    private fun loadVersions() {
        if (versionsLoading) return
        versionsLoading = true
        versionsError = null
        rebuildVersionList()
        Scheduler.io("create-versions") {
            try {
                val manifest = Mojang.versionManifest(Instances.sharedDataDir())
                val installed = runCatching { Mojang.localVersions(net.kdt.aerix.desktop.app.AppEnv.defaultGameDir) }.getOrDefault(emptyList())
                versions = manifest.versions.filter { it.isRelease || it.isSnapshot || it.isOld }
                if (selectedVersion == null) {
                    selectedVersion = manifest.latestRelease ?: versions.firstOrNull { it.isRelease }?.id
                }
            } catch (e: Exception) {
                Log.w("create", "version list failed: ${e.message}")
                versionsError = e.message
            } finally {
                versionsLoading = false
                Scheduler.ui {
                    rebuildVersionList()
                    rebuildLoaderPane()
                    syncHeaderPill()
                    updateInstallButton()
                }
            }
        }
    }

    /**
     * The Minecraft version the chosen loader builds on. All loaders follow the picked version
     * except Better than Adventure, which *is* the game and always bases itself on b1.7.3.
     */
    private fun effectiveMc(): String? = if (loader == Loader.BTA) "b1.7.3" else selectedVersion

    private fun syncHeaderPill() {
        headerPill?.text0 = "${selectedVersion ?: "…"}  •  ${Loader.displayName(loader)}"
    }

    private fun loadBuilds() {
        syncHeaderPill()
        val mc = effectiveMc() ?: return
        if (loader == Loader.VANILLA) return
        buildsLoading = true
        builds = emptyList()
        build = null
        buildsError = null
        rebuildLoaderPane()
        Scheduler.io("create-builds") {
            val result = runCatching { Loaders.builds(loader, mc, stableOnly) }
            builds = result.getOrElse {
                buildsError = it.message
                emptyList()
            }
            build = builds.firstOrNull()
            buildsLoading = false
            Scheduler.ui { rebuildLoaderPane(); updateInstallButton() }
        }
    }

    // ---- rendering ---------------------------------------------------------------------------

    private fun syncTabs() {
        versionTabButtons.forEachIndexed { i, pill -> pill.checkedState = i == tab; pill.repaint() }
    }

    private fun rebuildVersionList() {
        versionList.removeAll()
        if (versionsLoading && versions.isEmpty()) {
            versionList.add(padded(label("Loading the version list…", Fonts.body(12.5f), AerixColors.textMuted)))
        } else if (versionsError != null) {
            versionList.add(padded(label("Could not load versions: ${versionsError}", Fonts.body(12.5f), AerixColors.warning)))
            versionList.add(padded(linkButton("Try again") { loadVersions() }))
        } else {
            val typeFilter = when (tab) {
                0 -> "release"
                1 -> "snapshot"
                2 -> "old_beta"
                else -> "installed"
            }
            val rows = if (tab == 3) {
                // Installed versions: local profiles, including loader profiles already on disk
                Mojang.localVersions(Instances.sharedDataDir())
                    .filter { filter.isBlank() || it.contains(filter, true) }
            } else {
                versions.filter { v ->
                    (v.type == typeFilter || (tab == 2 && v.isOld)) &&
                            (filter.isBlank() || v.id.contains(filter, true))
                }.map { it.id }
            }
            if (rows.isEmpty()) {
                versionList.add(padded(label("Nothing matches.", Fonts.body(12.5f), AerixColors.textMuted)))
            }
            rows.take(400).forEach { id ->
                val row = VersionRow(id, id == selectedVersion, typeFor(id))
                row.addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        selectedVersion = id
                        if (!nameEdited) nameField.text = ""
                        rebuildVersionList()
                        loadBuilds()
                        rebuildLoaderPane()
                        updateInstallButton()
                    }
                })
                versionList.add(row)
                versionList.add(Box.createVerticalStrut(UiScale.px(4)))
            }
        }
        versionList.revalidate(); versionList.repaint()
        syncTabs()
    }

    private fun typeFor(id: String): String = versions.firstOrNull { it.id == id }?.type ?: "installed"

    private fun rebuildLoaderPane() {
        loaderPane.removeAll()
        loaderPane.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)

        // ---- loader grid
        loaderPane.add(paddedSectionEyebrow("MOD LOADER"))
        loaderPane.add(vspace(6))
        val grid = ClearPanel(GridLayout(0, 2, UiScale.px(6), UiScale.px(6)))
        Loader.all.forEach { entry ->
            val tile = LoaderTile(entry, entry == loader)
            tile.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (installing) return
                    loader = entry
                    stableOnly = true
                    stableSwitch.on = true
                    build = null
                    syncHeaderPill()
                    rebuildLoaderPane()
                    loadBuilds()
                    updateInstallButton()
                }
            })
            grid.add(tile)
        }
        loaderPane.add(grid)
        loaderPane.add(vspace(10))

        val mc = effectiveMc() ?: "…"
        if (loader == Loader.VANILLA) {
            loaderPane.add(padded(label("Vanilla has nothing to choose on top of Minecraft $mc — it is the base game itself.", Fonts.body(12f), AerixColors.textSecondary)))
        } else {
            val stableRow = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(8), 0))
            stableRow.add(label("Only stable builds", Fonts.body(12f), AerixColors.textSecondary))
            stableRow.add(stableSwitch)
            val labelText = when {
                build != null -> "${Loader.displayName(loader)} ${build?.label}"
                buildsLoading -> "Loading ${Loader.displayName(loader)} builds…"
                else -> "${Loader.displayName(loader)} builds"
            }
            val head = ClearPanel(BorderLayout())
            head.add(label(labelText, Fonts.titleMedium(12.5f)), BorderLayout.WEST)
            head.add(stableRow, BorderLayout.EAST)
            loaderPane.add(head)
            loaderPane.add(vspace(6))

            if (buildsLoading) {
                loaderPane.add(padded(label("Contacting the ${Loader.displayName(loader)} metadata server…", Fonts.body(12f), AerixColors.textMuted)))
            } else if (builds.isEmpty()) {
                loaderPane.add(padded(label(
                    buildsError?.let { "Could not load builds: $it" }
                        ?: "No ${Loader.displayName(loader)} builds for Minecraft $mc.",
                    Fonts.body(12f), AerixColors.warning
                )))
                loaderPane.add(padded(linkButton("Try again") { loadBuilds() }))
            } else {
                buildList.removeAll()
                builds.take(120).forEach { b ->
                    val row = BuildRow(b, b === build)
                    row.addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) { build = b; rebuildLoaderPane(); updateInstallButton() }
                    })
                    buildList.add(row)
                    buildList.add(Box.createVerticalStrut(UiScale.px(4)))
                }
                val scroll = aerixScroll(buildList)
                scroll.preferredSize = Dimension(100, UiScale.px(150))
                scroll.border = BorderFactory.createEmptyBorder()
                loaderPane.add(scroll)
            }
        }

        if (installing) {
            loaderPane.add(vspace(10))
            loaderPane.add(InstallProgressPanel(progressText, progressFraction))
        }
        loaderPane.revalidate(); loaderPane.repaint()
    }

    private fun updateInstallButton() {
        val can = effectiveMc() != null && !installing &&
                (loader == Loader.VANILLA || build != null)
        installButton.isEnabled = can
        installButton.setLabelText(
            when {
                installing -> "INSTALLING…"
                else -> "CREATE & INSTALL"
            }
        )
    }

    // ---- install -----------------------------------------------------------------------------

    private fun startInstall() {
        val mc = effectiveMc() ?: return
        val chosenLoader = loader
        if (chosenLoader != Loader.VANILLA && build == null) {
            shell.toast("Pick a ${Loader.displayName(chosenLoader)} build first")
            return
        }
        val desired = nameField.text.trim().ifBlank { Loader.displayName(chosenLoader) }
        installing = true
        progressFraction = 0f
        progressText = "Preparing…"
        rebuildLoaderPane()
        updateInstallButton()

        Scheduler.io("create-install") {
            var created: Instance? = null
            try {
                fun phase(text: String, fraction: Float) {
                    progressText = text
                    progressFraction = fraction
                    Scheduler.ui { rebuildLoaderPane() }
                }

                // 1) loader profile (Fabric/Quilt JSON, Forge/NeoForge/OptiFine installers, BTA profile)
                var versionId = mc
                if (chosenLoader != Loader.VANILLA) {
                    phase("Installing ${Loader.displayName(chosenLoader)} ${build?.label}", 0.05f)
                    val result = Loaders.install(Instances.sharedDataDir(), chosenLoader, mc, build!!) { text, fraction ->
                        phase(text, fraction * 0.35f)
                    }
                    versionId = result.versionId
                }

                // 2) the instance folder (so progress and partial downloads are inside it)
                phase("Creating the instance", 0.4f)
                created = Instances.create(
                    name = desired, versionId = versionId, mcVersion = mc,
                    loader = chosenLoader,
                    loaderVersion = build?.label
                )

                // 3) downloads into the shared install root, which the instance links to
                phase("Downloading Minecraft $mc", 0.45f)
                Installer.install(Instances.sharedDataDir(), versionId) { p ->
                    phase(p.phase, 0.45f + p.fraction * 0.55f)
                }

                InstanceLinks.ensure(created)
                created.save()
                progressText = "Done"
                progressFraction = 1f
                val finalInstance = created
                Scheduler.ui {
                    installing = false
                    shell.toast("${finalInstance.name} is ready")
                    net.kdt.aerix.desktop.app.Settings.set("currentInstance", finalInstance.name)
                    shell.navigate(Page.LIBRARY)
                    Instances.reload()
                }
            } catch (e: Exception) {
                Log.e("create", "install failed: ${e.message}", e)
                Scheduler.ui {
                    installing = false
                    progressText = ""
                    progressFraction = 0f
                    rebuildLoaderPane()
                    updateInstallButton()
                    javax.swing.JOptionPane.showMessageDialog(
                        this@CreateInstanceScreen,
                        "Could not create the instance:\n\n${e.message}",
                        "Installation failed", javax.swing.JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }
    }

    // ---- small helpers ------------------------------------------------------------------------

    private fun padded(c: JComponent): JComponent {
        c.alignmentX = 0f
        return c
    }

    private fun paddedSectionEyebrow(text: String): JComponent = Eyebrow(text).also { it.alignmentX = 0f }

    /** A ghost button sized to its text, left aligned, so it does not stretch across the column. */
    private fun linkButton(text: String, action: () -> Unit): JComponent {
        val b = AerixButton(text, ButtonStyle.GHOST).apply { compact = true; addActionListener { action() } }
        val row = ClearPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        row.add(b)
        row.alignmentX = 0f
        return row
    }
}

// ---- parts -------------------------------------------------------------------------------------

private class GlassPane : JPanel(BorderLayout()) {
    init { isOpaque = false }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        Paint.glass(g2, 0, 0, width, height, UiScale.px(Radii.panel), fill = AerixTheme.panel(), shadow = true)
        g2.dispose()
    }
}

private class MetaPill(text: String, val accented: Boolean = false) : JComponent() {
    var text0 = text
        set(value) { field = value; revalidate(); repaint() }
    init { preferredSize = Dimension(UiScale.px(10), UiScale.px(26)) }
    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(Fonts.labelStrong(10.5f))
        return Dimension(fm.stringWidth(text0) + UiScale.px(20), UiScale.px(26))
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val fill = if (accented) withAlpha(AerixTheme.accent, 30) else AerixTheme.panelRaised()
        val border = if (accented) withAlpha(AerixTheme.accent, 120) else AerixColors.borderSoft
        Paint.pill(g2, 0, 0, width, height, fill, border)
        g2.font = Fonts.labelStrong(10.5f)
        g2.color = if (accented) AerixTheme.accent else AerixColors.textSecondary
        val fm = g2.fontMetrics
        g2.drawString(text0, (width - fm.stringWidth(text0)) / 2, (height - fm.height) / 2 + fm.ascent)
        g2.dispose()
    }
}

private class VersionRow(private val id: String, private val selected: Boolean, private val type: String) : JComponent() {
    private var hover = false
    init {
        preferredSize = Dimension(100, UiScale.px(44))
        maximumSize = Dimension(Int.MAX_VALUE, UiScale.px(44))
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: MouseEvent) { hover = false; repaint() }
        })
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val fill = when {
            selected -> withAlpha(AerixTheme.accent, 34)
            hover -> withAlpha(Color.WHITE, 14)
            else -> withAlpha(Color.WHITE, 4)
        }
        g2.color = fill
        g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), UiScale.px(10) * 2f, UiScale.px(10) * 2f))
        if (selected) {
            g2.color = withAlpha(AerixTheme.accent, 110)
            g2.stroke = java.awt.BasicStroke(UiScale.px(1.2f))
            g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, UiScale.px(10) * 2f, UiScale.px(10) * 2f))
        }
        val dot = when (type) {
            "release" -> AerixColors.success
            "snapshot" -> AerixColors.warning
            "installed" -> AerixTheme.accent
            else -> AerixColors.textMuted
        }
        Paint.circle(g2, UiScale.px(14), height / 2, UiScale.px(3), dot)
        g2.font = Fonts.bodyStrong(12.5f)
        g2.color = if (selected) Color.WHITE else AerixColors.textPrimary
        g2.drawString(id, UiScale.px(26), height / 2 + g2.fontMetrics.ascent / 2 - 1)
        if (type != "release") {
            g2.font = Fonts.label(9.5f)
            g2.color = AerixColors.textMuted
            val tag = type.replace("_", " ")
            val w = g2.fontMetrics.stringWidth(tag)
            g2.drawString(tag, width - w - UiScale.px(10), height / 2 + g2.fontMetrics.ascent / 2 - 1)
        }
        g2.dispose()
    }
}

private class LoaderTile(private val entry: String, private val selected: Boolean) : JComponent() {
    private var hover = false
    init {
        preferredSize = Dimension(100, UiScale.px(52))
        maximumSize = Dimension(Int.MAX_VALUE, UiScale.px(52))
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: MouseEvent) { hover = false; repaint() }
        })
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val r = UiScale.px(12)
        g2.color = when {
            selected -> withAlpha(AerixTheme.accent, 26)
            hover -> withAlpha(Color.WHITE, 16)
            else -> withAlpha(AerixColors.panelRaisedBase, 0x60)
        }
        g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), r * 2f, r * 2f))
        g2.color = if (selected) withAlpha(AerixTheme.accent, 150) else AerixColors.borderSoft
        g2.stroke = java.awt.BasicStroke(UiScale.px(1.2f))
        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, r * 2f, r * 2f))
        val icon = when (entry) {
            Loader.FABRIC -> "build"
            Loader.QUILT -> "build"
            Loader.FORGE -> "style"
            Loader.NEOFORGE -> "update"
            Loader.OPTIFINE -> "visibility"
            Loader.BTA -> "gamepad"
            else -> "gamepad"
        }
        val tint = if (selected) AerixTheme.accent else AerixColors.textSecondary
        Icons.draw(g2, icon, UiScale.px(12), (height - UiScale.px(18)) / 2, UiScale.px(18), tint)
        g2.font = Fonts.titleMedium(12.5f)
        g2.color = if (selected) Color.WHITE else AerixColors.textPrimary
        g2.drawString(Loader.displayName(entry), UiScale.px(38), height / 2 + g2.fontMetrics.ascent / 2 - 1)
        if (selected) {
            Icons.draw(g2, "check", width - UiScale.px(24), (height - UiScale.px(14)) / 2, UiScale.px(14), AerixTheme.accent)
        }
        g2.dispose()
    }
}

private class BuildRow(private val build: Loaders.Build, private val selected: Boolean) : JComponent() {
    private var hover = false
    init {
        preferredSize = Dimension(100, UiScale.px(38))
        maximumSize = Dimension(Int.MAX_VALUE, UiScale.px(38))
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: MouseEvent) { hover = false; repaint() }
        })
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        if (selected || hover) {
            g2.color = if (selected) withAlpha(AerixTheme.accent, 30) else withAlpha(Color.WHITE, 12)
            g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), UiScale.px(9) * 2f, UiScale.px(9) * 2f))
        }
        val cx = UiScale.px(14)
        val cy = height / 2
        g2.color = if (selected) AerixTheme.accent else AerixColors.textSecondary
        g2.stroke = java.awt.BasicStroke(UiScale.px(1.6f))
        g2.draw(java.awt.geom.Ellipse2D.Float((cx - 6).toFloat(), (cy - 6).toFloat(), 12f, 12f))
        if (selected) {
            g2.color = AerixTheme.accent
            g2.fill(java.awt.geom.Ellipse2D.Float((cx - 3).toFloat(), (cy - 3).toFloat(), 6f, 6f))
        }
        g2.font = if (selected) Fonts.bodyStrong(12.5f) else Fonts.body(12.5f)
        g2.color = AerixColors.textPrimary
        g2.drawString(build.label, UiScale.px(30), cy + g2.fontMetrics.ascent / 2 - 1)
        if (build.stable) {
            val text = "Stable"
            g2.font = Fonts.labelStrong(9.5f)
            val w = g2.fontMetrics.stringWidth(text) + UiScale.px(14)
            Paint.pill(g2, width - w - UiScale.px(8), cy - UiScale.px(10), w, UiScale.px(20),
                withAlpha(AerixTheme.accent, if (selected) 50 else 24), withAlpha(AerixTheme.accent, 90))
            g2.color = AerixTheme.accent
            g2.drawString(text, width - w - UiScale.px(8) + UiScale.px(7), cy - UiScale.px(10) + UiScale.px(14))
        }
        g2.dispose()
    }
}

private class InstallProgressPanel(private val text: String, private val fraction: Float) : JComponent() {
    init { preferredSize = Dimension(100, UiScale.px(44)) }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        g2.font = Fonts.label(11.5f)
        g2.color = AerixColors.textSecondary
        g2.drawString(text.take(80), 0, UiScale.px(14))
        val barY = UiScale.px(22)
        Paint.pill(g2, 0, barY, width, UiScale.px(8), withAlpha(AerixColors.textMuted, 60))
        val w = (width * fraction.coerceIn(0f, 1f)).toInt()
        if (w > 0) Paint.pill(g2, 0, barY, maxOf(w, UiScale.px(8)), UiScale.px(8), AerixTheme.accent)
        g2.dispose()
    }
}

private class IconTilePreview(private val onClick: () -> Unit) : JComponent() {
    init { preferredSize = Dimension(UiScale.px(46), UiScale.px(46)); cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        g2.color = withAlpha(AerixTheme.accent, 26)
        g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), UiScale.px(12) * 2f, UiScale.px(12) * 2f))
        Icons.draw(g2, "image", (width - UiScale.px(20)) / 2, (height - UiScale.px(20)) / 2, UiScale.px(20), AerixTheme.accent)
        g2.dispose()
    }
}
