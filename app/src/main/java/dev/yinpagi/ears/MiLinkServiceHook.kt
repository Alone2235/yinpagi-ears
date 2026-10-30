package dev.yinpagi.ears

import android.bluetooth.BluetoothDevice

/**
 * MiLink (com.milink.service) hook: makes the system treat the YINPAGE/Jieli earbud
 * as a Mi-brand headset and routes the ANC buttons to the real RCSP channel.
 *
 * Key boundary: MxBluetoothManager / MxBluetoothService .checkIsMiTWS(BluetoothDevice)
 */
internal class MiLinkServiceHook : HookContext() {

    private val runtimeClasses = listOf(
        "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager",
        "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService",
    )

    override fun install() {
        var admission = false
        // Prefer service impl first (manager may reclassify an injected value).
        for (cls in runtimeClasses.asReversed()) {
            if (hookAdmission(cls)) { admission = true; break }
        }
        if (!admission) Log.e("MiLink: no checkIsMiTWS boundary found")

        runtimeClasses.forEach { cls ->
            hookIsMiTws(cls)
            hookNoiseCommand(cls, "openAnc", RcspFrame.ANC_MODE_ON)
            hookNoiseCommand(cls, "closeAnc", RcspFrame.ANC_MODE_OFF)
            hookNoiseCommand(cls, "openTransparent", RcspFrame.ANC_MODE_TRANSPARENCY)
        }
    }

    /** checkIsMiTWS(device): return true so MiLink admits our third-party earbud. */
    private fun hookAdmission(className: String): Boolean = runCatching {
        val m = findMethod(className, "checkIsMiTWS", BluetoothDevice::class.java)
        hookAfter(m) {
            val dev = args[0] as? BluetoothDevice ?: return@hookAfter
            if (!isTarget(dev)) return@hookAfter
            Log.i("admission checkIsMiTWS -> true for ${dev.name}")
            result = true
        }
        Log.i("hooked $className.checkIsMiTWS")
        true
    }.getOrElse { Log.e("checkIsMiTWS unavailable in $className", it); false }

    /** isMiTWS(device): keep reporting Mi-TWS identity for card rendering. */
    private fun hookIsMiTws(className: String) = runCatching {
        val m = findMethod(className, "isMiTWS", BluetoothDevice::class.java)
        hookAfter(m) {
            val dev = args[0] as? BluetoothDevice ?: return@hookAfter
            if (isTarget(dev)) result = true
        }
        Log.i("hooked $className.isMiTWS")
    }.onFailure { Log.e("isMiTWS unavailable in $className", it) }

    /** openAnc / closeAnc / openTransparent -> RCSP over GATT. */
    private fun hookNoiseCommand(className: String, methodName: String, mode: Byte) = runCatching {
        val m = findMethod(className, methodName, BluetoothDevice::class.java)
        hookBefore(m) {
            val dev = args[0] as? BluetoothDevice ?: return@hookBefore
            if (!isTarget(dev)) return@hookBefore
            Log.i("noise cmd $methodName -> mode=$mode")
            HeadsetBus.send(dev, mode)
            result = true      // 1 = HEADSET_OPERATION_SUCCESS
        }
        Log.i("hooked $className.$methodName")
    }.onFailure { Log.e("$methodName unavailable in $className", it) }

    private fun isTarget(dev: BluetoothDevice): Boolean {
        val name = runCatching { dev.name ?: "" }.getOrDefault("")
        return name.startsWith(MATCH_PREFIX, ignoreCase = true)
    }

    private companion object {
        const val MATCH_PREFIX = "YINPAGE"
    }
}
