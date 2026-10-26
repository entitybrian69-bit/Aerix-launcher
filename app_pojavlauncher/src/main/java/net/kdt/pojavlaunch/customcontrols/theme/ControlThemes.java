package net.kdt.pojavlaunch.customcontrols.theme;

import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Ambient.DRIFT;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Ambient.FLICKER;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Ambient.GLOW;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Ambient.ORBIT;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Ambient.RISE;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Ambient.SCROLL;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Ambient.SHIMMER;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Ambient.SWEEP;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Ambient.TWINKLE;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Press.FILL;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Press.FLASH;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Press.GLOW;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Press.LIFT;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Press.RING;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Press.RIPPLE;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Press.SHEEN;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Press.SQUASH;

import android.media.AudioManager;
import android.view.HapticFeedbackConstants;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The control themes: eleven pieces of pixel art, each with the motion its own picture suggests.
 *
 * <p>They are deliberately not recolours of one another. The rocket pattern climbs, the nebula
 * drifts, the black hole's ring turns, the static jitters, the comet sweeps - the artwork decides
 * the animation, so a control wearing one of these looks like a small window onto that picture
 * rather than a button with a picture behind it.
 */
public final class ControlThemes {

    private static final int TAP = HapticFeedbackConstants.VIRTUAL_KEY;
    private static final int SOFT = HapticFeedbackConstants.KEYBOARD_TAP;
    private static final int TICK = HapticFeedbackConstants.CLOCK_TICK;
    private static final int HEAVY = HapticFeedbackConstants.LONG_PRESS;
    private static final int CLICK = HapticFeedbackConstants.CONTEXT_CLICK;

    private static final int SND_CLICK = AudioManager.FX_KEY_CLICK;
    private static final int SND_KEY = AudioManager.FX_KEYPRESS_STANDARD;
    private static final int SND_SPACE = AudioManager.FX_KEYPRESS_SPACEBAR;
    private static final int SND_RETURN = AudioManager.FX_KEYPRESS_RETURN;
    private static final int SND_DELETE = AudioManager.FX_KEYPRESS_DELETE;
    private static final int SND_UP = AudioManager.FX_FOCUS_NAVIGATION_UP;
    private static final int SND_DOWN = AudioManager.FX_FOCUS_NAVIGATION_DOWN;

    /** Dark text for the one light picture; 0 means "keep whatever the layout uses". */
    private static final int DARK_TEXT = 0xFF14202A;

    public static final List<ControlTheme> ALL;

    static {
        List<ControlTheme> themes = new ArrayList<>(11);

        // A pale teapot pattern: drifts like something floating, and ripples when touched.
        themes.add(new ControlTheme("teapots", R.string.ctrl_theme_teapots,
                R.drawable.ctrl_theme_teapot, DRIFT, RIPPLE,
                0x40FFFFFF, DARK_TEXT, SOFT, SND_CLICK));

        // Rockets: the pattern climbs, and the control lifts under the finger.
        themes.add(new ControlTheme("rockets", R.string.ctrl_theme_rockets,
                R.drawable.ctrl_theme_rocket, RISE, LIFT,
                0x59000000, 0, TAP, SND_UP));

        // Nebula and moons: slow drift, glowing edge, a low heavy tick.
        themes.add(new ControlTheme("nebula", R.string.ctrl_theme_nebula,
                R.drawable.ctrl_theme_nebula, DRIFT, GLOW,
                0x4D000000, 0, TICK, SND_SPACE));

        // Sparse stardust sky: stars twinkle, ripple spreads from the touch.
        themes.add(new ControlTheme("stardust", R.string.ctrl_theme_stardust,
                R.drawable.ctrl_theme_stardust, TWINKLE, RIPPLE,
                0x66000000, 0, TICK, SND_DOWN));

        // A wide aurora: slides sideways under a sweeping highlight.
        themes.add(new ControlTheme("aurora", R.string.ctrl_theme_aurora,
                R.drawable.ctrl_theme_aurora, SCROLL, SHEEN,
                0x59000000, 0, SOFT, SND_CLICK));

        // A galaxy swirl: turns slowly, glows on press.
        themes.add(new ControlTheme("galaxy", R.string.ctrl_theme_galaxy,
                R.drawable.ctrl_theme_galaxy, SHIMMER, GLOW,
                0x59000000, 0, TICK, SND_SPACE));

        // Moon over a sunset: the glow breathes; press flashes like moonlight.
        themes.add(new ControlTheme("moonrise", R.string.ctrl_theme_moonrise,
                R.drawable.ctrl_theme_moonrise, GLOW, FLASH,
                0x4D000000, 0, SOFT, SND_SPACE));

        // Black hole: the disk turns, press sends a ring outward.
        themes.add(new ControlTheme("horizon", R.string.ctrl_theme_horizon,
                R.drawable.ctrl_theme_horizon, ORBIT, RING,
                0x59000000, 0, HEAVY, SND_RETURN));

        // Cyan interference: jitters like a bad signal, flash on press.
        themes.add(new ControlTheme("static", R.string.ctrl_theme_static,
                R.drawable.ctrl_theme_static, FLICKER, FLASH,
                0x4D000000, 0, CLICK, SND_KEY));

        // Blue sky full of coloured crosses: everything twinkles at its own pace.
        themes.add(new ControlTheme("starcross", R.string.ctrl_theme_starcross,
                R.drawable.ctrl_theme_starcross, TWINKLE, GLOW,
                0x59000000, 0, TAP, SND_UP));

        // Vaporwave comet: a streak crosses on its own, and again when pressed.
        themes.add(new ControlTheme("comet", R.string.ctrl_theme_comet,
                R.drawable.ctrl_theme_comet, SWEEP, FILL,
                0x4D000000, 0, CLICK, SND_RETURN));

        ALL = Collections.unmodifiableList(themes);
    }

    private ControlThemes() {}

    /** The theme with this id, or null when it is off, unknown, or was removed by an update. */
    @Nullable
    public static ControlTheme byId(@Nullable String id) {
        if (id == null || id.isEmpty()) return null;
        // Linear on purpose: eleven entries, called when a control is built or restyled, not per frame.
        for (ControlTheme theme : ALL) {
            if (theme.id.equals(id)) return theme;
        }
        return null;
    }
}
