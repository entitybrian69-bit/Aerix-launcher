/*
 * Aerix Launcher - the screen recorder's options panel.
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

package net.kdt.pojavlaunch.recording

import androidx.compose.runtime.mutableStateOf

/**
 * Bridge between the recorder (plain Kotlin, no Compose) and the options panel (Compose).
 *
 * The controller owns the settings; the panel only draws them and reports what was tapped. Keeping
 * the state here rather than inside the panel is what lets a rotation, or the panel being opened
 * again, come back to exactly what was chosen - and it keeps one copy of "which quality is
 * selected" instead of one per composition.
 *
 * [show] is the panel's visibility. Nothing else draws while it is true, because a settings sheet
 * underneath a live game is pointless if the game is still taking the touches.
 */
object RecorderUiState {
    /** The panel is open. Written by [RecorderController], read by the overlay. */
    val show = mutableStateOf(false)

    /**
     * A take is running or being started, so the panel shows "Stop & Save" instead of the presets.
     *
     * Mirrors [RecordingState], but as its own state so the panel recomposes when it changes even
     * while the overlay itself is not being ticked.
     */
    val active = mutableStateOf(false)

    /** "● REC 01:24" once a take is really running; blank while nothing is being captured. */
    val detail = mutableStateOf("")
}
