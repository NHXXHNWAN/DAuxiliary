package com.dauxiliary.core.xposed

import android.content.Context
import android.util.Log
import com.dauxiliary.core.registry.AppTarget
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap

/** QQ 9.3.x settings entry using QQ's native setting-item model. */
internal object QQSettingsEntryHook {
    private const val TAG = "DAuxiliary"
    private val PROVIDER_NAMES = listOf(
        "com.tencent.mobileqq.setting.main.MainSettingConfigProvider",
        "com.tencent.mobileqq.setting.main.NewSettingConfigProvider",
        "com.tencent.mobileqq.setting.main.b",
    )
    private const val SIMPLE_PROCESSOR = "com.tencent.mobileqq.setting.processor.i"
    private const val TITLE = "DAuxiliary 模块设置"

    private val installedLoaders = Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    private val installedMethods = Collections.newSetFromMap(WeakHashMap<Method, Boolean>())
    private val injectedLists = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())

    fun install(xposed: XposedInterface, classLoader: ClassLoader) {
        synchronized(installedLoaders) {
            if (!installedLoaders.add(classLoader)) return
        }
        runCatching {
            QQDexKitResolver.warmUp(classLoader)
            val hooks = hookProviders(xposed, classLoader)
            if (hooks == 0) {
                synchronized(installedLoaders) { installedLoaders.remove(classLoader) }
                Log.w(TAG, "No compatible QQ settings provider found")
            } else {
                Log.i(TAG, "QQ native settings entry hooks registered: providers=$hooks")
            }
        }.onFailure { error ->
            synchronized(installedLoaders) { installedLoaders.remove(classLoader) }
            Log.e(TAG, "QQ settings hook registration failed", error)
        }
    }

    private fun hookProviders(xposed: XposedInterface, classLoader: ClassLoader): Int {
        var count = 0
        PROVIDER_NAMES.forEach { name ->
            val provider = runCatching { classLoader.loadClass(name) }.getOrNull() ?: return@forEach
            provider.declaredMethods
                .filter { it.parameterTypes.contentEquals(arrayOf(Context::class.java)) && List::class.java.isAssignableFrom(it.returnType) }
                .forEach { method ->
                    if (!markMethod(method)) return@forEach
                    method.isAccessible = true
                    xposed.hook(method)
                        .setId("qq.settings.${provider.simpleName}.${method.name}")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept { chain ->
                            val result = chain.proceed()
                            val context = chain.args.firstOrNull() as? Context
                            if (context != null) injectProviderGroups(result, context, classLoader)
                            result
                        }
                    count++
                    Log.i(TAG, "QQ settings provider hooked: ${provider.name}.${method.name}")
                }
        }
        return count
    }

    private fun injectProviderGroups(result: Any?, context: Context, classLoader: ClassLoader) {
        val groups = (result as? MutableList<*>)?.let {
            @Suppress("UNCHECKED_CAST")
            it as MutableList<Any?>
        } ?: return
        synchronized(injectedLists) { if (!injectedLists.add(groups)) return }

        val candidates = groups.mapNotNull { group -> group?.let { findNestedMutableList(it)?.let { list -> it to list } } }
        if (candidates.isEmpty()) {
            removeInjectedMarker(groups)
            Log.w(TAG, "QQ settings provider returned no mutable item group")
            return
        }

        val processorClass = QQDexKitResolver.resolveOrNull(classLoader)
            ?: sequenceOf(SIMPLE_PROCESSOR, "com.tencent.mobileqq.setting.processor.h", "com.tencent.mobileqq.setting.processor.g")
                .mapNotNull { runCatching { classLoader.loadClass(it) }.getOrNull() }
                .firstOrNull(QQDexKitResolver::isSimpleProcessor)
        if (processorClass == null) {
            removeInjectedMarker(groups)
            Log.w(TAG, "QQ setting-item processor not resolved")
            return
        }

        val target = candidates.firstOrNull { (group, _) -> readCharSequenceFields(group).any { it == "功能" } }
            ?: candidates.firstOrNull { (_, items) -> items.any { it?.javaClass == processorClass } }
            ?: candidates.getOrNull(2)
            ?: candidates.first()
        val processor = createProcessor(processorClass, context, classLoader)
        if (processor == null) {
            removeInjectedMarker(groups)
            Log.w(TAG, "QQ setting-item processor construction failed")
            return
        }
        runCatching { target.second.add(processor) }
            .onSuccess { Log.i(TAG, "QQ native entry injected into ${target.first.javaClass.name}") }
            .onFailure { error ->
                removeInjectedMarker(groups)
                Log.e(TAG, "QQ native settings item insertion failed", error)
            }
    }

    private fun createProcessor(type: Class<*>, context: Context, classLoader: ClassLoader): Any? {
        val constructor = type.declaredConstructors.firstOrNull(::isSupportedProcessorConstructor) ?: return null
        constructor.isAccessible = true
        val processor = runCatching { instantiateProcessor(constructor, context) }.getOrNull() ?: return null
        val setter = type.declaredMethods.firstOrNull(QQDexKitResolver::isFunction0Setter) ?: return null
        setter.isAccessible = true
        val callbackType = setter.parameterTypes[0]
        val unit = runCatching {
            (callbackType.classLoader ?: classLoader).loadClass("kotlin.Unit").getField("INSTANCE").get(null)
        }.getOrNull()
        val callback = Proxy.newProxyInstance(callbackType.classLoader ?: classLoader, arrayOf(callbackType)) { proxy, method, args ->
            when (method.name) {
                "invoke" -> { QQInProcessSettings.open(context, classLoader); unit }
                "toString" -> "DAuxiliaryCallback"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> unit
            }
        }
        return runCatching { setter.invoke(processor, callback); processor }.getOrNull()
    }

    private fun isSupportedProcessorConstructor(constructor: Constructor<*>): Boolean {
        val p = constructor.parameterTypes
        return (p.size == 5 && Context::class.java.isAssignableFrom(p[0]) && p[1] == Int::class.javaPrimitiveType && CharSequence::class.java.isAssignableFrom(p[2]) && p[3] == Int::class.javaPrimitiveType) ||
            (p.size == 6 && Context::class.java.isAssignableFrom(p[0]) && p[1] == Int::class.javaPrimitiveType && CharSequence::class.java.isAssignableFrom(p[2]) && p[3] == Int::class.javaPrimitiveType)
    }

    private fun instantiateProcessor(constructor: Constructor<*>, context: Context): Any =
        if (constructor.parameterTypes.size == 6) constructor.newInstance(context, 0, TITLE, 0, null, null)
        else constructor.newInstance(context, 0, TITLE, 0, null)

    private fun findNestedMutableList(value: Any): MutableList<Any?>? {
        var type: Class<*>? = value.javaClass
        while (type != null && type != Any::class.java) {
            type.declaredFields.forEach { field -> runCatching { field.isAccessible = true; field.get(value) }.getOrNull()?.let { nested ->
                if (nested is MutableList<*>) {
                    @Suppress("UNCHECKED_CAST")
                    return nested as MutableList<Any?>
                }
            } }
            type = type.superclass
        }
        return null
    }

    private fun readCharSequenceFields(value: Any): List<String> {
        val values = mutableListOf<String>()
        var type: Class<*>? = value.javaClass
        while (type != null && type != Any::class.java) {
            type.declaredFields.forEach { field -> runCatching { field.isAccessible = true; (field.get(value) as? CharSequence)?.toString() }.getOrNull()?.let(values::add) }
            type = type.superclass
        }
        return values
    }

    private fun markMethod(method: Method): Boolean = synchronized(installedMethods) { installedMethods.add(method) }
    private fun removeInjectedMarker(groups: Any) { synchronized(injectedLists) { injectedLists.remove(groups) } }
}
