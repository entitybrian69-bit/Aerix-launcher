package net.kdt.aerix.desktop.ui.components

import net.kdt.aerix.desktop.ui.icons.Icons
import net.kdt.aerix.desktop.ui.theme.AerixColors
import net.kdt.aerix.desktop.ui.theme.AerixTheme
import net.kdt.aerix.desktop.ui.theme.Fonts
import net.kdt.aerix.desktop.ui.theme.Paint
import net.kdt.aerix.desktop.ui.theme.Radii
import net.kdt.aerix.desktop.ui.theme.Spacing
import net.kdt.aerix.desktop.ui.theme.UiScale
import net.kdt.aerix.desktop.ui.theme.darken
import net.kdt.aerix.desktop.ui.theme.mix
import net.kdt.aerix.desktop.ui.theme.withAlpha
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import javax.swing.AbstractButton
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.Timer
import javax.swing.border.EmptyBorder
import javax.swing.plaf.basic.BasicScrollBarUI

/**
 * The shared widget vocabulary of the Aerix shell.
 *
 * Every screen is assembled from these, so hover/press/focus behaviour, colours and radii stay
 * consistent with the Android launcher instead of drifting screen by screen. Each widget paints
 * itself through [AerixColors] and [AerixTheme], which means changing the accent colour or the
 * "Layout transparency" slider repaints the whole launcher live, exactly like the Compose build.
 */
object Ui {
    val transparent = Color(0, 0, 0, 0)
}

/** Base class: transparent, non-opaque, hover/press tracking, hand cursor. */
abstract class AerixControl : JComponent() {
    protected var hovered = false
    protected var pressed = false
    protected var focusedRing = false

    init {
        isOpaque = false
        isFocusable = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hovered = true; repaint(); onHover(true) }
            override fun mouseExited(e: MouseEvent) { hovered = false; pressed = false; repaint(); onHover(false) }
            override fun mousePressed(e: MouseEvent) { pressed = true; repaint() }
            override fun mouseReleased(e: MouseEvent) {
                val was = pressed; pressed = false; repaint()
                if (was && contains(e.point) && isEnabled) onClick(e)
            }
        })
        addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusGained(e: java.awt.event.FocusEvent) { focusedRing = true; repaint() }
            override fun focusLost(e: java.awt.event.FocusEvent) { focusedRing = false; repaint() }
        })
    }

    protected open fun onHover(hovering: Boolean) {}
    protected open fun onClick(e: MouseEvent) {}

    // A single button-shaped API on top of the hover/click hooks, so a click can be wired from
    // anywhere with addActionListener { } exactly like Swing's AbstractButton.
    private val actionListeners = ArrayList<() -> Unit>()
    fun addActionListener(listener: () -> Unit) { actionListeners.add(listener) }
    protected fun fireAction() { actionListeners.toList().forEach { runCatching { it() } } }

    // Value changes: sliders and switches both report through this hook.
    private val changeListeners = ArrayList<() -> Unit>()
    fun addChangeListener(listener: () -> Unit) { changeListeners.add(listener) }
    protected fun fireChanged() { changeListeners.toList().forEach { runCatching { it() } } }
}

// ---------------------------------------------------------------------------------------------
// Panels
// ---------------------------------------------------------------------------------------------

/** A plain transparent container that never paints a background (so wallpapers show through). */
open class ClearPanel(layout: java.awt.LayoutManager? = null) : JPanel(layout) {
    init { isOpaque = false }
    override fun paintComponent(g: Graphics) { /* deliberately empty */ }

    /**
     * Rows inside a `BoxLayout.Y_AXIS` must not grow. Swing's default maximum is 32767, so a box
     * with spare height hands the space to its children and a label ends up 80 pixels tall.
     * Panels that really do want to stretch (page bodies) declare it with [grow].
     */
    var grow: Boolean = false

    override fun getMaximumSize(): Dimension =
        if (grow) super.getMaximumSize() else Dimension(Int.MAX_VALUE, preferredSize.height)
}

/**
 * A vertical layout that does exactly what it says.
 *
 * `BoxLayout` is the obvious choice and it is what this launcher used first, but it has two
 * behaviours that produced visible bugs on the shipped pages: it hands children their *maximum*
 * size (so a label becomes as tall as the page), and when a child's maximum width is smaller than
 * the container it distributes the leftover in ways that depend on the container's own maximum —
 * measured on the accounts page: a 171-pixel button in a 330-pixel column landed at x=161 with a
 * 161-pixel cell, and an empty-state message centred in 1070 pixels was drawn from x=0 and ran off
 * the card entirely.
 *
 * This layout has no such rules. Children are stacked top to bottom at their preferred height,
 * every cell is the full available width, and [alignment] decides where inside that cell the child
 * is drawn. Children with an unbounded maximum width (rows, rules, buttons) fill the cell; labels
 * keep their preferred width so that their own alignment can centre or right-align them.
 *
 * Used both as a `LayoutManager` ([of]) and as a panel ([VerticalStack]).
 */
open class VerticalLayout(
    var gap: Int = 0,
    /** 0 = left, 0.5 = centre, 1 = right. A child's own [java.awt.Component.getAlignmentX] wins. */
    var alignment: Float = 0f
) : java.awt.LayoutManager {

    companion object {
        /** A transparent panel using this layout; the usual way to build a column. */
        fun of(gap: Int = 0, alignment: Float = 0f, vararg rows: java.awt.Component): ClearPanel {
            val panel = ClearPanel()
            panel.layout = VerticalLayout(gap, alignment)
            rows.forEach { panel.add(it) }
            return panel
        }
    }

    override fun addLayoutComponent(name: String?, comp: java.awt.Component) = Unit
    override fun removeLayoutComponent(comp: java.awt.Component) = Unit

    private fun visible(parent: java.awt.Container) = parent.components.filter { it.isVisible }

    override fun preferredLayoutSize(parent: java.awt.Container): Dimension {
        var w = 0
        var h = 0
        visible(parent).forEachIndexed { index, c ->
            val p = c.preferredSize
            w = maxOf(w, p.width)
            h += p.height
            if (index > 0) h += gap
        }
        val insets = parent.insets
        return Dimension(w + insets.left + insets.right, h + insets.top + insets.bottom)
    }

    override fun minimumLayoutSize(parent: java.awt.Container): Dimension = preferredLayoutSize(parent)

    override fun layoutContainer(parent: java.awt.Container) {
        val insets = parent.insets
        val availW = parent.width - insets.left - insets.right
        var y = insets.top
        val rows = visible(parent)
        rows.forEachIndexed { index, c ->
            val p = c.preferredSize
            // Text keeps its own width so that `alignmentX` decides where it sits. Anything else
            // that reports an unbounded maximum width (buttons, rows, rules) fills the column.
            val stretch = c.maximumSize.width >= Int.MAX_VALUE &&
                c !is javax.swing.JLabel && c !is javax.swing.AbstractButton
            // Buttons hug their label; rows, rules and cards fill the column.
            val w = if (stretch) availW else minOf(p.width, availW)
            val align = if (c.alignmentX == 0.5f || c.alignmentX == 1f) c.alignmentX else alignment
            val x = insets.left + when {
                stretch -> 0
                align >= 0.99f -> availW - w
                align >= 0.49f -> (availW - w) / 2
                else -> 0
            }
            c.setBounds(x, y, w, p.height)
            y += p.height
            if (index < rows.size - 1) y += gap
        }
    }
}

/** A transparent column. See [VerticalLayout]. */
open class VerticalStack(gap: Int = 0, alignment: Float = 0f) : ClearPanel() {
    init {
        layout = VerticalLayout(gap, alignment)
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

/** A glass card, the direct descendant of the Compose `Surface` with `liquidGlass`. */
open class GlassCard(
    var radius: Int = Radii.panel,
    var strong: Boolean = false,
    var padding: Insets = Insets(Spacing.xl, Spacing.xl, Spacing.xl, Spacing.xl)
) : JPanel(java.awt.BorderLayout()) {
    init {
        isOpaque = false
        border = EmptyBorder(padding)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val fill = if (strong) AerixTheme.panelRaised() else AerixTheme.panel()
        Paint.glass(g2, 0, 0, width, height, UiScale.px(radius), fill = fill)
        if (AerixTheme.layoutTransparency() < 70) Paint.accentGlow(g2, 0, 0, width, height, withAlpha(AerixTheme.accent, 18))
        g2.dispose()
        super.paintComponent(g)
    }
}

/** A card with no shadow, used inside scroll areas where shadows smear. */
open class FlatCard(var radius: Int = Radii.card) : JPanel(java.awt.BorderLayout()) {
    init { isOpaque = false }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        Paint.glass(g2, 0, 0, width, height, UiScale.px(radius), shadow = false)
        g2.dispose()
        super.paintComponent(g)
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

enum class ButtonStyle { PRIMARY, SECONDARY, GHOST, DANGER, GLASS }

/**
 * The Aerix primary/secondary button. The Android build uses a filled accent pill for the main
 * action ("CREATE A WORLD", "PLAY", "Create & Install Instance") and a translucent glass capsule
 * for everything else; both are here, and both animate their fill slightly on hover.
 */
class AerixButton(
    labelText: String = "",
    var style: ButtonStyle = ButtonStyle.SECONDARY,
    var icon: String? = null
) : AbstractButton() {
    fun setLabelText(v: String) { text = v; revalidate(); repaint() }
    private var hoverAmount = 0f
    private var animator: Timer? = null
    var radiusOverride: Int? = null
    var compact = false
    var horizontalPadding = 22

    init {
        // AbstractButton does not create its model until a concrete Swing subclass calls init(),
        // so a bare AbstractButton subclass has to provide one before anything listens to it.
        if (model == null) setModel(javax.swing.DefaultButtonModel())
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        text = labelText
        font = Fonts.titleMedium(13.5f)
        foreground = AerixColors.textPrimary
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        model.addChangeListener { repaint() }
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { animateTo(1f) }
            override fun mouseExited(e: MouseEvent) { animateTo(0f) }
        })
        addPropertyChangeListener("enabled") { repaint() }
    }

    private fun animateTo(target: Float) {
        animator?.stop()
        val start = hoverAmount
        val started = System.currentTimeMillis()
        animator = Timer(16) { e ->
            val t = ((System.currentTimeMillis() - started) / 140f).coerceIn(0f, 1f)
            hoverAmount = start + (target - start) * t
            if (t >= 1f) (e.source as Timer).stop()
            repaint()
        }.also { it.start() }
    }

    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(font)
        val h = UiScale.px(if (compact) 34 else 46)
        val iconW = if (icon != null) UiScale.px(20) + Spacing.sm else 0
        val w = fm.stringWidth(text) + UiScale.px(horizontalPadding) * 2 + iconW
        return Dimension(maxOf(w, UiScale.px(48)), h)
    }

    /**
     * Buttons keep their own height inside a BoxLayout. Without this the default maximum (32767)
     * lets a Y-axis box stretch a pill into a full-height oval.
     */
    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val h = height
        val w = width
        val r = UiScale.px(radiusOverride ?: (h / 2))
        val enabled = isEnabled
        val baseAlpha = if (enabled) 1f else 0.45f
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, baseAlpha)
        val accent = AerixTheme.accent
        val fill: Color
        val border: Color?
        val fg: Color
        when (style) {
            ButtonStyle.PRIMARY -> {
                fill = mix(accent, Color.WHITE, hoverAmount * 0.12f)
                border = null
                fg = AerixTheme.onAccent
            }
            ButtonStyle.SECONDARY -> {
                fill = mix(withAlpha(Color(0x24, 0x33, 0x41), (0xE0 * AerixTheme.layoutTransparency() / 100).coerceIn(60, 224)), accent, hoverAmount * 0.10f)
                border = withAlpha(Color.WHITE, 0x3A)
                fg = AerixColors.textPrimary
            }
            ButtonStyle.GHOST -> {
                fill = withAlpha(Color.WHITE, (hoverAmount * 26).toInt())
                border = null
                fg = AerixColors.textSecondary
            }
            ButtonStyle.DANGER -> {
                fill = mix(withAlpha(AerixColors.danger, 0x30), AerixColors.danger, hoverAmount * 0.35f)
                border = withAlpha(AerixColors.danger, 0x70)
                fg = mix(AerixColors.textPrimary, Color.WHITE, hoverAmount)
            }
            ButtonStyle.GLASS -> {
                fill = if (hoverAmount > 0f) mix(AerixTheme.panel(), AerixTheme.panelRaised(), hoverAmount) else AerixTheme.panel()
                border = withAlpha(AerixColors.border, 0x70)
                fg = AerixColors.textPrimary
            }
        }
        if (hasFocus()) {
            g2.color = withAlpha(accent, 120)
            g2.stroke = BasicStroke(UiScale.px(2f))
            g2.draw(RoundRectangle2D.Float(1f, 1f, (w - 2).toFloat(), (h - 2).toFloat(), r * 2f, r * 2f))
        }
        Paint.pill(g2, 0, 0, w, h, fill, border)
        if (style == ButtonStyle.PRIMARY && hoverAmount > 0f) {
            g2.color = withAlpha(Color.WHITE, (hoverAmount * 22).toInt())
            g2.fill(RoundRectangle2D.Float(0f, 0f, w.toFloat(), h.toFloat(), r * 2f, r * 2f))
        }

        // content: icon + label, centred together
        g2.font = font
        val fm = g2.fontMetrics
        val textW = fm.stringWidth(text)
        val iconSize = UiScale.px(18)
        val icon0 = icon
        val gap = if (icon0 != null && text.isNotEmpty()) Spacing.sm else 0
        val totalW = textW + (if (icon0 != null) iconSize + gap else 0)
        var x = (w - totalW) / 2
        if (icon0 != null) {
            Icons.draw(g2, icon0, x, (h - iconSize) / 2, iconSize, fg)
            x += iconSize + gap
        }
        g2.color = fg
        val baseline = (h - fm.height) / 2 + fm.ascent
        g2.drawString(text, x, baseline)
        g2.dispose()
    }
}

/** A round icon button used in page headers and cards (the Android `IconButton`). */
class IconButton(
    var iconName: String,
    var tint: Color = AerixColors.textSecondary,
    var size: Int = 40,
    var fillColor: Color? = null,
    var tooltip: String? = null
) : AerixControl() {
    private var hover = 0f
    private var timer: Timer? = null

    init {
        preferredSize = Dimension(UiScale.px(size), UiScale.px(size))
        minimumSize = preferredSize
        if (tooltip != null) toolTipText = tooltip
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { animate(1f) }
            override fun mouseExited(e: MouseEvent) { animate(0f) }
        })
    }

    override fun onClick(e: MouseEvent) {
        super.onClick(e)
        fireAction()
    }

    private fun animate(target: Float) {
        timer?.stop()
        val start = hover
        val t0 = System.currentTimeMillis()
        timer = Timer(16) { e ->
            val t = ((System.currentTimeMillis() - t0) / 130f).coerceIn(0f, 1f)
            hover = start + (target - start) * t
            if (t >= 1f) (e.source as Timer).stop()
            repaint()
        }.also { it.start() }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val s = minOf(width, height)
        val bg = fillColor ?: withAlpha(Color.WHITE, (hover * 22).toInt())
        if (bg.alpha > 0) {
            g2.color = if (fillColor != null) mix(fillColor!!, Color.WHITE, hover * 0.1f) else bg
            g2.fill(Ellipse2D.Float((width - s) / 2f, (height - s) / 2f, s.toFloat(), s.toFloat()))
        }
        if (hasFocus()) {
            g2.color = withAlpha(AerixTheme.accent, 150)
            g2.stroke = BasicStroke(UiScale.px(1.6f))
            g2.draw(Ellipse2D.Float(1f, 1f, (width - 2).toFloat(), (height - 2).toFloat()))
        }
        val iconSize = (s * 0.52f).toInt()
        val color = if (!isEnabled) withAlpha(tint, 90) else mix(tint, AerixTheme.accent, hover * 0.7f)
        Icons.draw(g2, iconName, (width - iconSize) / 2, (height - iconSize) / 2, iconSize, color)
        g2.dispose()
    }
}

/** A pill-shaped tab, used by the Library, Discover and Settings segmented controls. */
class PillTab(
    labelText: String,
    var checkedState: Boolean = false,
    var icon: String? = null
) : AbstractButton() {
    private var hover = false

    init {
        if (model == null) setModel(javax.swing.DefaultButtonModel())
        text = labelText
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        font = Fonts.titleMedium(12.5f)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hover = true; repaint() }
            override fun mouseExited(e: MouseEvent) { hover = false; repaint() }
        })
        model.addChangeListener { repaint() }
    }

    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(font)
        val icon0 = icon
        val iconW = if (icon0 != null) UiScale.px(16) + Spacing.smCompact else 0
        return Dimension(fm.stringWidth(text) + UiScale.px(30) + iconW, UiScale.px(36))
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val r = UiScale.px(18)
        val fill = when {
            checkedState -> withAlpha(AerixTheme.accent, 0x3D)
            hover -> withAlpha(Color.WHITE, 18)
            else -> AerixTheme.panelTrack()
        }
        Paint.pill(g2, 0, 0, width, height, fill,
            if (checkedState) withAlpha(AerixTheme.accent, 0x88) else withAlpha(AerixColors.borderSoft, 0x40))
        g2.font = font
        val fm = g2.fontMetrics
        val iconSize = UiScale.px(15)
        val icon0 = icon
        val gap = if (icon0 != null) Spacing.smCompact else 0
        val textW = fm.stringWidth(text)
        val total = textW + (if (icon0 != null) iconSize + gap else 0)
        var x = (width - total) / 2
        val color = if (checkedState) AerixTheme.accent else if (hover) AerixColors.textPrimary else AerixColors.textSecondary
        if (icon0 != null) {
            Icons.draw(g2, icon0, x, (height - iconSize) / 2, iconSize, color)
            x += iconSize + gap
        }
        g2.color = color
        g2.drawString(text, x, (height - fm.height) / 2 + fm.ascent)
        g2.dispose()
    }
}

// ---------------------------------------------------------------------------------------------
// Inputs
// ---------------------------------------------------------------------------------------------

/** A round-rect text field matching `MineEditText`: glass track, accent caret focus ring. */
class AerixField(
    var placeholder: String = "",
    var text0: String = "",
    var icon: String? = null
) : JTextField(text0) {
    init {
        isOpaque = false
        border = EmptyBorder(UiScale.px(12), UiScale.px(if (icon != null) 40 else 14), UiScale.px(12), UiScale.px(14))
        font = Fonts.body(13.5f)
        foreground = AerixColors.textPrimary
        caretColor = AerixTheme.accent
        selectionColor = withAlpha(AerixTheme.accent, 90)
        selectedTextColor = AerixColors.canvas
        preferredSize = Dimension(UiScale.px(220), UiScale.px(44))
        addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusGained(e: java.awt.event.FocusEvent) = repaint()
            override fun focusLost(e: java.awt.event.FocusEvent) = repaint()
        })
        addCaretListener { repaint() }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val focused = hasFocus()
        Paint.pill(g2, 0, 0, width, height, AerixTheme.panelTrack(),
            if (focused) withAlpha(AerixTheme.accent, 0xB0) else withAlpha(AerixColors.borderSoft, 0x55))
        val icon0 = icon
        if (icon0 != null) {
            val s = UiScale.px(18)
            Icons.draw(g2, icon0, UiScale.px(13), (height - s) / 2, s,
                if (focused) AerixTheme.accent else AerixColors.textMuted)
        }
        if (this.text.isEmpty() && !focused) {
            g2.font = font
            g2.color = AerixColors.textMuted
            val fm = g2.fontMetrics
            val x = UiScale.px(if (icon0 != null) 40 else 14)
            g2.drawString(placeholder, x, (height - fm.height) / 2 + fm.ascent)
        }
        g2.dispose()
        super.paintComponent(g)
    }
}

/** Switch styled after the Compose `Switch` in MiraiSettingsComponents. */
class AerixSwitch(initial: Boolean = false) : AerixControl() {
    var on: Boolean = initial
        set(value) { field = value; repaint(); firePropertyChange("on", !value, value) }
    private var knob = if (initial) 1f else 0f
    private var timer: Timer? = null
    var label: String? = null

    init {
        preferredSize = Dimension(UiScale.px(48), UiScale.px(28))
        minimumSize = preferredSize
        isFocusable = true
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { on = !on; fireChanged(); fireAction(); animate() }
        })
    }

    private fun animate() {
        timer?.stop()
        val start = knob
        val target = if (on) 1f else 0f
        val t0 = System.currentTimeMillis()
        timer = Timer(14) { e ->
            val t = ((System.currentTimeMillis() - t0) / 150f).coerceIn(0f, 1f)
            knob = start + (target - start) * t
            if (t >= 1f) (e.source as Timer).stop()
            repaint()
        }.also { it.start() }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val h = height
        val trackW = width
        val trackH = (h * 0.72f).toInt().coerceAtLeast(UiScale.px(16))
        val y = (h - trackH) / 2
        val accent = AerixTheme.accent
        val track = mix(withAlpha(Color(0x3A, 0x44, 0x4C), 220), accent, knob)
        Paint.pill(g2, 0, y, trackW, trackH, track, withAlpha(Color.WHITE, 0x30))
        val knobR = (trackH * 0.78f) / 2
        val cx = (knobR + 2 + (trackW - 2 * knobR - 4) * knob).toInt()
        g2.color = mix(Color(0xE8, 0xEF, 0xF2), Color.WHITE, knob)
        g2.fill(Ellipse2D.Float(cx - knobR, y + trackH / 2f - knobR, knobR * 2, knobR * 2))
        if (hasFocus()) {
            g2.color = withAlpha(accent, 130)
            g2.stroke = BasicStroke(UiScale.px(1.4f))
            g2.draw(RoundRectangle2D.Float(0f, y.toFloat(), trackW.toFloat(), trackH.toFloat(), trackH.toFloat(), trackH.toFloat()))
        }
        g2.dispose()
    }
}

/** Slider with an accent-filled track, used for RAM, transparency, motion speed… */
class AerixSlider(
    var min: Int = 0,
    var max: Int = 100,
    value: Int = 0,
    var step: Int = 1,
    var suffix: String = "",
    var formatter: ((Int) -> String)? = null
) : AerixControl() {
    var value: Int = value.coerceIn(min, max)
        set(v) {
            val nv = (((v.coerceIn(min, max)) / step) * step)
            if (field == nv) return
            field = nv
            repaint()
            firePropertyChange("value", -1, nv)
            fireChanged()
        }

    init {
        preferredSize = Dimension(UiScale.px(220), UiScale.px(30))
        minimumSize = Dimension(UiScale.px(120), UiScale.px(30))
        var dragging = false
        val update = { x: Int ->
            val pad = knobRadius()
            val t = ((x - pad).toFloat() / (width - pad * 2).coerceAtLeast(1)).coerceIn(0f, 1f)
            this.value = min + Math.round(t * (max - min))
        }
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { dragging = true; update(e.x) }
            override fun mouseReleased(e: MouseEvent) { dragging = false }
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseDragged(e: MouseEvent) { if (dragging) update(e.x) }
        })
        addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: java.awt.event.KeyEvent) {
                when (e.keyCode) {
                    java.awt.event.KeyEvent.VK_LEFT -> this@AerixSlider.value = this@AerixSlider.value - step
                    java.awt.event.KeyEvent.VK_RIGHT -> this@AerixSlider.value = this@AerixSlider.value + step
                    java.awt.event.KeyEvent.VK_HOME -> this@AerixSlider.value = min
                    java.awt.event.KeyEvent.VK_END -> this@AerixSlider.value = max
                }
            }
        })
    }

    private fun knobRadius(): Int = UiScale.px(9)

    val displayText: String get() = formatter?.invoke(value) ?: "$value$suffix"

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val h = height
        val trackH = UiScale.px(6)
        val y = (h - trackH) / 2
        val pad = knobRadius()
        val usable = width - pad * 2
        val t = if (max == min) 0f else (value - min).toFloat() / (max - min)
        val accent = AerixTheme.accent
        Paint.pill(g2, 0, y, width, trackH, withAlpha(Color(0x7D, 0x90, 0x9D), 0x50))
        Paint.pill(g2, 0, y, (pad + usable * t).toInt().coerceAtLeast(trackH), trackH,
            mix(accent, Color.WHITE, if (hovered) 0.1f else 0f))
        val cx = (pad + usable * t).toInt()
        val r = knobRadius()
        g2.color = withAlpha(accent, 60)
        g2.fill(Ellipse2D.Float(cx - r - 3f, (h / 2f) - r - 3f, (r + 3) * 2f, (r + 3) * 2f))
        g2.color = Color(0xF2, 0xF7, 0xF5)
        g2.fill(Ellipse2D.Float(cx - r.toFloat(), (h / 2f) - r, r * 2f, r * 2f))
        g2.color = mix(accent, Color.WHITE, 0.2f)
        g2.fill(Ellipse2D.Float(cx - r + 3f, (h / 2f) - r + 3f, r * 2f - 6f, r * 2f - 6f))
        if (hasFocus()) {
            g2.color = withAlpha(accent, 120)
            g2.stroke = BasicStroke(UiScale.px(1.4f))
            g2.draw(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), 10f, 10f))
        }
        g2.dispose()
    }
}

// ---------------------------------------------------------------------------------------------
// Text
// ---------------------------------------------------------------------------------------------

/**
 * A one-line label that refuses to grow vertically.
 *
 * Everything sits in `BoxLayout` page bodies, and a plain `JLabel` reports a maximum size of
 * 32767×32767 — so the box hands it all the leftover height and the text floats in a tall cell.
 */
/**
 * A label that behaves inside a `BoxLayout`.
 *
 * Two Swing traps live here, both found by photographing the real pages:
 *
 *  * `BoxLayout` hands every child its **maximum** size. The default maximum is 32767 in both
 *    axes, so a label in a vertical box becomes as tall as the box and its text floats in the
 *    middle of a cell far taller than it needs. The height is therefore capped at the preferred
 *    height.
 *  * The width must stay uncapped. Capping it at the preferred width looks like the obvious fix
 *    for horizontal alignment, but `BoxLayout` then hands the child only *half* of a floating
 *    maximum (a max of 264 in a 900-pixel box becomes a 132-pixel cell, measured, not guessed)
 *    and the text is produced from a negative origin and clipped — an eyebrow rendered as
 *    "YOUR NEXT WOR". Leaving the width free gives the label the full row, which is what an
 *    alignment of 0 already means; the text simply starts at the left edge of that row.
 */
open class AerixLabel(text: String) : JLabel(text) {
    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

fun label(text: String, font: Font = Fonts.body(), color: Color = AerixColors.textPrimary): JLabel =
    AerixLabel(text).apply {
        this.font = font
        foreground = color
        isOpaque = false
    }

/**
 * A paragraph that wraps at [width] pixels. `JLabel` needs HTML for wrapping, and plain `\n` in a
 * label collapses into one long line that runs off the card — this is the safe way to show prose.
 */
fun paragraph(text: String, width: Int, font: Font = Fonts.body(12.5f), color: Color = AerixColors.textSecondary): JLabel {
    val safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\n", "<br>")
    return AerixLabel("<html><body style='width:${width}px'>$safe</body></html>").apply {
        this.font = font
        foreground = color
        isOpaque = false
    }
}

fun heading(text: String, size: Float = 22f, color: Color = AerixColors.textPrimary): JLabel =
    AerixLabel(text).apply {
        font = Fonts.headline(size)
        foreground = color
        isOpaque = false
    }

/** Small uppercase caption with letter spacing, used above section titles ("YOUR SPACE"). */
class EyebrowText(var text: String, var color: Color = Color(0xB4, 0x3F, 0xC4, 0xBE)) : JComponent() {
    init {
        font = Fonts.labelStrong(10.5f)
        preferredSize = Dimension(UiScale.px(font.size + text.length * 9), UiScale.px(18))
    }

    override fun getMaximumSize(): Dimension = preferredSize

    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(font)
        return Dimension(fm.stringWidth(spaced()) + UiScale.px(4), fm.height + UiScale.px(2))
    }

    fun spaced(): String = text.uppercase().toCharArray().joinToString("\u200A").replace("\u200A", " ")

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        g2.font = font
        g2.color = color
        g2.drawString(spaced(), 0, height - 3)
        g2.dispose()
    }
}

// ---------------------------------------------------------------------------------------------
// Layout helpers
// ---------------------------------------------------------------------------------------------

fun row(gap: Int = Spacing.md, vararg comps: java.awt.Component): JPanel =
    ClearPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, gap, 0)).apply { comps.forEach { add(it) } }

fun column(gap: Int = Spacing.md, vararg comps: java.awt.Component): JPanel =
    ClearPanel().apply {
        layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
        comps.forEach { c ->
            (c as? JComponent)?.alignmentX = 0f
            add(c)
            add(javax.swing.Box.createVerticalStrut(UiScale.px(gap)))
        }
    }

fun spacer(): java.awt.Component = javax.swing.Box.createHorizontalGlue()
fun vspace(h: Int): java.awt.Component = javax.swing.Box.createVerticalStrut(UiScale.px(h))

/** A card section: title, optional subtitle, then the body. Used all over Settings/Discover. */
class SectionCard(
    title: String,
    subtitle: String? = null
) : FlatCard(Radii.card) {
    val body: JPanel = ClearPanel().apply {
        layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
    }

    init {
        border = EmptyBorder(Spacing.lgPlus, Spacing.xl, Spacing.lgPlus, Spacing.xl)
        val head = ClearPanel(java.awt.BorderLayout())
        val titles = ClearPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(heading(title, 16f).apply { alignmentX = 0f })
            if (subtitle != null) add(label(subtitle, Fonts.body(12f), AerixColors.textMuted).apply { alignmentX = 0f })
        }
        head.add(titles, java.awt.BorderLayout.WEST)
        add(head, java.awt.BorderLayout.NORTH)
        add(body, java.awt.BorderLayout.CENTER)
    }

    /** Cards size to their content even inside a `BoxLayout.Y_AXIS` page body. */
    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

/** A settings row: label + optional description on the left, control on the right. */
class SettingRow(
    labelText: String,
    description: String? = null,
    val control: JComponent
) : ClearPanel(java.awt.BorderLayout()) {
    init {
        border = EmptyBorder(Spacing.sm, 0, Spacing.sm, 0)
        val left = ClearPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(label(labelText, Fonts.bodyStrong(13.5f)).apply { alignmentX = 0f })
            if (description != null) {
                add(JLabel("<html><body style='width:340px'>${escape(description)}</body></html>").apply {
                    font = Fonts.body(11.5f)
                    foreground = AerixColors.textMuted
                    isOpaque = false
                    alignmentX = 0f
                })
            }
        }
        add(left, java.awt.BorderLayout.CENTER)
        val right = ClearPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0))
        right.add(control)
        add(right, java.awt.BorderLayout.EAST)
    }

    companion object {
        fun escape(s: String) = s.replace("<", "&lt;").replace(">", "&gt;")
    }
}

/** Vertical scroll with a thin accent scrollbar, so long pages keep the glass look. */
fun aerixScroll(view: java.awt.Component, horizontal: Boolean = false): JScrollPane {
    val pane = JScrollPane(view)
    pane.isOpaque = false
    pane.viewport.isOpaque = false
    pane.border = BorderFactory.createEmptyBorder()
    pane.verticalScrollBar.let { bar -> styleScrollBar(bar) }
    pane.horizontalScrollBar.let { bar -> styleScrollBar(bar) }
    pane.horizontalScrollBarPolicy = if (horizontal) ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
    else ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    pane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
    pane.verticalScrollBar.unitIncrement = 24
    pane.horizontalScrollBar.unitIncrement = 24
    return pane
}

private fun styleScrollBar(bar: JScrollBar) {
    bar.isOpaque = false
    bar.preferredSize = Dimension(UiScale.px(8), UiScale.px(8))
    bar.ui = object : BasicScrollBarUI() {
        override fun configureScrollBarColors() {
            thumbColor = withAlpha(Color.WHITE, 60)
            trackColor = Color(0, 0, 0, 0)
        }
        override fun createDecreaseButton(orientation: Int): JButton = invisibleButton()
        override fun createIncreaseButton(orientation: Int): JButton = invisibleButton()
        private fun invisibleButton(): JButton = object : JButton() {
            init { preferredSize = Dimension(0, 0); minimumSize = preferredSize; maximumSize = preferredSize }
            override fun paint(g: Graphics) {}
        }
        override fun paintTrack(g: Graphics, c: JComponent, trackBounds: Rectangle) {}
        override fun paintThumb(g: Graphics, c: JComponent, thumbBounds: Rectangle) {
            if (thumbBounds.isEmpty || !scrollbar.isEnabled) return
            val g2 = g.create() as Graphics2D
            Paint.quality(g2)
            g2.color = if (isThumbRollover) withAlpha(AerixTheme.accent, 150) else withAlpha(Color.WHITE, 70)
            val w = thumbBounds.width
            val inset = 2
            g2.fill(RoundRectangle2D.Float(
                thumbBounds.x.toFloat() + inset, thumbBounds.y.toFloat() + inset,
                (thumbBounds.width - inset * 2).toFloat(), (thumbBounds.height - inset * 2).toFloat(),
                (w - inset * 2).toFloat(), (w - inset * 2).toFloat()
            ))
            g2.dispose()
        }
    }
}

/** A progress bar in the Aerix accent, replaced by [Paint.glass] for its track. */
class AerixProgress(var value: Float = 0f) : JComponent() {
    init {
        preferredSize = Dimension(UiScale.px(220), UiScale.px(10))
        minimumSize = preferredSize
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val h = height
        Paint.pill(g2, 0, 0, width, h, withAlpha(Color(0x7D, 0x90, 0x9D), 0x40))
        val w = (width * value.coerceIn(0f, 1f)).toInt()
        if (w > 0) Paint.pill(g2, 0, 0, maxOf(w, h), h, AerixTheme.accent)
        g2.dispose()
    }
}

/** The empty-state block used by lists that have nothing to show yet. */
class EmptyState(icon: String, title: String, message: String) : ClearPanel() {
    init {
        // A stack rather than a BoxLayout: the message is longer than its preferred width allows
        // for, and BoxLayout centred it in a cell it then drew from x=0, running off the card.
        layout = VerticalLayout(gap = 0, alignment = 0.5f)
        val art = object : JComponent() {
            init { preferredSize = Dimension(UiScale.px(76), UiScale.px(76)); alignmentX = CENTER_ALIGNMENT }
            // BoxLayout would otherwise stretch the badge into a card-sized ellipse
            override fun getMaximumSize(): Dimension = preferredSize
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                Paint.quality(g2)
                g2.color = withAlpha(Color.WHITE, 16)
                g2.fill(Ellipse2D.Float(0f, 0f, width.toFloat(), height.toFloat()))
                Icons.draw(g2, icon, width / 4, height / 4, width / 2, withAlpha(AerixTheme.accent, 200))
                g2.dispose()
            }
        }
        add(art)
        add(vspace(Spacing.md))
        add(label(title, Fonts.title(17f)).apply { alignmentX = CENTER_ALIGNMENT })
        add(vspace(Spacing.xs))
        add(paragraph(message, UiScale.px(420), Fonts.body(12.5f), AerixColors.textMuted).apply { alignmentX = CENTER_ALIGNMENT })
        alignmentX = CENTER_ALIGNMENT
    }
}

/** A coloured dot used for status ("Ready", "Installing"). */
class StatusDot(var color: Color = AerixColors.success, var pulse: Boolean = false) : JComponent() {
    private var phase = 0f
    private val timer = Timer(40) { phase += 0.05f; if (phase > 1f) phase = 0f; repaint() }

    init {
        preferredSize = Dimension(UiScale.px(18), UiScale.px(18))
        if (pulse) timer.start()
    }

    override fun addNotify() { super.addNotify(); if (pulse && !timer.isRunning) timer.start() }
    override fun removeNotify() { timer.stop(); super.removeNotify() }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        Paint.quality(g2)
        val r = minOf(width, height) / 2f - 2
        val cx = width / 2f
        val cy = height / 2f
        if (pulse) {
            g2.color = withAlpha(color, ((1f - phase) * 90).toInt())
            g2.fill(Ellipse2D.Float(cx - r * (1 + phase * 1.6f), cy - r * (1 + phase * 1.6f), r * 2 * (1 + phase * 1.6f), r * 2 * (1 + phase * 1.6f)))
        }
        g2.color = color
        g2.fill(Ellipse2D.Float(cx - r * 0.62f, cy - r * 0.62f, r * 1.24f, r * 1.24f))
        g2.dispose()
    }
}

/** A divider that matches the glass theme. */
class AerixDivider : JComponent() {
    init { preferredSize = Dimension(1, UiScale.px(1)); maximumSize = Dimension(Int.MAX_VALUE, UiScale.px(1)) }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.color = withAlpha(AerixColors.borderSoft, 0x50)
        g2.fillRect(0, 0, width, maxOf(1, UiScale.px(1)))
        g2.dispose()
    }
}
