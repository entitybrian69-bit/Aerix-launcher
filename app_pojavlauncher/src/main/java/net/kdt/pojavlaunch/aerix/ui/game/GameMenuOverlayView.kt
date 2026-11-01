/*
 * Aerix Launcher in-game menu host.
 *
 * The overlay is a Compose view inside the game activity: it is added to the layout, stays out of
 * the way while the game runs, and is only composed and drawn when the player opens it. A hidden
 * overlay costs no frames, which matters because the game keeps rendering behind it.
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

package net.kdt.pojavlaunch.aerix.ui.game

import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import net.kdt.pojavlaunch.aerix.bridge.GameDownloadTarget
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavGameDownloadsBridge
import net.kdt.pojavlaunch.aerix.theme.AerixLauncherTheme

/**
 * Full screen in-game menu.
 *
 * The game activity only has to hand it the callbacks, call [showMenu] and [hideMenu], and pass
 * the back button on with [handleBackPress] while it returns true.
 */
class GameMenuOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val composeView = ComposeView(context).apply {
        isFocusable = true
        isFocusableInTouchMode = true
    }
    private val callbacksState = mutableStateOf<GameMenuCallbacks?>(null)
    private val revisionState = mutableIntStateOf(0)
    private val targetState = mutableStateOf(GameDownloadTarget.of(null))
    private var contentInstalled = false
    private var imeBottomPx = 0

    /** Handler of the back button, published by the composition while the menu is on screen. */
    private var backHandler: (() -> Unit)? = null

    /** Version of the launcher, shown in the bottom bar of the menu. */
    private val versionLine: String by lazy {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        if (version.isNullOrEmpty()) "Aerix Launcher" else "Aerix Launcher · $version"
    }

    init {
        visibility = GONE
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        setWillNotDraw(true)
        addView(
            composeView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        // The menu reads the launcher theme and the current account, both of which need the
        // context of the app and are normally set up by the launcher activity.
        AerixPreferences.init(context.applicationContext)
        PojavAccountBridge.attach(context.applicationContext)
    }

    /** Called by the game activity, before the menu is used for the first time. */
    fun setCallbacks(callbacks: GameMenuCallbacks) {
        callbacksState.value = callbacks
    }

    fun isMenuShown(): Boolean = visibility == View.VISIBLE

    /**
     * Driven directly from GameActivity's decorView OnApplyWindowInsetsListener so the overlay
     * lifts cleanly above the soft keyboard in landscape without resizing the OpenGL game surface.
     */
    fun onImeInsetsChanged(bottomPx: Int) {
        val clamped = bottomPx.coerceAtLeast(0)
        if (imeBottomPx == clamped && composeView.paddingBottom == clamped) return
        imeBottomPx = clamped
        if (visibility == View.VISIBLE) {
            composeView.setPadding(0, 0, 0, clamped)
        }
    }

    /**
     * Hides the Android soft keyboard if currently visible while inside the overlay.
     * @return true if the keyboard was visible and dismissed.
     */
    fun dismissKeyboardIfShown(): Boolean {
        val rootInsets = ViewCompat.getRootWindowInsets(this)
        val imeShown = imeBottomPx > 0 ||
            (rootInsets != null && rootInsets.isVisible(WindowInsetsCompat.Type.ime()))
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val focusedChild = findFocus()
        if (imeShown) {
            val token = focusedChild?.windowToken ?: windowToken
            if (token != null) {
                imm?.hideSoftInputFromWindow(token, 0)
            }
            imeBottomPx = 0
            composeView.setPadding(0, 0, 0, 0)
            return true
        }
        return false
    }

    /** Open the menu, refreshing the instance it downloads into. */
    fun showMenu() {
        if (isMenuShown()) return
        targetState.value = PojavGameDownloadsBridge.runningTarget()
        // Who the game is running as, for the character panel. Loaded once, no network.
        PojavAccountBridge.ensureLoaded()
        installContent()
        composeView.setPadding(0, 0, 0, imeBottomPx)
        visibility = VISIBLE
        revisionState.intValue++
        post { composeView.requestFocus() }
    }

    fun hideMenu() {
        if (visibility != View.VISIBLE) return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val token = findFocus()?.windowToken ?: windowToken
        if (token != null) {
            imm?.hideSoftInputFromWindow(token, 0)
        }
        clearFocus()
        imeBottomPx = 0
        composeView.setPadding(0, 0, 0, 0)
        visibility = GONE
        backHandler = null
    }

    /**
     * Pass the back button on to the menu.
     * @return true when the menu used the press, in which case the game must not see it
     */
    fun handleBackPress(): Boolean {
        if (!isMenuShown()) return false
        val handler = backHandler
        if (handler != null) {
            handler()
        } else {
            hideMenu()
        }
        return true
    }

    private fun installContent() {
        if (contentInstalled) return
        contentInstalled = true
        // Compose looks the owners up from the content root; an activity that is not a
        // ComponentActivity may not have set them yet.
        val host = rootView
        findOwnerIn<LifecycleOwner>(context)?.let { owner ->
            if (host.findViewTreeLifecycleOwner() == null) host.setViewTreeLifecycleOwner(owner)
        }
        findOwnerIn<SavedStateRegistryOwner>(context)?.let { owner ->
            if (host.findViewTreeSavedStateRegistryOwner() == null) {
                host.setViewTreeSavedStateRegistryOwner(owner)
            }
        }
        findOwnerIn<ViewModelStoreOwner>(context)?.let { owner ->
            if (host.findViewTreeViewModelStoreOwner() == null) host.setViewTreeViewModelStoreOwner(owner)
        }
        findOwnerIn<OnBackPressedDispatcherOwner>(context)?.let { owner ->
            if (host.findViewTreeOnBackPressedDispatcherOwner() == null) {
                host.setViewTreeOnBackPressedDispatcherOwner(owner)
            }
        }
        composeView.setContent {
            AerixLauncherTheme {
                GameMenuScreen(
                    callbacks = callbacksState.value ?: EmptyCallbacks,
                    revision = revisionState.intValue,
                    target = targetState.value,
                    versionLine = versionLine,
                    onBackHandlerChanged = { backHandler = it },
                    onCloseRequest = { hideMenu() }
                )
            }
        }
    }

    /** Walk up from the context of this view to the activity behind it. */
    private inline fun <reified T> findOwnerIn(context: Context?): T? {
        var candidate = context
        while (candidate != null) {
            if (candidate is T) return candidate
            candidate = (candidate as? ContextWrapper)?.baseContext
        }
        return null
    }

    /** Used before the activity hands its own callbacks over; every entry hides the menu. */
    private object EmptyCallbacks : GameMenuCallbacks {
        override fun onScreenRecorder() = Unit
        override fun onResumeGame() = Unit
        override fun onForceCloseGame() = Unit
        override fun onLogOutput() = Unit
        override fun onSendCustomKey() = Unit
        override fun onQuickSettings() = Unit
        override fun onCustomControls() = Unit
        override fun onRequestStoragePermission() = Unit
        override fun onSwitchAccount(account: net.kdt.pojavlaunch.aerix.bridge.AerixAccount) = Unit
    }
}
