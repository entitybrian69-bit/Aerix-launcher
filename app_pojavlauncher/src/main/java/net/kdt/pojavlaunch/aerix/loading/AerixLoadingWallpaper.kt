/*
 * Aerix Launcher UI layer for PojavLauncher.
 * The launcher's own wallpaper, drawn behind the controls while the game is starting.
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
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
 * Draws the launcher's wallpaper behind the controls while the game is starting.
 *
 * Off - the default - nothing is drawn and nothing is created, so the boot looks exactly as it
 * always has: the controls on black, with the Aerix menu button on top.
 *
 * On, the wallpaper takes the place of that black: the same photograph, recorded animation or
 * imported clip the launcher itself uses, through the same [AerixBackground], so it always matches
 * what is set. The control buttons and the Aerix menu button stay where they are and stay on top;
 * the wallpaper sits underneath them.
 *
 * The switch lives in the Aerix menu, so it can be flipped while the game is still starting: the
 * menu writes the preference and calls [refresh], which adds or drops the layer there and then.
 *
 * [detach] takes the layer away the moment the game window appears - the wallpaper is for the wait,
 * not for the game, and a video wallpaper stops decoding with it.
 */
object AerixLoadingWallpaper {
    private var enabled by mutableStateOf(false)
    private var composeView: ComposeView? = null
    private var host: ViewGroup? = null

    /**
     * Attaches to the layout that holds the game surface and the controls.
     *
     * The layer is added directly after the game surface, which is the one place in the hierarchy
     * where it is above the game - and therefore visible during the boot - while the control
     * buttons and the menu button are still drawn over it.
     */
    @JvmStatic
    fun attach(controlLayout: ViewGroup) {
        if (host === controlLayout) {
            refresh(controlLayout.context)
            return
        }
        detach()
        host = controlLayout
        refresh(controlLayout.context)
    }

    /** Re-reads the setting and shows or drops the wallpaper to match. */
    @JvmStatic
    fun refresh(context: Context) {
        val layout = host ?: return
        enabled = readPref(context)
        if (enabled) ensureLayer(layout) else dropLayer()
    }

    /**
     * Removes the wallpaper. Called when the game window appears: from that moment the game draws
     * itself, and anything of ours still decoding would only cost frames.
     */
    @JvmStatic
    fun detach() {
        enabled = false
        dropLayer()
        host = null
    }

    private fun readPref(context: Context): Boolean {
        runCatching { AerixPreferences.init(context.applicationContext) }
        return runCatching { AerixPreferences.loadingWallpaper }.getOrDefault(false)
    }

    /**
     * Builds the wallpaper layer the first time it is actually wanted. With the setting off -
     * the default - nothing is created at all: no ComposeView, no composition, no recomposer.
     */
    private fun ensureLayer(layout: ViewGroup) {
        if (composeView != null) return
        val layer = ComposeView(layout.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Touches belong to the controls underneath.
            isClickable = false
            isFocusable = false
            ensureViewTreeOwners(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                AerixLauncherTheme {
                    AerixBackground { }
                }
            }
        }
        // Straight after the game surface: above it (so it is visible while the game boots) and
        // below the control buttons (so they stay where the player put them).
        val index = if (layout.childCount > 1) 1 else layout.childCount
        layout.addView(layer, index)
        composeView = layer
    }

    /**
     * Drops the layer completely. Its composition is disposed first, which runs the wallpaper's own
     * teardown - releasing a video player rather than letting it decode into nowhere - and only then
     * is the view removed from the hierarchy.
     */
    private fun dropLayer() {
        val view = composeView ?: return
        composeView = null
        runCatching {
            view.visibility = View.GONE
            view.disposeComposition()
            (view.parent as? ViewGroup)?.removeView(view)
        }
    }

    /**
     * A ComposeView added to this activity's view tree needs the owners the activity provides;
     * without them the composition never starts.
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
