package net.kdt.aerix.desktop.ui.screens

import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Scheduler
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.content.Content
import net.kdt.aerix.desktop.core.content.ContentInstaller
import java.nio.file.Files
import net.kdt.aerix.desktop.core.instance.Instance
import net.kdt.aerix.desktop.core.instance.Instances
import net.kdt.aerix.desktop.ui.components.AerixButton
import net.kdt.aerix.desktop.ui.components.AerixField
import net.kdt.aerix.desktop.ui.components.ButtonStyle
import net.kdt.aerix.desktop.ui.components.ClearPanel
import net.kdt.aerix.desktop.ui.components.EmptyState
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
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Discover — the store front for mods, modpacks, resource packs, shaders and worlds.
 *
 * The Android screen is a search field over a horizontally categorised feed of cards with a
 * Modrinth/CurseForge switch. Desktop has room for the cards to be wider, so they are: a poster,
 * the project name, its summary, stats, and an install button per card that asks which instance to
 * install into the first time.
 */
class DiscoverScreen(private val shell: Shell) : JPanel(BorderLayout()), Refreshable {
    private var kind = Content.Kind.MODPACK
    private var source = Content.Source.MODRINTH
    private var query = ""
    private var gameVersion = ""
    private var loader = ""
    private var offset = 0
    private var loading = false
    private var error: String? = null
    private var results: List<Content.Project> = emptyList()
    private val iconCache = HashMap<String, BufferedImage?>()

    private val searchField = AerixField(placeholder = "Search Modrinth…", icon = "search").apply {
        preferredSize = Dimension(UiScale.px(360), UiScale.px(42))
    }
    private val kindRow = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(6), 0))
    private val sourceButtons = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(6), 0))
    /**
     * Cards, or a single full-width message.
     *
     * A `GridLayout` cannot hold one message and cards at once: with a single child it hands that
     * child the whole viewport height, which left "Discover is offline" and the loading text
     * stacked at the bottom of an empty screen. The content is therefore swapped between two
     * views, and the scroll pane is re-pointed rather than mutated in place.
     */
    private var grid = ClearPanel(GridLayout(0, 3, UiScale.px(14), UiScale.px(14)))
    private val scroll = aerixScroll(grid)
    private val footer = ClearPanel(FlowLayout(FlowLayout.CENTER, UiScale.px(8), UiScale.px(10)))
    private val loadingLabel = label("", Fonts.body(12.5f), AerixColors.textMuted)
    private var versionField: AerixField
    private var loaderField: AerixField

    init {
        isOpaque = false
        versionField = AerixField(placeholder = "1.21.1", icon = "gamepad").apply {
            preferredSize = Dimension(UiScale.px(120), UiScale.px(36))
            text0 = Settings.string("discoverVersion", "")
            addActionListener { gameVersion = text; offset = 0; load() }
            addFocusListener(object : java.awt.event.FocusAdapter() {
                override fun focusLost(e: java.awt.event.FocusEvent) {
                    if (text != gameVersion) { gameVersion = text; offset = 0; load() }
                }
            })
        }
        loaderField = AerixField(placeholder = "fabric", icon = "build").apply {
            preferredSize = Dimension(UiScale.px(120), UiScale.px(36))
            text0 = Settings.string("discoverLoader", "")
            addActionListener { loader = text; offset = 0; load() }
            addFocusListener(object : java.awt.event.FocusAdapter() {
                override fun focusLost(e: java.awt.event.FocusEvent) {
                    if (text != loader) { loader = text; offset = 0; load() }
                }
            })
        }

        add(buildHeader(), BorderLayout.NORTH)
        val body = ClearPanel(BorderLayout())
        body.border = BorderFactory.createEmptyBorder(0, UiScale.px(Spacing.xl), 0, UiScale.px(Spacing.xl))
        scroll.border = BorderFactory.createEmptyBorder()
        body.add(scroll, BorderLayout.CENTER)
        body.add(footer, BorderLayout.SOUTH)
        add(body, BorderLayout.CENTER)

        searchField.addActionListener { query = searchField.text; offset = 0; load() }
        searchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) {}
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) {}
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {}
        })
        searchField.addCaretListener {
            if (searchField.text.isEmpty() && query.isNotEmpty()) { query = ""; offset = 0; load() }
        }
        rebuildKindRow()
        load()
    }

    override fun refresh() = Unit

    private fun buildHeader(): JPanel {
        val header = ClearPanel(BorderLayout())
        header.border = BorderFactory.createEmptyBorder(
            UiScale.px(Spacing.lg), UiScale.px(Spacing.xl), UiScale.px(Spacing.md), UiScale.px(Spacing.xl)
        )
        val titles = ClearPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(heading("Discover", 20f).apply { alignmentX = 0f })
            add(label("Mods, modpacks, worlds and shaders that install in one click", Fonts.label(12f), AerixColors.textMuted).apply { alignmentX = 0f })
        }
        header.add(titles, BorderLayout.WEST)

        val right = ClearPanel(FlowLayout(FlowLayout.RIGHT, UiScale.px(8), 0))
        right.add(versionField)
        right.add(loaderField)
        right.add(searchField)
        header.add(right, BorderLayout.EAST)

        val rows = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        rows.add(header)
        val filters = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(14), 0))
        filters.border = BorderFactory.createEmptyBorder(0, UiScale.px(Spacing.xl), UiScale.px(Spacing.sm), UiScale.px(Spacing.xl))
        filters.add(kindRow)
        filters.add(sourceButtons)
        rows.add(filters)
        return rows
    }

    private fun rebuildKindRow() {
        kindRow.removeAll()
        Content.Kind.entries.forEach { k ->
            val tab = PillTab(k.label, k == kind).apply {
                addActionListener { kind = k; offset = 0; rebuildKindRow(); load() }
            }
            kindRow.add(tab)
        }
        sourceButtons.removeAll()
        listOf(Content.Source.MODRINTH to "Modrinth", Content.Source.CURSEFORGE to "CurseForge").forEach { (s, name) ->
            val tab = PillTab(name, s == source).apply {
                addActionListener { source = s; offset = 0; rebuildKindRow(); load() }
            }
            sourceButtons.add(tab)
        }
        kindRow.revalidate(); kindRow.repaint()
        sourceButtons.revalidate(); sourceButtons.repaint()
    }

    private fun load() {
        if (loading) return
        loading = true
        error = null
        showMessage(label("Loading ${kind.label.lowercase()}…", Fonts.body(12.5f), AerixColors.textMuted))
        val requestKind = kind
        val requestSource = source
        val requestQuery = query
        val requestVersion = gameVersion.trim().ifBlank { null }
        val requestLoader = loader.trim().ifBlank { null }
        val requestOffset = offset
        Scheduler.io("discover") {
            val outcome = runCatching {
                Content.search(requestQuery, requestKind, requestSource, requestVersion, requestLoader, offset = requestOffset)
            }

            Scheduler.ui {

                loading = false
                outcome.onSuccess { list ->
                    results = list
                    renderResults()
                }.onFailure { e ->
                    Log.w("discover", "search failed: ${e.message}")
                    error = e.message
                    // The exception text carries a URL and a stack-free reason; the player needs
                    // the reason and a way forward, not the host name.
                    val reason = e.message?.let { raw ->
                        when {
                            raw.contains("401") || raw.contains("403") -> "CurseForge needs an API key. Add one in Settings → Online."
                            raw.contains("429") -> "Too many requests just now. Wait a minute, then retry."
                            raw.contains("timeout", true) || raw.contains("reach", true) || raw.contains("handshake", true) ->
                                "No connection to the content service. Check your network or proxy in Settings."
                            else -> "The content service answered with an error. Try again in a moment."
                        }
                    } ?: "The content service could not be reached."
                    showMessage(EmptyState("warning", "Discover is offline", reason))
                }
            }
        }
    }

    private fun renderResults() {
        if (results.isEmpty()) {
            showMessage(EmptyState("search", "Nothing found", "Try a different search, or clear the version filter."))
        } else {
            showCards()
            results.forEach { project ->
                grid.add(ProjectCard(project, shell, iconCache) { install(project) })
            }
        }
        footer.removeAll()
        if (offset > 0) footer.add(pageButton("Previous") { offset = (offset - 30).coerceAtLeast(0); load() })
        if (results.size >= 30) footer.add(pageButton("Next page") { offset += 30; load() })
        footer.revalidate(); footer.repaint()
    }

    /** Replaces the scroll view with a single centred message. */
    private fun showMessage(component: JComponent) {
        val wrap = ClearPanel(java.awt.GridBagLayout())
        wrap.add(component, java.awt.GridBagConstraints())
        scroll.setViewportView(wrap)
        scroll.revalidate(); scroll.repaint()
    }

    /** A fresh grid: mutating the old one leaves the previous single-child row height behind. */
    private fun showCards() {
        grid = ClearPanel(GridLayout(0, 3, UiScale.px(14), UiScale.px(14)))
        scroll.setViewportView(grid)
        scroll.revalidate(); scroll.repaint()
    }

    private fun pageButton(text: String, action: () -> Unit) =
        AerixButton(text, ButtonStyle.GHOST).apply { compact = true; addActionListener { action() } }

    // ---- install --------------------------------------------------------------------------

    private fun install(project: Content.Project) {
        val instances = Instances.list()
        if (instances.isEmpty()) {
            shell.toast("Create an instance first — content installs into an instance")
            shell.navigate(Page.CREATE)
            return
        }
        val target = chooseInstance(instances, project.title) ?: return
        val dialog = ProgressDialog(shell, "Installing ${project.title}")
        dialog.isVisible = true
        val currentKind = kind
        Scheduler.io("content-install") {
            try {
                val (installed, message) = installProject(project, currentKind, target) { text, fraction ->
                    dialog.update(text, fraction)
                }
                Scheduler.ui {
                    dialog.dispose()
                    shell.toast(message)
                    shell.refreshPages()
                }
            } catch (e: Exception) {
                Log.e("discover", "install failed: ${e.message}", e)
                Scheduler.ui {
                    dialog.dispose()
                    JOptionPane.showMessageDialog(shell, "Could not install ${project.title}:\n\n${e.message}",
                        "Install failed", JOptionPane.ERROR_MESSAGE)
                }
            }
        }
    }

    /**
     * Whole-project install. Modpacks use the pack's own version and are refused when they target
     * a different Minecraft version than the instance (installing a 1.21 pack into a 1.12 instance
     * is a crash on the first launch, not a feature).
     */
    private fun installProject(
        project: Content.Project,
        kind: Content.Kind,
        target: Instance,
        progress: (String, Float) -> Unit
    ): Pair<Int, String> {
        progress("Resolving versions", 0.05f)
        val versions = Content.versions(project, target.mcVersion.ifBlank { null }, target.loader.ifBlank { null })
            .ifEmpty { Content.versions(project) }
        val version = versions.firstOrNull() ?: throw IllegalStateException("This project has no downloadable file for ${target.subtitle()}")
        val url = version.downloadUrl ?: throw IllegalStateException("The project does not publish a direct download")
        val fileName = version.fileName ?: "${project.slug.ifBlank { project.id }}-${version.versionNumber}.jar"

        if (project.source == Content.Source.MODRINTH && project.title.isNotBlank() &&
            fileName.endsWith(".mrpack")) {
            progress("Downloading the modpack", 0.15f)
            val dir = net.kdt.aerix.desktop.app.AppEnv.cacheDir.resolve("modpacks").also { java.nio.file.Files.createDirectories(it) }
            val archive = dir.resolve(ContentInstaller.sanitiseFile(fileName))
            java.nio.file.Files.write(archive, net.kdt.aerix.desktop.core.net.Http.download(url))
            progress("Installing pack files", 0.35f)
            val result = ContentInstaller.installPack(target, archive) { p ->
                progress(p.phase, 0.35f + p.fraction * 0.6f)
            }
            progress("Done", 1f)
            val skipped = if (result.skipped.isEmpty()) "" else " (${result.skipped.size} skipped)"
            return result.installedFiles to "Installed ${result.installedFiles} files into ${target.name}$skipped"
        }

        progress("Downloading $fileName", 0.3f)
        if (kind == Content.Kind.MODPACK) {
            return installPackFromArchive(project, target, url, fileName, progress)
        }
        val folder = kind
        progress("Downloading $fileName", 0.5f)
        val path = ContentInstaller.installFile(target, folder, url, fileName, version.sha1) { p ->
            progress(p.phase, 0.5f + p.fraction * 0.5f)
        }
        // Required dependencies (Fabric API and friends) are not optional: a mod without them
        // crashes on launch, so install them too and say how many came along.
        val dependencyNote = if (project.source == Content.Source.MODRINTH) {
            installRequiredDependencies(version, target, progress)
        } else ""
        progress("Installed", 1f)
        return 1 to "Installed ${path.fileName} into ${target.name} → ${folder.folder}$dependencyNote"
    }

    /**
     * Installs every `required` Modrinth dependency of [version] that the instance does not already
     * have. Each one is resolved to its own best matching version, and the list is walked breadth
     * first with a visited set so a dependency cycle cannot loop forever.
     */
    private fun installRequiredDependencies(
        version: Content.ProjectVersion,
        target: Instance,
        progress: (String, Float) -> Unit
    ): String {
        val queue = ArrayDeque(version.dependencies.filter { it.kind.equals("required", true) })
        val visited = hashSetOf(version.projectId)
        var installed = 0
        val failures = ArrayList<String>()
        while (queue.isNotEmpty() && installed < 12) {
            val dependency = queue.removeFirst()
            if (!visited.add(dependency.projectId)) continue
            progress("Resolving dependencies", 0.75f)
            val picked = dependency.versionId?.let { id -> Content.modrinthVersion(id) }
                ?: Content.modrinthProject(dependency.projectId)?.let { dep ->
                    runCatching { Content.versions(dep, target.mcVersion.ifBlank { null }, target.loader.ifBlank { null }) }
                        .getOrDefault(emptyList()).firstOrNull()
                }
            if (picked == null) { failures.add(dependency.projectId); continue }
            val url = picked.downloadUrl ?: continue
            val fileName = picked.fileName ?: "${dependency.projectId}-${picked.versionNumber}.jar"
            val kind = Content.modrinthProject(dependency.projectId)?.let { dep ->
                when (dep.projectType) {
                    "resourcepack" -> Content.Kind.RESOURCE_PACK
                    "shader" -> Content.Kind.SHADER
                    "datapack" -> Content.Kind.WORLD
                    else -> Content.Kind.MOD
                }
            } ?: Content.Kind.MOD
            if (!hasInstalled(target, kind, fileName)) {
                runCatching {
                    ContentInstaller.installFile(target, kind, url, fileName, picked.sha1) { p ->
                        progress("Dependency ${p.phase}", 0.78f + p.fraction * 0.15f)
                    }
                    installed++
                }.onFailure { failures.add("${picked.name}: ${it.message}") }
            }
            queue.addAll(picked.dependencies.filter { it.kind.equals("required", true) })
        }
        return when {
            installed > 0 && failures.isEmpty() -> " (plus $installed ${if (installed == 1) "dependency" else "dependencies"})"
            failures.isNotEmpty() -> " — ${failures.size} dependency download(s) failed"
            else -> ""
        }
    }

    /** True when a file with this name is already in the instance's folder for [kind]. */
    private fun hasInstalled(target: Instance, kind: Content.Kind, fileName: String): Boolean =
        runCatching { Files.isRegularFile(ContentInstaller.folderFor(target, kind).resolve(ContentInstaller.sanitiseFile(fileName))) }
            .getOrDefault(false)

    private fun installPackFromArchive(
        project: Content.Project,
        target: Instance,
        url: String,
        fileName: String,
        progress: (String, Float) -> Unit
    ): Pair<Int, String> {
        progress("Downloading the modpack", 0.2f)
        val dir = net.kdt.aerix.desktop.app.AppEnv.cacheDir.resolve("modpacks").also { java.nio.file.Files.createDirectories(it) }
        val archive = dir.resolve(ContentInstaller.sanitiseFile(fileName))
        java.nio.file.Files.write(archive, net.kdt.aerix.desktop.core.net.Http.download(url))
        progress("Installing pack files", 0.4f)
        val result = ContentInstaller.installPack(target, archive) { p ->
            progress(p.phase, 0.4f + p.fraction * 0.55f)
        }
        progress("Done", 1f)
        return result.installedFiles to "Installed ${result.installedFiles} files into ${target.name}"
    }

    private fun chooseInstance(instances: List<Instance>, title: String): Instance? {
        val options = instances.map { "${it.name}  ·  ${it.subtitle()}" }.toTypedArray()
        val choice = JOptionPane.showInputDialog(
            this, "Install “$title” into which instance?", "Choose instance",
            JOptionPane.QUESTION_MESSAGE, null, options, options.firstOrNull()
        ) as? String ?: return null
        return instances[options.indexOf(choice)]
    }
}

private class ProjectCard(
    private val project: Content.Project,
    private val shell: Shell,
    private val iconCache: HashMap<String, BufferedImage?>,
    private val onInstall: () -> Unit
) : JComponent() {
    private var hover = false
    private var icon: BufferedImage? = null
    private var iconRequested = false

    init {
        preferredSize = Dimension(UiScale.px(280), UiScale.px(230))
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: MouseEvent) { hover = false; repaint() }
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) showMenu(e) else showDetails()
            }
        })
        toolTipText = project.description
    }

    private fun showMenu(e: MouseEvent) {
        val menu = javax.swing.JPopupMenu()
        val open = javax.swing.JMenuItem("View details")
        open.addActionListener { showDetails() }
        val install = javax.swing.JMenuItem("Install…")
        install.addActionListener { onInstall() }
        menu.add(open); menu.add(install)
        menu.show(this, e.x, e.y)
    }

    private fun showDetails() {
        JOptionPane.showMessageDialog(
            this,
            "${project.title}\n\n${project.description}\n\nDownloads: ${formatCount(project.downloads)}" +
                    (if (project.gameVersions.isNotEmpty()) "\nVersions: ${project.gameVersions.take(6).joinToString(", ")}" else "") +
                    (if (project.loaders.isNotEmpty()) "\nLoaders: ${project.loaders.take(6).joinToString(", ")}" else ""),
            "Project details", JOptionPane.INFORMATION_MESSAGE
        )
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val r = UiScale.px(Radii.card)
        g2.color = if (hover) AerixTheme.panelRaised() else AerixTheme.panel()
        g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), r * 2f, r * 2f))
        if (hover) {
            g2.color = withAlpha(AerixTheme.accent, 120)
            g2.stroke = java.awt.BasicStroke(UiScale.px(1.3f))
            g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, r * 2f, r * 2f))
        } else {
            g2.color = AerixColors.borderSoft
            g2.stroke = java.awt.BasicStroke(UiScale.px(1f))
            g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, r * 2f, r * 2f))
        }

        val pad = UiScale.px(14)
        val iconSize = UiScale.px(48)
        if (icon == null && !iconRequested) {
            iconRequested = true
            val url = project.iconUrl
            if (url != null) {
                iconCache[url]?.let { icon = it }
                if (icon == null) {
                    Scheduler.io("discover-icon") {
                        val img = runCatching {
                            val bytes = net.kdt.aerix.desktop.core.net.Http.download(url)
                            javax.imageio.ImageIO.read(bytes.inputStream())
                        }.getOrNull()
                        iconCache[url] = img
                        Scheduler.ui { icon = img; repaint() }
                    }
                }
            }
        }
        val img = icon
        if (img != null) {
            g2.clip = RoundRectangle2D.Float(pad.toFloat(), pad.toFloat(), iconSize.toFloat(), iconSize.toFloat(), UiScale.px(12) * 2f, UiScale.px(12) * 2f)
            g2.drawImage(img, pad, pad, iconSize, iconSize, null)
            g2.clip = null
        } else {
            g2.color = withAlpha(AerixTheme.accent, 26)
            g2.fill(RoundRectangle2D.Float(pad.toFloat(), pad.toFloat(), iconSize.toFloat(), iconSize.toFloat(), UiScale.px(12) * 2f, UiScale.px(12) * 2f))
            net.kdt.aerix.desktop.ui.icons.Icons.draw(g2, "modpacks", pad + UiScale.px(12), pad + UiScale.px(12), UiScale.px(24), AerixTheme.accent)
        }

        val textX = pad + iconSize + UiScale.px(12)
        g2.font = Fonts.titleMedium(13.5f)
        g2.color = AerixColors.textPrimary
        g2.drawString(fit(project.title, g2.fontMetrics, width - textX - pad), textX, pad + UiScale.px(18))
        g2.font = Fonts.label(10.5f)
        g2.color = project.source.let { if (it == Content.Source.MODRINTH) AerixColors.modrinthBrand else AerixColors.curseForgeBrand }
        g2.drawString(if (project.source == Content.Source.MODRINTH) "MODRINTH" else "CURSEFORGE", textX, pad + UiScale.px(34))

        // description, two lines
        g2.font = Fonts.body(11.5f)
        g2.color = AerixColors.textSecondary
        var y = pad + iconSize + UiScale.px(24)
        for (line in wrap(project.description, g2.fontMetrics, width - pad * 2, 2)) {
            g2.drawString(line, pad, y)
            y += g2.fontMetrics.height
        }

        // stats row
        val statY = height - pad - UiScale.px(28)
        net.kdt.aerix.desktop.ui.icons.Icons.draw(g2, "download", pad, statY + UiScale.px(2), UiScale.px(13), AerixColors.textMuted)
        g2.font = Fonts.label(10.5f)
        g2.color = AerixColors.textMuted
        g2.drawString(formatCount(project.downloads), pad + UiScale.px(18), statY + UiScale.px(13))
        if (project.gameVersions.isNotEmpty()) {
            g2.drawString("·", pad + UiScale.px(70), statY + UiScale.px(13))
            g2.drawString(project.gameVersions.first().take(10), pad + UiScale.px(82), statY + UiScale.px(13))
        }

        // install button
        val btnW = UiScale.px(76)
        val btnH = UiScale.px(28)
        val bx = width - pad - btnW
        val by = height - pad - btnH
        Paint.pill(g2, bx, by, btnW, btnH, if (hover) AerixTheme.accent else withAlpha(AerixColors.textPrimary, 24))
        g2.font = Fonts.labelStrong(10.5f)
        g2.color = if (hover) AerixTheme.onAccent else AerixColors.textPrimary
        g2.drawString("INSTALL", bx + UiScale.px(14), by + UiScale.px(18))
        g2.dispose()
    }

    private fun fit(text: String, fm: java.awt.FontMetrics, maxWidth: Int): String {
        if (fm.stringWidth(text) <= maxWidth) return text
        var t = text
        while (t.length > 3 && fm.stringWidth(t + "…") > maxWidth) t = t.dropLast(1)
        return t + "…"
    }

    private fun wrap(text: String, fm: java.awt.FontMetrics, maxWidth: Int, maxLines: Int): List<String> {
        val words = text.split(" ")
        val lines = ArrayList<String>()
        var current = StringBuilder()
        for (w in words) {
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (fm.stringWidth(candidate) <= maxWidth) current = StringBuilder(candidate)
            else {
                lines.add(current.toString())
                if (lines.size >= maxLines) return lines
                current = StringBuilder(w)
            }
        }
        if (current.isNotEmpty() && lines.size < maxLines) lines.add(current.toString())
        return lines
    }
}

fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
    else -> n.toString()
}
