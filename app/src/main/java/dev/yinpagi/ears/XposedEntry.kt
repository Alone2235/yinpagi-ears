package dev.yinpagi.ears

import android.bluetooth.BluetoothDevice
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findClass

class XposedEntry : IXposedHookLoadPackage {
    companion object {
        const val MATCH_NAME_PREFIX = "YINPAGE"
        const val XIAOMI_VENDOR_ID = 0x2717
        const val XIAOMI_PRODUCT_ID = 0x1200
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.xiaomi.bluetooth") return
        spoofHeadsetMetadata(lpparam.classLoader)
        hookNoiseControl(lpparam.classLoader)
    }

    private fun spoofHeadsetMetadata(cl: ClassLoader) {
        val devCls = try { findClass("android.bluetooth.BluetoothDevice", cl) }
        catch (_: Throwable) { return }
        XposedBridge.hookAllMethods(devCls, "getMetadata", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val name = runCatching {
                    (param.thisObject as BluetoothDevice).name ?: ""
                }.getOrDefault("")
                if (!name.startsWith(MATCH_NAME_PREFIX, ignoreCase = true)) return
                val map = (param.result as? MutableMap<String, ByteArray>) ?: return
                map["is_untethered_headset"] = byteArrayOf(0x01)
                map["device_type"] = byteArrayOf(0x00, 0x01)
                map["vendor_id"] = ByteUtils.toBytes(XIAOMI_VENDOR_ID)
                map["product_id"] = ByteUtils.toBytes(XIAOMI_PRODUCT_ID)
                map["companion_app"] = "dev.yinpagi.ears".toByteArray()
                map["fastpair_customized"] = byteArrayOf(0x01)
                param.result = map
            }
        })
    }

    private fun hookNoiseControl(cl: ClassLoader) {
        listOf(
            "com.android.bluetooth.ble.app.headset.BluetoothHeadsetService",
            "com.xiaomi.bluetooth.ble.app.headset.BluetoothHeadsetService"
        ).forEach { cn ->
            try {
                val svc = findClass(cn, cl)
                XposedBridge.hookAllMethods(svc, "setAncLevel", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val dev = param.args.firstOrNull() as? BluetoothDevice ?: return
                        val name = runCatching { dev.name ?: "" }.getOrDefault("")
                        if (!name.startsWith(MATCH_NAME_PREFIX, ignoreCase = true)) return
                        val level = (param.args.lastOrNull() as? Int) ?: return
                        val mode = when (level) {
                            0 -> RcspFrame.ANC_MODE_OFF
                            1 -> RcspFrame.ANC_MODE_ON
                            2 -> RcspFrame.ANC_MODE_TRANSPARENCY
                            else -> return
                        }
                        HeadsetBus.get(dev)?.setNoiseMode(mode)
                        param.result = true
                    }
                })
            } catch (_: Throwable) { }
        }
    }
}

object ByteUtils {
    fun toBytes(v: Int): ByteArray = byteArrayOf((v shr 8).toByte(), v.toByte())
}

object HeadsetBus {
    private val map = mutableMapOf<String, JieliGattController>()
    fun get(device: BluetoothDevice): JieliGattController? = map[device.address]
    fun register(device: BluetoothDevice, ctx: android.content.Context) {
        map.getOrPut(device.address) {
            JieliGattController(ctx, device).also { it.connect() }
        }
    }
}
