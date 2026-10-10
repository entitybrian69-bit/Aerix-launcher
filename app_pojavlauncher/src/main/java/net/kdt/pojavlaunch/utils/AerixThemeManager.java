package net.kdt.pojavlaunch.utils;

import net.kdt.pojavlaunch.R;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.view.View;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.util.Locale;

/** Shared wallpaper-aware accent palette for Aerix's launcher UI. */
public final class AerixThemeManager {
    public static final String SECTION_HOME = "home";
    public static final String SECTION_ACCOUNT = "account";
    public static final String SECTION_LIBRARY = "library";
    public static final String SECTION_DISCOVER = "discover";
    public static final String SECTION_SERVERS = "servers";
    public static final String SECTION_APPEARANCE = "appearance";
    public static final String SECTION_SKINS = "skins";
    public static final String SECTION_SETTINGS = "settings";
    public static final String SECTION_RENDERER = "renderer";
    public static final String SECTION_CONTROLS = "controls";
    public static final String SECTION_JAVA = "java";

    private static final String PREF_PRESET = "aerix_theme_preset";
    private static final String PREF_CUSTOM = "aerix_theme_custom_hex";
    private static final String PREF_COLOR_MODE = "aerix_theme_color_mode";
    private static final String PREF_WALLPAPER_COLOR = "aerix_theme_wallpaper_hex";
    private static final String PREF_SECTION_PREFIX = "aerix_theme_section_";
    private static final String MODE_WALLPAPER = "wallpaper";
    private static final String MODE_PRESET = "preset";
    private static final String MODE_CUSTOM = "custom";
    private static final String MODE_MATERIAL = "material";

    private static final String[] PRESET_NAMES = {
            "Arctic", "Violet", "Cyan", "Emerald", "Sunset",
            "Rose", "Amber", "Crimson", "Ocean", "Teal",
            "Lime", "Indigo", "Graphite", "Copper", "Frost"
    };
    private static final int[] PRESET_COLORS = {
            0xFF56B4E9, 0xFF9B7BFF, 0xFF00BCD4, 0xFF22C55E, 0xFFFF7043,
            0xFFEC4899, 0xFFFFB020, 0xFFEF4444, 0xFF3B82F6, 0xFF14B8A6,
            0xFF84CC16, 0xFF6366F1, 0xFF94A3B8, 0xFFC87941, 0xFFA5D8FF
    };

    private AerixThemeManager() {
    }

    public static String[] presetNames() { return PRESET_NAMES.clone(); }
    public static int[] presetColors() { return PRESET_COLORS.clone(); }

    public static int selectedPreset() {
        int index = LauncherPreferences.DEFAULT_PREF.getInt(PREF_PRESET, 0);
        return Math.max(0, Math.min(PRESET_NAMES.length - 1, index));
    }

    public static void setPreset(int index) {
        if (index < 0 || index >= PRESET_NAMES.length) throw new IllegalArgumentException("Unknown theme preset");
        LauncherPreferences.DEFAULT_PREF.edit()
                .putInt(PREF_PRESET, index)
                .putString(PREF_COLOR_MODE, MODE_PRESET)
                .remove(PREF_CUSTOM)
                .apply();
    }

    public static boolean setCustomHex(String hex) {
        if (!isValidHex(hex)) return false;
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(PREF_CUSTOM, normalizeHex(hex))
                .putString(PREF_COLOR_MODE, MODE_CUSTOM)
                .apply();
        return true;
    }

    /** Called after selecting any catalog or user wallpaper; its palette drives global accents. */
    public static void setWallpaperAccent(int color) {
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(PREF_WALLPAPER_COLOR, colorHex(color))
                .putString(PREF_COLOR_MODE, MODE_WALLPAPER)
                .apply();
    }

    public static void setWallpaperAccentIfMissing(int color) {
        if (isValidHex(LauncherPreferences.DEFAULT_PREF.getString(PREF_WALLPAPER_COLOR, null))) return;
        LauncherPreferences.DEFAULT_PREF.edit().putString(PREF_WALLPAPER_COLOR, colorHex(color)).apply();
    }

    public static boolean isValidHex(String value) {
        if (value == null) return false;
        String normalized = value.trim();
        if (normalized.startsWith("#")) normalized = normalized.substring(1);
        return normalized.matches("(?i)[0-9a-f]{6}");
    }

    public static String normalizeHex(String value) {
        if (!isValidHex(value)) throw new IllegalArgumentException("Expected a #RRGGBB color");
        String normalized = value.trim();
        if (normalized.startsWith("#")) normalized = normalized.substring(1);
        return "#" + normalized.toUpperCase(Locale.ROOT);
    }

    public static String currentThemeLabel() {
        String mode = colorMode();
        if (MODE_MATERIAL.equals(mode)) return "Material You";
        if (MODE_WALLPAPER.equals(mode)) {
            String wallpaperColor = LauncherPreferences.DEFAULT_PREF.getString(PREF_WALLPAPER_COLOR, null);
            return isValidHex(wallpaperColor) ? "Wallpaper " + normalizeHex(wallpaperColor) : "Wallpaper color";
        }
        String custom = LauncherPreferences.DEFAULT_PREF.getString(PREF_CUSTOM, null);
        if (MODE_CUSTOM.equals(mode) && isValidHex(custom)) return "Custom " + normalizeHex(custom);
        return PRESET_NAMES[selectedPreset()];
    }

    public static void setMaterialYouEnabled(boolean enabled) {
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(PREF_COLOR_MODE, enabled ? MODE_MATERIAL : MODE_WALLPAPER)
                .apply();
    }

    public static boolean isMaterialYouEnabled() { return MODE_MATERIAL.equals(colorMode()); }

    private static String colorMode() {
        return LauncherPreferences.DEFAULT_PREF.getString(PREF_COLOR_MODE, MODE_WALLPAPER);
    }

    public static void setSectionAccent(String section, String hexOrNull) {
        if (section == null || !section.matches("[a-z_]+")) throw new IllegalArgumentException("Invalid theme section");
        android.content.SharedPreferences.Editor editor = LauncherPreferences.DEFAULT_PREF.edit();
        if (hexOrNull == null) editor.remove(PREF_SECTION_PREFIX + section);
        else {
            if (!isValidHex(hexOrNull)) throw new IllegalArgumentException("Expected a #RRGGBB color");
            editor.putString(PREF_SECTION_PREFIX + section, normalizeHex(hexOrNull));
        }
        editor.apply();
    }

    public static int accentColor(Context context, String section) {
        String sectionHex = LauncherPreferences.DEFAULT_PREF.getString(PREF_SECTION_PREFIX + section, null);
        if (isValidHex(sectionHex)) return Color.parseColor(normalizeHex(sectionHex));
        String mode = colorMode();
        if (MODE_MATERIAL.equals(mode) && Build.VERSION.SDK_INT >= 31) {
            try {
                WallpaperColors colors = WallpaperManager.getInstance(context)
                        .getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                if (colors != null && colors.getPrimaryColor() != null) return colors.getPrimaryColor().toArgb();
            } catch (RuntimeException ignored) {
                // Fall through to the chosen wallpaper palette or explicit preset.
            }
        }
        if (MODE_WALLPAPER.equals(mode)) {
            String wallpaperHex = LauncherPreferences.DEFAULT_PREF.getString(PREF_WALLPAPER_COLOR, null);
            if (isValidHex(wallpaperHex)) return Color.parseColor(normalizeHex(wallpaperHex));
        }
        if (MODE_CUSTOM.equals(mode)) {
            String custom = LauncherPreferences.DEFAULT_PREF.getString(PREF_CUSTOM, null);
            if (isValidHex(custom)) return Color.parseColor(normalizeHex(custom));
        }
        return PRESET_COLORS[selectedPreset()];
    }

    public static int presetColor(int index) {
        if (index < 0 || index >= PRESET_COLORS.length) throw new IllegalArgumentException("Unknown theme preset");
        return PRESET_COLORS[index];
    }

    public static String colorHex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    /** Keep ordinary actions legible over wallpapers without obscuring the image. */
    public static void tintButton(View view, Context context, String section) {
        if (view == null) return;
        view.setBackgroundResource(R.drawable.aerix_nav_button);
        view.setBackgroundTintList(null);
    }

    /** Primary actions get a brighter glass surface, not an opaque theme color. */
    public static void tintPrimaryButton(View view, Context context, String section) {
        if (view == null) return;
        view.setBackgroundResource(R.drawable.aerix_primary_button);
        view.setBackgroundTintList(null);
    }

    /** Keeps the rail glass-neutral until selected, then applies the wallpaper-aware accent. */
    public static void tintNavigationButton(View view, Context context, String section) {
        if (view == null) return;
        int accent = accentColor(context, section);
        int active = Color.argb(76, Color.red(accent), Color.green(accent), Color.blue(accent));
        int pressed = Color.argb(44, Color.red(accent), Color.green(accent), Color.blue(accent));
        view.setBackgroundTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_activated},
                        new int[]{android.R.attr.state_pressed},
                        new int[]{}
                },
                new int[]{active, pressed, Color.TRANSPARENT}
        ));
    }
}
