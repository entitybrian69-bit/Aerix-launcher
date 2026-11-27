package net.kdt.aerix.desktop.ui.screens

import net.kdt.aerix.desktop.app.AppEnv
import net.kdt.aerix.desktop.ui.components.AerixButton
import net.kdt.aerix.desktop.ui.components.ButtonStyle
import net.kdt.aerix.desktop.ui.components.ClearPanel
import net.kdt.aerix.desktop.ui.components.SectionCard
import net.kdt.aerix.desktop.ui.components.aerixScroll
import net.kdt.aerix.desktop.ui.components.heading
import net.kdt.aerix.desktop.ui.components.label
import net.kdt.aerix.desktop.ui.components.paragraph
import net.kdt.aerix.desktop.ui.components.vspace
import net.kdt.aerix.desktop.ui.shell.Page
import net.kdt.aerix.desktop.ui.shell.Shell
import net.kdt.aerix.desktop.ui.theme.AerixColors
import net.kdt.aerix.desktop.ui.theme.AerixTheme
import net.kdt.aerix.desktop.ui.theme.Fonts
import net.kdt.aerix.desktop.ui.theme.Paint
import net.kdt.aerix.desktop.ui.theme.Spacing
import net.kdt.aerix.desktop.ui.theme.UiScale
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * About: what the launcher is, which build is running, and the credits the GPL requires.
 *
 * The Android screen shows the app label, version and package with a credits block; this keeps the
 * same information plus the licence notice and links to the upstream projects, because a desktop
 * user can actually open them.
 */
class AboutScreen(private val shell: Shell) : JPanel(BorderLayout()) {
    init {
        isOpaque = false
        add(buildHeader(), BorderLayout.NORTH)
        val body = ClearPanel(BorderLayout())
        body.border = BorderFactory.createEmptyBorder(0, UiScale.px(Spacing.xl), UiScale.px(Spacing.xl), UiScale.px(Spacing.xl))
        val host = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        host.add(HeroCard())
        host.add(vspace(14))

        val build = SectionCard("This build", "What you are running right now")
        build.body.add(kv("Launcher", "${AppEnv.APP_NAME} ${AppEnv.version} (${AppEnv.buildChannel})"))
        build.body.add(kv("Application id", "net.kdt.aerix.desktop"))
        build.body.add(kv("Java", "${System.getProperty("java.version")} · ${System.getProperty("java.vendor")}"))
        build.body.add(kv("System", "${System.getProperty("os.name")} ${System.getProperty("os.version")} · ${System.getProperty("os.arch")}"))
        build.body.add(kv("Memory", "${AppEnv.totalMemoryMb} MB physical · ${AppEnv.freeMemoryMb()} MB free"))
        build.body.add(kv("Data folder", AppEnv.appData.toString()))
        host.add(build)
        host.add(vspace(14))

        val credits = SectionCard("Credits and licence", "Aerix Launcher is free software under GPL-3.0")
        // paragraph(), not label(): a JLabel treats "\n" as one long line, so this text used to
        // run off the card and widen the whole page to 2879 pixels.
        credits.body.add(paragraph(
            "Aerix Launcher is a fork of PojavLauncher (GPL-3.0) and keeps its licence, notices and " +
                    "attribution. It bundles no third-party launcher code beyond the Android fork it came " +
                    "from; the desktop UI, its settings and its launch pipeline are a reimplementation for " +
                    "Windows, sharing the game-installation concepts and the public APIs (Mojang, Modrinth, " +
                    "CurseForge, Fabric, Quilt, Forge, NeoForge, Adoptium).",
            width = 760, font = Fonts.body(12.5f), color = AerixColors.textSecondary))
        credits.body.add(vspace(10))
        val links = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(8), 0))
        links.add(AerixButton("Source on GitHub", ButtonStyle.SECONDARY, "github").apply {
            compact = true
            addActionListener { link("https://github.com/luciferstudios/Aerix-launcherPc") }
        })
        links.add(AerixButton("GPL-3.0", ButtonStyle.GHOST, "copyright").apply {
            compact = true
            addActionListener { link("https://www.gnu.org/licenses/gpl-3.0.html") }
        })
        links.add(AerixButton("PojavLauncher", ButtonStyle.GHOST, "link").apply {
            compact = true
            addActionListener { link("https://github.com/PojavLauncherTeam/PojavLauncher") }
        })
        credits.body.add(links)
        host.add(credits)
        host.add(vspace(14))

        val more = SectionCard("Also in the launcher", "Places worth knowing about")
        val row = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(8), 0))
        row.add(AerixButton("Team", ButtonStyle.GHOST, "person").apply { compact = true; addActionListener { shell.navigate(Page.CONTRIBUTION) } })
        row.add(AerixButton("Console", ButtonStyle.GHOST, "logs").apply { compact = true; addActionListener { shell.navigate(Page.LOGS) } })
        row.add(AerixButton("Files", ButtonStyle.GHOST, "files").apply { compact = true; addActionListener { shell.navigate(Page.FILES) } })
        row.add(AerixButton("Settings", ButtonStyle.GHOST, "settings").apply { compact = true; addActionListener { shell.navigate(Page.SETTINGS) } })
        more.body.add(row)
        host.add(more)

        val scroll = aerixScroll(host)
        scroll.border = BorderFactory.createEmptyBorder()
        body.add(scroll, BorderLayout.CENTER)
        add(body, BorderLayout.CENTER)
    }

    private fun buildHeader(): JPanel {
        val header = ClearPanel(BorderLayout())
        header.border = BorderFactory.createEmptyBorder(
            UiScale.px(Spacing.lg), UiScale.px(Spacing.xl), UiScale.px(Spacing.md), UiScale.px(Spacing.xl)
        )
        header.add(heading("About", 20f), BorderLayout.WEST)
        return header
    }

    private fun kv(key: String, value: String): JComponent {
        val row = ClearPanel(BorderLayout())
        row.border = BorderFactory.createEmptyBorder(0, 0, UiScale.px(6), 0)
        row.add(label(key, Fonts.labelStrong(11.5f), AerixColors.textMuted), BorderLayout.WEST)
        val v = label(value, Fonts.body(12f), AerixColors.textPrimary)
        row.add(v, BorderLayout.CENTER)
        return row
    }

    private fun link(url: String) {
        runCatching {
            if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().browse(java.net.URI.create(url))
        }.onFailure { shell.toast("Could not open the browser") }
    }

    private inner class HeroCard : JComponent() {
        private var logo: BufferedImage? = null
        init { preferredSize = java.awt.Dimension(100, UiScale.px(150)) }
        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            Paint.quality(g2)
            Paint.glass(g2, 0, 0, width, height, UiScale.px(24), fill = AerixTheme.panel())
            if (logo == null) {
                logo = runCatching {
                    AboutScreen::class.java.getResourceAsStream("/aerix/brand/logo_round.png")?.use {
                        javax.imageio.ImageIO.read(it)
                    }
                }.getOrNull()
            }
            val size = UiScale.px(96)
            logo?.let { g2.drawImage(it, UiScale.px(26), (height - size) / 2, size, size, null) }
            val x = UiScale.px(26) + size + UiScale.px(24)
            g2.font = Fonts.display(30f)
            g2.color = AerixColors.textPrimary
            g2.drawString("Aerix Launcher", x, UiScale.px(66))
            g2.font = Fonts.label(12f)
            g2.color = AerixTheme.accent
            g2.drawString("Windows desktop · ${AppEnv.version}", x, UiScale.px(86))
            g2.font = Fonts.body(12f)
            g2.color = AerixColors.textSecondary
            g2.drawString("A Minecraft: Java Edition launcher, made to feel like the worlds it opens.", x, UiScale.px(108))
            g2.dispose()
        }
    }
}
