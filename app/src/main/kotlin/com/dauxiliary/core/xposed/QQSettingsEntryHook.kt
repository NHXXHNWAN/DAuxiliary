package com.dauxiliary.core.xposed

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap

/**
 * Injects the module entry into QQ's native settings model and keeps a conservative
 * view fallback for QQ builds that no longer expose that model to the hook.
 */
internal object QQSettingsEntryHook {
    private const val TAG = "DAuxiliary"
    private const val TITLE = "DAuxiliary"
    private const val FALLBACK_TAG = "dauxiliary.qq.settings.fallback"
    private const val FALLBACK_RETRY_DELAY_MS = 180L

    private val PROVIDER_NAMES = listOf(
        "com.tencent.mobileqq.setting.main.MainSettingConfigProvider",
        "com.tencent.mobileqq.setting.main.NewSettingConfigProvider",
        "com.tencent.mobileqq.setting.main.b",
    )
    private val PROCESSOR_NAMES = listOf(
        "com.tencent.mobileqq.setting.processor.i",
        "com.tencent.mobileqq.setting.processor.j",
        "com.tencent.mobileqq.setting.processor.h",
        "com.tencent.mobileqq.setting.processor.g",
        "as3.i",
    )
    private val FRAGMENT_NAMES = listOf(
        "com.tencent.mobileqq.setting.main.MainSettingFragment",
        "com.tencent.mobileqq.fragment.QQSettingSettingFragment",
    )
    private val ACTIVITY_NAMES = listOf(
        "com.tencent.mobileqq.activity.QQSettingSettingActivity",
    )

    private val installedLoaders = Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    private val installedMethods = Collections.newSetFromMap(WeakHashMap<Method, Boolean>())
    private val injectedLists = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val pendingFallbacks = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())

    fun resetForHotReload() {
        synchronized(installedLoaders) { installedLoaders.clear() }
        synchronized(installedMethods) { installedMethods.clear() }
        synchronized(injectedLists) { injectedLists.clear() }
        synchronized(pendingFallbacks) { pendingFallbacks.clear() }
    }

    fun install(xposed: XposedInterface, classLoader: ClassLoader) {
        synchronized(installedLoaders) {
            if (!installedLoaders.add(classLoader)) return
        }
        runCatching {
            QQDexKitResolver.warmUp(classLoader)
            val providerHooks = hookProviders(xposed, classLoader)
            val fallbackHooks = hookViewFallbacks(xposed, classLoader)
            val hooks = providerHooks + fallbackHooks
            if (hooks == 0) {
                synchronized(installedLoaders) { installedLoaders.remove(classLoader) }
                Log.w(TAG, "No compatible QQ settings provider or view fallback found")
            } else {
                Log.i(TAG, "QQ settings entry hooks registered: providers=$providerHooks, fallbacks=$fallbackHooks")
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
                            val context = chain.getArg(0) as? Context
                            if (context != null) injectProviderGroups(result, context, classLoader)
                            result
                        }
                    count++
                    Log.i(TAG, "QQ settings provider hooked: ${provider.name}.${method.name}")
                }
        }
        return count
    }

    /** Hook only QQ's known settings classes; no Android framework method is modified. */
    private fun hookViewFallbacks(xposed: XposedInterface, classLoader: ClassLoader): Int {
        var count = 0
        FRAGMENT_NAMES.forEach { name ->
            val fragment = runCatching { classLoader.loadClass(name) }.getOrNull() ?: return@forEach
            fragment.declaredMethods.filter(::isFragmentLifecycleMethod).forEach { method ->
                if (!markMethod(method)) return@forEach
                method.isAccessible = true
                xposed.hook(method)
                    .setId("qq.settings.fallback.${fragment.simpleName}.${method.name}")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val result = chain.proceed()
                        val source = if (method.name == "onViewCreated") chain.getArg(0) as? View else result as? View
                        scheduleFallback(chain.getThisObject(), source, classLoader)
                        result
                    }
                count++
                Log.i(TAG, "QQ settings view fallback hooked: ${fragment.name}.${method.name}")
            }
        }
        ACTIVITY_NAMES.forEach { name ->
            val activity = runCatching { classLoader.loadClass(name) }.getOrNull() ?: return@forEach
            activity.declaredMethods.filter(::isActivityLifecycleMethod).forEach { method ->
                if (!markMethod(method)) return@forEach
                method.isAccessible = true
                xposed.hook(method)
                    .setId("qq.settings.fallback.${activity.simpleName}.${method.name}")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val result = chain.proceed()
                        scheduleFallback(chain.getThisObject(), null, classLoader)
                        result
                    }
                count++
                Log.i(TAG, "QQ settings activity fallback hooked: ${activity.name}.${method.name}")
            }
        }
        return count
    }

    private fun isFragmentLifecycleMethod(method: Method): Boolean {
        val p = method.parameterTypes
        return when (method.name) {
            "onViewCreated" -> method.returnType == Void.TYPE && p.size == 2 && View::class.java.isAssignableFrom(p[0]) && p[1] == Bundle::class.java
            "doOnCreateView", "onCreateView" -> View::class.java.isAssignableFrom(method.returnType) && p.size == 3 && LayoutInflater::class.java.isAssignableFrom(p[0]) && ViewGroup::class.java.isAssignableFrom(p[1]) && p[2] == Bundle::class.java
            "doOnCreate" -> method.returnType == Void.TYPE && p.size == 1 && p[0] == Bundle::class.java
            else -> false
        }
    }

    private fun isActivityLifecycleMethod(method: Method): Boolean {
        val p = method.parameterTypes
        return (method.name == "doOnCreate" || method.name == "onCreate") && method.returnType == Void.TYPE && p.contentEquals(arrayOf(Bundle::class.java))
    }

    private fun scheduleFallback(thisObject: Any?, source: View?, classLoader: ClassLoader) {
        val key = thisObject ?: source ?: return
        synchronized(pendingFallbacks) { if (!pendingFallbacks.add(key)) return }
        val handler = Handler(Looper.getMainLooper())
        fun attempt(remaining: Int) {
            handler.post {
                val activity = resolveActivity(thisObject, source)
                val root = resolveRoot(thisObject, source, activity)
                val complete = activity != null && root != null &&
                    (hasNativeEntry(activity) || injectFallbackEntry(root, activity, classLoader))
                if (!complete && remaining > 0) {
                    handler.postDelayed({ attempt(remaining - 1) }, FALLBACK_RETRY_DELAY_MS)
                } else {
                    synchronized(pendingFallbacks) { pendingFallbacks.remove(key) }
                }
            }
        }
        handler.postDelayed({ attempt(3) }, FALLBACK_RETRY_DELAY_MS)
    }

    private fun hasNativeEntry(activity: Activity): Boolean {
        val decor = activity.window?.decorView as? ViewGroup ?: return false
        return listOfNotNull(decor.findViewById<View>(android.R.id.content), decor).any { containsNativeEntry(it, 0) }
    }

    private fun containsNativeEntry(view: View, depth: Int): Boolean {
        if (depth > 10) return false
        if (view is TextView && view.text?.toString() == TITLE) return true
        val group = view as? ViewGroup ?: return false
        for (index in 0 until group.childCount) if (containsNativeEntry(group.getChildAt(index), depth + 1)) return true
        return false
    }

    private fun injectFallbackEntry(root: View, activity: Activity, classLoader: ClassLoader): Boolean {
        val rootGroup = root as? ViewGroup ?: return false
        if (rootGroup.findViewWithTag<View>(FALLBACK_TAG) != null) return true
        val activityRoot = activity.window?.decorView as? ViewGroup
        if (activityRoot?.findViewWithTag<View>(FALLBACK_TAG) != null) return true
        val container = findFallbackContainer(root) ?: return false
        if (container.findViewWithTag<View>(FALLBACK_TAG) != null) return true

        val context = container.context
        val entry = TextView(context).apply {
            tag = FALLBACK_TAG
            text = TITLE
            gravity = Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(resolveThemeColor(context, android.R.attr.textColorPrimary, Color.DKGRAY))
            setPadding(dp(context, 20), 0, dp(context, 20), 0)
            minHeight = dp(context, 52)
            isClickable = true
            isFocusable = true
            contentDescription = TITLE
            background = resolveSelectableBackground(context)
            setOnClickListener { QQInProcessSettings.open(activity, classLoader) }
        }

        return runCatching {
            if (container is LinearLayout) {
                val index = sourceIndex(root, container)
                val layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                if (index in 0..container.childCount) container.addView(entry, index, layoutParams) else container.addView(entry, layoutParams)
            } else if (container is FrameLayout) {
                val layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
                layoutParams.setMargins(dp(context, 8), 0, dp(context, 8), dp(context, 8))
                container.addView(entry, layoutParams)
            } else {
                container.addView(entry, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            Log.i(TAG, "QQ settings view fallback entry injected into ${container.javaClass.name}")
            true
        }.getOrElse { error ->
            Log.w(TAG, "QQ settings view fallback insertion failed", error)
            false
        }
    }

    private fun findFallbackContainer(root: View): ViewGroup? {
        findVerticalLinearLayout(root, 0)?.let { return it }
        val directParent = root.parent as? ViewGroup
        if (directParent != null && isUsableContainer(directParent)) return directParent
        val rootGroup = root as? ViewGroup
        if (rootGroup != null && isUsableContainer(rootGroup)) return rootGroup
        return null
    }

    private fun findVerticalLinearLayout(view: View, depth: Int): LinearLayout? {
        if (depth > 8) return null
        if (view is LinearLayout && view.orientation == LinearLayout.VERTICAL) return view
        val group = view as? ViewGroup ?: return null
        for (index in 0 until group.childCount) findVerticalLinearLayout(group.getChildAt(index), depth + 1)?.let { return it }
        return null
    }

    private fun isUsableContainer(group: ViewGroup): Boolean {
        val name = group.javaClass.name
        return !name.contains("RecyclerView", ignoreCase = true) && !name.contains("ListView", ignoreCase = true) && !name.contains("ScrollView", ignoreCase = true) && !name.contains("AdapterView", ignoreCase = true)
    }

    private fun sourceIndex(source: View, container: LinearLayout): Int {
        if (source.parent === container) return (container.indexOfChild(source) + 1).coerceAtMost(container.childCount)
        return -1
    }

    private fun resolveActivity(thisObject: Any?, source: View?): Activity? {
        if (thisObject is Activity) return thisObject
        source?.context?.let { findActivity(it)?.let { activity -> return activity } }
        invokeNoArg(thisObject, "getActivity")?.let { activity -> if (activity is Activity) return activity }
        HostActivityTracker.currentActivity()?.let { return it }
        return null
    }

    private fun resolveRoot(thisObject: Any?, source: View?, activity: Activity?): View? {
        source?.let { return it }
        invokeNoArg(thisObject, "getView")?.let { view -> if (view is View) return view }
        activity?.findViewById<View>(android.R.id.content)?.let { return it }
        return activity?.window?.decorView
    }

    private fun invokeNoArg(instance: Any?, name: String): Any? {
        if (instance == null) return null
        var type: Class<*>? = instance.javaClass
        while (type != null && type != Any::class.java) {
            val method = type.declaredMethods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
            if (method != null) return runCatching { method.isAccessible = true; method.invoke(instance) }.getOrNull()
            type = type.superclass
        }
        return null
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is android.content.ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    private fun resolveThemeColor(context: Context, attribute: Int, fallback: Int): Int {
        val array = context.obtainStyledAttributes(intArrayOf(attribute))
        return try { array.getColor(0, fallback) } finally { array.recycle() }
    }

    private fun resolveSelectableBackground(context: Context) = runCatching {
        val array = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        try { array.getDrawable(0) } finally { array.recycle() }
    }.getOrNull()

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun injectProviderGroups(result: Any?, context: Context, classLoader: ClassLoader) {
        HostActivityTracker.register(context)
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
            ?: PROCESSOR_NAMES.asSequence().mapNotNull { runCatching { classLoader.loadClass(it) }.getOrNull() }.firstOrNull(QQDexKitResolver::isSimpleProcessor)
        if (processorClass == null) {
            removeInjectedMarker(groups)
            Log.w(TAG, "QQ setting-item processor not resolved")
            return
        }
        val target = candidates.firstOrNull { (group, _) -> readCharSequenceFields(group).any { it == "功能" || it == "设置" || it == "辅助" } }
            ?: candidates.firstOrNull { (_, items) -> items.any { it?.javaClass == processorClass } }
            ?: candidates.getOrNull(2) ?: candidates.first()
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
        val unit = runCatching { (callbackType.classLoader ?: classLoader).loadClass("kotlin.Unit").getField("INSTANCE").get(null) }.getOrNull()
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
        return (p.size == 4 || p.size == 5 || p.size == 6) && Context::class.java.isAssignableFrom(p[0]) && p[1] == Int::class.javaPrimitiveType && CharSequence::class.java.isAssignableFrom(p[2]) && p[3] == Int::class.javaPrimitiveType
    }

    private fun instantiateProcessor(constructor: Constructor<*>, context: Context): Any = when (constructor.parameterTypes.size) {
        4 -> constructor.newInstance(context, 0, TITLE, 0)
        5 -> constructor.newInstance(context, 0, TITLE, 0, null)
        else -> constructor.newInstance(context, 0, TITLE, 0, null, null)
    }

    private fun findNestedMutableList(value: Any): MutableList<Any?>? {
        var type: Class<*>? = value.javaClass
        while (type != null && type != Any::class.java) {
            type.declaredFields.forEach { field ->
                runCatching { field.isAccessible = true; field.get(value) }.getOrNull()?.let { nested ->
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
                runCatching { field.isAccessible = true; (field.get(value) as? CharSequence)?.toString() }.getOrNull()?.let(values::add)
            }
            type = type.superclass
        }
        return values
    }

    private fun markMethod(method: Method): Boolean = synchronized(installedMethods) { installedMethods.add(method) }
    private fun removeInjectedMarker(groups: Any) { synchronized(injectedLists) { injectedLists.remove(groups) } }
}
