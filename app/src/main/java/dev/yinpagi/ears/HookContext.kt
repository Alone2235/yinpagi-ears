package dev.yinpagi.ears

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/** Base for per-process hook installers (libxposed API 102 chained style). */
abstract class HookContext {
    lateinit var module: XposedModule
    lateinit var appClassLoader: ClassLoader
    lateinit var packageName: String

    abstract fun install()

    fun findClass(name: String): Class<*> = Class.forName(name, false, appClassLoader)

    fun findClassOrNull(name: String): Class<*>? = runCatching { findClass(name) }.getOrNull()

    fun findMethod(className: String, methodName: String, vararg types: Class<*>): Method =
        findClass(className).getDeclaredMethod(methodName, *types).apply { isAccessible = true }

    fun findMethodByParamCount(className: String, methodName: String, count: Int): Method =
        findClass(className).declaredMethods
            .first { it.name == methodName && it.parameterCount == count }
            .apply { isAccessible = true }

    /** Run [block] before original; block may set [HookParam.result] to skip original. */
    fun hookBefore(method: Method, block: HookParam.() -> Unit) {
        module.hook(method).intercept { chain ->
            val p = HookParam(chain, null).apply(block)
            if (p.hasResult) p.result else chain.proceed()
        }
    }

    /** Same as [hookAfter] but for constructors (libxposed accepts Executable). */
    fun hookAfterCtor(ctor: java.lang.reflect.Constructor<*>, block: HookParam.() -> Unit) {
        module.hook(ctor).intercept { chain ->
            val original = chain.proceed()
            HookParam(chain, original).apply(block).result
        }
    }

    /** Run [block] after original; block may replace [HookParam.result]. */
    fun hookAfter(method: Method, block: HookParam.() -> Unit) {
        module.hook(method).intercept { chain ->
            val original = chain.proceed()
            HookParam(chain, original).apply(block).result
        }
    }
}

class HookParam(chain: XposedInterface.Chain, initialResult: Any?) {
    val args: List<Any?> = chain.args
    val instance: Any? = chain.thisObject

    var hasResult: Boolean = false
        private set

    var result: Any? = initialResult
        set(value) {
            hasResult = true
            field = value
        }
}

fun getObjectField(instance: Any?, fieldName: String): Any? {
    var type: Class<*>? = instance?.javaClass
    while (type != null) {
        val cur = type
        runCatching {
            return cur.getDeclaredField(fieldName).apply { isAccessible = true }.get(instance)
        }
        type = cur.superclass
    }
    throw NoSuchFieldException(fieldName)
}
