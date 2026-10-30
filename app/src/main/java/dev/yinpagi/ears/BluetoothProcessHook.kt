package dev.yinpagi.ears

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile

/** com.android.bluetooth: A2DP connect/disconnect -> open/close RCSP GATT channel. */
internal class BluetoothProcessHook : HookContext() {
    override fun install() {
        runCatching {
            hookAfter(findMethodByParamCount(
                "com.android.bluetooth.a2dp.A2dpService", "handleConnectionStateChanged", 3)) {
                val dev = args.getOrNull(0) as? BluetoothDevice ?: return@hookAfter
                val cur = args.getOrNull(2) as? Int ?: return@hookAfter
                val name = runCatching { dev.name ?: "" }.getOrDefault("")
                if (!name.startsWith("YINPAGE", ignoreCase = true)) return@hookAfter
                when (cur) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i("A2DP connected: $name"); HeadsetBus.open(dev)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i("A2DP disconnected: $name"); HeadsetBus.close(dev)
                    }
                }
            }
            Log.i("hooked A2dpService.handleConnectionStateChanged")
        }.onFailure { Log.e("A2dpService hook failed", it) }
    }
}
