package net.kdt.aerix.desktop.ui.shell

import net.kdt.aerix.desktop.app.AppEnv
import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Scheduler
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.auth.accounts.Accounts
import net.kdt.aerix.desktop.core.game.GameLauncher
import net.kdt.aerix.desktop.core.instance.Instance
import net.kdt.aerix.desktop.core.instance.Instances
import net.kdt.aerix.desktop.ui.components.AerixButton
import net.kdt.aerix.desktop.ui.components.ButtonStyle
import net.kdt.aerix.desktop.ui.components.ClearPanel
import net.kdt.aerix.desktop.ui.components.IconButton
import net.kdt.aerix.desktop.ui.components.Ui
import net.kdt.aerix.desktop.ui.components.aerixScroll
import net.kdt.aerix.desktop.ui.screens.DropImport
import net.kdt.aerix.desktop.ui.icons.Icons
import net.kdt.aerix.desktop.ui.theme.AerixColors
import net.kdt.aerix.desktop.ui.theme.AerixTheme
import net.kdt.aerix.desktop.ui.theme.Backdrops
import net.kdt.aerix.desktop.ui.theme.Fonts
import net.kdt.aerix.desktop.ui.theme.Metrics
import net.kdt.aerix.desktop.ui.theme.Paint
import net.kdt.aerix.desktop.ui.theme.Radii
import net.kdt.aerix.desktop.ui.theme.Spacing
import net.kdt.aerix.desktop.ui.theme.UiScale
import net.kdt.aerix.desktop.ui.theme.mix
import net.kdt.aerix.desktop.ui.theme.withAlpha
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * The Aerix shell: a custom frameless window with the Android launcher's navigation rail on the
 * left, the account chip at its foot, and a routed page area.
 *
 * The rail mirrors `AerixNavigationRail.kt`: the Aerix mark at the top, then Home, Create, Library,
 * Discover, Modpacks, Releases, Settings, and the account avatar at the bottom. Selected items get
 * the accent pill, hovered items lift, and the rail widens into labelled mode on wide windows
 * exactly like `expandedRail` does on tablets.
 */
enum class Page(val label: String, val icon: String) {
    HOME("Home", "home"),
    CREATE("Create", "create"),
    LIBRARY("Library", "library"),
    DISCOVER("Discover", "discover"),
    MODPACKS("Modpacks", "modpacks"),
    RELEASES("Releases", "releases"),
    SERVERS("Servers", "servers"),
    SETTINGS("Settings", "settings"),
    ACCOUNTS("Accounts", "accounts"),
    CONTRIBUTION("Team", "person"),
    ABOUT("About", "info"),
    LOGS("Console", "logs"),
    FILES("Files", "files")
}

// ---------------------------------------------------------------------------------------------
// Window
// ---------------------------------------------------------------------------------------------

class AerixWindow : JFrame("Aerix Launcher") {
    private val background = object : JPanel(BorderLayout()) {
        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            Paint.quality(g2)
            g2.drawImage(backdrop(), 0, 0, width, height, null)
            g2.dispose()
        }
    }

    val shell: Shell

    init {
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        val icon = runCatching {
            val img = net.kdt.aerix.desktop.ui.icons.Icons.image("client_crystal", 64, null)
            java.util.ArrayList<java.awt.Image>().apply {
                listOf(16, 32, 48, 64, 128, 256).forEach { size ->
                    runCatching {
                        val stream = AerixWindow::class.java.getResourceAsStream("/aerix/brand/icon_$size.png") ?: return@runCatching
                        add(javax.imageio.ImageIO.read(stream))
                    }
                }
                if (isEmpty()) add(img)
            }
        }.getOrDefault(emptyList())
        if (icon.isNotEmpty()) setIconImages(icon)
        shell = Shell(this)
        contentPane = background
        background.add(shell, BorderLayout.CENTER)
        minimumSize = Dimension(UiScale.px(980), UiScale.px(640))
        val (w, h) = windowSize()
        setSize(w, h)
        isLocationByPlatform = true
        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent) {
                onCloseRequested()
            }
        })
        installDropTarget()
        installShortcuts()
    }

    /** Set by [net.kdt.aerix.desktop.main] when a tray icon exists and the player asked for it. */
    var onHideToTray: (() -> Unit)? = null

    private fun installDropTarget() {
        val handler = object : java.awt.dnd.DropTargetAdapter() {
            override fun drop(e: java.awt.dnd.DropTargetDropEvent) {
                acceptDrop(e, this@AerixWindow)
            }
            override fun dragEnter(e: java.awt.dnd.DropTargetDragEvent) {
                if (e.transferable.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                    e.acceptDrag(java.awt.dnd.DnDConstants.ACTION_COPY)
                } else e.rejectDrag()
            }
        }
        dropTarget.addDropTargetListener(handler)
    }

    /**
     * Drag and drop: the desktop replacement for Android's share-sheet import. A pack, a mod or a
     * whole world folder dropped anywhere on the window is recognised and routed; see [DropImport].
     */
    private fun acceptDrop(e: java.awt.dnd.DropTargetDropEvent, window: AerixWindow) {
        runCatching {
            e.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY)
            @Suppress("UNCHECKED_CAST")
            val files = e.transferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<java.io.File>
            e.dropComplete(true)
            if (files.isEmpty()) return
            val target = files.first()
            SwingUtilities.invokeLater {
                DropImport.importFile(window.shell, target.toPath())
                if (files.size > 1) {
                    files.drop(1).forEach { extra ->
                        runCatching { DropImport.importFile(window.shell, extra.toPath()) }
                    }
                }
            }
        }.onFailure {
            e.dropComplete(false)
            Log.w("drop", "drop failed: ${it.message}")
        }
    }

    /** Ctrl+1…9 jump to a page, Ctrl+, opens Settings, F5 refreshes, Alt+Left goes back. */
    private fun installShortcuts() {
        val root = rootPane
        val map = root.inputMap
        val actions = root.actionMap
        fun bind(keystroke: String, name: String, action: java.awt.event.ActionListener) {
            val key = javax.swing.KeyStroke.getKeyStroke(keystroke)
            map.put(key, name)
            actions.put(name, object : javax.swing.AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) = action.actionPerformed(e)
            })
        }
        val navPages = listOf(Page.HOME, Page.CREATE, Page.LIBRARY, Page.DISCOVER, Page.MODPACKS, Page.SERVERS, Page.RELEASES, Page.SETTINGS, Page.ACCOUNTS)
        navPages.forEachIndexed { index, page ->
            bind("ctrl pressed ${'1' + index}", "nav-$index") { shell.navigate(page) }
        }
        bind("ctrl COMMA", "nav-settings") { shell.navigate(Page.SETTINGS) }
        bind("F5", "refresh") { shell.refreshPages(); shell.toast("Refreshed") }
        bind("alt LEFT", "back") { if (!shell.goBack()) shell.navigate(Page.HOME) }
        bind("ctrl L", "console") { shell.navigate(Page.LOGS) }
        bind("ctrl F", "files") { shell.navigate(Page.FILES) }
        bind("ESCAPE", "escape") { if (!shell.goBack()) Unit }
    }

    private fun windowSize(): Pair<Int, Int> {
        val savedW = Settings.int("windowWidth", 1440)
        val savedH = Settings.int("windowHeight", 900)
        val screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val w = savedW.coerceIn(UiScale.px(980), maxOf(UiScale.px(980), screen.width))
        val h = savedH.coerceIn(UiScale.px(640), maxOf(UiScale.px(640), screen.height))
        if (Settings.bool("windowMaximized", false)) extendedState = JFrame.MAXIMIZED_BOTH
        return w to h
    }

    fun persistWindowState() {
        if (extendedState and JFrame.MAXIMIZED_BOTH != 0) extendedState = JFrame.NORMAL
        Settings.set("windowWidth", width)
        Settings.set("windowHeight", height)
        Settings.set("windowMaximized", extendedState and JFrame.MAXIMIZED_BOTH != 0)
    }

    private fun onCloseRequested() {
        val playing = GameLauncher.runningGames()
        val stopGames = Settings.bool("closeGameWithLauncher", false)
        if (playing.isNotEmpty()) {
            // Three real answers, not two: keep the game running, stop it, or go back. Which ones
            // are offered depends on the setting, so the dialog never claims something it will
            // not do — "close anyway, the game keeps running" was shown even when the setting
            // said the opposite.
            val options = if (stopGames)
                arrayOf("Stop the game and quit", "Keep the game running and quit", "Cancel")
            else
                arrayOf("Quit, leave the game running", "Stop the game and quit", "Cancel")
            val message = if (stopGames)
                "Minecraft is still running.\n\"Close the game with the launcher\" is on, so quitting stops it."
            else
                "Minecraft is still running.\nIt keeps running in its own window if you quit."
            val choice = javax.swing.JOptionPane.showOptionDialog(
                this, message, "Aerix Launcher",
                javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]
            )
            val selected = options.getOrNull(choice) ?: return
            if (selected == "Cancel") return
            if (selected.startsWith("Stop")) GameLauncher.stopAll()
            else GameLauncher.detachAll()
        } else if (stopGames) {
            GameLauncher.stopAll()
        }
        persistWindowState()
        Settings.saveNow()
        Shell.saveNavState()
        if (Settings.bool("minimizeToTrayOnClose", false) && onHideToTray != null) {
            onHideToTray?.invoke()
            return
        }
        dispose()
        System.exit(0)
    }

    private var cachedBackdrop: java.awt.image.BufferedImage? = null
    private var cachedKey = ""

    fun backdrop(): java.awt.image.BufferedImage {
        val id = Settings.string("selected_wallpaper_id", "wp_01_lush_caves")
        val blur = if (Settings.bool("backgroundBlur", false)) 4 else 0
        val key = "$id:${width}x$height:$blur"
        if (key == cachedKey) cachedBackdrop?.let { return it }
        val img = Backdrops.background(id, width, height, blur, AerixTheme.backgroundOpacity())
        cachedBackdrop = img
        cachedKey = key
        return img
    }

    fun invalidateBackdrop() { cachedKey = "" }
}

// ---------------------------------------------------------------------------------------------
// Shell
// ---------------------------------------------------------------------------------------------

class Shell(val window: AerixWindow?) : JPanel(BorderLayout()) {
    private val rail = NavRail(this)
    internal val pageHost = JPanel(CardLayout())
    private val pages = LinkedHashMap<Page, JComponent>()
    private var current: Page? = null
    private val history = ArrayList<Page>()

    /**
     * A shell with no window, for the headless self test and the screenshot renderer. Everything a
     * page does goes through this class, so the pages paint identically with or without a frame.
     */
    constructor(unattached: Boolean) : this(window = null) {
        isHeadless = unattached
    }

    internal var isHeadless = false

    init {
        isOpaque = false
        if (!isHeadless) add(rail, BorderLayout.WEST)
        pageHost.isOpaque = false
        add(pageHost, BorderLayout.CENTER)
        // repaint the whole shell when theme-affecting settings change
        Settings.onChange { key ->
            if (key in THEME_KEYS) {
                window?.invalidateBackdrop()
                AerixTheme.refresh()
                if (!isHeadless) SwingUtilities.updateComponentTreeUI(this@Shell)
                revalidate(); repaint()
            }
        }
        SwingUtilities.invokeLater {
            val page = Settings.string("aerixLastSection", Page.HOME.name)
                .let { runCatching { Page.valueOf(it) }.getOrDefault(Page.HOME) }
            navigate(page, remember = false)
        }
    }

    companion object {
        private val THEME_KEYS = setOf(
            "launcherColorTheme", "launcherCustomColor", "layoutTransparency", "selected_wallpaper_id",
            "backgroundBlur", "launcherBackgroundOpacity", "launcherFont", "themeMotion"
        )

        fun saveNavState() {
            // the shell persists the current page itself
        }

        fun trayAvailable(): Boolean = java.awt.SystemTray.isSupported()
    }

    fun navigate(page: Page, remember: Boolean = true) {
        if (remember && current != null && current != page) history.add(current!!)
        val component = pages.getOrPut(page) { createPage(page) }
        current = page
        (pageHost.layout as CardLayout).show(pageHost, page.name)
        Settings.set("aerixLastSection", page.name)
        rail.refresh()
        pageHost.repaint()
    }

    fun goBack(): Boolean {
        val prev = history.removeLastOrNull() ?: return false
        navigate(prev, remember = false)
        return true
    }

    fun currentPage(): Page? = current

    private fun createPage(page: Page): JComponent {
        val component = when (page) {
            Page.HOME -> net.kdt.aerix.desktop.ui.screens.HomeScreen(this)
            Page.LIBRARY -> net.kdt.aerix.desktop.ui.screens.LibraryScreen(
                this, null
            )
            Page.CREATE -> net.kdt.aerix.desktop.ui.screens.CreateInstanceScreen(this)
            Page.DISCOVER -> net.kdt.aerix.desktop.ui.screens.DiscoverScreen(this)
            Page.MODPACKS -> net.kdt.aerix.desktop.ui.screens.ModpacksScreen(this)
            Page.RELEASES -> net.kdt.aerix.desktop.ui.screens.ReleasesScreen(this)
            Page.SERVERS -> net.kdt.aerix.desktop.ui.screens.ServersScreen(this)
            Page.SETTINGS -> net.kdt.aerix.desktop.ui.screens.SettingsScreen(this)
            Page.ACCOUNTS -> net.kdt.aerix.desktop.ui.screens.AccountsScreen(this)
            Page.CONTRIBUTION -> net.kdt.aerix.desktop.ui.screens.ContributionScreen(this)
            Page.ABOUT -> net.kdt.aerix.desktop.ui.screens.AboutScreen(this)
            Page.LOGS -> net.kdt.aerix.desktop.ui.screens.ConsoleScreen(this)
            Page.FILES -> net.kdt.aerix.desktop.ui.screens.FilesScreen(this)
        }
        pageHost.add(component, page.name)
        return component
    }

    fun openInstance(instance: Instance?) {
        navigate(Page.LIBRARY)
        (pages[Page.LIBRARY] as? net.kdt.aerix.desktop.ui.screens.LibraryScreen)?.select(instance)
    }

    fun refreshPages() {
        pages.values.forEach { if (it is Refreshable) it.refresh() }
        revalidate()
        repaint()
    }

    fun toast(message: String) {
        Toast.show(this, message)
    }
}

/** Pages that reload their data when something changed elsewhere. */
interface Refreshable { fun refresh() }

// ---------------------------------------------------------------------------------------------
// Navigation rail
// ---------------------------------------------------------------------------------------------

class NavRail(private val shell: Shell) : JComponent() {
    private val items = listOf(
        Page.HOME, Page.CREATE, Page.LIBRARY, Page.DISCOVER,
        Page.MODPACKS, Page.SERVERS, Page.RELEASES, Page.SETTINGS
    )
    private var hovered: Int = -1
    private var hoverAnim = FloatArray(items.size + 2)
    private val timer = Timer(16) { tick() }
    private var accountHover = false

    init {
        isOpaque = false
        preferredSize = Dimension(UiScale.px(Metrics.railWidth + 26), 100)
        addMouseListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) { updateHover(e.point.y, e.point.x) }
            override fun mouseExited(e: MouseEvent) { hovered = -1; accountHover = false; startTimer() }
            override fun mouseClicked(e: MouseEvent) { click(e.point.y, e.point.x) }
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) { updateHover(e.point.y, e.point.x) }
        })
        addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) { startTimer() }
        })
        addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: java.awt.event.KeyEvent) {
                if (!e.isControlDown && !e.isAltDown) {
                    if (e.keyCode == java.awt.event.KeyEvent.VK_DOWN) moveSelection(1)
                    if (e.keyCode == java.awt.event.KeyEvent.VK_UP) moveSelection(-1)
                }
            }
        })
        isFocusable = true
        ToolTipManagerHolder.enable(this)
    }

    private fun moveSelection(delta: Int) {
        val current = shell.currentPage()
        val idx = items.indexOf(current).let { if (it < 0) 0 else it }
        val next = (idx + delta).coerceIn(0, items.size - 1)
        shell.navigate(items[next])
    }

    private fun itemAt(y: Int): Int {
        val top = UiScale.px(96)
        val step = UiScale.px(Metrics.railButton + 12)
        val idx = (y - top) / step
        return if (y >= top && idx in items.indices && (y - top) % step < UiScale.px(Metrics.railButton)) idx else -1
    }

    private fun updateHover(y: Int, x: Int) {
        val idx = itemAt(y)
        if (idx != hovered) { hovered = idx; startTimer() }
        val acc = accountHit(x, y)
        if (acc != accountHover) { accountHover = acc; startTimer() }
        cursor = if (idx >= 0 || acc || logoHit(x, y)) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
    }

    private fun logoHit(x: Int, y: Int): Boolean =
        y < UiScale.px(80) && x > 0 && x < width && y > UiScale.px(16)

    private fun accountHit(x: Int, y: Int): Boolean {
        val avatarY = height - UiScale.px(84)
        return y > avatarY && y < avatarY + UiScale.px(Metrics.railButton)
    }

    private fun click(y: Int, x: Int) {
        val idx = itemAt(y)
        if (idx >= 0) { shell.navigate(items[idx]); return }
        if (accountHit(x, y)) { shell.navigate(Page.ACCOUNTS); return }
        if (logoHit(x, y)) shell.navigate(Page.ABOUT)
    }

    private fun startTimer() { if (!timer.isRunning) timer.start() }

    private fun tick() {
        val target = shell.currentPage()
        var active = false
        for (i in items.indices) {
            val want = if (i == hovered) 1f else 0f
            if (kotlin.math.abs(hoverAnim[i] - want) > 0.01f) {
                hoverAnim[i] += (want - hoverAnim[i]) * 0.25f
                active = true
            } else hoverAnim[i] = want
        }
        if (!active) timer.stop()
        repaint()
    }

    fun refresh() { repaint() }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val railW = UiScale.px(Metrics.railWidth)
        val pad = UiScale.px(13)
        // The rail is a floating glass capsule like the Android one, not a solid bar.
        Paint.glass(
            g2, pad, pad, railW, height - pad * 2, UiScale.px(32),
            fill = withAlpha(AerixColors.canvas, (0xC8 * AerixTheme.layoutTransparency() / 100).coerceIn(120, 200)),
            shadow = true
        )
        val cx = pad + railW / 2

        // Aerix mark
        val logoSize = UiScale.px(44)
        val logoY = pad + UiScale.px(14)
        runCatching {
            val stream = Shell::class.java.getResourceAsStream("/aerix/brand/logo_round.png")
            if (stream != null) {
                val img = javax.imageio.ImageIO.read(stream)
                g2.drawImage(img, cx - logoSize / 2, logoY, logoSize, logoSize, null)
            } else {
                g2.color = withAlpha(AerixTheme.accent, 40)
                g2.fill(Ellipse2D.Float((cx - logoSize / 2).toFloat(), logoY.toFloat(), logoSize.toFloat(), logoSize.toFloat()))
                Icons.draw(g2, "client_crystal", cx - UiScale.px(13), logoY + UiScale.px(10), UiScale.px(26), AerixTheme.accent)
            }
        }

        val top = UiScale.px(96)
        val step = UiScale.px(Metrics.railButton + 12)
        val size = UiScale.px(Metrics.railButton)
        val current = shell.currentPage()
        items.forEachIndexed { i, page ->
            val y = top + i * step
            val x = cx - size / 2
            val selected = current == page
            if (selected) {
                g2.color = withAlpha(AerixTheme.accent, 0x38)
                g2.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), size.toFloat(), size.toFloat(),
                    UiScale.px(16).toFloat(), UiScale.px(16).toFloat()))
                g2.color = AerixTheme.accent
                g2.stroke = java.awt.BasicStroke(UiScale.px(1.4f))
                g2.draw(RoundRectangle2D.Float(x + 0.5f, y + 0.5f, (size - 1).toFloat(), (size - 1).toFloat(),
                    UiScale.px(16).toFloat(), UiScale.px(16).toFloat()))
            } else if (hoverAnim[i] > 0.01f) {
                g2.color = withAlpha(Color.WHITE, (hoverAnim[i] * 24).toInt())
                g2.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), size.toFloat(), size.toFloat(),
                    UiScale.px(16).toFloat(), UiScale.px(16).toFloat()))
            }
            val tint = when {
                selected -> AerixTheme.accent
                hoverAnim[i] > 0.5f -> AerixColors.textPrimary
                else -> AerixColors.textSecondary
            }
            val iconSize = UiScale.px(21)
            Icons.draw(g2, page.icon, cx - iconSize / 2, y + (size - iconSize) / 2, iconSize, tint)
            if (selected) {
                g2.color = AerixTheme.accent
                g2.fill(RoundRectangle2D.Float((pad - UiScale.px(4)).toFloat(), (y + size / 2 - UiScale.px(11)).toFloat(),
                    UiScale.px(3).toFloat(), UiScale.px(22).toFloat(), 3f, 3f))
            }
        }

        // account avatar at the foot of the rail
        val avatarSize = UiScale.px(40)
        val avatarY = height - UiScale.px(84)
        val account = Accounts.current()
        val avatarX = cx - avatarSize / 2
        val avatarImg = net.kdt.aerix.desktop.ui.components.SkinFace.face(account, avatarSize)
        if (avatarImg != null) {
            g2.drawImage(avatarImg, avatarX, avatarY, null)
        } else {
            g2.color = withAlpha(AerixColors.textMuted, 90)
            g2.fill(Ellipse2D.Float(avatarX.toFloat(), avatarY.toFloat(), avatarSize.toFloat(), avatarSize.toFloat()))
            Icons.draw(g2, "person", avatarX + avatarSize / 4, avatarY + avatarSize / 4, avatarSize / 2, AerixColors.canvas)
        }
        g2.color = if (accountHover) AerixTheme.accent else withAlpha(AerixColors.border, 0x80)
        g2.stroke = java.awt.BasicStroke(UiScale.px(1.6f))
        g2.draw(Ellipse2D.Float(avatarX.toFloat(), avatarY.toFloat(), avatarSize.toFloat(), avatarSize.toFloat()))
        g2.dispose()
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val idx = itemAt(event.y)
        if (idx >= 0) return items[idx].label
        if (accountHit(event.x, event.y)) return Accounts.current()?.username ?: "Accounts"
        if (logoHit(event.x, event.y)) return "Aerix Launcher ${AppEnv.version}"
        return null
    }
}

private object ToolTipManagerHolder {
    fun enable(c: JComponent) { javax.swing.ToolTipManager.sharedInstance().registerComponent(c) }
}

// ---------------------------------------------------------------------------------------------
// Toast
// ---------------------------------------------------------------------------------------------

class ToastPanel(private val message: String) : JComponent() {
    init {
        font = Fonts.bodyStrong(13f)
        isOpaque = false
    }

    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(font)
        return Dimension(fm.stringWidth(message) + UiScale.px(48), UiScale.px(44))
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        Paint.pill(g2, 0, 0, width, height, withAlpha(AerixColors.canvas, 235), withAlpha(AerixTheme.accent, 0x88))
        g2.font = font
        g2.color = AerixColors.textPrimary
        val fm = g2.fontMetrics
        g2.drawString(message, UiScale.px(20), (height - fm.height) / 2 + fm.ascent)
        g2.dispose()
    }
}

object Toast {
    private var active: JComponent? = null

    fun show(shell: JComponent, message: String) {
        active?.let { shell.remove(it) }
        val toast = ToastPanel(message)
        toast.bounds = Rectangle(
            shell.width - toast.preferredSize.width - UiScale.px(24),
            shell.height - toast.preferredSize.height - UiScale.px(24),
            toast.preferredSize.width, toast.preferredSize.height
        )
        shell.add(toast)
        active = toast
        shell.repaint()
        Timer(2600) {
            shell.remove(toast)
            if (active == toast) active = null
            shell.repaint()
        }.apply { isRepeats = false }.start()
    }
}
