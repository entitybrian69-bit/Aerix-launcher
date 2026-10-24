package net.kdt.pojavlaunch.customcontrols.handleview;

import android.content.Context;

/** Size of the floating logo, independent of the Minecraft control layout's button scale. */
public final class MenuButtonSize {
    public static final int MIN_DP = 20;
    public static final int MAX_DP = 96;
    public static final int DEFAULT_DP = 46;
    private static final String KEY = "menuButtonSizeDp";
    static final String PREFERENCES = "aerix_menu_button";

    private MenuButtonSize() {}

    public static int clamp(int sizeDp) { return Math.max(MIN_DP, Math.min(MAX_DP, sizeDp)); }

    public static int read(Context context) {
        try {
            return clamp(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getInt(KEY, DEFAULT_DP));
        } catch (ClassCastException invalidOldPreference) {
            return DEFAULT_DP;
        }
    }

    public static void save(Context context, int sizeDp) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putInt(KEY, clamp(sizeDp)).apply();
    }

    public static int iconPixels(int buttonPixels) { return Math.max(1, Math.round(buttonPixels * 26f / DEFAULT_DP)); }
}
