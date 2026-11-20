package net.kdt.pojavlaunch.customcontrols.handleview;

import android.content.Context;

/**
 * Whether the floating Aerix logo is drawn while the player is playing.
 *
 * <p>The logo is one of the few things a player can neither move out of the way for good nor remove:
 * it sits over the game, over the controls and over whatever the game is drawing at that spot. This
 * is the switch that takes it off the screen during play and puts it back when the in-game menu is
 * open, so the menu is always reachable from itself.
 *
 * <p>The preference is stored next to the size and the dragged position, in the same file, because it
 * is the same decision: how the logo behaves on this device. Size, position and visibility all
 * survive separately - changing one must never erase another.
 */
public final class MenuButtonVisibility {
    private static final String KEY = "menuButtonHiddenWhilePlaying";

    /** Showing the logo is the behaviour everybody already has, so that is the default. */
    public static final boolean DEFAULT_HIDDEN_WHILE_PLAYING = false;

    private MenuButtonVisibility() {}

    /** True when the player asked for the logo to stay out of the way during play. */
    public static boolean isHiddenWhilePlaying(Context context) {
        try {
            return context.getSharedPreferences(MenuButtonSize.PREFERENCES, Context.MODE_PRIVATE)
                    .getBoolean(KEY, DEFAULT_HIDDEN_WHILE_PLAYING);
        } catch (ClassCastException invalidOldPreference) {
            return DEFAULT_HIDDEN_WHILE_PLAYING;
        }
    }

    public static void setHiddenWhilePlaying(Context context, boolean hidden) {
        context.getSharedPreferences(MenuButtonSize.PREFERENCES, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY, hidden).apply();
    }
}
