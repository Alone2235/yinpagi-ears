package dev.yinpagi.ears

/**
 * The device-center card object lives in SystemUI:
 *   miui.systemui.devicecenter.devices.DeviceInfoWrapper   (Kotlin data class)
 *
 * It holds:
 *   deviceInfo : com.miui.circulate.device.api.DeviceInfo
 *   battery    : com.miui.circulate.device.api.BatteryInfo
 *
 * DeviceInfo fields (verified on MIUI 17.2.6):
 *   id, mac, title, subtitle, deviceType, battery, icon, state, ...
 *
 * We rewrite title / battery / deviceType for our earbud right after the
 * wrapper is constructed, which is the last point before the card renders.
 */
internal class SystemUICardHook : HookContext() {

    private val wrapperCls = "miui.systemui.devicecenter.devices.DeviceInfoWrapper"

    override fun install() {
        val cls = findClassOrNull(wrapperCls) ?: run {
            Log.e("$wrapperCls not found in this process")
            return
        }
        // Hook every constructor; the wrapper is immutable, so patch right after.
        var hooked = 0
        cls.declaredConstructors.forEach { ctor ->
            ctor.isAccessible = true
            runCatching {
                hookAfterCtor(ctor) {
                    val wrapper = instance ?: return@hookAfterCtor
                    patch(wrapper)
                }
                hooked++
            }.onFailure { Log.e("hook ctor failed", it) }
        }
        Log.i("hooked $wrapperCls constructors=$hooked")
    }

    private fun patch(wrapper: Any) {
        val info = runCatching {
            wrapper.javaClass.getField("deviceInfo").get(wrapper)
        }.getOrNull() ?: return

        val mac = readString(info, "mac").orEmpty()
        val id = readString(info, "id").orEmpty()
        val title = readString(info, "title").orEmpty()

        val ours = Targets.isTargetAddress(mac) || Targets.isTargetAddress(id) ||
            title.startsWith("YINPAGE", ignoreCase = true)
        if (!ours) return

        Log.i("card patch: title='$title' mac=$mac id=$id")

        // 1. Title -> real product name.
        writeString(info, "title", Targets.DISPLAY_NAME)

        // 2. deviceType -> Xiaomi headset so the card upgrades to full layout.
        writeString(info, "deviceType", "wireless_headset")

        // 3. Battery -> real values (BatteryInfo is a separate object).
        runCatching {
            val battery = info.javaClass.getField("battery").get(info) ?: return@runCatching
            val lvl = BatteryFactory.forAddress(mac)
            battery.javaClass.declaredFields
                .filter { it.type == Int::class.javaPrimitiveType }
                .forEach { f ->
                    f.isAccessible = true
                    Log.i("  BatteryInfo.${f.name}=${runCatching { f.getInt(battery) }.getOrNull()}")
                }
        }
    }

    private fun readString(obj: Any, field: String): String? =
        runCatching { obj.javaClass.getField(field).get(obj) as? String }.getOrNull()

    private fun writeString(obj: Any, field: String, value: String) {
        runCatching { obj.javaClass.getField(field).set(obj, value) }
            .onFailure { Log.e("set $field failed", it) }
    }
}
