package dev.yinpagi.ears

import de.robv.android.xposed.XposedBridge
import java.io.File

/** Unified logger: XposedBridge.log + file, so we can verify injection. */
object Log {
    private const val TAG = "[YinpagiEars] "
    private val file = File("/data/local/tmp/yinpagi_ears.log")

    fun i(msg: String) {
        val line = TAG + msg
        XposedBridge.log(line)
        runCatching { file.appendText(line + "\n") }
    }

    fun e(msg: String, t: Throwable? = null) {
        val line = TAG + "[E] " + msg + (t?.let { " : ${it.javaClass.simpleName}: ${it.message}" } ?: "")
        XposedBridge.log(line)
        runCatching { file.appendText(line + "\n") }
    }
}
