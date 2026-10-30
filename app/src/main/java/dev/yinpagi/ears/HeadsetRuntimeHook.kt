package dev.yinpagi.ears

import android.bluetooth.BluetoothDevice

/**
 * Real HyperOS headset-card runtime (com.miui.headset.runtime).
 * Verified on com.milink.service 17.2.6:
 *   ProfileContext.getBatteryLevel(BluetoothDevice): List
 *   AncBatteryController.getAncState(BluetoothDevice): Int
 *   AncBatteryController.getBatteryLevelCache(BluetoothDevice): List
 *   AncBatteryController.getSwitchState(String): Int
 *   AncBatteryController.setAncStateBlock(BluetoothDevice, Int): Int
 */
internal class HeadsetRuntimeHook : HookContext() {

    private val profileContext = "com.miui.headset.runtime.ProfileContext"
    private val ancController = "com.miui.headset.runtime.AncBatteryController"

    override fun install() {
        hookBatteryList(profileContext, "getBatteryLevel")
        hookBatteryList(ancController, "getBatteryLevelCache")
        hookAncState()
        hookSwitchState()
        hookSetAncState()
    }

    /** Provide L/R battery cells so the card renders percentages. */
    private fun hookBatteryList(className: String, methodName: String) = runCatching {
        val m = findMethod(className, methodName, BluetoothDevice::class.java)
        hookAfter(m) {
            val dev = args[0] as? BluetoothDevice ?: return@hookAfter
            if (!Targets.isTarget(dev)) return@hookAfter
            val existing = result as? List<*>
            if (existing != null && existing.isNotEmpty()) return@hookAfter
            Log.i("$methodName -> synthetic battery for ${Targets.name(dev)}")
            result = BatteryFactory.forDevice(dev)
        }
        Log.i("hooked $className.$methodName")
    }.onFailure { Log.e("$methodName unavailable", it) }

    private fun hookAncState() = runCatching {
        val m = findMethod(ancController, "getAncState", BluetoothDevice::class.java)
        hookAfter(m) {
            val dev = args[0] as? BluetoothDevice ?: return@hookAfter
            if (!Targets.isTarget(dev)) return@hookAfter
            result = NoiseState.currentMode(runCatching { dev.address }.getOrDefault(""))
        }
        Log.i("hooked $ancController.getAncState")
    }.onFailure { Log.e("getAncState unavailable", it) }

    private fun hookSwitchState() = runCatching {
        val m = findMethod(ancController, "getSwitchState", String::class.java)
        hookAfter(m) {
            val addr = args[0] as? String ?: return@hookAfter
            if (!Targets.isTargetAddress(addr)) return@hookAfter
            result = 1
        }
        Log.i("hooked $ancController.getSwitchState")
    }.onFailure { Log.e("getSwitchState unavailable", it) }

    /** The ANC button itself: setAncStateBlock(device, mode) -> RCSP. */
    private fun hookSetAncState() = runCatching {
        val m = findMethod(
            ancController, "setAncStateBlock",
            BluetoothDevice::class.java, Int::class.javaPrimitiveType!!,
        )
        hookBefore(m) {
            val dev = args[0] as? BluetoothDevice ?: return@hookBefore
            if (!Targets.isTarget(dev)) return@hookBefore
            val mode = args[1] as? Int ?: return@hookBefore
            val rcsp = when (mode) {
                1 -> RcspFrame.ANC_MODE_ON
                2 -> RcspFrame.ANC_MODE_TRANSPARENCY
                else -> RcspFrame.ANC_MODE_OFF
            }
            Log.i("setAncStateBlock mode=$mode -> RCSP=$rcsp")
            HeadsetBus.send(dev, rcsp)
            NoiseState.setMode(runCatching { dev.address }.getOrDefault(""), mode)
            result = 0
        }
        Log.i("hooked $ancController.setAncStateBlock")
    }.onFailure { Log.e("setAncStateBlock unavailable", it) }
}

/** Matching helpers shared by all hooks. */
internal object Targets {
    private const val PREFIX = "YINPAGE"
    private val ADDRESSES = setOf("DC:36:77:B4:A7:69")

    fun isTarget(dev: BluetoothDevice): Boolean {
        val name = runCatching { dev.name ?: "" }.getOrDefault("")
        if (name.startsWith(PREFIX, ignoreCase = true)) return true
        return isTargetAddress(runCatching { dev.address }.getOrDefault(""))
    }

    fun isTargetAddress(addr: String): Boolean =
        ADDRESSES.any { it.equals(addr, ignoreCase = true) }

    fun name(dev: BluetoothDevice): String =
        runCatching { dev.name ?: dev.address }.getOrDefault("?")

    // --- HeadsetInfo helpers (reflection, no compile-time dep) ---

    /** Reads HeadsetInfo.getAddress() reflectively. */
    fun addressOf(info: Any): String = runCatching {
        info.javaClass.getMethod("getAddress").invoke(info) as? String ?: ""
    }.getOrDefault("")

    /** Reads HeadsetInfo.getName() reflectively. */
    fun nameOf(info: Any): String = runCatching {
        info.javaClass.getMethod("getName").invoke(info) as? String ?: ""
    }.getOrDefault("")

    /** True when this HeadsetInfo belongs to our earbud. */
    fun isTargetInfo(info: Any): Boolean {
        if (isTargetAddress(addressOf(info))) return true
        return nameOf(info).startsWith(PREFIX, ignoreCase = true)
    }

    /** Display name shown in the card header. */
    fun displayName(info: Any): String {
        val n = nameOf(info)
        return if (n.isNotEmpty() && n.startsWith(PREFIX, ignoreCase = true)) n else PREFIX + " Real 1 Pro"
    }
}
