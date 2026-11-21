package net.kdt.aerix.desktop.ui.screens

import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Scheduler
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.auth.MicrosoftAuth
import net.kdt.aerix.desktop.core.auth.accounts.Account
import net.kdt.aerix.desktop.core.auth.accounts.Accounts
import net.kdt.aerix.desktop.ui.components.AerixButton
import net.kdt.aerix.desktop.ui.components.VerticalStack
import net.kdt.aerix.desktop.ui.components.AerixField
import net.kdt.aerix.desktop.ui.components.AerixSwitch
import net.kdt.aerix.desktop.ui.components.ButtonStyle
import net.kdt.aerix.desktop.ui.components.ClearPanel
import net.kdt.aerix.desktop.ui.components.IconButton
import net.kdt.aerix.desktop.ui.components.PillTab
import net.kdt.aerix.desktop.ui.components.SectionCard
import net.kdt.aerix.desktop.ui.components.SkinFace
import net.kdt.aerix.desktop.ui.components.aerixScroll
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
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Accounts and the skin wardrobe.
 *
 * Left: the account list with add / select / remove. Right: the selected profile's character in
 * the 3D viewer, which can be dragged to rotate, plus its skin controls (import a PNG, reset to the
 * official skin, switch the model between classic and slim).
 *
 * Microsoft sign-in is the same two options as the Android build: the browser code that the player
 * pastes back, or the device code that shows a short code and a link. Tokens are sealed in
 * `SecureStore` and never shown anywhere in this window.
 */
class AccountsScreen(private val shell: Shell) : JPanel(BorderLayout()), Refreshable {
    private var selected: Account? = null
    private var addMode = 0 // 0 = microsoft, 1 = offline
    // Deliberately not BoxLayout: see VerticalStack. The "Add account" button and the empty-state
    // label both ended up in half-width cells that pushed them against the middle of the column.
    private val listHost = VerticalStack(gap = UiScale.px(8))
    private val detailHost = ClearPanel(BorderLayout())

    init {
        isOpaque = false
        add(buildHeader(), BorderLayout.NORTH)
        val body = ClearPanel(BorderLayout())
        body.border = BorderFactory.createEmptyBorder(0, UiScale.px(Spacing.xl), UiScale.px(Spacing.xl), UiScale.px(Spacing.xl))
        val listScroll = aerixScroll(listHost)
        listScroll.preferredSize = Dimension(UiScale.px(330), 100)
        listScroll.minimumSize = Dimension(UiScale.px(290), 100)
        listScroll.border = BorderFactory.createEmptyBorder()
        body.add(listScroll, BorderLayout.WEST)
        body.add(detailHost, BorderLayout.CENTER)
        add(body, BorderLayout.CENTER)
        Accounts.onChange { Scheduler.ui { rebuild() } }
        selected = Accounts.current() ?: Accounts.all().firstOrNull()
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
            add(heading("Accounts", 20f).apply { alignmentX = 0f })
            add(label("Microsoft, offline and imported profiles — switch anytime", Fonts.label(12f), AerixColors.textMuted).apply { alignmentX = 0f })
        }
        header.add(titles, BorderLayout.WEST)
        return header
    }

    private fun rebuild() {
        listHost.removeAll()
        listHost.border = BorderFactory.createEmptyBorder(0, 0, 0, UiScale.px(8))
        val all = Accounts.all()
        if (all.isEmpty()) {
            listHost.add(Box.createVerticalStrut(UiScale.px(20)))
            listHost.add(label("No accounts yet", Fonts.bodyStrong(13f), AerixColors.textMuted).apply { alignmentX = 0.5f })
        }
        all.forEach { acc ->
            listHost.add(AccountRow(acc, acc === selected) { selected = acc; rebuild() })
        }
        listHost.add(Box.createVerticalStrut(UiScale.px(8)))
        val addButton = AerixButton("Add account", ButtonStyle.PRIMARY, "add").apply {
            addActionListener { showAddDialog() }
        }
        addButton.alignmentX = 0f
        listHost.add(addButton)

        rebuildDetail()
        listHost.revalidate(); listHost.repaint()
    }

    private fun rebuildDetail() {
        detailHost.removeAll()
        val acc = selected
        if (acc == null) {
            detailHost.add(net.kdt.aerix.desktop.ui.components.EmptyState("accounts", "No account selected",
                "Add a Microsoft account to play online, or an offline profile to play single-player."), BorderLayout.CENTER)
            detailHost.revalidate(); detailHost.repaint()
            return
        }

        val card = ClearPanel(BorderLayout())
        card.border = BorderFactory.createEmptyBorder(0, UiScale.px(18), 0, 0)

        // header
        val head = ClearPanel(BorderLayout())
        val name = ClearPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(heading(acc.username, 22f).apply { alignmentX = 0f })
            val kind = when {
                acc.isMicrosoft -> "Microsoft account"
                acc.isOffline -> "Offline profile"
                else -> "Ely.by account"
            }
            add(label(kind + "  ·  " + (acc.effectiveUuid()), Fonts.label(11f), AerixColors.textMuted).apply { alignmentX = 0f })
        }
        head.add(name, BorderLayout.WEST)
        val actions = ClearPanel(FlowLayout(FlowLayout.RIGHT, UiScale.px(8), 0))
        if (Accounts.current() !== acc) {
            actions.add(AerixButton("Use this account", ButtonStyle.SECONDARY, "check").apply {
                compact = true
                addActionListener { Accounts.select(acc); shell.toast("Now playing as ${acc.username}"); rebuild() }
            })
        } else {
            actions.add(StatusBadge("ACTIVE", AerixColors.success))
        }
        actions.add(IconButton("delete", size = 36, tooltip = "Remove account").apply {
            addActionListener {
                val ok = JOptionPane.showConfirmDialog(this@AccountsScreen,
                    "Remove the profile “${acc.username}”?\nThe account's sealed token is deleted with it.",
                    "Remove account", JOptionPane.OK_CANCEL_OPTION)
                if (ok == JOptionPane.OK_OPTION) {
                    Accounts.remove(acc)
                    selected = Accounts.current()
                    shell.toast("Removed ${acc.username}")
                    rebuild()
                }
            }
        })
        head.add(actions, BorderLayout.EAST)
        head.preferredSize = Dimension(100, UiScale.px(56))
        card.add(head, BorderLayout.NORTH)

        // viewer + controls
        val content = ClearPanel(GridLayout(1, 2, UiScale.px(16), 0))
        content.border = BorderFactory.createEmptyBorder(UiScale.px(16), 0, 0, 0)
        content.add(CharacterViewer(acc))

        val controls = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val skinCard = SectionCard("Skin", "Imported skins stay on this PC")
        val skinActions = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(8), UiScale.px(4)))
        skinActions.add(AerixButton("Import skin…", ButtonStyle.SECONDARY, "upload").apply {
            compact = true
            addActionListener {
                val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("Minecraft skin (PNG)", "png") }
                if (chooser.showOpenDialog(this@AccountsScreen) == JFileChooser.APPROVE_OPTION) {
                    if (SkinFace.importSkin(acc, chooser.selectedFile)) {
                        shell.toast("Skin imported")
                        rebuildDetail()
                    } else shell.toast("That PNG is not a 64×64 or 64×32 Minecraft skin")
                }
            }
        })
        skinActions.add(AerixButton("Reset to official", ButtonStyle.GHOST, "refresh").apply {
            compact = true
            addActionListener {
                runCatching { SkinFace.skinFile(acc).delete() }
                shell.toast("Skin reset")
                rebuildDetail()
            }
        })
        skinCard.body.add(skinActions)

        val modelRow = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(6), 0))
        listOf("Classic" to "classic", "Slim" to "slim").forEach { (nameOf, value) ->
            modelRow.add(PillTab(nameOf, acc.skinModel == value).apply {
                addActionListener {
                    acc.skinModel = value
                    acc.save()
                    rebuildDetail()
                }
            })
        }
        skinCard.body.add(net.kdt.aerix.desktop.ui.components.SettingRow("Model", "Slim arms are 3 pixels wide instead of 4.", modelRow))
        controls.add(skinCard)
        controls.add(vspace(12))

        val tokenCard = SectionCard("Session", "Tokens are sealed with DPAPI (AES-GCM fallback)")
        if (acc.isMicrosoft) {
            val expires = if (acc.expiresAt > 0)
                java.time.Instant.ofEpochMilli(acc.expiresAt).toString().replace("T", " ").take(19) + " UTC"
            else "unknown"
            tokenCard.body.add(label("Access token expires: $expires", Fonts.label(11.5f), AerixColors.textSecondary))
            tokenCard.body.add(vspace(6))
            tokenCard.body.add(AerixButton("Refresh sign-in", ButtonStyle.SECONDARY, "refresh").apply {
                compact = true
                addActionListener {
                    Scheduler.io("refresh-token") {
                        val outcome = runCatching { Accounts.ensureFreshToken(acc) }
                        Scheduler.ui {
                            outcome.onSuccess { shell.toast("Session refreshed") }
                                .onFailure {
                                    JOptionPane.showMessageDialog(this@AccountsScreen,
                                        "The session expired and could not be refreshed:\n\n${it.message}\n\nSign in again.",
                                        "Session expired", JOptionPane.WARNING_MESSAGE)
                                    showMicrosoftSignIn()
                                }
                        }
                    }
                }
            })
        } else {
            tokenCard.body.add(label("Offline profiles have no session; they can play single-player and LAN.", Fonts.label(11.5f), AerixColors.textMuted))
        }
        controls.add(tokenCard)
        controls.add(Box.createVerticalGlue())
        content.add(controls)
        card.add(content, BorderLayout.CENTER)
        detailHost.add(card, BorderLayout.CENTER)
        detailHost.revalidate(); detailHost.repaint()
    }

    // ---- add account --------------------------------------------------------------------------

    private fun showAddDialog() {
        val panel = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val tabs = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(6), 0))
        tabs.add(PillTab("Microsoft", true))
        panel.add(label("Add a Microsoft account", Fonts.headline(18f)))
        panel.add(vspace(6))
        panel.add(label("Choose how to sign in. Both use the official Microsoft login.", Fonts.body(12f), AerixColors.textSecondary))
        panel.add(vspace(16))
        panel.add(AerixButton("Open the browser", ButtonStyle.PRIMARY, "link").apply {
            addActionListener {
                SwingUtilities.getWindowAncestor(this@AccountsScreen)?.let { }
                showMicrosoftSignIn()
            }
        })
        panel.add(vspace(8))
        panel.add(AerixButton("Show a device code", ButtonStyle.SECONDARY, "keyboard").apply {
            addActionListener { showDeviceCode() }
        })
        panel.add(vspace(16))
        panel.add(label("Or play offline:", Fonts.label(11.5f), AerixColors.textMuted))
        val nameField = AerixField(placeholder = "Offline username", icon = "person").apply {
            preferredSize = Dimension(UiScale.px(240), UiScale.px(40))
        }
        val row = ClearPanel(FlowLayout(FlowLayout.LEFT, UiScale.px(8), 0))
        row.add(nameField)
        row.add(AerixButton("Add offline", ButtonStyle.GHOST, "add").apply {
            compact = true
            addActionListener {
                val acc = Accounts.addOffline(nameField.text)
                selected = acc
                shell.toast("Added offline profile ${acc.username}")
                SwingUtilities.getWindowAncestor(panel)?.let { w -> (w as? java.awt.Window)?.dispose() }
                rebuild()
            }
        })
        panel.add(row)
        JOptionPane.showMessageDialog(shell, panel, "Add account", JOptionPane.PLAIN_MESSAGE)
    }

    private fun showMicrosoftSignIn() {
        val url = MicrosoftAuth.browserUrl()
        RunCatchingShowUrl(url)
        val field = AerixField(placeholder = "Paste the redirected URL or the code", icon = "keyboard").apply {
            preferredSize = Dimension(UiScale.px(420), UiScale.px(42))
        }
        val panel = ClearPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        panel.add(label("A browser window opened at login.live.com.", Fonts.bodyStrong(13f)))
        panel.add(vspace(4))
        panel.add(label("After signing in, the page lands on an empty redirect with a code in its address bar.\nPaste that whole address (or just the code) below.", Fonts.label(11.5f), AerixColors.textSecondary))
        panel.add(vspace(10))
        panel.add(field)
        val choice = JOptionPane.showConfirmDialog(shell, panel, "Microsoft sign-in", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        if (choice != JOptionPane.OK_OPTION) return
        val code = MicrosoftAuth.extractCode(field.text)
        if (code.isNullOrBlank()) { shell.toast("No code found in that text"); return }
        signInWith { MicrosoftAuth.completeWithBrowserCode(code) }
    }

    private fun RunCatchingShowUrl(url: String) {
        val opened = runCatching { MicrosoftAuth.openBrowser(url) }.getOrDefault(false)
        if (!opened) {
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(url), null)
            shell.toast("Browser could not open — the sign-in URL is on your clipboard")
        }
    }

    private fun showDeviceCode() {
        val dialog = ProgressDialog(shell, "Requesting a device code")
        dialog.isVisible = true
        Scheduler.io("device-code") {
            val outcome = runCatching {
                val device = MicrosoftAuth.requestDeviceCode()
                    ?: throw IllegalStateException("Microsoft did not return a device code. Try the browser option instead.")
                Scheduler.ui {
                    dialog.dispose()
                    val code = device.userCode
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(code), null)
                    runCatching { MicrosoftAuth.openBrowser(device.verificationUri) }
                    val choice = JOptionPane.showConfirmDialog(
                        shell,
                        "Enter this code on the Microsoft page:\n\n     $code     (copied to the clipboard)\n\nThe page is already open. Press OK once you have approved it.",
                        "Microsoft sign-in", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE
                    )
                    if (choice != JOptionPane.OK_OPTION) return@ui
                    val pollDialog = ProgressDialog(shell, "Waiting for approval")
                    pollDialog.isVisible = true
                    Scheduler.io("device-poll") {
                        val result = runCatching { MicrosoftAuth.completeWithDeviceCode(device) }
                        Scheduler.ui {
                            pollDialog.dispose()
                            result.onSuccess { session ->
                                if (session == null) return@onSuccess
                                val acc = Accounts.addMicrosoft(session)
                                selected = acc
                                Accounts.select(acc)
                                shell.toast("Signed in as ${acc.username}")
                                rebuild()
                            }.onFailure {
                                JOptionPane.showMessageDialog(shell, "Sign-in failed:\n${it.message}", "Microsoft sign-in", JOptionPane.ERROR_MESSAGE)
                            }
                        }
                    }
                }
            }
            if (outcome.isFailure) {
                Scheduler.ui {
                    dialog.dispose()
                    JOptionPane.showMessageDialog(shell, "Could not request a code:\n${outcome.exceptionOrNull()?.message}",
                        "Microsoft sign-in", JOptionPane.ERROR_MESSAGE)
                }
            }
        }
    }

    private fun signInWith(exchange: () -> MicrosoftAuth.Session) {
        val dialog = ProgressDialog(shell, "Signing in")
        dialog.isVisible = true
        Scheduler.io("ms-signin") {
            val outcome = runCatching { exchange() }
            Scheduler.ui {
                dialog.dispose()
                outcome.onSuccess { session ->
                    val acc = Accounts.addMicrosoft(session)
                    selected = acc
                    Accounts.select(acc)
                    shell.toast("Signed in as ${acc.username}")
                    rebuild()
                }.onFailure { e ->
                    Log.w("accounts", "sign-in failed: ${e.message}")
                    JOptionPane.showMessageDialog(shell, "Sign-in failed:\n\n${e.message}", "Microsoft sign-in", JOptionPane.ERROR_MESSAGE)
                }
            }
        }
    }
}

// ---- parts ---------------------------------------------------------------------------------

private class AccountRow(private val account: Account, private val selected: Boolean, onClick: () -> Unit) : JComponent() {
    private var hover = false
    private var face: BufferedImage? = null
    init {
        preferredSize = Dimension(UiScale.px(320), UiScale.px(66))
        maximumSize = Dimension(Int.MAX_VALUE, UiScale.px(66))
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
        val r = UiScale.px(Radii.cardSmall)
        g2.color = when {
            selected -> withAlpha(AerixTheme.accent, 32)
            hover -> AerixTheme.panelRaised()
            else -> AerixTheme.panel()
        }
        g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), r * 2f, r * 2f))
        g2.color = if (selected) withAlpha(AerixTheme.accent, 0xAA) else AerixColors.borderSoft
        g2.stroke = java.awt.BasicStroke(UiScale.px(1.1f))
        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, r * 2f, r * 2f))
        val size = UiScale.px(42)
        if (face == null) face = SkinFace.face(account, size)
        val img = face
        if (img != null) {
            g2.clip = java.awt.geom.Ellipse2D.Float(UiScale.px(11).toFloat(), ((height - size) / 2).toFloat(), size.toFloat(), size.toFloat())
            g2.drawImage(img, UiScale.px(11), (height - size) / 2, null)
            g2.clip = null
        }
        g2.font = Fonts.titleMedium(13f)
        g2.color = AerixColors.textPrimary
        g2.drawString(account.username, UiScale.px(64), height / 2 - 2)
        g2.font = Fonts.label(10.5f)
        g2.color = if (Accounts.current() === account) AerixTheme.accent else AerixColors.textMuted
        g2.drawString(
            when {
                Accounts.current() === account -> "Active profile"
                account.isMicrosoft -> "Microsoft"
                else -> "Offline"
            },
            UiScale.px(64), height / 2 + UiScale.px(14)
        )
        g2.dispose()
    }
}

/** The draggable 3D character. Rotation is a repaint, not a render loop: cheap and always smooth. */
private class CharacterViewer(private val account: Account) : JComponent() {
    private var image: BufferedImage? = null
    private var key = ""
    private var lastX = 0
    private var lastY = 0
    private var yaw = -18f
    private var pitch = 0f
    private var slim = true

    init {
        preferredSize = Dimension(UiScale.px(300), UiScale.px(420))
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { lastX = e.x; lastY = e.y }
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                yaw += (e.x - lastX) * 0.9f
                pitch = (pitch + (e.y - lastY) * 0.5f).coerceIn(-28f, 28f)
                lastX = e.x; lastY = e.y
                key = ""
                repaint()
            }
        })
        cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
        toolTipText = "Drag to rotate"
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        Paint.glass(g2, 0, 0, width, height, UiScale.px(Radii.panel), fill = AerixTheme.panel())
        val k = "${account.effectiveUuid()}:${account.skinModel}:${width}x$height:${System.identityHashCode(image)}"
        if (k != key) {
            image = SkinFace.renderCharacter(
                SkinFace.skin(account), width, height,
                yawDegrees = yaw, pitchDegrees = pitch,
                slimModel = account.skinModel == "slim", username = ""
            )
            key = k
        }
        image?.let { g2.drawImage(it, 0, 0, null) }
        g2.font = Fonts.label(10f)
        g2.color = AerixColors.textMuted
        g2.drawString("Drag to rotate", UiScale.px(12), height - UiScale.px(12))
        g2.dispose()
    }
}

private class StatusBadge(private val text: String, private val color: Color) : JComponent() {
    init { preferredSize = Dimension(UiScale.px(72), UiScale.px(26)) }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        Paint.pill(g2, 0, 0, width, height, withAlpha(color, 32), withAlpha(color, 110))
        g2.font = Fonts.labelStrong(10f)
        g2.color = color
        val fm = g2.fontMetrics
        g2.drawString(text, (width - fm.stringWidth(text)) / 2, (height - fm.height) / 2 + fm.ascent)
        g2.dispose()
    }
}
