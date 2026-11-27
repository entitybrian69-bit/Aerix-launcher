package net.kdt.aerix.desktop.ui.screens

import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.core.json.Json
import net.kdt.aerix.desktop.core.json.JsonArray
import net.kdt.aerix.desktop.core.json.JsonObject
import net.kdt.aerix.desktop.core.net.Http
import net.kdt.aerix.desktop.app.Scheduler
import net.kdt.aerix.desktop.ui.components.AerixButton
import net.kdt.aerix.desktop.ui.components.AerixField
import net.kdt.aerix.desktop.ui.components.ButtonStyle
import net.kdt.aerix.desktop.ui.components.ClearPanel
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
import java.awt.GridLayout
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
 * Servers: the community server list from the Android app's ServersScreen, parsed from the same
 * `server_artwork`/registry JSON, with the player's own favourites kept in settings.
 *
 * The list is cached; joining a server is a launch with `--server`/`--port` (or `quickPlayMultiplayer`
 * on 1.20+), which [PlayAction] already does for instances. This screen passes the address to the
 * chosen instance so a server row is genuinely clickable.
 */
class ServersScreen(private val shell: Shell) : JPanel(BorderLayout()), Refreshable {
    class Server(
        val name: String,
        val address: String,
        val description: String,
        val version: String,
        val players: String,
        val banner: String?
    )

    private var servers: List<Server> = emptyList()
    private var filter = ""
    private var loading = false
    private var error: String? = null
    private val grid = ClearPanel(GridLayout(0, 3, UiScale.px(12), UiScale.px(12)))
    private val bannerCache = HashMap<String, java.awt.image.BufferedImage?>()

    init {
        isOpaque = false
        add(buildHeader(), BorderLayout.NORTH)
        val body = ClearPanel(BorderLayout())
        body.border = BorderFactory.createEmptyBorder(0, UiScale.px(Spacing.xl), UiScale.px(Spacing.xl), UiScale.px(Spacing.xl))
        val scroll = aerixScroll(grid)
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
            add(heading("Servers", 20f).apply { alignmentX = 0f })
            add(label("Featured communities, plus whatever you add yourself", Fonts.label(12f), AerixColors.textMuted).apply { alignmentX = 0f })
        }
        header.add(titles, BorderLayout.WEST)
        val right = ClearPanel(FlowLayout(FlowLayout.RIGHT, UiScale.px(8), 0))
        right.add(AerixField(placeholder = "Search servers", icon = "search").apply {
            preferredSize = Dimension(UiScale.px(200), UiScale.px(34))
            addCaretListener { filter = text; render() }
        })
        right.add(AerixButton("Add server", ButtonStyle.SECONDARY, "add").apply {
            compact = true
            addActionListener { addServer() }
        })
        right.add(IconButton("refresh", size = 34, tooltip = "Reload").apply { addActionListener { load() } })
        header.add(right, BorderLayout.EAST)
        return header
    }

    private fun load() {
        if (loading) return
        loading = true
        error = null
        grid.removeAll()
        grid.add(label("Loading servers…", Fonts.body(12.5f), AerixColors.textMuted))
        grid.revalidate(); grid.repaint()
        Scheduler.io("servers") {
            val outcome = runCatching { fetchFeatured() }
            Scheduler.ui {
                loading = false
                outcome.onSuccess { list ->
                    servers = list + customServers()
                    render()
                }.onFailure {
                    Log.w("servers", "featured list failed: ${it.message}")
                    servers = customServers()
                    error = it.message
                    render()
                }
            }
        }
    }

    private fun render() {
        grid.removeAll()
        if (servers.isEmpty()) {
            grid.add(label(error?.let { "Could not load the list: $it" } ?: "No servers yet.", Fonts.body(12.5f), AerixColors.warning))
        }
        servers.filter { filter.isBlank() || it.name.contains(filter, true) || it.address.contains(filter, true) }
            .take(60)
            .forEach { grid.add(ServerCard(it, bannerCache) { join(it) }) }
        grid.revalidate(); grid.repaint()
    }

    /**
     * The featured list is the same LabyMod `server-media` catalogue the Android build ships:
     * `catalog.json` is bundled (240 entries with their canonical addresses and banner
     * filenames) and the banner itself streams from the LabyMod repository, exactly as the app
     * does. Artwork belongs to the servers; see `server_artwork_NOTICE.txt`.
     */
    private fun fetchFeatured(): List<Server> {
        val text = runCatching {
            ServersScreen::class.java.getResourceAsStream("/aerix/brand/server_catalog.json")?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull() ?: return emptyList()
        val doc = Json.parseObject(text)
        val revision = doc.str("revision") ?: ""
        return doc.arr("servers")?.objects()?.mapNotNull { o ->
            val slug = o.str("slug") ?: return@mapNotNull null
            val direct = o.str("direct") ?: return@mapNotNull null
            val file = o.str("file") ?: "background.png"
            Server(
                name = slug.replace('-', ' ').split(' ').joinToString(" ") { part ->
                    part.replaceFirstChar { c -> c.uppercase() }
                },
                address = direct,
                description = "Community server · click to join from one of your instances",
                version = "Java",
                players = "",
                banner = "https://raw.githubusercontent.com/LabyMod/server-media/$revision/minecraft_servers/$slug/$file"
            )
        } ?: emptyList()
    }

    private fun customServers(): List<Server> {
        val stored = net.kdt.aerix.desktop.app.Settings.string("customServers", "")
        if (stored.isBlank()) return emptyList()
        return runCatching {
            Json.parseArray(stored).objects().mapNotNull { o ->
                val address = o.str("address") ?: return@mapNotNull null
                Server(o.str("name") ?: address, address, o.str("description") ?: "", "Java", "", null)
            }
        }.getOrDefault(emptyList())
    }

    private fun addServer() {
        val name = JOptionPane.showInputDialog(this, "Server name")
        if (name.isNullOrBlank()) return
        val address = JOptionPane.showInputDialog(this, "Address (host or host:port)")
        if (address.isNullOrBlank()) return
        val stored = net.kdt.aerix.desktop.app.Settings.string("customServers", "")
        val arr = runCatching { if (stored.isBlank()) JsonArray() else Json.parseArray(stored) }.getOrDefault(JsonArray())
        arr.add(JsonObject().put("name", name.trim()).put("address", address.trim()))
        net.kdt.aerix.desktop.app.Settings.set("customServers", Json.pretty(arr))
        load()
    }

    private fun join(server: Server) {
        val instances = net.kdt.aerix.desktop.core.instance.Instances.list()
        if (instances.isEmpty()) { shell.toast("Create an instance first"); return }
        val options = instances.map { it.name }.toTypedArray()
        val pick = JOptionPane.showInputDialog(this, "Join ${server.name} with which instance?", "Join server",
            JOptionPane.QUESTION_MESSAGE, null, options, options.first()) as? String ?: return
        val inst = instances[options.indexOf(pick)]
        net.kdt.aerix.desktop.app.Settings.set("pendingServer", server.address)
        shell.toast("Joining ${server.address} with ${inst.name}")
        PlayAction.launch(shell, inst)
    }
}

private class ServerCard(private val server: ServersScreen.Server, private val bannerCache: HashMap<String, java.awt.image.BufferedImage?>, private val onClick: () -> Unit) : JComponent() {
    private var hover = false
    private var banner: java.awt.image.BufferedImage? = null
    private var requested = false
    init {
        preferredSize = Dimension(UiScale.px(300), UiScale.px(150))
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = server.address
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: MouseEvent) { hover = false; repaint() }
            override fun mouseClicked(e: MouseEvent) = onClick()
        })
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val r = UiScale.px(16)
        g2.clip = RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), r * 2f, r * 2f)
        g2.color = AerixTheme.panel(true)
        g2.fillRect(0, 0, width, height)
        if (banner == null && !requested) {
            requested = true
            val url = server.banner
            if (url != null) {
                bannerCache[url]?.let { banner = it }
                if (banner == null) {
                    Scheduler.io("server-banner") {
                        val img = runCatching {
                            javax.imageio.ImageIO.read(Http.download(url).inputStream())
                        }.getOrNull()
                        bannerCache[url] = img
                        Scheduler.ui { banner = img; repaint() }
                    }
                }
            }
        }
        val img = banner
        if (img != null) {
            val scaled = net.kdt.aerix.desktop.ui.theme.Backdrops.cover(img, width, height)
            g2.drawImage(scaled, 0, 0, null)
            g2.color = Color(4, 12, 18, 170)
            g2.fillRect(0, 0, width, height)
        } else {
            g2.color = AerixTheme.panelRaised(true)
            g2.fillRect(0, 0, width, height)
            net.kdt.aerix.desktop.ui.icons.Icons.draw(g2, "servers", width - UiScale.px(56), UiScale.px(10), UiScale.px(44), withAlpha(AerixTheme.accent, 60))
        }
        g2.clip = null
        g2.font = Fonts.headline(16f)
        g2.color = Color.WHITE
        g2.drawString(server.name.take(22), UiScale.px(14), UiScale.px(26))
        g2.font = Fonts.labelStrong(10.5f)
        g2.color = AerixTheme.accent
        g2.drawString(server.address.take(30), UiScale.px(14), UiScale.px(42))
        g2.font = Fonts.body(11f)
        g2.color = Color(0xD0, 0xDA, 0xD8)
        wrap(server.description, g2.fontMetrics, width - UiScale.px(28), 2).forEachIndexed { i, line ->
            g2.drawString(line, UiScale.px(14), UiScale.px(62) + i * (g2.fontMetrics.height + 1))
        }
        // join affordance
        val w = UiScale.px(70)
        Paint.pill(g2, width - w - UiScale.px(12), height - UiScale.px(36), w, UiScale.px(26),
            if (hover) AerixTheme.accent else withAlpha(Color.WHITE, 30))
        g2.font = Fonts.labelStrong(10f)
        g2.color = if (hover) AerixTheme.onAccent else Color.WHITE
        val text = "JOIN"
        val fm = g2.fontMetrics
        g2.drawString(text, width - w - UiScale.px(12) + (w - fm.stringWidth(text)) / 2, height - UiScale.px(36) + UiScale.px(17))
        if (hover) {
            g2.color = withAlpha(AerixTheme.accent, 160)
            g2.stroke = java.awt.BasicStroke(UiScale.px(1.4f))
            g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, r * 2f, r * 2f))
        }
        g2.dispose()
    }
    private fun wrap(text: String, fm: java.awt.FontMetrics, maxWidth: Int, maxLines: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val words = text.split(" ")
        val lines = ArrayList<String>()
        var cur = StringBuilder()
        for (w in words) {
            val candidate = if (cur.isEmpty()) w else "$cur $w"
            if (fm.stringWidth(candidate) <= maxWidth) cur = StringBuilder(candidate)
            else {
                lines.add(cur.toString())
                if (lines.size >= maxLines) return lines
                cur = StringBuilder(w)
            }
        }
        if (cur.isNotEmpty() && lines.size < maxLines) lines.add(cur.toString())
        return lines
    }
}
