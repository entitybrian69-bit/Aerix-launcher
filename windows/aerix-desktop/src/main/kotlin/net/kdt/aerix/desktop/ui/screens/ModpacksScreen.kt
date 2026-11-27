package net.kdt.aerix.desktop.ui.screens

import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Scheduler
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.content.Content
import net.kdt.aerix.desktop.core.content.ContentInstaller
import net.kdt.aerix.desktop.core.instance.Instance
import net.kdt.aerix.desktop.core.instance.Instances
import net.kdt.aerix.desktop.ui.components.AerixButton
import net.kdt.aerix.desktop.ui.components.ButtonStyle
import net.kdt.aerix.desktop.ui.components.ClearPanel
import net.kdt.aerix.desktop.ui.components.EmptyState
import net.kdt.aerix.desktop.ui.components.PillTab
import net.kdt.aerix.desktop.ui.components.SectionCard
import net.kdt.aerix.desktop.ui.components.aerixScroll
import net.kdt.aerix.desktop.ui.components.heading
import net.kdt.aerix.desktop.ui.components.label
import net.kdt.aerix.desktop.ui.components.vspace
import net.kdt.aerix.desktop.ui.shell.Page
import net.kdt.aerix.desktop.ui.shell.Refreshable
import net.kdt.aerix.desktop.ui.shell.Shell
import net.kdt.aerix.desktop.ui.theme.AerixColors
import net.kdt.aerix.desktop.ui.theme.Fonts
import net.kdt.aerix.desktop.ui.theme.Spacing
import net.kdt.aerix.desktop.ui.theme.UiScale
import net.kdt.aerix.desktop.ui.theme.withAlpha
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
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
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Modpacks: import a `.mrpack` or CurseForge `.zip` into a new instance, or export an existing
 * instance's content as a shareable archive. Drag and drop is handled by the window (see
 * [Shell]'s drop target), and this page explains and performs the same operation from a button.
 */
class ModpacksScreen(private val shell: Shell) : JPanel(BorderLayout()), Refreshable {
    private val importList = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val exportHost = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val bodyHost = ClearPanel(BorderLayout())
    private var tab = 0

    init {
        isOpaque = false
        add(buildHeader(), BorderLayout.NORTH)
        val body = ClearPanel(BorderLayout())
        body.border = BorderFactory.createEmptyBorder(0, UiScale.px(Spacing.xl), UiScale.px(Spacing.xl), UiScale.px(Spacing.xl))
        val scroll = aerixScroll(bodyHost)
        scroll.border = BorderFactory.createEmptyBorder()
        body.add(scroll, BorderLayout.CENTER)
        add(body, BorderLayout.CENTER)
        rebuild()
    }

    override fun refresh() = rebuild()

    private fun buildHeader(): JPanel {
        val header = ClearPanel(BorderLayout())
        header.border = BorderFactory.createEmptyBorder(
            UiScale.px(Spacing.lg), UiScale.px(Spacing.xl), UiScale.px(Spacing.md), UiScale.px(Spacing.xl)
        )
        val titles = ClearPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(heading("Modpacks", 20f).apply { alignmentX = 0f })
            add(label("Import a pack into a new instance, or export one to share", Fonts.label(12f), AerixColors.textMuted).apply { alignmentX = 0f })
        }
        header.add(titles, BorderLayout.WEST)
        val tabs = ClearPanel(FlowLayout(FlowLayout.RIGHT, UiScale.px(6), 0))
        tabs.add(PillTab("Import", tab == 0).apply { addActionListener { tab = 0; rebuild() } })
        tabs.add(PillTab("Export", tab == 1).apply { addActionListener { tab = 1; rebuild() } })
        header.add(tabs, BorderLayout.EAST)
        return header
    }

    private fun rebuild() {
        bodyHost.removeAll()
        val host = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        host.border = BorderFactory.createEmptyBorder(0, 0, 0, UiScale.px(8))
        if (tab == 0) buildImport(host) else buildExport(host)
        bodyHost.add(host, BorderLayout.CENTER)
        bodyHost.revalidate(); bodyHost.repaint()
    }

    private fun buildImport(host: JPanel) {
        val intro = SectionCard("Import a modpack", "Drop a file anywhere on the window, or pick one here")
        intro.body.add(label("Supported: Modrinth .mrpack, CurseForge .zip exports, and plain .zip packs with an overrides folder.",
            Fonts.body(12.5f), AerixColors.textSecondary))
        intro.body.add(vspace(10))
        val buttons = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(8), 0))
        buttons.add(AerixButton("Choose a pack file…", ButtonStyle.PRIMARY, "import").apply {
            addActionListener { choosePack() }
        })
        buttons.add(AerixButton("Browse Discover", ButtonStyle.SECONDARY, "discover").apply {
            addActionListener { shell.navigate(Page.DISCOVER) }
        })
        intro.body.add(buttons)
        host.add(intro)
        host.add(vspace(12))

        val list = SectionCard("Packs on this PC", "Files in your Downloads and Aerix packs folder")
        val candidates = findLocalPacks()
        if (candidates.isEmpty()) {
            list.body.add(label("No .mrpack or modpack .zip found in Downloads or ${packsDir()}.", Fonts.body(12.5f), AerixColors.textMuted))
        } else {
            candidates.forEach { pack ->
                list.body.add(LocalPackRow(pack) { installPack(pack) })
            }
        }
        host.add(list)
    }

    private fun buildExport(host: JPanel) {
        val instances = Instances.list()
        if (instances.isEmpty()) {
            host.add(EmptyState("package_2", "Nothing to export", "Create an instance with mods first."))
            return
        }
        val card = SectionCard("Export an instance", "Creates a Modrinth-format .mrpack with your mods and configs")
        instances.forEach { inst ->
            val row = ClearPanel(BorderLayout())
            row.border = BorderFactory.createEmptyBorder(0, 0, UiScale.px(6), 0)
            row.add(label(inst.name, Fonts.bodyStrong(12.5f)), BorderLayout.WEST)
            val actions = ClearPanel(FlowLayout(FlowLayout.RIGHT, UiScale.px(6), 0))
            actions.add(AerixButton("Export .mrpack", ButtonStyle.GHOST, "share").apply {
                compact = true
                addActionListener { export(inst) }
            })
            row.add(actions, BorderLayout.EAST)
            card.body.add(row)
        }
        host.add(card)
        host.add(vspace(12))
        val note = SectionCard("What goes in the pack", "So you know before you share it")
        note.body.add(label("• mods/ jars are listed by hash and are downloaded by Modrinth, not embedded\n" +
                "• config/, resourcepacks/ and shaderpacks/ are copied as overrides\n" +
                "• saves/, logs/ and account data are never included", Fonts.body(12f), AerixColors.textSecondary))
        host.add(note)
    }

    private fun choosePack() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose a modpack"
            fileFilter = FileNameExtensionFilter("Modpacks (.mrpack, .zip)", "mrpack", "zip")
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        installPack(chooser.selectedFile.toPath())
    }

    private fun installPack(archive: Path) {
        val name = JOptionPane.showInputDialog(this, "Name for the new instance", archive.fileName.toString().substringBeforeLast('.'))
        if (name.isNullOrBlank()) return
        val dialog = ProgressDialog(shell, "Importing modpack")
        dialog.isVisible = true
        Scheduler.io("pack-import") {
            try {
                // Read the pack's own Minecraft version so the instance is created correctly.
                var mcVersion = ""
                var loader = "vanilla"
                runCatching {
                    java.util.zip.ZipInputStream(Files.newInputStream(archive)).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (entry.name == "modrinth.index.json") {
                                val doc = net.kdt.aerix.desktop.core.json.Json.parseObject(String(zip.readBytes()))
                                val deps = doc.obj("dependencies")
                                mcVersion = deps?.str("minecraft") ?: ""
                                loader = when {
                                    deps?.has("fabric-loader") == true -> "fabric"
                                    deps?.has("quilt-loader") == true -> "quilt"
                                    deps?.has("forge") == true -> "forge"
                                    deps?.has("neoforge") == true -> "neoforge"
                                    else -> "vanilla"
                                }
                            }
                            entry = zip.nextEntry
                        }
                    }
                }
                if (mcVersion.isBlank()) mcVersion = Settings.string("discoverVersion", "")
                val inst = Instances.create(name.trim(), mcVersion, mcVersion, loader)
                val result = ContentInstaller.installPack(inst, archive) { p ->
                    dialog.update(p.phase, p.fraction)
                }
                Scheduler.ui {
                    dialog.dispose()
                    val extra = if (result.skipped.isEmpty()) "" else "\nSkipped ${result.skipped.size} file(s); see the console."
                    JOptionPane.showMessageDialog(this,
                        "Imported ${result.installedFiles} file(s) into “${inst.name}”.$extra\n\nPress Install in the library to download Minecraft $mcVersion, then Play.",
                        "Pack imported", JOptionPane.INFORMATION_MESSAGE)
                    shell.navigate(Page.LIBRARY)
                }
            } catch (e: Exception) {
                Log.e("modpacks", "import failed: ${e.message}", e)
                Scheduler.ui {
                    dialog.dispose()
                    JOptionPane.showMessageDialog(this, "Could not import the pack:\n\n${e.message}", "Import failed", JOptionPane.ERROR_MESSAGE)
                }
            }
        }
    }

    private fun export(inst: Instance) {
        val chooser = JFileChooser().apply {
            dialogTitle = "Save modpack"
            selectedFile = java.io.File("${Instances.sanitiseName(inst.name)}.mrpack")
        }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
        val target = chooser.selectedFile.toPath()
        Scheduler.io("pack-export") {
            try {
                ModpackExporter.export(inst, target)
                Scheduler.ui { shell.toast("Exported ${target.fileName}") }
            } catch (e: Exception) {
                Scheduler.ui { JOptionPane.showMessageDialog(this, "Export failed:\n\n${e.message}", "Export failed", JOptionPane.ERROR_MESSAGE) }
            }
        }
    }

    private fun packsDir(): Path = net.kdt.aerix.desktop.app.AppEnv.appData.resolve("modpacks")

    private fun findLocalPacks(): List<Path> {
        val dirs = listOfNotNull(
            net.kdt.aerix.desktop.app.AppEnv.home.resolve("Downloads"),
            packsDir()
        )
        return dirs.filter { Files.isDirectory(it) }.flatMap { dir ->
            runCatching {
                Files.list(dir).use { s ->
                    s.filter { p -> val n = p.fileName.toString().lowercase(); n.endsWith(".mrpack") || n.endsWith(".zip") }
                        .toList()
                }
            }.getOrDefault(emptyList())
        }.take(12)
    }
}

/** Builds a Modrinth-format `.mrpack` from an instance: index for mods, overrides for configs. */
object ModpackExporter {
    fun export(instance: Instance, target: Path) {
        val game = instance.gameDir
        val index = net.kdt.aerix.desktop.core.json.JsonObject()
            .put("formatVersion", 1)
            .put("game", "minecraft")
            .put("versionId", "1.0.0")
            .put("name", instance.name)
            .put("summary", "Exported from Aerix Launcher")
        val deps = net.kdt.aerix.desktop.core.json.JsonObject()
        if (instance.mcVersion.isNotBlank()) deps.put("minecraft", instance.mcVersion)
        if (instance.loader == "fabric") deps.put("fabric-loader", instance.loaderVersion ?: "0.16.0")
        if (instance.loader == "quilt") deps.put("quilt-loader", instance.loaderVersion ?: "0.24.0")
        if (instance.loader == "forge") deps.put("forge", instance.loaderVersion ?: "")
        if (instance.loader == "neoforge") deps.put("neoforge", instance.loaderVersion ?: "")
        index.put("dependencies", deps)

        val files = net.kdt.aerix.desktop.core.json.JsonArray()
        val mods = game.resolve("mods")
        if (Files.isDirectory(mods)) {
            Files.list(mods).use { s ->
                s.filter { it.fileName.toString().endsWith(".jar") }.forEach { jar ->
                    val rel = "mods/" + jar.fileName
                    val sha1 = runCatching { net.kdt.aerix.desktop.core.net.Downloader.sha1(jar) }.getOrNull() ?: return@forEach
                    val sha512 = runCatching { net.kdt.aerix.desktop.core.net.Downloader.sha512(jar) }.getOrNull()
                    val size = runCatching { Files.size(jar) }.getOrDefault(0L)
                    val hashes = net.kdt.aerix.desktop.core.json.JsonObject().put("sha1", sha1)
                    if (sha512 != null) hashes.put("sha512", sha512)
                    files.add(
                        net.kdt.aerix.desktop.core.json.JsonObject()
                            .put("path", rel)
                            // A local-only jar cannot be re-downloaded; the empty list tells importers
                            // to ask the player for it rather than pretend it will arrive.
                            .put("hashes", hashes)
                            .put("env", net.kdt.aerix.desktop.core.json.JsonObject().put("client", "required").put("server", "optional"))
                            .put("downloads", net.kdt.aerix.desktop.core.json.JsonArray())
                            .put("fileSize", size)
                    )
                }
            }
        }
        index.put("files", files)

        Files.createDirectories(target.parent)
        java.util.zip.ZipOutputStream(Files.newOutputStream(target)).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("modrinth.index.json"))
            zip.write(net.kdt.aerix.desktop.core.json.Json.pretty(index).toByteArray())
            zip.closeEntry()
            for (folder in listOf("config", "resourcepacks", "shaderpacks")) {
                val dir = game.resolve(folder)
                if (!Files.isDirectory(dir)) continue
                Files.walk(dir).use { walk ->
                    walk.filter { Files.isRegularFile(it) }.forEach { p ->
                        val rel = "overrides/" + folder + "/" + dir.relativize(p).toString().replace('\\', '/')
                        zip.putNextEntry(java.util.zip.ZipEntry(rel))
                        Files.copy(p, zip)
                        zip.closeEntry()
                    }
                }
            }
        }
    }
}

private class LocalPackRow(private val path: Path, private val onClick: () -> Unit) : JComponent() {
    private var hover = false
    init {
        preferredSize = Dimension(100, UiScale.px(48))
        maximumSize = Dimension(Int.MAX_VALUE, UiScale.px(48))
        cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: MouseEvent) { hover = false; repaint() }
            override fun mouseClicked(e: MouseEvent) = onClick()
        })
        toolTipText = path.toString()
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        net.kdt.aerix.desktop.ui.theme.Paint.quality(g2)
        if (hover) {
            g2.color = withAlpha(java.awt.Color.WHITE, 12)
            g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), UiScale.px(9) * 2f, UiScale.px(9) * 2f))
        }
        net.kdt.aerix.desktop.ui.icons.Icons.draw(g2, "zip", UiScale.px(6), (height - UiScale.px(18)) / 2, UiScale.px(18), net.kdt.aerix.desktop.ui.theme.AerixTheme.accent)
        g2.font = Fonts.bodyStrong(12.5f)
        g2.color = AerixColors.textPrimary
        g2.drawString(path.fileName.toString(), UiScale.px(32), height / 2 - 2)
        g2.font = Fonts.label(10.5f)
        g2.color = AerixColors.textMuted
        g2.drawString(humanSize(runCatching { Files.size(path) }.getOrDefault(0L)) + "  ·  " + path.parent, UiScale.px(32), height / 2 + UiScale.px(14))
        g2.dispose()
    }
}
