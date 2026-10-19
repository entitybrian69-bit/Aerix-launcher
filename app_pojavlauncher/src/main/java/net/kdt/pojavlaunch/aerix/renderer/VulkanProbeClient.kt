package net.kdt.pojavlaunch.aerix.renderer

import android.content.*
import android.os.*

/** Child failure/timeout becomes an unavailable report; caller must retain its GLES choice. */
internal object VulkanProbeClient {
    @Volatile var latestReport: String? = null
        private set
    private var requested = false
    @Synchronized fun ensureProbed(context: Context) {
        if (requested) return
        requested = true
        query(context) { latestReport = it; android.util.Log.i("AerixVulkanProbe", it) }
    }

    fun query(context: Context, callback: (String) -> Unit) {
        val app = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            var finished = false
            var bound = false
            lateinit var connection: ServiceConnection
            lateinit var timeout: Runnable
            fun finish(report: String) {
                if (finished) return
                finished = true
                handler.removeCallbacks(timeout)
                if (bound) runCatching { app.unbindService(connection) }
                callback(report)
            }
            timeout = Runnable { finish("{\"error\":\"probe timeout\"}") }
            val reply = Messenger(Handler(Looper.getMainLooper()) { msg ->
                if (msg.what == 1) finish(msg.data.getString("report") ?: "{\"error\":\"empty report\"}")
                true
            })
            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    runCatching { Messenger(binder).send(Message.obtain(null, 1).apply { replyTo = reply }) }
                        .onFailure { finish("{\"error\":\"probe IPC failed\"}") }
                }
                override fun onServiceDisconnected(name: ComponentName) = finish("{\"error\":\"probe process exited\"}")
                override fun onBindingDied(name: ComponentName) = finish("{\"error\":\"probe binding died\"}")
                override fun onNullBinding(name: ComponentName) = finish("{\"error\":\"probe refused binding\"}")
            }
            try {
                bound = app.bindService(Intent(app, VulkanProbeService::class.java), connection, Context.BIND_AUTO_CREATE)
                if (!bound) finish("{\"error\":\"probe service unavailable\"}")
                else handler.postDelayed(timeout, 5000)
            } catch (_: Exception) { finish("{\"error\":\"probe service denied\"}") }
        }
    }
}
