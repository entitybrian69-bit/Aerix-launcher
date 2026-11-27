package net.kdt.aerix.desktop.app

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities

/**
 * The launcher's threading model.
 *
 * Android gave the app a main looper plus an unbounded executor (`PojavApplication.sExecutorService`).
 * Here every task names the lane it belongs to: disk/network work on a bounded IO pool, CPU-heavy
 * work (hashing, image scaling, NBT) on a compute pool, and UI mutations always on the Swing EDT.
 * Nothing on the UI thread is allowed to block.
 */
object Scheduler {
    private fun factory(prefix: String): ThreadFactory {
        val counter = AtomicInteger()
        return ThreadFactory { r ->
            val t = Thread(r, "$prefix-${counter.incrementAndGet()}")
            t.isDaemon = true
            t.priority = Thread.NORM_PRIORITY
            t
        }
    }

    private val ioPool: ExecutorService = Executors.newFixedThreadPool(
        (Runtime.getRuntime().availableProcessors() * 3).coerceIn(4, 12), factory("aerix-io")
    )
    private val computePool: ExecutorService = Executors.newFixedThreadPool(
        (Runtime.getRuntime().availableProcessors()).coerceIn(2, 8), factory("aerix-cpu")
    )
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(2, factory("aerix-timer"))

    fun io(name: String = "task", block: () -> Unit): java.util.concurrent.Future<*> =
        ioPool.submit {
            try {
                block()
            } catch (t: Throwable) {
                Log.e(name, "background task failed: ${t.message}", t)
            }
        }

    fun compute(name: String = "task", block: () -> Unit): java.util.concurrent.Future<*> =
        computePool.submit {
            try {
                block()
            } catch (t: Throwable) {
                Log.e(name, "compute task failed: ${t.message}", t)
            }
        }

    fun later(delayMs: Long, block: () -> Unit): ScheduledFuture<*> =
        scheduler.schedule({ runCatching(block) }, delayMs, TimeUnit.MILLISECONDS)

    fun every(periodMs: Long, block: () -> Unit): ScheduledFuture<*> =
        scheduler.scheduleWithFixedDelay({ runCatching(block) }, periodMs, periodMs, TimeUnit.MILLISECONDS)

    /** Runs [block] on the Swing event thread, immediately if already there. */
    fun ui(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block()
        else SwingUtilities.invokeLater { runCatching(block).onFailure { Log.e("ui", "ui task failed: ${it.message}", it) } }
    }

    fun uiAndWait(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeAndWait(block)
    }

    fun shutdown() {
        ioPool.shutdownNow()
        computePool.shutdownNow()
        scheduler.shutdownNow()
    }
}
