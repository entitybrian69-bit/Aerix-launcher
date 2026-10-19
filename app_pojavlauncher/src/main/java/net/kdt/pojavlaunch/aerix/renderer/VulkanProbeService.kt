package net.kdt.pojavlaunch.aerix.renderer

import android.app.Service
import android.content.Intent
import android.os.*

/** No plugin code, custom driver path, or instance files are loaded in this process. */
@androidx.annotation.Keep
class VulkanProbeService : Service() {
    private external fun nativeProbe(): String
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == 1 && message.replyTo != null) {
            val result = try {
                System.loadLibrary("aerix_vulkan_probe")
                nativeProbe()
            } catch (_: LinkageError) { "{\"error\":\"native probe linkage failed\"}" }
            catch (_: Exception) { "{\"error\":\"capability probe failed\"}" }
            runCatching { message.replyTo.send(Message.obtain(null, 1).apply { data = Bundle().apply { putString("report", result) } }) }
        }
        true
    })
    override fun onBind(intent: Intent?): IBinder = messenger.binder
}
