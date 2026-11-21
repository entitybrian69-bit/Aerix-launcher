package net.kdt.aerix.desktop.ui.screens

import net.kdt.aerix.desktop.ui.components.ClearPanel
import net.kdt.aerix.desktop.ui.components.SectionCard
import net.kdt.aerix.desktop.ui.components.aerixScroll
import net.kdt.aerix.desktop.ui.components.heading
import net.kdt.aerix.desktop.ui.components.label
import net.kdt.aerix.desktop.ui.components.vspace
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
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The team page, with the same members, colours and duties as `AerixContribution`.
 *
 * Android's version opens a per-member page; a desktop window has room to show the owner large and
 * the co-owners beneath, with every duty visible without another click — which is the point of the
 * page in the first place.
 */
class ContributionScreen(private val shell: Shell) : JPanel(BorderLayout()) {
    class Member(val id: String, val name: String, val role: String, val title: String, val color: Color, val logo: String, val duties: List<String>)

    private val members = listOf(
        Member("entitybrian", "Entitybrian", "Owner", "Developer / Programmer", Color(0xE2374E), "owner", listOf(
            "Writes the launcher: the Aerix shell, its settings and its launcher screens.",
            "Programs the renderer backends and the plugin support that loads them.",
            "Builds, signs and ships every release.",
            "Answers the bug reports and fixes what they turn up."
        )),
        Member("zerx-mc", "Zerx-mc", "Co-owner", "Runtime and renderer manager", Color(0x63C7FF), "zerx", listOf(
            "Keeps the renderers working: Proton, Emesis and the bundled GL backends.",
            "Manages the Java runtimes the launcher installs and picks.",
            "Tests renderer and runtime builds on real devices before they ship.",
            "Tracks the upstream renderer projects this launcher bundles."
        )),
        Member("fire-playz", "Fire playz", "Co-owner", "Social and online manager", Color(0x63C7FF), "fire", listOf(
            "Runs the launcher's social presence and announcements.",
            "Handles the community: questions, reports and feedback.",
            "Keeps the download pages and online listings up to date.",
            "Passes what the community asks for on to the developers."
        ))
    )

    init {
        isOpaque = false
        add(buildHeader(), BorderLayout.NORTH)
        val body = ClearPanel(BorderLayout())
        body.border = BorderFactory.createEmptyBorder(0, UiScale.px(Spacing.xl), UiScale.px(Spacing.xl), UiScale.px(Spacing.xl))
        val host = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val owner = members.first()
        host.add(MemberHero(owner))
        host.add(vspace(16))
        val row = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(14), 0))
        members.drop(1).forEach { row.add(MemberCard(it)) }
        host.add(row)
        host.add(vspace(16))
        val note = SectionCard("Open source", "Aerix Launcher is built on the shoulders of these projects")
        note.body.add(net.kdt.aerix.desktop.ui.components.paragraph(
            "PojavLauncher — the Android Java launcher this is a fork of (GPL-3.0)\n" +
                "Zalith Launcher 2 / mirai-launcher — the settings and library layouts the UI follows\n" +
                "Modrinth and CurseForge — the content catalogues\n" +
                "Eclipse Temurin — the Java runtimes the launcher downloads",
            width = 560))
        host.add(note)
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
        val titles = ClearPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(heading("Contribution", 20f).apply { alignmentX = 0f })
            add(label("The people who make Aerix", Fonts.label(12f), AerixColors.textMuted).apply { alignmentX = 0f })
        }
        header.add(titles, BorderLayout.WEST)
        return header
    }

    private inner class MemberHero(private val member: Member) : JComponent() {
        init { preferredSize = Dimension(100, UiScale.px(200)) }
        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            Paint.quality(g2)
            Paint.glass(g2, 0, 0, width, height, UiScale.px(24), fill = AerixTheme.panel())
            val logoSize = UiScale.px(112)
            val logo = TeamLogo.load(member.logo, logoSize)
            g2.clip = Ellipse2D.Float(UiScale.px(28).toFloat(), ((height - logoSize) / 2).toFloat(), logoSize.toFloat(), logoSize.toFloat())
            g2.drawImage(logo, UiScale.px(28), (height - logoSize) / 2, null)
            g2.clip = null
            g2.color = member.color
            g2.stroke = java.awt.BasicStroke(UiScale.px(2.4f))
            g2.draw(Ellipse2D.Float(UiScale.px(28).toFloat(), ((height - logoSize) / 2).toFloat(), logoSize.toFloat(), logoSize.toFloat()))
            val textX = UiScale.px(28) + logoSize + UiScale.px(26)
            g2.font = Fonts.display(28f)
            g2.color = AerixColors.textPrimary
            g2.drawString(member.name, textX, UiScale.px(56))
            g2.font = Fonts.labelStrong(11f)
            g2.color = member.color
            g2.drawString(member.role.uppercase() + "   ·   " + member.title, textX, UiScale.px(76))
            g2.font = Fonts.body(12.5f)
            g2.color = AerixColors.textSecondary
            member.duties.take(3).forEachIndexed { i, duty ->
                g2.drawString("• " + duty.take(90), textX, UiScale.px(104) + i * UiScale.px(20))
            }
            g2.dispose()
        }
    }

    private inner class MemberCard(private val member: Member) : JComponent() {
        init { preferredSize = Dimension(UiScale.px(330), UiScale.px(248)) }
        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            Paint.quality(g2)
            Paint.glass(g2, 0, 0, width, height, UiScale.px(20), fill = AerixTheme.panel())
            val logoSize = UiScale.px(56)
            val logo = TeamLogo.load(member.logo, logoSize)
            g2.clip = Ellipse2D.Float(UiScale.px(18).toFloat(), UiScale.px(18).toFloat(), logoSize.toFloat(), logoSize.toFloat())
            g2.drawImage(logo, UiScale.px(18), UiScale.px(18), null)
            g2.clip = null
            g2.color = member.color
            g2.stroke = java.awt.BasicStroke(UiScale.px(2f))
            g2.draw(Ellipse2D.Float(UiScale.px(18).toFloat(), UiScale.px(18).toFloat(), logoSize.toFloat(), logoSize.toFloat()))
            g2.font = Fonts.titleMedium(15f)
            g2.color = AerixColors.textPrimary
            g2.drawString(member.name, UiScale.px(86), UiScale.px(42))
            g2.font = Fonts.labelStrong(10.5f)
            g2.color = member.color
            g2.drawString(member.role.uppercase(), UiScale.px(86), UiScale.px(60))
            g2.font = Fonts.label(11f)
            g2.color = AerixColors.textMuted
            g2.drawString(member.title, UiScale.px(18), UiScale.px(98))
            g2.font = Fonts.body(11.5f)
            g2.color = AerixColors.textSecondary
            // duties wrap onto a second line when they are long, instead of being clipped away
            var y = UiScale.px(120)
            for (duty in member.duties) {
                for (line in wrap("• $duty", g2.fontMetrics, width - UiScale.px(36))) {
                    if (y < height - UiScale.px(8)) g2.drawString(line, UiScale.px(18), y)
                    y += UiScale.px(17)
                }
            }
            g2.dispose()
        }
        private fun wrap(text: String, fm: java.awt.FontMetrics, maxWidth: Int): List<String> {
            if (fm.stringWidth(text) <= maxWidth) return listOf(text)
            val words = text.split(' ')
            val lines = ArrayList<String>()
            var line = ""
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (fm.stringWidth(candidate) <= maxWidth) line = candidate
                else {
                    if (line.isNotEmpty()) lines.add(line)
                    line = word
                }
            }
            if (line.isNotEmpty()) lines.add(line)
            return lines
        }
    }
}

private object TeamLogo {
    private val cache = HashMap<String, BufferedImage>()
    fun load(name: String, size: Int): BufferedImage {
        cache["$name:$size"]?.let { return it }
        val img = runCatching {
            TeamLogo::class.java.getResourceAsStream("/aerix/brand/team/$name.png")?.use {
                javax.imageio.ImageIO.read(it)
            }
        }.getOrNull()
        val scaled = if (img != null) {
            val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            Paint.quality(g)
            g.drawImage(img, 0, 0, size, size, null)
            g.dispose()
            out
        } else {
            val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            Paint.quality(g)
            g.color = Color(0x1F3A4C)
            g.fillOval(0, 0, size, size)
            g.dispose()
            out
        }
        return scaled
    }
}
