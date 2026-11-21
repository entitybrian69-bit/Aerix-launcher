package net.kdt.pojavlaunch.recording

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * Reactive UI state shared between [RecorderController], [RecorderPanel], and in-game menus.
 */
object RecorderUiState {
    val show = mutableStateOf(false)
    val active = mutableStateOf(false)
    val phase = mutableStateOf(RecordingState.Phase.IDLE)
    val detail = mutableStateOf("")
    val startedAtElapsedMs = mutableLongStateOf(0L)
}
