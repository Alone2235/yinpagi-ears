package dev.yinpagi.ears

import android.app.Application
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * LSPosed entry using the modern libxposed API (102).
 *
 * Declared via META-INF/xposed/java_init.list (not assets/xposed_init).
 * Recommended scope lives in META-INF/xposed/scope.list.
 */
class HookEntry : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        Log.module = this
        Log.i("onModuleLoaded process=${param.processName} api=$apiVersion")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        Log.module = this
        val pkg = param.packageName
        val proc = runCatching { Application.getProcessName() }.getOrDefault("?")
        Log.i("onPackageReady pkg=$pkg process=$proc first=${param.isFirstPackage}")

        if (!param.isFirstPackage) return
        installFor(pkg, proc, param.classLoader)
    }

    private fun installFor(pkg: String, proc: String, cl: ClassLoader) {
        val hooks: List<HookContext> = when (pkg) {
            "com.android.bluetooth" -> listOf(BluetoothProcessHook())
            "com.xiaomi.bluetooth" -> listOf(XiaomiBluetoothProcessHook())
            "com.android.systemui" -> listOf(SystemUIPluginHook())
            "com.milink.service" -> {
                if (proc in MILINK_PROCESSES) listOf(MiLinkServiceHook()) else {
                    Log.i("skip milink process=$proc"); emptyList()
                }
            }
            else -> emptyList()
        }
        if (hooks.isEmpty()) return
        hooks.forEach { hook ->
            hook.module = this
            hook.appClassLoader = cl
            hook.packageName = pkg
            runCatching { hook.install() }
                .onSuccess { Log.i("installed ${hook.javaClass.simpleName} in $pkg") }
                .onFailure { Log.e("install failed ${hook.javaClass.simpleName} in $pkg", it) }
        }
    }

    private companion object {
        // Every process that may host MxBluetoothManager. The class lives in
        // com.milink.runtime on this ROM, so include it as well.
        val MILINK_PROCESSES = setOf(
            "com.milink.runtime",
            "com.milink.service",
            "com.milink.service:core",
            "com.milink.service:audio",
            "com.milink.service:ui",
            "com.milink.service:provider",
            "com.milink.crossdeviceservice",
        )
    }
}
