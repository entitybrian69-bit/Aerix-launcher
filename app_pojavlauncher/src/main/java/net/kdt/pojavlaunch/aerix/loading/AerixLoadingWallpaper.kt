/*
 * Aerix Launcher UI layer for PojavLauncher.
 * The launcher's own wallpaper, drawn behind the "waiting for the game" screen.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.loading

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.setPadding
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixColorTheme
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.components.AerixBackground
import net.kdt.pojavlaunch.aerix.theme.AerixLauncherTheme

/**
 * Puts the launcher's wallpaper behind the loading screen, and lets the player switch it on or off
 * from that screen itself.
 *
 * Off - the default - the screen is opaque black, exactly as it always was.
 *
 * On, the screen shows whatever wallpaper is applied in the launcher: a photograph, one of the
 * recorded animations, or an imported clip, all through the same [AerixBackground] the launcher
 * uses. Nothing is special-cased, so it always matches what is set.
 *
 * The toggle works in place: the state is a Compose [mutableStateOf] and the preference is written
 * the moment it is tapped, so it applies immediately without leaving the game or the screen, and
 * it is remembered for next time.
 *
 * Everything is torn down in [detach], which the activity calls as the game window appears - so the
 * wallpaper, and any video decoding with it, is gone before the Mojang logo is on screen.
 */
object AerixLoadingWallpaper {
    private const val AERIX_BLUE = 0xFF63C6F2.toInt()

    private var enabled by mutableStateOf(false)
    private var composeView: ComposeView? = null
    private var toggle: TextView? = null
    private var host: ViewGroup? = null

    /** Adds the wallpaper layer and the toggle to [loadingScreen]. */
    @JvmStatic
    fun attach(loadingScreen: ViewGroup) {
        if (host === loadingScreen) return
        detach()
        host = loadingScreen
        runCatching { AerixPreferences.init(loadingScreen.context.applicationContext) }
        enabled = AerixPreferences.loadingWallpaper

        val wallpaper = ComposeView(loadingScreen.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            ensureViewTreeOwners(this)
            // Disposing when the view leaves the window is what stops a video wallpaper decoding
            // after the loading screen is gone; the activity itself lives on with the game.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                AerixLauncherTheme {
                    if (enabled) AerixBackground { }
                }
            }
            visibility = if (enabled) View.VISIBLE else View.GONE
        }
        // Behind the spinner, which is the first child already in the layout.
        loadingScreen.addView(wallpaper, 0)
        composeView = wallpaper

        toggle = buildToggle(loadingScreen.context).also { button ->
            button.setOnClickListener { setEnabled(loadingScreen.context, !enabled) }
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (56 * loadingScreen.resources.displayMetrics.density).toInt()
            }
            loadingScreen.addView(button, params)
        }
        applyState()
    }

    /** Switches the wallpaper on or off without leaving the screen. */
    @JvmStatic
    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        runCatching { AerixPreferences.saveLoadingWallpaper(value) }
        applyState()
    }

    /**
     * Removes the wallpaper and the toggle. Called when the game window appears: from that moment
     * the game draws itself, and anything of ours still decoding would only cost frames.
     */
    @JvmStatic
    fun detach() {
        enabled = false
        composeView?.let { view ->
            runCatching {
                view.visibility = View.GONE
                // Dispose first, so a video wallpaper stops decoding immediately rather than
                // waiting for the detach callback.
                view.disposeComposition()
                (view.parent as? ViewGroup)?.removeView(view)
            }
        }
        composeView = null
        toggle?.let { (it.parent as? ViewGroup)?.removeView(it) }
        toggle = null
        host = null
    }

    private fun applyState() {
        val screen = host ?: return
        // Opaque black with nothing behind it, or see-through so the wallpaper shows.
        screen.setBackgroundColor(if (enabled) 0x00000000 else 0xFF000000.toInt())
        composeView?.visibility = if (enabled) View.VISIBLE else View.GONE
        toggle?.let { styleToggle(it, enabled, accentColor()) }
    }

    private fun buildToggle(context: Context): TextView = TextView(context).apply {
        text = context.getString(R.string.aerix_loading_wallpaper_toggle)
        textSize = 13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(context, 18), dp(context, 11), dp(context, 18), dp(context, 11))
        isClickable = true
        isFocusable = true
        elevation = dp(context, 6).toFloat()
    }

    /** The accent the launcher is using, read from the preferences rather than the theme. */
    private fun accentColor(): Int = runCatching {
        if (AerixPreferences.colorTheme == AerixColorTheme.CUSTOM) {
            AerixPreferences.customColor
        } else {
            AERIX_BLUE
        }
    }.getOrDefault(AERIX_BLUE)

    private fun styleToggle(button: TextView, on: Boolean, accentColor: Int) {
        val shape = GradientDrawable().apply {
            cornerRadius = dp(button.context, 20).toFloat()
            setColor(if (on) accentColor else 0xE6141B24.toInt())
            setStroke(dp(button.context, 1), if (on) accentColor else 0x66FFFFFF)
        }
        ViewCompat.setBackground(button, shape)
        button.setTextColor(if (on) 0xFF0A1016.toInt() else 0xFFFFFFFF.toInt())
        button.text = button.context.getString(
            if (on) R.string.aerix_loading_wallpaper_on else R.string.aerix_loading_wallpaper_toggle
        )
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /**
     * A ComposeView added to this activity's view tree needs the owners the activity provides;
     * without them the composition never starts. Same requirement, and same fix, as the log box.
     */
    @SuppressLint("RestrictedApi")
    private fun ensureViewTreeOwners(view: View) {
        val activity = view.context.findActivity() ?: return
        val decor = activity.window?.decorView
        val targets = if (decor != null && decor !== view) listOf(decor, view) else listOf(view)
        for (target in targets) {
            if (target.findViewTreeLifecycleOwner() == null && activity is LifecycleOwner) {
                target.setViewTreeLifecycleOwner(activity)
            }
            if (target.findViewTreeViewModelStoreOwner() == null && activity is ViewModelStoreOwner) {
                target.setViewTreeViewModelStoreOwner(activity)
            }
            if (target.findViewTreeSavedStateRegistryOwner() == null && activity is SavedStateRegistryOwner) {
                target.setViewTreeSavedStateRegistryOwner(activity)
            }
            if (target.findViewTreeOnBackPressedDispatcherOwner() == null &&
                activity is OnBackPressedDispatcherOwner
            ) {
                target.setViewTreeOnBackPressedDispatcherOwner(activity)
            }
        }
    }

    private fun Context.findActivity(): android.app.Activity? {
        var context = this
        while (context is android.content.ContextWrapper) {
            if (context is android.app.Activity) return context
            context = context.baseContext
        }
        return null
    }
}
