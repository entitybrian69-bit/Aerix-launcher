package net.kdt.pojavlaunch.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import java.util.concurrent.CopyOnWriteArrayList

/** Cross-process recording phase synchronized between `:recorder` and `:game`. */
object RecordingState {
    const val ACTION = "net.al.aerixlauncher.RECORDING_STATE"
    private const val EXTRA_PHASE = "phase"
    private const val EXTRA_DETAIL = "detail"
    private const val EXTRA_STARTED_AT = "started_at"
    private const val EXTRA_NOTICE = "notice"
    private const val EXTRA_LONG = "long_notice"

    enum class Phase { IDLE, STARTING, RECORDING, STOPPING }

    @Volatile
    var phase: Phase = Phase.IDLE
        private set

    @Volatile
    var detail: String = ""
        private set

    @Volatile
    var startedAtElapsedMs: Long = 0L
        private set

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun isActive(): Boolean = phase != Phase.IDLE

    fun addListener(listener: () -> Unit) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun update(next: Phase, info: String = "", startedAt: Long = 0L) {
        phase = next
        detail = info
        startedAtElapsedMs = if (next == Phase.RECORDING && startedAt > 0L) {
            startedAt
        } else if (next == Phase.RECORDING) {
            if (startedAtElapsedMs > 0L) startedAtElapsedMs else SystemClock.elapsedRealtime()
        } else {
            0L
        }
        val active = next != Phase.IDLE
        main.post {
            RecorderUiState.phase.value = next
            RecorderUiState.active.value = active
            RecorderUiState.detail.value = info
            RecorderUiState.startedAtElapsedMs.longValue = startedAtElapsedMs
            listeners.forEach { it() }
        }
    }

    fun publish(
        context: Context,
        next: Phase,
        info: String = "",
        startedAt: Long = 0L,
        notice: String? = null,
        longNotice: Boolean = false
    ) {
        update(next, info, startedAt)
        val intent = Intent(ACTION)
            .setPackage(context.packageName)
            .putExtra(EXTRA_PHASE, next.name)
            .putExtra(EXTRA_DETAIL, info)
            .putExtra(EXTRA_STARTED_AT, startedAtElapsedMs)
            .putExtra(EXTRA_LONG, longNotice)
        if (notice != null) intent.putExtra(EXTRA_NOTICE, notice)
        context.sendBroadcast(intent)
    }

    fun register(context: Context): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val parsed = runCatching {
                    Phase.valueOf(intent.getStringExtra(EXTRA_PHASE) ?: Phase.IDLE.name)
                }.getOrDefault(Phase.IDLE)
                val info = intent.getStringExtra(EXTRA_DETAIL).orEmpty()
                val startedAt = intent.getLongExtra(EXTRA_STARTED_AT, 0L)
                update(parsed, info, startedAt)
                val notice = intent.getStringExtra(EXTRA_NOTICE)
                if (!notice.isNullOrBlank()) {
                    val duration = if (intent.getBooleanExtra(EXTRA_LONG, false)) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    Toast.makeText(ctx, notice, duration).show()
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(ACTION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, IntentFilter(ACTION))
        }
        return receiver
    }
}
