package net.kdt.pojavlaunch.aerix.tools

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

internal object RendererProbe {
    private val mutex = Mutex()
    suspend fun run(context: Context, renderer: String): String = mutex.withLock {
        check(!AerixToolsBridge.gameRunning(context)) { "Close Minecraft before testing graphics drivers" }
        withContext(Dispatchers.Main) {
            var connection: ServiceConnection? = null
            try {
                withTimeout(15_000) {
                    suspendCancellableCoroutine { continuation ->
                        val delivered = java.util.concurrent.atomic.AtomicBoolean()
                        fun finish(result: String) { if (delivered.compareAndSet(false, true)) continuation.resume(result) }
                        val listener = object : ServiceConnection {
                            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                                Thread({
                                    val result = runCatching { IRendererProbe.Stub.asInterface(binder).run(renderer) }
                                        .getOrElse { "ERROR\nDriver process failed. ${it.javaClass.simpleName}" }
                                    finish(result)
                                }, "AerixRendererProbe").apply { isDaemon = true }.start()
                            }
                            override fun onServiceDisconnected(name: ComponentName?) {
                                finish("ERROR\nDriver process crashed")
                            }
                            override fun onNullBinding(name: ComponentName?) {
                                finish("ERROR\nDriver process refused connection")
                            }
                        }
                        connection = listener
                        if (!context.bindService(Intent(context, RendererProbeService::class.java), listener, Context.BIND_AUTO_CREATE)) {
                            finish("ERROR\nUnable to start driver process")
                        }
                    }
                }
            } finally {
                connection?.let { runCatching { context.unbindService(it) } }
            }
        }.also { delay(300) }
    }
}
