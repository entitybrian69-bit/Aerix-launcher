package net.kdt.aerix.desktop.ui.screens

import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Scheduler
import net.kdt.aerix.desktop.core.game.JavaRuntimes
import net.kdt.aerix.desktop.core.game.Mojang
import net.kdt.aerix.desktop.core.game.VersionSummary
import net.kdt.aerix.desktop.core.instance.Instances
import net.kdt.aerix.desktop.ui.components.AerixButton
import net.kdt.aerix.desktop.ui.components.AerixField
import net.kdt.aerix.desktop.ui.components.ButtonStyle
import net.kdt.aerix.desktop.ui.components.ClearPanel
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
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.JPanel

/**
 * Other releases: the whole Mojang catalogue with an install button per version.
 *
 * Android's OtherReleasesScreen lists snapshots and old builds; this does the same and adds a
 * one-click "download this version into the shared install root" action, which is what the
 * Releases page is for on desktop.
 */
class ReleasesScreen(private val shell: Shell) : JPanel(BorderLayout()), Refreshable {
    private var versions: List<VersionSummary> = emptyList()
    private var loading = false
    private var loadError: String? = null
    private var tab = 0
    private var filter = ""
    private val listHost = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val installed = HashSet<String>()

    init {
        isOpaque = false
        add(buildHeader(), BorderLayout.NORTH)
        val body = ClearPanel(BorderLayout())
        body.border = BorderFactory.createEmptyBorder(0, UiScale.px(Spacing.xl), UiScale.px(Spacing.xl), UiScale.px(Spacing.xl))
        val scroll = aerixScroll(listHost)
        scroll.border = BorderFactory.createEmptyBorder()
        body.add(scroll, BorderLayout.CENTER)
        add(body, BorderLayout.CENTER)
        load()
    }

    override fun refresh() = load()

    private fun buildHeader(): JPanel {
        val header = ClearPanel(BorderLayout())
        header.border = BorderFactory.createEmptyBorder(
            UiScale.px(Spacing.lg), UiScale.px(Spacing.xl), UiScale.px(Spacing.md), UiScale.px(Spacing.xl)
        )
        val titles = ClearPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(heading("Other releases", 20f).apply { alignmentX = 0f })
            add(label("Every Minecraft version, one click to install", Fonts.label(12f), AerixColors.textMuted).apply { alignmentX = 0f })
        }
        header.add(titles, BorderLayout.WEST)
        val right = ClearPanel(FlowLayout(FlowLayout.RIGHT, UiScale.px(6), 0))
        listOf("Releases", "Snapshots", "Old").forEachIndexed { i, name ->
            right.add(PillTab(name, i == tab).apply { addActionListener { tab = i; rebuild() } })
        }
        right.add(AerixField(placeholder = "Filter versions", icon = "search").apply {
            preferredSize = Dimension(UiScale.px(190), UiScale.px(34))
            addCaretListener { filter = text; rebuild() }
        })
        header.add(right, BorderLayout.EAST)
        return header
    }

    private fun load() {
        if (loading) return
        loading = true
        loadError = null
        listHost.removeAll()
        listHost.add(label("Loading the version list…", Fonts.body(12.5f), AerixColors.textMuted))
        listHost.revalidate(); listHost.repaint()
        Scheduler.io("releases") {
            val outcome = runCatching { Mojang.versionManifest(Instances.sharedDataDir()) }
            val local = runCatching { Mojang.localVersions(Instances.sharedDataDir()) }.getOrDefault(emptyList())
            Scheduler.ui {
                loading = false
                outcome.onSuccess { manifest -> versions = manifest.versions; loadError = null }
                    .onFailure {
                        Log.w("releases", "manifest failed: ${it.message}")
                        loadError = it.message
                    }
                installed.clear(); installed.addAll(local)
                rebuild()
            }
        }
    }

    private fun rebuild() {
        listHost.removeAll()
        listHost.border = BorderFactory.createEmptyBorder(0, 0, 0, UiScale.px(8))
        val type = when (tab) { 0 -> "release"; 1 -> "snapshot"; else -> "old" }
        val shown = versions.filter { v ->
            when (type) {
                "old" -> v.isOld
                else -> v.type == type
            } && (filter.isBlank() || v.id.contains(filter, true))
        }.take(300)
        if (shown.isEmpty()) {
            // Distinguish "the catalogue could not be reached" from "this tab is empty", because
            // the fix is different: a retry, versus switching tabs or clearing the filter.
            val message = when {
                loadError != null -> "Could not reach Mojang: $loadError"
                versions.isEmpty() && !loading -> "No version list yet. Check your connection and retry."
                filter.isNotBlank() -> "Nothing matches “$filter”."
                else -> "Nothing to show in this tab."
            }
            val empty = ClearPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                alignmentX = 0f
                add(label(message, Fonts.body(12.5f), AerixColors.textSecondary).apply { alignmentX = 0f })
                if (loadError != null || versions.isEmpty()) {
                    add(Box.createVerticalStrut(UiScale.px(8)))
                    add(AerixButton("Try again", ButtonStyle.GHOST, "refresh").apply {
                        compact = true
                        alignmentX = 0f
                        addActionListener { load() }
                    })
                }
            }
            listHost.add(empty)
        }
        shown.forEach { v ->
            listHost.add(ReleaseRow(v, installed.contains(v.id)) { install(v) })
            listHost.add(Box.createVerticalStrut(UiScale.px(4)))
        }
        listHost.revalidate(); listHost.repaint()
    }

    private fun install(v: VersionSummary) {
        val java = JavaRuntimes.estimateJavaFor(v.id)
        val ok = JOptionPane.showConfirmDialog(this,
            "Download Minecraft ${v.id} into the shared install root?\n\nThis needs Java $java and about 300 MB.\n" +
                    "The version becomes available to every instance.",
            "Install ${v.id}", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE)
        if (ok != JOptionPane.OK_OPTION) return
        val dialog = ProgressDialog(shell, "Installing ${v.id}")
        dialog.isVisible = true
        Scheduler.io("release-${v.id}") {
            try {
                net.kdt.aerix.desktop.core.game.Installer.install(Instances.sharedDataDir(), v.id, v.url) { p ->
                    dialog.update(p.phase, p.fraction, p.detail)
                }
                Scheduler.ui {
                    dialog.dispose()
                    shell.toast("${v.id} installed")
                    installed.add(v.id)
                    rebuild()
                    shell.navigate(Page.CREATE)
                }
            } catch (e: Exception) {
                Scheduler.ui {
                    dialog.dispose()
                    JOptionPane.showMessageDialog(this, "Install failed:\n\n${e.message}", "Install", JOptionPane.ERROR_MESSAGE)
                }
            }
        }
    }
}

private class ReleaseRow(private val version: VersionSummary, private val isInstalled: Boolean, private val onClick: () -> Unit) : JComponent() {
    private var hover = false
    init {
        preferredSize = Dimension(100, UiScale.px(52))
        maximumSize = Dimension(Int.MAX_VALUE, UiScale.px(52))
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
        g2.color = if (hover) AerixTheme.panelRaised() else AerixTheme.panel()
        g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), UiScale.px(11) * 2f, UiScale.px(11) * 2f))
        val dot = when {
            version.isRelease -> AerixColors.success
            version.isSnapshot -> AerixColors.warning
            else -> AerixColors.textMuted
        }
        Paint.circle(g2, UiScale.px(18), height / 2, UiScale.px(4), dot)
        g2.font = Fonts.titleMedium(13f)
        g2.color = AerixColors.textPrimary
        g2.drawString(version.id + (if (isInstalled) "  ·  installed" else ""), UiScale.px(32), height / 2 - 2)
        g2.font = Fonts.label(10.5f)
        g2.color = AerixColors.textMuted
        val date = version.releaseTime.take(10)
        g2.drawString("${version.type.replace('_', ' ')}  ·  $date", UiScale.px(32), height / 2 + UiScale.px(14))
        // install affordance
        val w = UiScale.px(84)
        Paint.pill(g2, width - w - UiScale.px(10), (height - UiScale.px(26)) / 2, w, UiScale.px(26),
            if (hover) withAlpha(AerixTheme.accent, 210) else withAlpha(AerixColors.textPrimary, 22))
        g2.font = Fonts.labelStrong(10f)
        g2.color = if (hover) AerixTheme.onAccent else AerixColors.textPrimary
        val text = if (isInstalled) "REINSTALL" else "DOWNLOAD"
        val fm = g2.fontMetrics
        g2.drawString(text, width - w - UiScale.px(10) + (w - fm.stringWidth(text)) / 2, (height - fm.height) / 2 + fm.ascent)
        g2.dispose()
    }
}
