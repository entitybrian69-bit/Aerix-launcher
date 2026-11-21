package net.kdt.aerix.desktop.ui.screens

import net.kdt.aerix.desktop.app.Scheduler
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.auth.accounts.Account
import net.kdt.aerix.desktop.core.auth.accounts.Accounts
import net.kdt.aerix.desktop.core.game.GameLauncher
import net.kdt.aerix.desktop.core.instance.Instance
import net.kdt.aerix.desktop.core.instance.Instances
import net.kdt.aerix.desktop.ui.components.AerixButton
import net.kdt.aerix.desktop.ui.components.AerixProgress
import net.kdt.aerix.desktop.ui.components.ButtonStyle
import net.kdt.aerix.desktop.ui.components.ClearPanel
import net.kdt.aerix.desktop.ui.components.SkinFace
import net.kdt.aerix.desktop.ui.components.StatusDot
import net.kdt.aerix.desktop.ui.components.Ui
import net.kdt.aerix.desktop.ui.shell.Page
import net.kdt.aerix.desktop.ui.shell.Refreshable
import net.kdt.aerix.desktop.ui.shell.Shell
import net.kdt.aerix.desktop.ui.theme.AerixColors
import net.kdt.aerix.desktop.ui.theme.AerixTheme
import net.kdt.aerix.desktop.ui.theme.Backdrops
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
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/**
 * The Play dashboard. Same information architecture as `HomeScreen.kt`:
 *
 *   ┌──────────────────────────────────────────┬────────────────┐
 *   │ hero card (wallpaper, headline, profiles)│ account / skin │
 *   │                                          │  PLAY button   │
 *   ├──────────────────────────────────────────┴────────────────┤
 *   │ YOUR SPACE — instance cards with icons, versions, LOAD    │
 *   └───────────────────────────────────────────────────────────┘
 */
class HomeScreen(private val shell: Shell) : JPanel(BorderLayout()), Refreshable {
    private val content = ClearPanel(BorderLayout())
    private val scroll = javax.swing.JScrollPane(content)

    init {
        isOpaque = false
        scroll.isOpaque = false
        scroll.viewport.isOpaque = false
        scroll.border = javax.swing.BorderFactory.createEmptyBorder()
        scroll.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        scroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        scroll.verticalScrollBar.unitIncrement = 30
        scroll.verticalScrollBar.isOpaque = false
        add(scroll, BorderLayout.CENTER)
        Accounts.onChange { Scheduler.ui { refresh() } }
        Instances.onChange { Scheduler.ui { refresh() } }
        Settings.onChange { key -> if (key == "selected_wallpaper_id" || key == "backgroundOpacity") Scheduler.ui { refresh() } }
        refresh()
    }

    override fun refresh() {
        content.removeAll()
        val page = ClearPanel(BorderLayout())
        page.border = javax.swing.border.EmptyBorder(UiScale.px(Spacing.lg), UiScale.px(Spacing.xl), UiScale.px(Spacing.xxl), UiScale.px(Spacing.xxl))

        val header = ClearPanel(BorderLayout())
        val headline = ClearPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(Eyebrow("YOUR NEXT WORLD STARTS HERE"))
            add(Box.createVerticalStrut(UiScale.px(6)))
            val title = object : JComponent() {
                init { alignmentX = 0f; preferredSize = Dimension(UiScale.px(600), UiScale.px(52)) }
                override fun paintComponent(g: Graphics) {
                    val g2 = g.create() as Graphics2D
                    Paint.quality(g2)
                    g2.font = Fonts.display(38f)
                    g2.color = AerixColors.textPrimary
                    g2.drawString("Make room for wonder.", 0, UiScale.px(36))
                    g2.dispose()
                }
            }
            add(title)
            add(Box.createVerticalStrut(UiScale.px(4)))
            val subtitle = Label("Build a world, choose your style, and make it yours.", Fonts.body(14.5f), AerixColors.textSecondary)
            subtitle.alignmentX = 0f
            add(subtitle)
        }
        header.add(headline, BorderLayout.WEST)
        val quick = ClearPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, UiScale.px(8), 0))
        quick.add(pillButton("Create", "create") { shell.navigate(Page.CREATE) })
        quick.add(pillButton("Library", "library") { shell.navigate(Page.LIBRARY) })
        quick.add(pillButton("Discover", "discover") { shell.navigate(Page.DISCOVER) })
        header.add(quick, BorderLayout.EAST)
        page.add(header, BorderLayout.NORTH)

        val body = ClearPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        gbc.gridx = 0; gbc.gridy = 0
        gbc.insets = java.awt.Insets(0, 0, 0, UiScale.px(18))
        body.add(HeroCard(shell), gbc)
        gbc.gridx = 1
        gbc.weightx = 0.0
        gbc.insets = java.awt.Insets(0, 0, 0, 0)
        body.add(PlayerCard(shell), gbc)

        gbc.gridx = 0; gbc.gridy = 1
        gbc.gridwidth = 2
        gbc.weighty = 0.0
        gbc.insets = java.awt.Insets(UiScale.px(24), 0, 0, 0)
        body.add(spaceSection(), gbc)
        page.add(body, BorderLayout.CENTER)
        content.add(page, BorderLayout.CENTER)
        revalidate(); repaint()
    }

    private fun pillButton(text: String, icon: String, action: () -> Unit): AerixButton =
        AerixButton(text, ButtonStyle.SECONDARY, icon).apply {
            compact = true
            horizontalPadding = 16
            addActionListener { action() }
        }

    // -----------------------------------------------------------------------------------------

    private fun spaceSection(): JPanel {
        val section = ClearPanel(BorderLayout())
        val head = ClearPanel(BorderLayout())
        val title = Eyebrow("YOUR SPACE")
        head.add(title, BorderLayout.WEST)
        val right = ClearPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, UiScale.px(8), 0))
        right.add(pillButton("New instance", "create") { shell.navigate(Page.CREATE) })
        right.add(pillButton("Manage", "library") { shell.navigate(Page.LIBRARY) })
        head.add(right, BorderLayout.EAST)
        section.add(head, BorderLayout.NORTH)

        val cards = ClearPanel(GridLayout(0, 3, UiScale.px(16), UiScale.px(16)))
        cards.border = javax.swing.border.EmptyBorder(UiScale.px(14), 0, 0, 0)
        val recent = Instances.list().sortedByDescending { it.lastPlayed }.take(6)
        if (recent.isEmpty()) {
            cards.add(AddInstanceCard { shell.navigate(Page.CREATE) })
            repeat(2) { cards.add(EmptyHintCard()) }
        } else {
            recent.forEach { cards.add(InstanceCard(shell, it)) }
        }
        section.add(cards, BorderLayout.CENTER)
        return section
    }
}

// ---------------------------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------------------------

private class HeroCard(private val shell: Shell) : JComponent() {
    private val create = AerixButton("CREATE A WORLD", ButtonStyle.PRIMARY, "create")
    private val library = AerixButton("OPEN LIBRARY", ButtonStyle.GHOST, "library").apply { horizontalPadding = 14 }
    private var heroImage: BufferedImage? = null
    private var heroKey = ""

    init {
        layout = null
        create.addActionListener { shell.navigate(Page.CREATE) }
        library.addActionListener { shell.navigate(Page.LIBRARY) }
        add(create); add(library)
        preferredSize = Dimension(UiScale.px(700), UiScale.px(360))
        minimumSize = Dimension(UiScale.px(420), UiScale.px(320))
        addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) = layoutChildren()
        })
    }

    override fun doLayout() = layoutChildren()

    private fun layoutChildren() {
        val pad = UiScale.px(34)
        val btnH = create.preferredSize.height
        create.bounds = java.awt.Rectangle(pad, height - pad - btnH, create.preferredSize.width, btnH)
        val libW = library.preferredSize.width
        library.bounds = java.awt.Rectangle(pad + create.preferredSize.width + UiScale.px(18), height - pad - btnH, libW, btnH)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val r = UiScale.px(Radii.hero)
        val shape = RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), r * 2f, r * 2f)
        g2.clip = shape
        // hero wallpaper, independent of the window wallpaper
        val id = Settings.string("heroWallpaper", "wp_12_snowy_valley")
        val key = "$id:${width}x$height"
        if (key != heroKey) {
            val src = Backdrops.load(id) ?: Backdrops.load("wp_12_snowy_valley")
            heroImage = src?.let { Backdrops.cover(it, width.coerceAtLeast(1), height.coerceAtLeast(1)) }
            heroKey = key
        }
        val img = heroImage
        if (img != null) g2.drawImage(img, 0, 0, null) else {
            g2.color = Color(0x0E2233)
            g2.fillRect(0, 0, width, height)
        }
        // readability scrim, like the Android hero's gradient
        g2.paint = java.awt.GradientPaint(0f, 0f, Color(0, 10, 18, 215), 0f, height * 0.9f, Color(0, 10, 18, 90))
        g2.fillRect(0, 0, width, height)
        g2.paint = java.awt.GradientPaint(0f, 0f, Color(0, 10, 18, 120), width * 0.55f, 0f, Color(0, 10, 18, 0))
        g2.fillRect(0, 0, width, height)
        g2.clip = null

        val pad = UiScale.px(34)
        g2.font = Fonts.labelStrong(11f)
        Paint.circle(g2, pad + 4, pad + 6, 4, AerixTheme.accent)
        g2.color = AerixTheme.accent
        g2.drawString("Y O U R   N E X T   W O R L D   S T A R T S   H E R E", pad + 16, pad + 10)

        g2.font = Fonts.display(40f)
        g2.color = Color.WHITE
        g2.drawString("Make room", pad, pad + UiScale.px(64))
        g2.drawString("for wonder.", pad, pad + UiScale.px(112))
        g2.font = Fonts.body(15f)
        g2.color = Color(0xC8, 0xD1, 0xCF)
        g2.drawString("Build a world, choose your style, and make it yours.", pad, pad + UiScale.px(146))

        // profiles count pill
        val count = Instances.list().size
        val pillY = height - pad - UiScale.px(44) - UiScale.px(52)
        Paint.pill(g2, pad, pillY, UiScale.px(126), UiScale.px(36), withAlpha(Color.BLACK, 90), withAlpha(Color.WHITE, 40))
        net.kdt.aerix.desktop.ui.icons.Icons.draw(g2, "library", pad + UiScale.px(12), pillY + UiScale.px(9), UiScale.px(17), Color(0xE8F1F5))
        g2.font = Fonts.labelStrong(12.5f)
        g2.color = Color(0xE8F1F5)
        g2.drawString(if (count == 1) "1 profile" else "$count profiles", pad + UiScale.px(36), pillY + UiScale.px(23))
        g2.dispose()
    }
}

// ---------------------------------------------------------------------------------------------
// Player card
// ---------------------------------------------------------------------------------------------

private class PlayerCard(private val shell: Shell) : JComponent() {
    private val play = AerixButton("PLAY", ButtonStyle.PRIMARY, "play")
    private val addAccount = AerixButton("+ Add Account", ButtonStyle.SECONDARY)
    private var character: BufferedImage? = null
    private var characterKey = ""
    private var pillHover = false

    init {
        preferredSize = Dimension(UiScale.px(300), UiScale.px(360))
        minimumSize = Dimension(UiScale.px(280), UiScale.px(360))
        add(play); add(addAccount)
        play.addActionListener { playSelected() }
        addAccount.addActionListener { shell.navigate(Page.ACCOUNTS) }
        // The instance pill is painted (so it can hold two lines of text); these listeners make it
        // behave like the button it looks like — open the library to pick another instance.
        cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseMoved(e: java.awt.event.MouseEvent) {
                val inside = pillRect().contains(e.point)
                if (inside != pillHover) {
                    pillHover = inside
                    cursor = java.awt.Cursor.getPredefinedCursor(if (inside) java.awt.Cursor.HAND_CURSOR else java.awt.Cursor.DEFAULT_CURSOR)
                    repaint()
                }
            }
            override fun mouseExited(e: java.awt.event.MouseEvent) { pillHover = false; repaint() }
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (pillRect().contains(e.point)) shell.navigate(Page.LIBRARY)
            }
        })
        addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) = layoutChildren()
        })
    }

    private fun pillRect(): java.awt.Rectangle {
        val pad = UiScale.px(18)
        val playH = UiScale.px(52)
        val accH = UiScale.px(40)
        val selH = UiScale.px(58)
        val selY = height - pad - playH - accH - selH - UiScale.px(20)
        return java.awt.Rectangle(pad, selY, width - pad * 2, selH)
    }

    private fun selectedInstance(): Instance? {
        val selected = Settings.string("currentInstance", "")
        return Instances.find(selected) ?: Instances.list().maxByOrNull { it.lastPlayed } ?: Instances.list().firstOrNull()
    }

    private fun playSelected() {
        val instance = selectedInstance() ?: run {
            shell.navigate(Page.CREATE); return
        }
        PlayAction.launch(shell, instance)
    }

    override fun doLayout() = layoutChildren()

    private fun layoutChildren() {
        val pad = UiScale.px(18)
        val playH = UiScale.px(52)
        play.bounds = java.awt.Rectangle(pad, height - pad - playH, width - pad * 2, playH)
        val accH = UiScale.px(40)
        addAccount.bounds = java.awt.Rectangle(pad, height - pad - playH - accH - UiScale.px(10), width - pad * 2, accH)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        Paint.glass(g2, 0, 0, width, height, UiScale.px(Radii.panel))
        val pad = UiScale.px(18)
        // the two buttons stack from the bottom, and the select-instance pill sits above them
        val playH = UiScale.px(52)
        val accH = UiScale.px(40)
        val selH = UiScale.px(58)
        val selY = height - pad - playH - accH - selH - UiScale.px(20)

        val account = Accounts.current()
        val nameY = selY - UiScale.px(42)
        val subY = selY - UiScale.px(24)
        val characterHeight = nameY - UiScale.px(34)
        val key = "${account?.username}:${characterHeight}:${width}:${account?.skinModel}"
        if (key != characterKey) {
            val skin = account?.let { SkinFace.skin(it) }
            character = SkinFace.renderCharacter(
                skin, width - pad * 2, characterHeight.coerceAtLeast(60),
                yawDegrees = -14f, slimModel = account?.skinModel == "slim", username = ""
            )
            characterKey = key
        }
        character?.let { g2.drawImage(it, pad, UiScale.px(10), null) }

        // account label
        g2.font = Fonts.titleMedium(14f)
        g2.color = AerixColors.textPrimary
        val name = account?.username ?: "No account"
        val fm = g2.fontMetrics
        g2.drawString(name, (width - fm.stringWidth(name)) / 2, nameY)
        g2.font = Fonts.label(11.5f)
        g2.color = AerixColors.textMuted
        val sub = when {
            account == null -> "Sign in or add an offline profile"
            account.isMicrosoft -> "Microsoft account"
            else -> "Offline profile"
        }
        val fm2 = g2.fontMetrics
        g2.drawString(sub, (width - fm2.stringWidth(sub)) / 2, subY)

        // selected instance line above PLAY
        val instance = selectedInstance()
        Paint.pill(g2, pad, selY, width - pad * 2, selH,
            withAlpha(Color.BLACK, if (pillHover) 100 else 70),
            withAlpha(if (pillHover) AerixTheme.accent else AerixColors.borderSoft, 0x60))
        g2.font = Fonts.titleMedium(13.5f)
        g2.color = AerixColors.textPrimary
        val line1 = instance?.name ?: "No instance selected"
        val fm3 = g2.fontMetrics
        g2.drawString(clip(line1, fm3, width - pad * 2 - 24), pad + 12, selY + UiScale.px(23))
        g2.font = Fonts.label(11.5f)
        g2.color = AerixTheme.accent
        val line2 = instance?.subtitle() ?: "Select instance"
        g2.drawString(clip(line2, g2.fontMetrics, width - pad * 2 - 24), pad + 12, selY + UiScale.px(41))
        g2.dispose()
    }

    private fun clip(text: String, fm: java.awt.FontMetrics, maxWidth: Int): String {
        if (fm.stringWidth(text) <= maxWidth) return text
        var t = text
        while (t.length > 3 && fm.stringWidth(t + "…") > maxWidth) t = t.dropLast(1)
        return t + "…"
    }
}

// ---------------------------------------------------------------------------------------------
// Instance cards
// ---------------------------------------------------------------------------------------------

class InstanceCard(private val shell: Shell, private val instance: Instance) : JComponent() {
    private var hover = false
    private var icon: BufferedImage? = null
    private var iconKey = ""

    init {
        preferredSize = Dimension(UiScale.px(300), UiScale.px(168))
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: MouseEvent) { hover = false; repaint() }
            override fun mouseClicked(e: MouseEvent) {
                if (javax.swing.SwingUtilities.isRightMouseButton(e)) showContextMenu(e)
                else shell.openInstance(instance)
            }
        })
        toolTipText = instance.subtitle()
    }

    private fun showContextMenu(e: MouseEvent) {
        val menu = InstanceMenu.build(shell, instance)
        menu.show(this, e.x, e.y)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        Paint.glass(g2, 0, 0, width, height, UiScale.px(Radii.card), fill = if (hover) AerixTheme.panelRaised() else AerixTheme.panel())
        if (hover) Paint.accentGlow(g2, 0, 0, width, height, withAlpha(AerixTheme.accent, 22))

        val pad = UiScale.px(16)
        val iconSize = UiScale.px(54)
        val key = "${instance.iconPath}:$iconSize"
        if (key != iconKey) {
            icon = instance.iconPath?.takeIf { java.nio.file.Files.isRegularFile(it) }?.let {
                runCatching { javax.imageio.ImageIO.read(it.toFile()) }.getOrNull()
            }?.let { backdropsRound(it, iconSize) } ?: IconTile.instanceTile(instance, iconSize)
            iconKey = key
        }
        icon?.let { g2.drawImage(it, pad, pad, null) }

        val textX = pad + iconSize + UiScale.px(14)
        g2.font = Fonts.titleMedium(15f)
        g2.color = AerixColors.textPrimary
        g2.drawString(clip(instance.name, g2.fontMetrics, width - textX - pad), textX, pad + UiScale.px(22))
        g2.font = Fonts.label(11.5f)
        g2.color = AerixColors.textMuted
        g2.drawString(clip(instance.subtitle(), g2.fontMetrics, width - textX - pad), textX, pad + UiScale.px(40))

        // chips
        var chipX = pad
        val chipY = height - pad - UiScale.px(28)
        chipX = chip(g2, chipX, chipY, instance.loader.let { net.kdt.aerix.desktop.core.instance.Loader.displayName(it) },
            if (instance.loader == "vanilla") AerixColors.textSecondary else AerixTheme.accent)
        if (instance.favorite) chip(g2, chipX, chipY, "Favorite", AerixColors.warning)
        if (GameLauncher.isRunning(instance)) {
            chip(g2, chipX + UiScale.px(70), chipY, "Running", AerixColors.success)
        }

        // LOAD / PLAY affordance
        val btnW = UiScale.px(96)
        val btnH = UiScale.px(34)
        val bx = width - pad - btnW
        val by = height - pad - btnH
        Paint.pill(g2, bx, by, btnW, btnH,
            if (hover) AerixTheme.accent else withAlpha(AerixColors.textPrimary, 22),
            if (hover) null else withAlpha(AerixColors.borderSoft, 0x70))
        val fg = if (hover) AerixTheme.onAccent else AerixColors.textPrimary
        g2.font = Fonts.labelStrong(11.5f)
        val label = if (GameLauncher.isRunning(instance)) "RUNNING" else "LAUNCH"
        val fm = g2.fontMetrics
        net.kdt.aerix.desktop.ui.icons.Icons.draw(g2, if (GameLauncher.isRunning(instance)) "pause" else "play",
            bx + UiScale.px(14), by + (btnH - UiScale.px(14)) / 2, UiScale.px(14), fg)
        g2.color = fg
        g2.drawString(label, bx + UiScale.px(34), by + (btnH - fm.height) / 2 + fm.ascent)
        g2.dispose()
    }

    private fun chip(g2: Graphics2D, x: Int, y: Int, text: String, color: Color): Int {
        g2.font = Fonts.labelStrong(10.5f)
        val fm = g2.fontMetrics
        val w = fm.stringWidth(text) + UiScale.px(18)
        Paint.pill(g2, x, y, w, UiScale.px(26), withAlpha(color, 30), withAlpha(color, 90))
        g2.color = color
        g2.drawString(text, x + UiScale.px(9), y + (UiScale.px(26) - fm.height) / 2 + fm.ascent)
        return x + w + UiScale.px(6)
    }

    private fun clip(text: String, fm: java.awt.FontMetrics, maxWidth: Int): String {
        if (fm.stringWidth(text) <= maxWidth) return text
        var t = text
        while (t.length > 3 && fm.stringWidth(t + "…") > maxWidth) t = t.dropLast(1)
        return t + "…"
    }

    private fun backdropsRound(img: BufferedImage, size: Int): BufferedImage {
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        Paint.quality(g)
        g.clip = RoundRectangle2D.Float(0f, 0f, size.toFloat(), size.toFloat(), UiScale.px(14).toFloat(), UiScale.px(14).toFloat())
        g.drawImage(img, 0, 0, size, size, null)
        g.dispose()
        return out
    }
}

/** Generates the instance tile when the player has not chosen artwork: loader glyph on a tinted plate. */
object IconTile {
    fun instanceTile(instance: Instance, size: Int): BufferedImage {
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        Paint.quality(g)
        val base = Color(instance.color)
        val r = UiScale.px(14)
        g.paint = java.awt.GradientPaint(0f, 0f, mix(base, AerixColors.canvas, 0.25f), size.toFloat(), size.toFloat(), mix(base, AerixColors.canvas, 0.75f))
        g.fill(RoundRectangle2D.Float(0f, 0f, size.toFloat(), size.toFloat(), r * 2f, r * 2f))
        val icon = when (instance.loader) {
            "fabric", "quilt" -> "build"
            "forge", "neoforge" -> "style"
            "optifine" -> "visibility"
            else -> "gamepad"
        }
        val iconSize = (size * 0.52f).toInt()
        net.kdt.aerix.desktop.ui.icons.Icons.draw(g, icon, (size - iconSize) / 2, (size - iconSize) / 2, iconSize, mix(AerixColors.canvas, base, 0.15f))
        g.dispose()
        return out
    }

    fun versionTile(versionId: String, size: Int, accent: Color = AerixTheme.accent): BufferedImage {
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        Paint.quality(g)
        g.paint = java.awt.GradientPaint(0f, 0f, mix(accent, AerixColors.canvas, 0.2f), size.toFloat(), size.toFloat(), mix(accent, AerixColors.canvas, 0.7f))
        g.fill(RoundRectangle2D.Float(0f, 0f, size.toFloat(), size.toFloat(), UiScale.px(12) * 2f, UiScale.px(12) * 2f))
        val iconSize = (size * 0.5f).toInt()
        net.kdt.aerix.desktop.ui.icons.Icons.draw(g, "gamepad", (size - iconSize) / 2, (size - iconSize) / 2, iconSize, mix(AerixColors.canvas, accent, 0.1f))
        g.dispose()
        return out
    }
}

private class AddInstanceCard(private val onClick: () -> Unit) : JComponent() {
    private var hover = false
    init {
        preferredSize = Dimension(UiScale.px(300), UiScale.px(168))
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
        Paint.pill(g2, 0, 0, width, height, withAlpha(AerixTheme.accent, if (hover) 26 else 14),
            withAlpha(AerixTheme.accent, 90), 1.4f)
        val iconSize = UiScale.px(34)
        net.kdt.aerix.desktop.ui.icons.Icons.draw(g2, "create", (width - iconSize) / 2, height / 2 - iconSize - UiScale.px(4), iconSize, AerixTheme.accent)
        g2.font = Fonts.titleMedium(14f)
        g2.color = AerixColors.textPrimary
        val text = "Create a new instance"
        val fm = g2.fontMetrics
        g2.drawString(text, (width - fm.stringWidth(text)) / 2, height / 2 + UiScale.px(8))
        g2.font = Fonts.label(11.5f)
        g2.color = AerixColors.textMuted
        val sub = "Vanilla, Fabric, Forge, NeoForge, Quilt"
        val fm2 = g2.fontMetrics
        g2.drawString(sub, (width - fm2.stringWidth(sub)) / 2, height / 2 + UiScale.px(26))
        g2.dispose()
    }
}

private class EmptyHintCard : JComponent() {
    init { preferredSize = Dimension(UiScale.px(300), UiScale.px(168)) }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        Paint.pill(g2, 0, 0, width, height, withAlpha(Color.WHITE, 8), withAlpha(AerixColors.borderSoft, 0x40))
        g2.font = Fonts.body(12.5f)
        g2.color = AerixColors.textMuted
        val text = "Recent worlds appear here"
        val fm = g2.fontMetrics
        g2.drawString(text, (width - fm.stringWidth(text)) / 2, height / 2)
        g2.dispose()
    }
}

// ---------------------------------------------------------------------------------------------
// Small shared bits used across screens (kept here to avoid a utilities grab-bag)
// ---------------------------------------------------------------------------------------------

fun Eyebrow(text: String): JComponent = object : JComponent() {
    init { font = Fonts.labelStrong(10.5f) }
    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(font)
        return Dimension(fm.stringWidth(spaced(text)) + 2, fm.height + 2)
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        g2.font = font
        g2.color = AerixTheme.accent
        g2.drawString(spaced(text), 0, height - 2)
        g2.dispose()
    }
}

fun spaced(text: String): String = text.uppercase().map { it.toString() }.joinToString(" ")

fun Label(text: String, font: java.awt.Font = Fonts.body(), color: Color = AerixColors.textPrimary): JComponent =
    object : JComponent() {
        var text0 = text
        init { this.font = font }
        override fun getPreferredSize(): Dimension {
            val fm = getFontMetrics(font)
            return Dimension(fm.stringWidth(text0) + 2, fm.height)
        }
        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            Paint.quality(g2)
            g2.font = font
            g2.color = color
            g2.drawString(text0, 0, g2.fontMetrics.ascent)
            g2.dispose()
        }
    }
