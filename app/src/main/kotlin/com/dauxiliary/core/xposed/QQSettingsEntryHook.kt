package com.dauxiliary.core.xposed

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.dauxiliary.core.registry.AppTarget
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap

/** QQ entry implemented at QQ's settings provider layer, with a view fallback. */
internal object QQSettingsEntryHook {
    private const val TAG = "DAuxiliary"
    private const val MAIN_SETTING_FRAGMENT = "com.tencent.mobileqq.setting.main.MainSettingFragment"
    private const val FUNCTION0 = "kotlin.jvm.functions.Function0"
    private val installedLoaders = Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    private val installedMethods = Collections.newSetFromMap(WeakHashMap<Method, Boolean>())
    private val injectedLists = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val injectedParents = Collections.newSetFromMap(WeakHashMap<ViewGroup, Boolean>())

    fun install(xposed: XposedInterface, classLoader: ClassLoader) {
        synchronized(installedLoaders) {
            if (installedLoaders.contains(classLoader)) return
        }
        runCatching {
            var providerCount = 0
            listOf(
                "com.tencent.mobileqq.setting.main.MainSettingConfigProvider",
                "com.tencent.mobileqq.setting.main.NewSettingConfigProvider",
                "com.tencent.mobileqq.setting.main.b",
            ).forEach { providerCount += hookProvider(xposed, classLoader, it) }
            hookFragmentFallback(xposed, classLoader)
            synchronized(installedLoaders) { installedLoaders.add(classLoader) }
            android.util.Log.i(TAG, "QQ entry hooks registered, providers=$providerCount")
        }.onFailure { error ->
            android.util.Log.w(TAG, "QQ entry hook registration failed", error)
        }
    }

    private fun hookProvider(xposed: XposedInterface, classLoader: ClassLoader, name: String): Int {
        val provider = runCatching { classLoader.loadClass(name) }.getOrNull() ?: return 0
        val methods = provider.declaredMethods.filter { method ->
            List::class.java.isAssignableFrom(method.returnType) &&
                method.parameterTypes.size == 1 &&
                Context::class.java.isAssignableFrom(method.parameterTypes[0])
        }
        methods.forEach { method ->
            method.isAccessible = true
            synchronized(installedMethods) {
                if (!installedMethods.add(method)) return@forEach
            }
            xposed.hook(method)
                .setId("qq.settings.provider.${provider.simpleName}.${method.name}")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    val context = chain.args.firstOrNull() as? Context
                    if (context != null) injectProviderList(result, context, classLoader)
                    result
                }
        }
        return methods.size
    }

    private fun injectProviderList(result: Any?, context: Context, classLoader: ClassLoader) {
        val groups = result as? MutableList<Any?> ?: return
        synchronized(injectedLists) {
            if (!injectedLists.add(groups)) return
        }
        val group = groups.firstOrNull() ?: return
        val processors = findNestedList(group) ?: run {
            synchronized(injectedLists) { injectedLists.remove(groups) }
            return
        }
        val processorClass = findSimpleProcessorClass(classLoader) ?: run {
            synchronized(injectedLists) { injectedLists.remove(groups) }
            android.util.Log.w(TAG, "QQ SimpleItemProcessor class not identified for this version")
            return
        }
        val processor = createProcessor(processorClass, context, classLoader) ?: run {
            synchronized(injectedLists) { injectedLists.remove(groups) }
            return
        }
        val newGroup = createGroup(group.javaClass, processor) ?: run {
            synchronized(injectedLists) { injectedLists.remove(groups) }
            return
        }
        runCatching { groups.add(minOf(1, groups.size), newGroup) }
            .onFailure {
                synchronized(injectedLists) { injectedLists.remove(groups) }
                android.util.Log.w(TAG, "QQ settings provider list is not mutable", it)
            }
    }

    private fun findSimpleProcessorClass(classLoader: ClassLoader): Class<*>? {
        val candidateNames = listOf(
            "com.tencent.mobileqq.setting.processor.g",
            "com.tencent.mobileqq.setting.processor.h",
            "com.tencent.mobileqq.setting.processor.i",
            "com.tencent.mobileqq.setting.processor.j",
            "as3.i",
            "c25.i",
            "a35.i",
        )
        val baseClass = listOf(
            "com.tencent.mobileqq.setting.main.processor.AccountSecurityItemProcessor",
            "com.tencent.mobileqq.setting.main.processor.AboutItemProcessor",
        ).firstNotNullOfOrNull { name ->
            runCatching { classLoader.loadClass(name).superclass }.getOrNull()
        } ?: return null
        val candidates = candidateNames.mapNotNull { name ->
            runCatching { classLoader.loadClass(name) }.getOrNull()
                ?.takeIf { it.superclass == baseClass && it.declaredConstructors.any(::isProcessorConstructor) }
        }.distinct()
        return candidates.singleOrNull()
    }

    private fun createProcessor(type: Class<*>, context: Context, classLoader: ClassLoader): Any? {
        val constructor = type.declaredConstructors.firstOrNull(::isProcessorConstructor) ?: return null
        constructor.isAccessible = true
        val args = if (constructor.parameterTypes.size == 5) {
            arrayOf(context, 0, "DAuxiliary", 0, null)
        } else {
            arrayOf(context, 0, "DAuxiliary", 0)
        }
        val processor = runCatching { constructor.newInstance(*args) }.getOrNull() ?: return null
        val setter = type.declaredMethods.firstOrNull { method ->
            method.returnType == Void.TYPE && method.parameterTypes.size == 1 &&
                method.parameterTypes[0].name == FUNCTION0
        } ?: return processor
        setter.isAccessible = true
        val callbackType = setter.parameterTypes[0]
        val unit = runCatching {
            callbackType.classLoader?.loadClass("kotlin.Unit")?.getField("INSTANCE")?.get(null)
        }.getOrNull()
        val callback = Proxy.newProxyInstance(
            callbackType.classLoader ?: classLoader,
            arrayOf(callbackType),
        ) { _, method, _ ->
            if (method.name == "invoke") {
                findActivity(context)?.let { HostSettingsDialog.show(it, AppTarget.QQ) }
            }
            unit
        }
        runCatching { setter.invoke(processor, callback) }
        return processor
    }

    private fun isProcessorConstructor(constructor: Constructor<*>): Boolean {
        val p = constructor.parameterTypes
        return p.size in 4..5 &&
            Context::class.java.isAssignableFrom(p[0]) &&
            p[1] == Int::class.javaPrimitiveType &&
            CharSequence::class.java.isAssignableFrom(p[2]) &&
            p[3] == Int::class.javaPrimitiveType &&
            (p.size == 4 || p[4] == String::class.java)
    }

    private fun createGroup(type: Class<*>, processor: Any): Any? {
        val constructor = type.declaredConstructors.firstOrNull { constructor ->
            val p = constructor.parameterTypes
            p.size == 3 && List::class.java.isAssignableFrom(p[0]) &&
                CharSequence::class.java.isAssignableFrom(p[1]) &&
                CharSequence::class.java.isAssignableFrom(p[2])
        } ?: type.declaredConstructors.firstOrNull { constructor ->
            val p = constructor.parameterTypes
            p.size == 5 && List::class.java.isAssignableFrom(p[0]) &&
                CharSequence::class.java.isAssignableFrom(p[1]) &&
                CharSequence::class.java.isAssignableFrom(p[2])
        } ?: return null
        constructor.isAccessible = true
        val values = arrayListOf(processor)
        return runCatching {
            if (constructor.parameterTypes.size == 5) {
                constructor.newInstance(values, "", "", 6, null)
            } else {
                constructor.newInstance(values, "", "")
            }
        }.getOrNull()
    }

    private fun findNestedList(value: Any): MutableList<Any?>? {
        value.javaClass.declaredFields.forEach { field ->
            runCatching {
                field.isAccessible = true
                val nested = field.get(value)
                if (nested is MutableList<*>) return nested as MutableList<Any?>
            }
        }
        return null
    }

    private fun hookFragmentFallback(xposed: XposedInterface, classLoader: ClassLoader) {
        val fragment = runCatching { classLoader.loadClass(MAIN_SETTING_FRAGMENT) }.getOrNull() ?: return
        fragment.declaredMethods.filter { it.name in setOf("doOnCreateView", "onViewCreated", "doOnCreate") }
            .forEach { method ->
                method.isAccessible = true
                synchronized(installedMethods) {
                    if (!installedMethods.add(method)) return@forEach
                }
                xposed.hook(method)
                    .setId("qq.main_setting.${method.name}.${method.parameterTypes.size}")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val result = chain.proceed()
                        val activity = findActivity(chain.thisObject)
                        if (activity != null) {
                            activity.runOnUiThread {
                                val view = (result as? View) ?: findFragmentView(chain.thisObject)
                                view?.postDelayed({ injectViewTree(view, activity) }, 120L)
                            }
                        }
                        result
                    }
            }
    }

    private fun findActivity(value: Any?): Activity? {
        if (value is Activity) return value
        var current: Context? = value as? Context
        repeat(4) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
        }
        return runCatching {
            value?.javaClass?.methods
                ?.firstOrNull { it.name == "getActivity" && it.parameterTypes.isEmpty() }
                ?.invoke(value) as? Activity
        }.getOrNull()
    }

    private fun findFragmentView(value: Any?): View? = runCatching {
        value?.javaClass?.methods
            ?.firstOrNull { it.name == "getView" && it.parameterTypes.isEmpty() }
            ?.invoke(value) as? View
    }.getOrNull()

    private fun injectViewTree(view: View, activity: Activity) {
        if (!view.isAttachedToWindow || view.findViewWithTag<View>("$TAG.qq_view") != null) return
        val queue = ArrayDeque<ViewGroup>()
        if (view is ViewGroup) queue.add(view)
        while (queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            if (parent.findViewWithTag<View>("$TAG.qq_view") != null) return
            val row = TextView(activity).apply {
                tag = "$TAG.qq_view"
                text = "DAuxiliary 模块设置"
                textSize = 16f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(activity, 20), dp(activity, 16), dp(activity, 20), dp(activity, 16))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(activity, 12).toFloat()
                }
                setOnClickListener { HostSettingsDialog.show(activity, AppTarget.QQ) }
            }
            if (runCatching { parent.addView(row, 0, ViewGroup.MarginLayoutParams(-1, -2)) }.isSuccess) {
                injectedParents.add(parent)
                return
            }
            for (index in 0 until parent.childCount) {
                (parent.getChildAt(index) as? ViewGroup)?.let(queue::add)
            }
        }
    }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}