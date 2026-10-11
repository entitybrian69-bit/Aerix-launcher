package net.kdt.pojavlaunch.ui;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.R;

/**
 * Maps the palette name kept in the preferences onto the theme that paints it.
 *
 * Every Mirai palette is one style: the same component shapes and the same
 * widget styling, only the colours differ.
 */
public final class MiraiTheme {

    public static final String PALETTE_DEFAULT = "embermire";

    private MiraiTheme() {
    }

    public static int themeFor(@NonNull String palette) {
        switch (palette) {
            case "velvetrose":
                return R.style.MiraiTheme_VelvetRose;
            case "mistwave":
                return R.style.MiraiTheme_Mistwave;
            case "glacier":
                return R.style.MiraiTheme_Glacier;
            case "verdantfield":
                return R.style.MiraiTheme_VerdantField;
            case "urbanash":
                return R.style.MiraiTheme_UrbanAsh;
            case "verdantdawn":
                return R.style.MiraiTheme_VerdantDawn;
            case "embermire":
            default:
                return R.style.MiraiTheme_Embermire;
        }
    }
}
