package net.kdt.pojavlaunch.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Syncs recorder phase & status across `:recorder` (`ScreenRecordingService`) and `:game`
 * (`RecorderController` / `GameMenuScreen`) so isolating `MediaCodec` + `VirtualDisplay` in
 * `:recorder` never touches `:game`'s OpenGL/Vulkan drivers or JVM signal handlers.
 */
object RecordingState {
    const val ACTION_STATE_SYNC = "net.kdt.pojavlaunch.recording.ACTION_STATE_SYNC"
    private const val EXTRA_PHASE = "phase"
    private const val EXTRA_DETAIL = "detail"

    enum class Phase { IDLE, STARTING, RECORDING, STOPPING }

    var phase by mutableStateOf(Phase.IDLE)
        private set
    var detail by mutableStateOf("")
        private set

    @JvmStatic
    fun isActive() = phase != Phase.IDLE

    private val listeners = CopyOnWriteArraySet<Runnable>()
    private val main = Handler(Looper.getMainLooper())
    private val receiverRegistered = AtomicBoolean(false)

    fun registerSyncReceiver(context: Context) {
        val app = context.applicationContext
        if (!receiverRegistered.compareAndSet(false, true)) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != ACTION_STATE_SYNC) return
                val name = intent.getStringExtra(EXTRA_PHASE) ?: return
                val nextPhase = Phase.values().firstOrNull { it.name == name } ?: Phase.IDLE
                val nextDetail = intent.getStringExtra(EXTRA_DETAIL).orEmpty()
                applyLocal(nextPhase, nextDetail)
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                app,
                receiver,
                IntentFilter(ACTION_STATE_SYNC),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    private fun applyLocal(value: Phase, message: String) {
        main.post {
            phase = value
            detail = message
            listeners.forEach { it.run() }
        }
    }

    fun update(value: Phase, message: String = "", broadcastContext: Context? = null) {
        applyLocal(value, message)
        if (broadcastContext != null) {
            runCatching {
                val app = broadcastContext.applicationContext
                app.sendBroadcast(
                    Intent(ACTION_STATE_SYNC)
                        .setPackage(app.packageName)
                        .putExtra(EXTRA_PHASE, value.name)
                        .putExtra(EXTRA_DETAIL, message)
                )
            }
        }
    }

    fun addListener(listener: Runnable) {
        listeners.add(listener)
        listener.run()
    }

    fun removeListener(listener: Runnable) {
        listeners.remove(listener)
    }
}
