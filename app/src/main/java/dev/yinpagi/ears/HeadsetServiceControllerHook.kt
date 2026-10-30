package dev.yinpagi.ears

/**
 * com.miui.circulate.api.protocol.headset.HeadsetServiceController
 * drives the native device-center card. Verified on 17.2.6:
 *
 *   clientIsMmaHeadset(CirculateDeviceInfo, CirculateServiceInfo): boolean
 *   clientGetHeadsetProperty(CirculateDeviceInfo, HeadsetDeviceInfo): int
 *   convertAncState(int): int
 *
 * clientIsMmaHeadset decides "is this a Xiaomi headset"; without it the card
 * stays in the minimal third-party layout (that is why the popup showed only
 * disconnect / more settings and the phone name as title).
 */
internal class HeadsetServiceControllerHook : HookContext() {

    private val cls = "com.miui.circulate.api.protocol.headset.HeadsetServiceController"
    private val deviceInfo = "com.miui.circulate.api.protocol.headset.HeadsetDeviceInfo"

    override fun install() {
        Log.i("HeadsetServiceControllerHook.install start")
        val c = findClassOrNull(cls)
        if (c == null) {
            Log.e("$cls NOT FOUND in this process")
            return
        }
        Log.i("$cls found, methods=${c.declaredMethods.size}")
        runCatching {
            c.declaredMethods.filter {
                it.name.contains("Mma", true) || it.name.contains("Headset", true)
            }.forEach { Log.i("  candidate: ${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})") }
        }
        hookIsMmaHeadset()
        hookGetHeadsetProperty()
        Log.i("HeadsetServiceControllerHook.install done")
    }

    /** Force MMA (Xiaomi) classification for our earbud. */
    private fun hookIsMmaHeadset() = runCatching {
        val found = findClass(cls).declaredMethods.firstOrNull {
            it.name == "clientIsMmaHeadset" && it.parameterCount == 2
        }
        if (found == null) {
            Log.e("clientIsMmaHeadset method missing")
            return@runCatching
        }
        found.isAccessible = true
        Log.i("hooking clientIsMmaHeadset params=${found.parameterTypes.map { p -> p.name }}")
        val m = found
        hookAfter(m) {
            val addr = addressFrom(args.getOrNull(0)) ?: return@hookAfter
            if (!Targets.isTargetAddress(addr)) return@hookAfter
            Log.i("clientIsMmaHeadset -> true for $addr")
            result = true
        }
        Log.i("hooked $cls.clientIsMmaHeadset")
    }.onFailure { Log.e("clientIsMmaHeadset unavailable", it) }

    /** Fill HeadsetDeviceInfo fields (name / power / mode) for the card. */
    private fun hookGetHeadsetProperty() = runCatching {
        val found = findClass(cls).declaredMethods.firstOrNull {
            it.name == "clientGetHeadsetProperty" && it.parameterCount == 2 &&
                it.parameterTypes[1].name == deviceInfo
        }
        if (found == null) {
            Log.e("clientGetHeadsetProperty method missing")
            return@runCatching
        }
        found.isAccessible = true
        Log.i("hooking clientGetHeadsetProperty")
        val m = found
        hookBefore(m) {
            val addr = addressFrom(args.getOrNull(0)) ?: return@hookBefore
            if (!Targets.isTargetAddress(addr)) return@hookBefore
            val info = args.getOrNull(1) ?: return@hookBefore
            overwrite(info, addr)
            Log.i("clientGetHeadsetProperty filled for $addr")
            result = 100  // HEADSET_OPERATION_SUCCESS
        }
        Log.i("hooked $cls.clientGetHeadsetProperty")
    }.onFailure { Log.e("clientGetHeadsetProperty unavailable", it) }

    private fun overwrite(info: Any, addr: String) {
        val c = info.javaClass
        runCatching { c.getField("name").set(info, Targets.DISPLAY_NAME) }
        runCatching { c.getField("power").set(info, BatteryFactory.forAddress(addr)) }
        runCatching { c.getField("mode").setInt(info, NoiseState.currentMode(addr)) }
        runCatching { c.getField("mac").set(info, addr) }
    }

    /** CirculateDeviceInfo exposes the MAC under some field name. */
    private fun addressFrom(obj: Any?): String? {
        if (obj == null) return null
        val c = obj.javaClass
        for (name in listOf("mac", "address", "deviceId", "id")) {
            runCatching {
                val v = c.getField(name).get(obj) as? String
                if (!v.isNullOrBlank()) return v
            }
            runCatching {
                val v = c.getMethod("get" + name.replaceFirstChar { it.uppercase() })
                    .invoke(obj) as? String
                if (!v.isNullOrBlank()) return v
            }
        }
        return null
    }
}
