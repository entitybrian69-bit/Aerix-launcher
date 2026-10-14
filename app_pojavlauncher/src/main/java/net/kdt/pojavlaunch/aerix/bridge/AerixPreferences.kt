/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Preference adapter for the Aerix-only launcher settings that come from
 * mirai-launcher- MiraiLauncher/src/main/java/com/movtery/zalithlauncher/setting/AllSettings.kt
 * and ui/theme/MiraiThemeManager.kt.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.bridge

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.kdt.pojavlaunch.prefs.LauncherPreferences

/** Color theme types kept from Zalith/Aerix. Only AERIX and CUSTOM are reachable from the port. */
enum class AerixColorTheme { AERIX, CUSTOM }

/**
 * Stores the Aerix launcher appearance settings in their own SharedPreferences file so they never
 * collide with Pojav keys. Keys whose meaning overlaps a Pojav setting are written through to
 * [LauncherPreferences] so the backend stays the single source of truth for them.
 */
object AerixPreferences {
    private const val FILE = "aerix_ui_prefs"

    const val KEY_COLOR_THEME = "launcherColorTheme"
    const val KEY_CUSTOM_COLOR = "launcherCustomColor"
    const val KEY_BACKGROUND_OPACITY = "launcherBackgroundOpacity"
    const val KEY_FULL_SCREEN = "launcherFullScreen"
    const val KEY_ANIMATE_SPEED = "launcherAnimateSpeed"
    const val KEY_SELECTED_WALLPAPER = "selected_wallpaper_id"
    const val KEY_LAST_SECTION = "aerixLastSection"
    const val KEY_BACKGROUND_BLUR = "backgroundBlur"

    const val DEFAULT_CUSTOM_COLOR = 0xFF8DEFE0.toInt()
    const val DEFAULT_BACKGROUND_OPACITY = 60
    const val DEFAULT_ANIMATE_SPEED = 3
    const val DEFAULT_WALLPAPER = "wp_01_lush_caves"

    private var prefs: SharedPreferences? = null

    var colorTheme by mutableStateOf(AerixColorTheme.AERIX)
        private set
    var customColor by mutableIntStateOf(DEFAULT_CUSTOM_COLOR)
        private set
    var backgroundOpacity by mutableIntStateOf(DEFAULT_BACKGROUND_OPACITY)
        private set
    var fullScreen by mutableStateOf(true)
        private set
    var animateSpeed by mutableIntStateOf(DEFAULT_ANIMATE_SPEED)
        private set
    var selectedWallpaper by mutableStateOf(DEFAULT_WALLPAPER)
        private set
    var lastSection by mutableStateOf("HOME")
        private set

    /** Wallpaper blur radius in dp (Aerix "backgroundBlur"); rendered on Android 12+ only. */
    var backgroundBlur by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        colorTheme = runCatching {
            AerixColorTheme.valueOf(p.getString(KEY_COLOR_THEME, AerixColorTheme.AERIX.name)!!)
        }.getOrDefault(AerixColorTheme.AERIX)
        customColor = p.getInt(KEY_CUSTOM_COLOR, DEFAULT_CUSTOM_COLOR)
        backgroundOpacity = p.getInt(KEY_BACKGROUND_OPACITY, DEFAULT_BACKGROUND_OPACITY).coerceIn(20, 100)
        fullScreen = p.getBoolean(KEY_FULL_SCREEN, true)
        animateSpeed = p.getInt(KEY_ANIMATE_SPEED, DEFAULT_ANIMATE_SPEED).coerceIn(0, 10)
        selectedWallpaper = p.getString(KEY_SELECTED_WALLPAPER, DEFAULT_WALLPAPER) ?: DEFAULT_WALLPAPER
        lastSection = p.getString(KEY_LAST_SECTION, "HOME") ?: "HOME"
        backgroundBlur = p.getInt(KEY_BACKGROUND_BLUR, 0).coerceIn(0, 20)
    }

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs?.edit()?.apply { block(); apply() }
    }

    fun saveColorTheme(value: AerixColorTheme) {
        colorTheme = value
        edit { putString(KEY_COLOR_THEME, value.name) }
    }

    fun saveCustomColor(argb: Int) {
        customColor = argb
        edit { putInt(KEY_CUSTOM_COLOR, argb) }
    }

    fun saveBackgroundOpacity(value: Int) {
        backgroundOpacity = value.coerceIn(20, 100)
        edit { putInt(KEY_BACKGROUND_OPACITY, backgroundOpacity) }
    }

    /**
     * Aerix "launcherFullScreen" maps onto the launcher host window. The Pojav "ignoreNotch"
     * setting stays the owner of the cutout behaviour, so this only toggles the system bars.
     */
    fun saveFullScreen(value: Boolean) {
        fullScreen = value
        edit { putBoolean(KEY_FULL_SCREEN, value) }
    }

    fun saveAnimateSpeed(value: Int) {
        animateSpeed = value.coerceIn(0, 10)
        edit { putInt(KEY_ANIMATE_SPEED, animateSpeed) }
    }

    fun saveSelectedWallpaper(id: String) {
        selectedWallpaper = id
        edit { putString(KEY_SELECTED_WALLPAPER, id) }
    }

    fun saveLastSection(name: String) {
        lastSection = name
        edit { putString(KEY_LAST_SECTION, name) }
    }

    fun saveBackgroundBlur(value: Int) {
        backgroundBlur = value.coerceIn(0, 20)
        edit { putInt(KEY_BACKGROUND_BLUR, backgroundBlur) }
    }

    /** Animation length used by section transitions, derived from the Aerix speed slider (0..10). */
    fun transitionMillis(): Int = if (animateSpeed == 0) 0 else 120 + (10 - animateSpeed) * 40

    /** Pojav notch handling, exposed here so the Aerix launcher settings page can show it. */
    fun ignoreNotch(): Boolean = LauncherPreferences.PREF_IGNORE_NOTCH
}
