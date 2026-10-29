package dev.yinpagi.ears

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import kotlinx.coroutines.*
import java.util.*

/**
 * Connects to a paired YINPAGE / Jieli earbud and sends RCSP noise-mode commands
 * over the standard Jieli GATT service (0000AE00 / AE01 write / AE02 notify).
 */
class JieliGattController(
    private val context: Context,
    private val device: BluetoothDevice,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else {
                g.close()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(SERVICE_UUID) ?: return
            writeChar = svc.getCharacteristic(WRITE_UUID)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            RcspFrame.parse(value) // future: surface noise-mode state to UI
        }
    }

    @SuppressLint("MissingPermission")
    fun connect() {
        scope.launch {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    @SuppressLint("MissingPermission")
    fun setNoiseMode(mode: Byte) {
        val ch = writeChar ?: return
        val frame = RcspFrame.setNoiseMode(mode)
        ch.value = frame
        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        gatt?.writeCharacteristic(ch)
    }

    fun release() {
        gatt?.disconnect()
        gatt?.close()
        scope.cancel()
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000ae00-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: UUID = UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: UUID = UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb")
    }
}
