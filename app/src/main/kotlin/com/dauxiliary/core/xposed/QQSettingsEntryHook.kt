package com.dauxiliary.core.xposed

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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

/** QQ 9.3.x settings entry. The 9.3.60 provider is main.b -> SettingConfigProvider. */
internal object QQSettingsEntryHook {
    private const val TAG = "DAuxiliary"
    private const val MAIN_PROVIDER = "com.tencent.mobileqq.setting.main.b"
    private const val MAIN_SETTING_FRAGMENT = "com.tencent.mobileqq.setting.main.MainSettingFragment"
    private const val GROUP_PROCESSOR = "com.tencent.mobileqq.setting.processor.b"
    private const val SIMPLE_PROCESSOR = "com.tencent.mobileqq.setting.processor.i"
    private const val SIMPLE_BASE = "com.tencent.mobileqq.setting.processor.c"
    private const val FUNCTION0 = "kotlin.jvm.functions.Function0"
    private const val TITLE = "DAuxiliary 模块设置"

    private val installedLoaders = Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    private val installedMethods = Collections.newSetFromMap(WeakHashMap<Method, Boolean>())
    private val injectedLists = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())

    fun install(xposed: XposedInterface, classLoader: ClassLoader) {
        synchronized(installedLoaders) {
            if (!installedLoaders.add(classLoader)) return
        }
        var providerHooks = 0
        var fallbackHooks = 0
        runCatching {
            QQDexKitResolver.warmUp(classLoader)
            providerHooks = hookProvider(xposed, classLoader)
            fallbackHooks = hookFragmentFallback(xposed, classLoader)
            android.util.Log.i(
                TAG,
                "QQ 9.3 settings hooks registered: provider=$providerHooks fallback=$fallbackHooks",
            )
        }.onFailure { error ->
            synchronized(installedLoaders) { installedLoaders.remove(classLoader) }
            android.util.Log.e(TAG, "QQ settings hook registration failed", error)
        }
    }

    private fun hookProvider(xposed: XposedInterface, classLoader: ClassLoader): Int {
        val provider = runCatching { classLoader.loadClass(MAIN_PROVIDER) }.getOrNull()
        if (provider == null) {
            android.util.Log.w(TAG, "QQ provider class not found: $MAIN_PROVIDER")
            return 0
        }
        val method = provider.declaredMethods.firstOrNull { candidate ->
            candidate.name == "f" &&
                candidate.parameterTypes.contentEquals(arrayOf(Context::class.java)) &&
                List::class.java.isAssignableFrom(candidate.returnType)
        }
        if (method == null) {
            android.util.Log.w(
                TAG,
                "QQ provider method f(Context):List not found; methods=${provider.declaredMethods.joinToString { it.name }}",
            )
            return 0
        }
        if (!markMethod(method)) return 0
        method.isAccessible = true
        xposed.hook(method)
            .setId("qq.settings.main.b.f")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                val context = chain.args.firstOrNull() as? Context
                if (context != null) injectProviderGroups(result, context, classLoader)
                result
            }
        android.util.Log.i(TAG, "QQ provider hooked: ${provider.name}.${method.name}${method.parameterTypes.contentToString()}")
        return 1
    }

    private fun injectProviderGroups(result: Any?, context: Context, classLoader: ClassLoader) {
        val groups = (result as? MutableList<*>)?.let { list ->
            @Suppress("UNCHECKED_CAST")
            list as MutableList<Any?>
        }
        if (groups == null) {
            android.util.Log.w(TAG, "QQ provider returned non-mutable list: ${result?.javaClass?.name}")
            return
        }
        synchronized(injectedLists) {
            if (!injectedLists.add(groups)) return
        }

        val candidates = groups.mapNotNull { group ->
            group ?: return@mapNotNull null
            findNestedMutableList(group)?.let { group to it }
        }
        if (candidates.isEmpty()) {
            removeInjectedMarker(groups)
            android.util.Log.w(TAG, "QQ provider list has no SettingGroupProcessor child list")
            return
        }

        val processorClass = QQDexKitResolver.resolveOrNull(classLoader)
            ?: sequenceOf(
                SIMPLE_PROCESSOR,
                "com.tencent.mobileqq.setting.processor.h",
                "com.tencent.mobileqq.setting.processor.g",
            ).mapNotNull { name -> runCatching { classLoader.loadClass(name) }.getOrNull() }
                .firstOrNull(QQDexKitResolver::isSimpleProcessor)
        if (processorClass == null) {
            removeInjectedMarker(groups)
            android.util.Log.w(TAG, "QQ simple processor not resolved by DexKit or fallback candidates")
            return
        }

        val target = candidates.firstOrNull { (group, _) ->
            readCharSequenceFields(group).any { it == "功能" }
        } ?: candidates.firstOrNull { (_, items) ->
            items.any { it?.javaClass == processorClass }
        } ?: candidates.getOrNull(2)
            ?: candidates.first()

        val processor = createProcessor(processorClass, context, classLoader)
        if (processor == null) {
            removeInjectedMarker(groups)
            android.util.Log.w(TAG, "QQ simple processor construction failed")
            return
        }
        val itemList = target.second
        runCatching {
            itemList.add(processor)
            android.util.Log.i(
                TAG,
                "QQ entry injected into group=${target.first.javaClass.name}, items=${itemList.size}",
            )
        }.onFailure { error ->
            removeInjectedMarker(groups)
            android.util.Log.e(TAG, "QQ group processor list is not mutable", error)
        }
    }

    private fun createProcessor(type: Class<*>, context: Context, classLoader: ClassLoader): Any? {
        val constructor = type.declaredConstructors.firstOrNull(QQDexKitResolver::isSimpleProcessorConstructor) ?: return null
        constructor.isAccessible = true
        val processor = runCatching {
            constructor.newInstance(context, 0, TITLE, 0, null)
        }.getOrNull() ?: return null

        val setter = type.declaredMethods.firstOrNull(QQDexKitResolver::isFunction0Setter)
            ?: return null
        setter.isAccessible = true
        val callbackType = setter.parameterTypes[0]
        val unit = runCatching {
            (callbackType.classLoader ?: classLoader).loadClass("kotlin.Unit")
                .getField("INSTANCE")
                .get(null)
        }.getOrNull()
        val callback = Proxy.newProxyInstance(
            callbackType.classLoader ?: classLoader,
            arrayOf(callbackType),
        ) { proxy, method, args ->
            when (method.name) {
                "invoke" -> {
                    findActivity(context)?.let { HostSettingsDialog.show(it, AppTarget.QQ) }
                    unit
                }
                "toString" -> "DAuxiliaryCallback"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> unit
            }
        }
        return runCatching {
            setter.invoke(processor, callback)
            processor
        }.getOrNull()
    }

    private fun isSimpleProcessorConstructor(constructor: Constructor<*>): Boolean {
        val p = constructor.parameterTypes
        return p.size == 5 &&
            Context::class.java.isAssignableFrom(p[0]) &&
            p[1] == Int::class.javaPrimitiveType &&
            CharSequence::class.java.isAssignableFrom(p[2]) &&
            p[3] == Int::class.javaPrimitiveType &&
            p[4] == String::class.java
    }

    private fun findNestedMutableList(value: Any): MutableList<Any?>? {
        var type: Class<*>? = value.javaClass
        while (type != null && type != Any::class.java) {
            type.declaredFields.forEach { field ->
                runCatching {
                    field.isAccessible = true
                    val nested = field.get(value)
                    if (nested is MutableList<*>) {
                        @Suppress("UNCHECKED_CAST")
                        return nested as MutableList<Any?>
                    }
                }
            }
            type = type.superclass
        }
        return null
    }

    private fun readCharSequenceFields(value: Any): List<String> {
        val values = mutableListOf<String>()
        var type: Class<*>? = value.javaClass
        while (type != null && type != Any::class.java) {
            type.declaredFields.forEach { field ->
                runCatching {
                    field.isAccessible = true
                    (field.get(value) as? CharSequence)?.toString()?.let(values::add)
                }
            }
            type = type.superclass
        }
        return values
    }

    private fun markMethod(method: Method): Boolean = synchronized(installedMethods) {
        installedMethods.add(method)
    }

    private fun removeInjectedMarker(groups: Any) {
        synchronized(injectedLists) { injectedLists.remove(groups) }
    }

    private fun hookFragmentFallback(xposed: XposedInterface, classLoader: ClassLoader): Int {
        val fragment = runCatching { classLoader.loadClass(MAIN_SETTING_FRAGMENT) }.getOrNull() ?: return 0
        val method = fragment.declaredMethods.firstOrNull { candidate ->
            candidate.name == "onViewCreated" &&
                candidate.parameterTypes.contentEquals(arrayOf(View::class.java, Bundle::class.java))
        } ?: return 0
        if (!markMethod(method)) return 0
        method.isAccessible = true
        xposed.hook(method)
            .setId("qq.main_setting.on_view_created")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                val activity = findActivity(chain.thisObject)
                val view = chain.args.firstOrNull() as? View
                if (activity != null && view != null) {
                    activity.runOnUiThread { view.postDelayed({ injectViewTree(view, activity) }, 160L) }
                }
                result
            }
        return 1
    }

    private fun findActivity(value: Any?): Activity? {
        if (value is Activity) return value
        var current: Context? = value as? Context
        repeat(6) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
        }
        return runCatching {
            value?.javaClass?.methods
                ?.firstOrNull { it.name == "getActivity" && it.parameterTypes.isEmpty() }
                ?.invoke(value) as? Activity
        }.getOrNull()
    }

    private fun injectViewTree(view: View, activity: Activity) {
        if (!view.isAttachedToWindow || view.findViewWithTag<View>("$TAG.qq_view") != null) return
        val queue = ArrayDeque<ViewGroup>()
        if (view is ViewGroup) queue.add(view)
        while (queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            if (parent.findViewWithTag<View>("$TAG.qq_view") != null) return
            val row = TextView(activity).apply {
                tag = "$TAG.qq_view"
                text = TITLE
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
            if (runCatching { parent.addView(row, 0, ViewGroup.MarginLayoutParams(-1, -2)) }.isSuccess) return
            for (index in 0 until parent.childCount) {
                (parent.getChildAt(index) as? ViewGroup)?.let(queue::add)
            }
        }
    }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
