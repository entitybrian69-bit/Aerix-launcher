package net.kdt.pojavlaunch.customcontrols.theme;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

/**
 * One animated look a control can wear.
 *
 * <p>Each theme is a piece of pixel art from the launcher's own collection, plus the motion that
 * art suggests: rockets climb, nebulae drift, stars twinkle, the black hole's ring turns. The
 * drawing lives in {@link ControlThemePainter}, so removing a theme is deleting its line in
 * {@link ControlThemes} - a layout that still names it falls back to the stock control.
 *
 * <p>Themes only ever change paint. They never touch key delivery, timing or hit areas: a themed
 * button sends exactly the same key event as an unthemed one, on the same frame.
 */
public final class ControlTheme {

    /** What the artwork does while the control is at rest. */
    public enum Ambient {
        /** The whole picture slides across and wraps - clouds, nebulae, patterns. */
        DRIFT,
        /** The picture climbs, like the rockets printed on it. */
        RISE,
        /** Stars light up and dim at their own pace, over a still sky. */
        TWINKLE,
        /** The picture bobs and breathes very slightly, like a held card. */
        SWAY,
        /** A ring turns around the middle - an accretion disk. */
        ORBIT,
        /** The picture's brightness jitters, like interference on a screen. */
        FLICKER,
        /** The picture turns slowly, so a swirl keeps swirling. */
        SHIMMER,
        /** A soft glow breathes behind the control - moonlight. */
        GLOW,
        /** A streak of light crosses the control every couple of seconds - a comet. */
        SWEEP,
        /** The picture slides sideways, slowly, like a wide sky. */
        SCROLL
    }

    /** What happens between touch down and release. */
    public enum Press {
        /** The edge lights up and fades. */
        GLOW,
        /** The control squashes under the finger and springs back. */
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
    /** The pixel art this theme wears. */
    @DrawableRes public final int art;
    public final Ambient ambient;
    public final Press press;
    /** Wash drawn over the art so the label stays readable on it. */
    public final int scrim;
    /** Text colour that survives the wash, or 0 to keep the layout's own. */
    public final int textColor;
    /** {@link android.view.HapticFeedbackConstants} value, felt on touch down. */
    public final int haptic;
    /** {@link android.media.AudioManager} sound effect, played on touch down. */
    public final int sound;

    ControlTheme(String id, @StringRes int nameRes, @DrawableRes int art, Ambient ambient,
                 Press press, int scrim, int textColor, int haptic, int sound) {
        this.id = id;
        this.nameRes = nameRes;
        this.art = art;
        this.ambient = ambient;
        this.press = press;
        this.scrim = scrim;
        this.textColor = textColor;
        this.haptic = haptic;
        this.sound = sound;
    }
}
