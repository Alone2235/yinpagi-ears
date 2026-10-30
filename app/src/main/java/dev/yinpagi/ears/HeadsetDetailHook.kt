package dev.yinpagi.ears

/**
 * The native headset popup is rendered by
 *   com.miui.circulateplus.world.headset.HeadSetsDetail
 * whose bind method is name-obfuscated (verified: "p" on 17.2.6).
 *
 * Signature we match (signature-based, name independent):
 *   void xxx(CirculateServiceInfo, ?, HeadsetDeviceInfo, CirculateDeviceInfo)
 *
 * HeadsetDeviceInfo exposes public fields: name / power / mode / type / mac /
 * headsetVolume / findRingState / audioEffectState / wiredState / isOutput.
 * We rewrite those for our earbud before the view binds.
 */
internal class HeadsetDetailHook : HookContext() {

    private val detailClass = "com.miui.circulateplus.world.headset.HeadSetsDetail"
    private val serviceInfo = "com.miui.circulate.api.service.CirculateServiceInfo"
    private val deviceInfo = "com.miui.circulate.api.protocol.headset.HeadsetDeviceInfo"
    private val circulateDeviceInfo = "com.miui.circulate.api.service.CirculateDeviceInfo"

    override fun install() {
        val cls = findClassOrNull(detailClass) ?: run {
            Log.e("HeadSetsDetail not found"); return
        }
        val bind = cls.declaredMethods.firstOrNull { m ->
            m.parameterTypes.size == 4 &&
                m.parameterTypes[0].name == serviceInfo &&
                m.parameterTypes[2].name == deviceInfo &&
                m.parameterTypes[3].name == circulateDeviceInfo
        } ?: run { Log.e("HeadSetsDetail bind method not found"); return }

        bind.isAccessible = true
        Log.i("found HeadSetsDetail bind=${bind.name}")

        hookBefore(bind) {
            val info = args.getOrNull(2) ?: return@hookBefore
            applyOverrides(info)
        }
        Log.i("hooked $detailClass.${bind.name}")
    }

    /** Rewrite HeadsetDeviceInfo fields for our earbud. */
    private fun applyOverrides(info: Any) {
        val cls = info.javaClass
        val mac = readString(cls, info, "mac").orEmpty()
        val name = readString(cls, info, "name").orEmpty()
        val ours = Targets.isTargetAddress(mac) ||
            name.startsWith("YINPAGE", ignoreCase = true)
        if (!ours) return

        writeString(cls, info, "name", Targets.DISPLAY_NAME)
        runCatching {
            cls.getField("power").set(info, BatteryFactory.forAddress(mac))
        }.onFailure { Log.e("set power failed", it) }
        runCatching {
            cls.getField("mode").setInt(info, NoiseState.currentMode(mac))
        }.onFailure { Log.e("set mode failed", it) }
        Log.i("overrode HeadsetDeviceInfo name=$name -> ${Targets.DISPLAY_NAME} mac=$mac")
    }

    private fun readString(cls: Class<*>, obj: Any, field: String): String? =
        runCatching { cls.getField(field).get(obj) as? String }.getOrNull()

    private fun writeString(cls: Class<*>, obj: Any, field: String, value: String) =
        runCatching { cls.getField(field).set(obj, value) }
            .onFailure { Log.e("set $field failed", it) }
}
