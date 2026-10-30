package dev.yinpagi.ears

import android.content.Context

/** com.xiaomi.bluetooth: capture application context for GATT operations. */
internal class XiaomiBluetoothProcessHook : HookContext() {
    override fun install() {
        runCatching {
            hookAfter(findMethod("com.android.bluetooth.ble.MiuiApplication", "onCreate")) {
                val ctx = instance as? Context ?: return@hookAfter
                HeadsetBus.appContext = ctx.applicationContext ?: ctx
                Log.i("captured app context")
            }
            Log.i("hooked MiuiApplication.onCreate")
        }.onFailure { Log.e("MiuiApplication hook failed", it) }
    }
}
