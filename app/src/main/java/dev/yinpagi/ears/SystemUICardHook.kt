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
 * We rewrite title / battery for our earbud right after the
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
        // Never let a card patch crash SystemUI.
        runCatching { patchInner(wrapper) }
            .onFailure { Log.e("card patch failed (ignored)", it) }
    }

    private fun patchInner(wrapper: Any) {
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

        // 1. Title -> real product name (pure display, safe).
        writeString(info, "title", Targets.DISPLAY_NAME)

        // NOTE: never touch deviceType here. Changing third_headset to another
        // category makes downstream card controllers resolve resources that do
        // not exist for this device, which crashes SystemUI (the safe-mode cause).

        // 2. Battery -> fill real values if the object already exists.
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
