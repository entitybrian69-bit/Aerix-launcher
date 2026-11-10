/*
 * Test doubles for the `android.os` plumbing, see AndroidGraphics.kt for why they exist. Handler and
 * Message are deliberately *functional* (a real one-thread scheduler behind them) so a whole capture
 * session - handshake, frame loop, back-pressure, stop - can be driven and asserted on the JVM.
 */
package android.os

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object Build {
    object VERSION {
        @JvmField val SDK_INT: Int = 34
    }
}

class Bundle {
    private val values = LinkedHashMap<String, Any?>()
    var parcels = 0

    fun putInt(key: String, value: Int) { values[key] = value }
    fun getInt(key: String): Int = (values[key] as? Int) ?: 0
    fun putParcelable(key: String, value: Any?) {
        parcels++
        values[key] = value
    }

    fun <T> getParcelable(key: String): T? = getParcelable(key, null)

    @Suppress("UNCHECKED_CAST")
    fun <T> getParcelable(key: String, type: Class<T>?): T? = values[key] as? T
}

class Message {
    var what: Int = 0
    var arg1: Int = 0
    var arg2: Int = 0
    var obj: Any? = null
    var data: Bundle? = null
    var replyTo: Messenger? = null

    companion object {
        @JvmStatic fun obtain(): Message = Message()
        @JvmStatic fun obtain(target: Messenger?, what: Int): Message = Message().apply {
            this.what = what
            replyTo = target
        }
    }
}

class Looper internal constructor(val name: String, val executor: ScheduledExecutorService) {
    companion object {
        private val main = Looper("main", Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "fake-main").apply { isDaemon = true }
        })

        @JvmStatic fun getMainLooper(): Looper = main
    }
}

class HandlerThread(name: String) {
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }
    val looper = Looper(name, scheduler)
    @Volatile private var quit = false

    fun start() = Unit
    val isAlive: Boolean get() = !quit
    fun quitSafely() {
        quit = true
        scheduler.shutdown()
    }
}

class Handler(private val handlerLooper: Looper, private val callback: Callback) {
    fun interface Callback {
        fun handleMessage(msg: Message): Boolean
    }

    constructor(looper: Looper) : this(looper, Callback { true })

    private val scheduler: ScheduledExecutorService get() = handlerLooper.executor
    private val pending = ConcurrentHashMap<Runnable, MutableList<Future<*>>>()

    fun post(action: Runnable): Boolean = postDelayed(action, 0L)

    fun postDelayed(action: Runnable, delayMs: Long): Boolean {
        if (scheduler.isShutdown) return false
        val future = if (delayMs <= 0L) scheduler.submit(action)
        else scheduler.schedule({ if (!scheduler.isShutdown) action.run() }, delayMs, TimeUnit.MILLISECONDS)
        pending.getOrPut(action) { Collections.synchronizedList(ArrayList()) }.add(future)
        return true
    }

    fun removeCallbacks(action: Runnable) {
        pending.remove(action)?.forEach { it.cancel(false) }
    }

    internal fun dispatch(msg: Message) {
        postDelayed(Runnable { callback?.handleMessage(msg) }, 0L)
    }

    fun drain(millis: Long) {
        val deadline = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < deadline) {
            runCatching { TimeUnit.MILLISECONDS.sleep(2) }
        }
    }
}

class Messenger(private val target: Handler) {
    val received = Collections.synchronizedList(ArrayList<Message>())

    fun send(message: Message) {
        received.add(message)
        target.dispatch(message)
    }
}
