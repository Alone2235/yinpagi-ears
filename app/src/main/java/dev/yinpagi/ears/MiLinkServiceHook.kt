package dev.yinpagi.ears

import android.bluetooth.BluetoothDevice

/**
 * MiLink hook: treat the YINPAGE/Jieli earbud as a Mi headset and route ANC.
 *
 * Verified on com.milink.service 17.2.6:
 *   checkIsMiTWS(BluetoothDevice): Int
 *   isMiTWS(String): Boolean
 *   openAnc/closeAnc/openTransparent(BluetoothDevice): Int
 */
internal class MiLinkServiceHook : HookContext() {

    private val runtimeClasses = listOf(
        "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager",
        "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService",
    )

    override fun install() {
        var admission = false
        for (cls in runtimeClasses.asReversed()) {
            if (hookAdmission(cls)) { admission = true; break }
        }
        if (!admission) Log.e("MiLink: no checkIsMiTWS boundary found")
        // The card itself is driven by com.miui.headset.runtime, not by
        // checkIsMiTWS alone. Without battery/ANC there is no card at all.
        runCatching { HeadsetRuntimeHook().apply {
            module = this@MiLinkServiceHook.module
            appClassLoader = this@MiLinkServiceHook.appClassLoader
            packageName = this@MiLinkServiceHook.packageName
        }.install() }.onFailure { Log.e("HeadsetRuntimeHook install failed", it) }

        // HeadsetInfo supplies the values the card actually renders (name,
        // battery cells, ANC mode). Without it the card keeps Xiaomi defaults.
        runCatching { HeadsetInfoHook().apply {
            module = this@MiLinkServiceHook.module
            appClassLoader = this@MiLinkServiceHook.appClassLoader
            packageName = this@MiLinkServiceHook.packageName
        }.install() }.onFailure { Log.e("HeadsetInfoHook install failed", it) }

        runtimeClasses.forEach { cls ->
            hookIsMiTws(cls)
            hookNoiseCommand(cls, "openAnc", RcspFrame.ANC_MODE_ON)
            hookNoiseCommand(cls, "closeAnc", RcspFrame.ANC_MODE_OFF)
            hookNoiseCommand(cls, "openTransparent", RcspFrame.ANC_MODE_TRANSPARENCY)
        }
    }

    /** checkIsMiTWS(BluetoothDevice): Int -> force "supported". */
    private fun hookAdmission(className: String): Boolean = runCatching {
        val m = findMethod(className, "checkIsMiTWS", BluetoothDevice::class.java)
        hookAfter(m) {
            val dev = args[0] as? BluetoothDevice ?: return@hookAfter
            if (!isTargetDevice(dev)) return@hookAfter
            Log.i("admission checkIsMiTWS -> 1 for ${safeName(dev)}")
            result = 1
        }
        Log.i("hooked $className.checkIsMiTWS")
        true
    }.getOrElse { Log.e("checkIsMiTWS unavailable in $className", it); false }

    /** isMiTWS(String address): Boolean */
    private fun hookIsMiTws(className: String) = runCatching {
        val m = findMethod(className, "isMiTWS", String::class.java)
        hookAfter(m) {
            val addr = args[0] as? String ?: return@hookAfter
            if (!isTargetAddress(addr)) return@hookAfter
            result = true
        }
        Log.i("hooked $className.isMiTWS")
    }.onFailure { Log.e("isMiTWS unavailable in $className", it) }

    /** openAnc / closeAnc / openTransparent(BluetoothDevice): Int */
    private fun hookNoiseCommand(className: String, methodName: String, mode: Byte) = runCatching {
        val m = findMethod(className, methodName, BluetoothDevice::class.java)
        hookBefore(m) {
            val dev = args[0] as? BluetoothDevice ?: return@hookBefore
            if (!isTargetDevice(dev)) return@hookBefore
            Log.i("noise cmd $methodName -> RCSP mode=$mode for ${safeName(dev)}")
            HeadsetBus.send(dev, mode)
            NoiseState.setMode(runCatching { dev.address }.getOrDefault(""),
                when (mode) {
                    RcspFrame.ANC_MODE_ON -> 1
                    RcspFrame.ANC_MODE_TRANSPARENCY -> 2
                    else -> 0
                })
            result = 100  // HEADSET_OPERATION_SUCCESS
        }
        Log.i("hooked $className.$methodName")
    }.onFailure { Log.e("$methodName unavailable in $className", it) }

    private fun safeName(dev: BluetoothDevice): String =
        runCatching { dev.name ?: dev.address }.getOrDefault("?")

    private fun isTargetDevice(dev: BluetoothDevice): Boolean {
        val name = runCatching { dev.name ?: "" }.getOrDefault("")
        if (name.startsWith(MATCH_PREFIX, ignoreCase = true)) return true
        return isTargetAddress(runCatching { dev.address }.getOrDefault(""))
    }

    private fun isTargetAddress(addr: String): Boolean =
        TARGET_ADDRESSES.any { it.equals(addr, ignoreCase = true) }

    private companion object {
        const val MATCH_PREFIX = "YINPAGE"
        val TARGET_ADDRESSES = setOf("DC:36:77:B4:A7:69")
    }
}
