package net.kdt.pojavlaunch.recording

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.CopyOnWriteArraySet

/** Service and game menu deliberately live in the same :game process. */
object RecordingState {
    enum class Phase { IDLE, STARTING, RECORDING, STOPPING }
    var phase by mutableStateOf(Phase.IDLE)
        private set
    var detail by mutableStateOf("")
        private set
    @JvmStatic fun isActive() = phase != Phase.IDLE
    private val listeners = CopyOnWriteArraySet<Runnable>()
    private val main = Handler(Looper.getMainLooper())
    fun update(value: Phase, message: String = "") {
        main.post { phase = value; detail = message; listeners.forEach { it.run() } }
    }
    fun addListener(listener: Runnable) { listeners.add(listener); listener.run() }
    fun removeListener(listener: Runnable) { listeners.remove(listener) }
}
