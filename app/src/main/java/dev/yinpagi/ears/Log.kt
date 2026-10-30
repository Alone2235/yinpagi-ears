package dev.yinpagi.ears

import io.github.libxposed.api.XposedModule
import java.io.File

/** Logs to Xposed log + /data/local/tmp/yinpagi_ears.log for diagnosis. */
object Log {
    private const val TAG = "[YinpagiEars] "
    private val file = File("/data/local/tmp/yinpagi_ears.log")

    @Volatile
    var module: XposedModule? = null

    fun i(msg: String) {
        val line = TAG + msg
        runCatching { module?.log(android.util.Log.INFO, "YinpagiEars", msg) }
        runCatching { file.appendText(line + "\n") }
    }

    fun e(msg: String, t: Throwable? = null) {
        val line = TAG + "[E] " + msg +
            (t?.let { " : ${it.javaClass.simpleName}: ${it.message}" } ?: "")
        runCatching { module?.log(android.util.Log.ERROR, "YinpagiEars", line) }
        runCatching { file.appendText(line + "\n") }
    }
}
