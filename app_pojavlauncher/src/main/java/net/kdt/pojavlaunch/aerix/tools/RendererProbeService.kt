package net.kdt.pojavlaunch.aerix.tools

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.system.Os
import net.kdt.pojavlaunch.game.renderer.GameRenderer
import net.kdt.pojavlaunch.game.renderer.def.Renderers

/** Driver failures are confined to this disposable process. */
@androidx.annotation.Keep
class RendererProbeService : Service() {
    private external fun runNative(library: String, gles: Int): String
    private val binder = object : IRendererProbe.Stub() {
        override fun run(renderer: String): String {
            require(renderer in listOf(Renderers.GL4ES_RENDERER, Renderers.LTW_RENDERER, Renderers.MOBILEGLUES_RENDERER, Renderers.NGGL4ES_RENDERER))
            val spec = GameRenderer.getKnownRenderer(renderer) ?: return "ERROR\nUnknown renderer"
            val environment = HashMap<String, String>()
            spec.setupEnvironment(this@RendererProbeService, environment)
            environment.forEach { (key, value) -> Os.setenv(key, value, true) }
            System.loadLibrary("aerix_renderer_probe")
            return runNative(applicationInfo.nativeLibraryDir + "/" + spec.library(), if (renderer == Renderers.GL4ES_RENDERER) 2 else 3)
        }
    }
    override fun onBind(intent: Intent?): IBinder = binder
    override fun onUnbind(intent: Intent?): Boolean {
        Process.killProcess(Process.myPid())
        return false
    }
}
