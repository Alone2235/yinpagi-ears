package dev.yinpagi.ears

import android.content.Context

/**
 * SystemUI loads its device-center code from the plugin apk "miui.systemui.plugin".
 * Classes like DeviceInfoWrapper are NOT visible from the app classloader at
 * onPackageReady time (verified: Class.forName failed). We must wait until the
 * plugin is loaded, grab its ClassLoader, and only then install the card hook.
 *
 * Mirrors HyperOriG's SystemUIPluginHook approach.
 */
internal class SystemUIPluginHook : HookContext() {

    private val initializedLoaders = mutableSetOf<ClassLoader>()

    override fun install() {
        // OS4 style: plugin callbacks run inside loadPlugin; install early hook first.
        runCatching {
            val factoryCls = findClass(
                "com.android.systemui.shared.plugins.PluginInstance\$PluginFactory",
            )
            val m = factoryCls.declaredMethods.firstOrNull {
                it.name == "createPluginContext"
            }
            m?.isAccessible = true
            if (m != null) {
                hookAfter(m) {
                    val ctx = result as? Context ?: return@hookAfter
                    if (ctx.packageName == "miui.systemui.plugin") {
                        initialize(ctx.classLoader)
                    }
                }
                Log.i("early plugin context hook installed")
            }
        }.onFailure { Log.i("early plugin hook unavailable, using loadPlugin fallback") }

        runCatching {
            val pluginCls = findClass("com.android.systemui.shared.plugins.PluginInstance")
            val m = pluginCls.declaredMethods.firstOrNull { it.name == "loadPlugin" }
            m?.isAccessible = true
            if (m != null) {
                hookAfter(m) {
                    val self = instance ?: return@hookAfter
                    val pkg = runCatching {
                        callInstance(self, "getPackageName")
                    }.getOrElse { callInstance(self, "getPackage") } as? String
                    if (pkg != "miui.systemui.plugin") return@hookAfter
                    val loader = runCatching {
                        val data = getObjectField(self, "pluginData")
                            ?: throw IllegalStateException("pluginData null")
                        val ctx = getObjectField(data, "context") as? Context
                            ?: throw IllegalStateException("context null")
                        ctx.classLoader
                    }.getOrElse {
                        val factory = getObjectField(self, "mPluginFactory")
                            ?: throw IllegalStateException("factory null")
                        (callOn(factory.javaClass, factory, "get")) as? ClassLoader
                            ?: throw IllegalStateException("loader null")
                    }
                    if (loader != null) initialize(loader)
                }
                Log.i("loadPlugin hook installed")
            } else Log.e("PluginInstance.loadPlugin not found")
        }.onFailure { Log.e("loadPlugin hook failed", it) }
    }

    private fun initialize(loader: ClassLoader) = synchronized(initializedLoaders) {
        if (loader in initializedLoaders) return@synchronized
        Log.i("plugin classloader captured, installing card hook")
        val cardHook = SystemUICardHook().apply {
            module = this@SystemUIPluginHook.module
            appClassLoader = loader
            packageName = this@SystemUIPluginHook.packageName
        }
        runCatching { cardHook.install() }
            .onSuccess { Log.i("SystemUICardHook installed in plugin classloader") }
            .onFailure { Log.e("SystemUICardHook install failed", it) }
        initializedLoaders.add(loader)
    }

    private fun callInstance(instance: Any, name: String): Any? =
        instance.javaClass.methods.firstOrNull { it.name == name }?.invoke(instance)

    private fun callOn(cls: Class<*>, instance: Any, name: String): Any? =
        cls.methods.firstOrNull { it.name == name }?.invoke(instance)
}
