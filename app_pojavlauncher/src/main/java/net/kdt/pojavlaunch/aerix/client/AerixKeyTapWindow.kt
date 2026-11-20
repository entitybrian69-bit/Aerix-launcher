/*
 * Aerix Launcher - the window that hosts the "tap a key" panel.
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
 */

package net.kdt.pojavlaunch.aerix.client

import android.app.Activity
import android.app.Dialog
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
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
import net.kdt.pojavlaunch.aerix.theme.AerixLauncherTheme

/**
 * Shows [AerixKeyTapPanel] in a dialog window.
 *
 * This lives in Kotlin because a `@Composable` cannot be called from Java: the Compose compiler adds
 * parameters that Java cannot see, so the activity can only own the key press itself. It also keeps
 * the view-tree owners in one place - a dialog has a window of its own, and a `ComposeView` inside it
 * finds no lifecycle owner, so the composition silently never starts. That failure looks exactly like
 * "the panel does nothing", which is the bug this whole feature exists to fix.
 */
/**
 * A plain Java-callable callback.
 *
 * A Kotlin function type is `Function1<Integer, Unit>`: it returns Unit, so Java's `void` method
 * reference cannot satisfy it. A named interface is one thing Java can pass a lambda or method
 * reference to, and it keeps the activity to one line.
 */
fun interface AerixKeyTapCallback {
    fun onTap(keyCode: Int)
}

object AerixKeyTapWindow {
    private var dialog: Dialog? = null
    private var shownAtUptimeMs = 0L

    /**
     * Some devices report the tap that opens the menu twice; the second one lands on whatever
     * appears underneath and shuts it again before it can be read. The pause menu already carries a
     * guard for exactly this, so the panel needs the same one or it would inherit the bug.
     */
    private const val DISMISS_GRACE_MS = 400L

    /** @param onTap receives the keycode to press. */
    @JvmStatic
    fun show(activity: Activity, onTap: AerixKeyTapCallback) {
        dismiss()
        val view = ComposeView(activity).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
            )
            setContent {
                AerixLauncherTheme {
                    AerixKeyTapPanel(
                        onTap = { onTap.onTap(it) },
                        onDismiss = { dismissIfDeliberate() }
                    )
                }
            }
        }
        val window = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen).apply {
            setContentView(view)
            setCanceledOnTouchOutside(false)
            window?.setDimAmount(0f)
            setOnKeyListener { _, keyCode, event ->
                if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) &&
                    event.action == KeyEvent.ACTION_UP
                ) {
                    dismiss()
                    true
                } else {
                    false
                }
            }
        }
        window.window?.decorView?.let { giveViewTreeOwners(it, activity) }
        window.show()
        dialog = window
        shownAtUptimeMs = SystemClock.uptimeMillis()
    }

    @JvmStatic
    fun dismiss() {
        val current = dialog
        dialog = null
        if (current != null && current.isShowing) current.dismiss()
    }

    /** Closes the panel, unless the tap that opened it is arriving a second time. */
    private fun dismissIfDeliberate() {
        if (SystemClock.uptimeMillis() - shownAtUptimeMs < DISMISS_GRACE_MS) return
        dismiss()
    }

    /** Walks up from [view] so the composition can find what the activity provides. */
    private fun giveViewTreeOwners(view: View, owner: Activity) {
        val chain = generateSequence(view) { it.parent as? View }.toList()
        for (target in chain) {
            if (target.findViewTreeLifecycleOwner() == null && owner is LifecycleOwner) {
                target.setViewTreeLifecycleOwner(owner)
            }
            if (target.findViewTreeViewModelStoreOwner() == null && owner is ViewModelStoreOwner) {
                target.setViewTreeViewModelStoreOwner(owner)
            }
            if (target.findViewTreeSavedStateRegistryOwner() == null && owner is SavedStateRegistryOwner) {
                target.setViewTreeSavedStateRegistryOwner(owner)
            }
            if (target.findViewTreeOnBackPressedDispatcherOwner() == null &&
                owner is OnBackPressedDispatcherOwner
            ) {
                target.setViewTreeOnBackPressedDispatcherOwner(owner)
            }
        }
    }
}
