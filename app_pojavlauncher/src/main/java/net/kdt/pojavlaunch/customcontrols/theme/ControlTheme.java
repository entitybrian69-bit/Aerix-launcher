package net.kdt.pojavlaunch.customcontrols.theme;

import androidx.annotation.StringRes;

/**
 * One animated look a control can wear.
 *
 * <p>A theme is a description, not a drawing: it says which fill the control wears at rest, what
 * happens under a finger, what the phone should feel and say. The drawing lives in
 * {@link ControlThemePainter}, so adding a theme is a one line entry in {@link ControlThemes}, and
 * removing it is deleting that line - a layout that still names it simply falls back to the stock
 * control.
 *
 * <p>Themes only ever change paint. They never touch key delivery, timing or hit areas: a themed
 * button sends exactly the same key event as an unthemed one, on the same frame.
 */
public final class ControlTheme {

    /** How the control is painted while nothing is happening. */
    public enum Fill {
        /** One flat colour. */
        SOLID,
        /** Two colours, top to bottom. */
        GRADIENT,
        /** A dark wash with a bright inner rim - frosted glass. */
        GLASS,
        /** See-through middle, thick coloured edge. */
        OUTLINE,
        /** Diagonal stripes - carbon fibre, matrix, caution tape. */
        STRIPES,
        /** Horizontal scanlines - CRT, hologram, arcade. */
        SCANLINE
    }

    /** What happens between touch down and release. */
    public enum Press {
        /** The edge lights up and fades. */
        GLOW,
        /** The whole control squashes under the finger and springs back. */
        SQUASH,
        /** A circle spreads out from the middle. */
        RIPPLE,
        /** A ring runs off the edges. */
        RING,
        /** A quick wash of white. */
        FLASH,
        /** A highlight sweeps across, like light over a polished surface. */
        SHEEN,
        /** The colour rises from the bottom. */
        FILL,
        /** The control grows a little instead of shrinking. */
        LIFT
    }

    public final String id;
    @StringRes public final int nameRes;
    public final int accent;
    public final int accent2;
    public final Fill fill;
    public final Press press;
    /** {@link android.view.HapticFeedbackConstants} value, felt on touch down. */
    public final int haptic;
    /** {@link android.media.AudioManager} sound effect, played on touch down. */
    public final int sound;
    /** Text colour for themes too light for white, or 0 to keep the layout's own. */
    public final int textColor;

    ControlTheme(String id, @StringRes int nameRes, int accent, int accent2, Fill fill, Press press,
                 int haptic, int sound, int textColor) {
        this.id = id;
        this.nameRes = nameRes;
        this.accent = accent;
        this.accent2 = accent2;
        this.fill = fill;
        this.press = press;
        this.haptic = haptic;
        this.sound = sound;
        this.textColor = textColor;
    }
}
