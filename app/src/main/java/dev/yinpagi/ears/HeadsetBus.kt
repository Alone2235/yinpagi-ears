package dev.yinpagi.ears

import android.bluetooth.BluetoothDevice
import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/** Cross-process registry of active earbud GATT channels. */
object HeadsetBus {
    @Volatile var appContext: Context? = null
    private val channels = ConcurrentHashMap<String, JieliGattController>()

    fun open(device: BluetoothDevice) {
        val context = appContext ?: runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication").invoke(null) as? Context
        }.getOrNull()
        if (context == null) { Log.e("no context for GATT open"); return }
        channels.getOrPut(device.address) {
            JieliGattController(context, device).also {
                it.connect(); Log.i("GATT channel opened for ${device.name}")
            }
        }
    }

    fun close(device: BluetoothDevice) {
        channels.remove(device.address)?.let { it.release(); Log.i("GATT closed ${device.name}") }
    }

    fun send(device: BluetoothDevice, mode: Byte) {
        val ch = channels[device.address]
        if (ch != null) { ch.setNoiseMode(mode); Log.i("sent noise mode=$mode"); return }
        runCatching {
            appContext?.sendBroadcast(android.content.Intent(ACTION_SET_NOISE).apply {
                setPackage("com.android.bluetooth")
                putExtra("address", device.address)
                putExtra("mode", mode.toInt())
            })
            Log.i("broadcast noise mode=$mode")
        }.onFailure { Log.e("broadcast failed", it) }
    }

    const val ACTION_SET_NOISE = "dev.yinpagi.ears.SET_NOISE_MODE"
}
