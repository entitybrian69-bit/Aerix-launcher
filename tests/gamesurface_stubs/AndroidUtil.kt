/*
 * Test double for `android.util.Log`, see AndroidGraphics.kt. Assertions read `Log.lines`, because the
 * reason a capture session fell back to mirroring the display is exactly the thing a bug report needs.
 */
package android.util

import java.util.Collections

object Log {
    val lines = Collections.synchronizedList(ArrayList<String>())
    private fun record(level: String, tag: String, message: String) {
        lines.add("$level/$tag: $message")
    }

    @JvmStatic fun i(tag: String, message: String): Int = record("I", tag, message).let { 0 }
    @JvmStatic fun w(tag: String, message: String): Int = record("W", tag, message).let { 0 }
    @JvmStatic fun w(tag: String, message: String, error: Throwable): Int =
        record("W", tag, "$message (${error.javaClass.simpleName})").let { 0 }
    @JvmStatic fun d(tag: String, message: String): Int = record("D", tag, message).let { 0 }
    @JvmStatic fun e(tag: String, message: String): Int = record("E", tag, message).let { 0 }
    @JvmStatic fun e(tag: String, message: String, error: Throwable): Int =
        record("E", tag, "$message (${error.javaClass.simpleName})").let { 0 }
}

