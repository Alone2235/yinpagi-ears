package dev.yinpagi.ears

/**
 * HeadsetInfo is the Kotlin data class that actually feeds the native card.
 * Verified getters on com.milink.service 17.2.6:
 *   getAddress(): String   getDeviceId(): String   getName(): String
 *   getPowers(): List      getMode(): Int          getType(): Int
 *   getSwitchState(): Int  getVolume(): Int        getWiredState(): Int
 *   getFindRingState(): Int  getAudioEffectState(): Int
 *
 * Every getter takes no args; we rewrite results for our earbud only.
 */
internal class HeadsetInfoHook : HookContext() {

    private val cls = "com.miui.headset.api.HeadsetInfo"

    override fun install() {
        // Name: the card header showed "Xiaomi 14 ..." -> force the real name.
        hookGetter("getName") { info -> Targets.displayName(info) }

        // Battery: L/R/case + charging flags.
        hookGetter("getPowers") { info -> BatteryFactory.forAddress(Targets.addressOf(info)) }

        // ANC mode (0 off / 1 anc / 2 transparency).
        hookGetter("getMode") { info -> NoiseState.currentMode(Targets.addressOf(info)) }

        // Advertise ANC switch support so the panel renders.
        hookGetter("getSwitchState") { _ -> 1 }

        // Volume: keep whatever the system has; nothing to force.
        hookGetter("getFindRingState") { _ -> 0 }
        hookGetter("getAudioEffectState") { _ -> 0 }
        hookGetter("getWiredState") { _ -> 0 }
    }

    /** Hook one no-arg getter; [value] returns the replacement, or null to keep. */
    private fun hookGetter(methodName: String, value: (Any) -> Any?) = runCatching {
        val m = findMethodByParamCount(cls, methodName, 0)
        hookAfter(m) {
            val info = instance ?: return@hookAfter
            if (!Targets.isTargetInfo(info)) return@hookAfter
            value(info)?.let { result = it }
        }
        Log.i("hooked $cls.$methodName")
    }.onFailure { Log.e("$cls.$methodName unavailable", it) }
}
