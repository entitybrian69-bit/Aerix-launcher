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
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.components.AerixBackground
import net.kdt.pojavlaunch.aerix.theme.AerixLauncherTheme

/**
 * Puts the launcher's wallpaper behind the loading screen, when the player has asked for it in the
 * in-game Aerix menu.
 *
 * Off - the default - the screen is opaque black, exactly as it always was.
 *
 * On, the screen shows whatever wallpaper is applied in the launcher: a photograph, one of the
 * recorded animations, or an imported clip, all through the same [AerixBackground] the launcher
 * uses. Nothing is special-cased, so it always matches what is set.
 *
 * The switch that controls it is in the in-game Aerix menu, with the rest of the settings, and the
 * choice is remembered for the next launch.
 *
 * Everything is torn down in [detach], which the activity calls as the game window appears - so the
 * wallpaper, and any video decoding with it, is gone before the Mojang logo is on screen.
 */
object AerixLoadingWallpaper {
    private var enabled by mutableStateOf(false)
    private var composeView: ComposeView? = null
    private var host: ViewGroup? = null

    /**
     * Adds the wallpaper layer to [loadingScreen] when the setting is on. Nothing else is added:
     * the switch that controls it lives in the in-game Aerix menu, not on the loading screen.
     */
    @JvmStatic
    fun attach(loadingScreen: ViewGroup) {
        if (host === loadingScreen) return
        detach()
        host = loadingScreen
        enabled = readPref(loadingScreen.context)
        applyState()
    }

    /** Re-reads the setting, for the menu to call when the switch is flipped. */
    @JvmStatic
    fun refresh(context: Context) {
        val screen = host ?: return
        enabled = readPref(context)
        applyState()
    }

    private fun readPref(context: Context): Boolean {
        runCatching { AerixPreferences.init(context.applicationContext) }
        return runCatching { AerixPreferences.loadingWallpaper }.getOrDefault(false)
    }

    /**
     * Builds the wallpaper layer the first time it is actually wanted. With the setting off - the
     * default - nothing is created at all: no ComposeView, no composition, no recomposer, not a
     * single allocation on the game's startup path.
     */
    private fun ensureComposeView(screen: ViewGroup) {
        if (composeView != null) return
        val wallpaper = ComposeView(screen.context).apply {
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
                    AerixBackground { }
                }
            }
        }
        // Behind the spinner, so the spinner stays on top of the wallpaper.
        screen.addView(wallpaper, 0)
        composeView = wallpaper
    }

    /**
     * Drops the wallpaper layer completely. Its composition is disposed first, which runs the
     * wallpaper's own teardown - releasing a video player rather than letting it decode into
     * nowhere - and only then is the view removed from the hierarchy.
     */
    private fun dropComposeView() {
        val view = composeView ?: return
        composeView = null
        runCatching {
            view.visibility = View.GONE
            view.disposeComposition()
            (view.parent as? ViewGroup)?.removeView(view)
        }
    }

    /**
     * Removes the wallpaper. Called when the game window appears: from that moment
     * the game draws itself, and anything of ours still decoding would only cost frames.
     */
    @JvmStatic
    fun detach() {
        enabled = false
        // Put the opaque black back before the layer goes, so the fade-out that follows is the
        // same clean fade it has always been rather than a see-through one.
        host?.setBackgroundColor(0xFF000000.toInt())
        dropComposeView()
        host = null
    }

    private fun applyState() {
        val screen = host ?: return
        if (enabled) {
            ensureComposeView(screen)
            // Transparent, so the wallpaper underneath is what the player sees.
            screen.setBackgroundColor(0x00000000)
        } else {
            // Off means off: the layer is disposed, not just hidden, so nothing keeps ticking.
            dropComposeView()
            screen.setBackgroundColor(0xFF000000.toInt())
        }
    }

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
