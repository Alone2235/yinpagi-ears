package dev.yinpagi.ears

import android.app.Application
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * LSPosed entry (libxposed API 102).
 *
 * Scope:
 *   com.android.bluetooth -> A2DP connection detection
 *   com.xiaomi.bluetooth  -> HyperOS headset UI host
 *   com.milink.service    -> MiLink headset card + ANC panel
 */
class HookEntry : XposedModule() {
    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return
        Log.module = this
        val pkg = param.packageName
        Log.i("onPackageLoaded pkg=$pkg process=${Application.getProcessName()}")

        val hooks: List<HookContext> = when (pkg) {
            "com.android.bluetooth" -> listOf(BluetoothProcessHook())
            "com.xiaomi.bluetooth" -> listOf(XiaomiBluetoothProcessHook())
            "com.milink.service" -> {
                val proc = Application.getProcessName()
                if (proc in MILINK_PROCESSES) listOf(MiLinkServiceHook()) else {
                    Log.i("skip milink process=$proc"); emptyList()
                }
            }
            else -> emptyList()
        }
        hooks.forEach { hook ->
            hook.module = this
            hook.appClassLoader = param.defaultClassLoader
            hook.packageName = pkg
            runCatching { hook.install() }
                .onSuccess { Log.i("installed ${hook.javaClass.simpleName} in $pkg") }
                .onFailure { Log.e("install failed ${hook.javaClass.simpleName} in $pkg", it) }
        }
    }

    private companion object {
        val MILINK_PROCESSES = setOf(
            "com.milink.service",
            "com.milink.service:core",
            "com.milink.service:audio",
            "com.milink.service:ui",
        )
    }
}
