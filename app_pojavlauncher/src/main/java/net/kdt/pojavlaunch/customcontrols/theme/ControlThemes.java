package net.kdt.pojavlaunch.customcontrols.theme;

import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Fill.GLASS;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Fill.GRADIENT;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Fill.OUTLINE;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Fill.SCANLINE;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Fill.SOLID;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Fill.STRIPES;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Press.FILL;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Press.FLASH;
import static net.kdt.pojavlaunch.customcontrols.theme.ControlTheme.Press.GLOW;
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
 * The control themes, in the order the editor shows them.
 *
 * <p>Twenty five looks, grouped by what they do rather than by colour: the neon and metal ones are
 * about the edge, the glass and ice ones about depth, the arcade and matrix ones about screens,
 * and the gem and sky ones about the fill. Every one of them animates, feels and sounds different,
 * and any of them can be taken off again with one tap.
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

    /** Dark text for the light themes; 0 means "keep whatever the layout uses". */
    private static final int DARK_TEXT = 0xFF10161B;

    public static final List<ControlTheme> ALL;

    static {
        List<ControlTheme> themes = new ArrayList<>(25);

        // Edge light: the border is the show.
        themes.add(new ControlTheme("neon_pulse", R.string.ctrl_theme_neon_pulse,
                0xFF00E5FF, 0xFF0066FF, OUTLINE, GLOW, TAP, SND_CLICK, 0));
        themes.add(new ControlTheme("hologram", R.string.ctrl_theme_hologram,
                0xFF80DEEA, 0xFF18FFFF, OUTLINE, SHEEN, TICK, SND_UP, 0));
        themes.add(new ControlTheme("cyber", R.string.ctrl_theme_cyber,
                0xFFFF00E5, 0xFF00E5FF, SCANLINE, RING, CLICK, SND_KEY, 0));
        themes.add(new ControlTheme("obsidian", R.string.ctrl_theme_obsidian,
                0xFF311B92, 0xFF120A1F, SOLID, FLASH, HEAVY, SND_DELETE, 0));
        themes.add(new ControlTheme("plasma", R.string.ctrl_theme_plasma,
                0xFFD500F9, 0xFF00E5FF, GRADIENT, GLOW, TAP, SND_SPACE, 0));

        // Glass & depth.
        themes.add(new ControlTheme("glass", R.string.ctrl_theme_glass,
                0xFFECEFF1, 0xFF90A4AE, GLASS, SHEEN, SOFT, SND_CLICK, 0));
        themes.add(new ControlTheme("frost", R.string.ctrl_theme_frost,
                0xFFB3E5FC, 0xFF4FC3F7, GLASS, SHEEN, SOFT, SND_CLICK, DARK_TEXT));
        themes.add(new ControlTheme("ripple", R.string.ctrl_theme_ripple,
                0xFF40C4FF, 0xFF0091EA, GLASS, RIPPLE, TAP, SND_KEY, 0));

        // Screens: arcade, pixel, CRT.
        themes.add(new ControlTheme("arcade", R.string.ctrl_theme_arcade,
                0xFFFFEA00, 0xFFFF3D00, SCANLINE, SQUASH, CLICK, SND_RETURN, DARK_TEXT));
        themes.add(new ControlTheme("pixel", R.string.ctrl_theme_pixel,
                0xFF66BB6A, 0xFF2E7D32, SOLID, SQUASH, SOFT, SND_KEY, 0));
        themes.add(new ControlTheme("matrix", R.string.ctrl_theme_matrix,
                0xFF00E676, 0xFF003B00, STRIPES, FILL, TICK, SND_DOWN, 0));
        themes.add(new ControlTheme("carbon", R.string.ctrl_theme_carbon,
                0xFF263238, 0xFF37474F, STRIPES, FLASH, CLICK, SND_DELETE, 0));

        // Metal & foil.
        themes.add(new ControlTheme("chrome", R.string.ctrl_theme_chrome,
                0xFFE0E0E0, 0xFF9E9E9E, GRADIENT, SHEEN, CLICK, SND_CLICK, DARK_TEXT));
        themes.add(new ControlTheme("gold", R.string.ctrl_theme_gold,
                0xFFFFD54F, 0xFFFF8F00, GRADIENT, SHEEN, TICK, SND_RETURN, DARK_TEXT));
        themes.add(new ControlTheme("sapphire", R.string.ctrl_theme_sapphire,
                0xFF1E88E5, 0xFF0D47A1, GRADIENT, GLOW, SOFT, SND_UP, 0));

        // Gems.
        themes.add(new ControlTheme("emerald", R.string.ctrl_theme_emerald,
                0xFF00C853, 0xFF1B5E20, GRADIENT, GLOW, SOFT, SND_UP, 0));
        themes.add(new ControlTheme("crimson", R.string.ctrl_theme_crimson,
                0xFFE53935, 0xFF4A0000, GRADIENT, RING, HEAVY, SND_RETURN, 0));
        themes.add(new ControlTheme("amethyst", R.string.ctrl_theme_amethyst,
                0xFFAB47BC, 0xFF4A148C, GRADIENT, GLOW, TICK, SND_SPACE, 0));
        themes.add(new ControlTheme("toxic", R.string.ctrl_theme_toxic,
                0xFF76FF03, 0xFF33691E, SOLID, SQUASH, HEAVY, SND_KEY, DARK_TEXT));

        // Sky & weather.
        themes.add(new ControlTheme("aurora", R.string.ctrl_theme_aurora,
                0xFF00E676, 0xFF00B0FF, GRADIENT, SHEEN, SOFT, SND_UP, 0));
        themes.add(new ControlTheme("ember", R.string.ctrl_theme_ember,
                0xFFFF6D00, 0xFFB71C1C, GRADIENT, GLOW, HEAVY, SND_SPACE, 0));
        themes.add(new ControlTheme("ocean", R.string.ctrl_theme_ocean,
                0xFF00BCD4, 0xFF001A1C, GRADIENT, RIPPLE, TICK, SND_DOWN, 0));
        themes.add(new ControlTheme("sunset", R.string.ctrl_theme_sunset,
                0xFFFF8A65, 0xFF6A1B9A, GRADIENT, SHEEN, SOFT, SND_CLICK, 0));
        themes.add(new ControlTheme("sakura", R.string.ctrl_theme_sakura,
                0xFFF8BBD0, 0xFFF06292, GRADIENT, SHEEN, SOFT, SND_UP, DARK_TEXT));
        themes.add(new ControlTheme("vaporwave", R.string.ctrl_theme_vaporwave,
                0xFFFF71CE, 0xFF01CDFE, GRADIENT, SHEEN, TICK, SND_SPACE, 0));

        ALL = Collections.unmodifiableList(themes);
    }

    private ControlThemes() {}

    /** The theme with this id, or null when it is off, unknown, or was removed by an update. */
    @Nullable
    public static ControlTheme byId(@Nullable String id) {
        if (id == null || id.isEmpty()) return null;
        // Linear on purpose: 25 entries, called when a control is built or restyled, not per frame.
        for (ControlTheme theme : ALL) {
            if (theme.id.equals(id)) return theme;
        }
        return null;
    }
}
