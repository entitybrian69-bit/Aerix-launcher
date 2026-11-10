package net.kdt.pojavlaunch.aerix.client

import android.os.SystemClock

/**
 * When the in-game A menu may accept a dismissal.
 *
 * The Client button and the menu's own backdrop are the same kind of control - a tap anywhere
 * outside the rectangle closes it - so a tap that opened the menu can also be the tap that closes
 * it. On a phone whose touch panel reports a tap twice (a capacitive bounce, a screen protector,
 * a glove, or simply a finger that jitters while the driver keeps the same contact id open) the
 * menu is composed under the finger, the second half of the tap lands on that backdrop, and the
 * player sees the panel flash open and shut again. The same gesture can reach the game as an ESC
 * key press, which closes the menu through the back path.
 *
 * So the backdrop ignores taps, and the back/escape path ignores keys, until the menu has actually
 * been drawn and a short grace window has passed. Nothing else in the overlay is gated this way:
 * the close button and every row inside the rectangle work from the first frame.
 *
 * This is also what the Performance page reports, so the fix can be seen rather than trusted.
 */
object AerixOverlayInput {
    /** How long after the menu is drawn an outside tap has to wait before it counts. */
    const val DISMISS_GRACE_MS = 240L

    /** Uptime at which the current menu became dismissable, or [NOT_ARMED] before that happens. */
    const val NOT_ARMED = Long.MAX_VALUE

    @Volatile
    var armedAtUptimeMs: Long = NOT_ARMED
        private set

    /** Counters are what makes the bug visible in a bug report instead of only on the device. */
    @Volatile
    var dismissedTaps: Int = 0
        private set

    @Volatile
    var dismissedKeys: Int = 0
        private set

    @Volatile
    var lastDismissedDeltaMs: Long = -1L
        private set

    /** Called when a modal opens: the backdrop cannot dismiss anything until [arm] has run. */
    fun noteOpened() {
        armedAtUptimeMs = NOT_ARMED
    }

    /**
     * Called once the menu has been composed and drawn. From here the backdrop listens, but only
     * for gestures that start after the grace window - the tail of the tap that opened the menu,
     * and a ghost duplicate of it, both began before this point.
     */
    fun arm() {
        armedAtUptimeMs = SystemClock.uptimeMillis() + DISMISS_GRACE_MS
    }

    /** True when a dismissal arriving at [nowMs] is too early to be a deliberate one. */
    fun isTooEarly(nowMs: Long = SystemClock.uptimeMillis()): Boolean = nowMs < armedAtUptimeMs

    /** @return true when the tap was swallowed because it belonged to the opening gesture. */
    fun acceptTapDismiss(nowMs: Long = SystemClock.uptimeMillis()): Boolean {
        if (isTooEarly(nowMs)) {
            dismissedTaps++
            lastDismissedDeltaMs = armedAtUptimeMs - nowMs
            log("Aerix overlay: ignored an outside tap " + (armedAtUptimeMs - nowMs) +
                "ms before the menu could be dismissed (device reported the opening tap twice)")
            return false
        }
        return true
    }

    /** @return true when a back/escape key may close the menu now. */
    fun acceptKeyDismiss(nowMs: Long = SystemClock.uptimeMillis()): Boolean {
        if (isTooEarly(nowMs)) {
            dismissedKeys++
            lastDismissedDeltaMs = armedAtUptimeMs - nowMs
            log("Aerix overlay: ignored a back/escape keypress " + (armedAtUptimeMs - nowMs) +
                "ms after the menu opened")
            return false
        }
        return true
    }

    /** One line for the Performance page: what the guard has done during this game session. */
    fun report(): String = when {
        armedAtUptimeMs == NOT_ARMED && dismissedTaps == 0 && dismissedKeys == 0 ->
            "Touch guard: the menu listens for taps as soon as it is drawn."
        else -> {
            val swallowed = dismissedTaps + dismissedKeys
            if (swallowed == 0) {
                "Touch guard: ready, nothing swallowed this session."
            } else {
                "Touch guard: kept the menu open through $swallowed repeated tap" +
                    (if (swallowed == 1) "" else "s") + " from your phone ($dismissedTaps tap, " +
                    "$dismissedKeys key) - the last one arrived ${lastDismissedDeltaMs}ms too early."
            }
        }
    }

    private fun log(text: String) {
        // appendToLog is a JNI call into the game's log buffer; a guard must never be the reason
        // a tap goes missing, so a failure here is swallowed rather than thrown into the gesture.
        runCatching { net.kdt.pojavlaunch.Logger.appendToLog(text) }
    }

    /** Test seam: forgets the counters and re-arms the guard as if nothing had happened. */
    fun resetForTest() {
        armedAtUptimeMs = NOT_ARMED
        dismissedTaps = 0
        dismissedKeys = 0
        lastDismissedDeltaMs = -1L
    }
}
