package com.dauxiliary.core.xposed

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap

/** Injects DAuxiliary into Telegram's native settings list, following TMoe's dual-version strategy. */
internal object TelegramSettingsEntryHook {
    private const val TAG = "DAuxiliary-TelegramEntry"
    private const val TITLE = "DAuxiliary 设置"
    private const val ENTRY_TAG = "dauxiliary.telegram.settings.entry"
    private const val OVERLAY_TAG = "dauxiliary.telegram.settings.overlay"
    private const val RETRY_DELAY_MS = 220L
    private const val RETRIES = 20
    private const val SETTINGS_ENTRY_ID = 0x7f0e4da1
    private const val SETTINGS_LANGUAGE_ITEM_ID = 10
    private const val SETTINGS_FACTORY_CLASS = "org.telegram.ui.SettingsActivity\$SettingCell\$Factory"
    private const val PROFILE_ADAPTER_CLASS = "org.telegram.ui.ProfileActivity\$ListAdapter"

    private val modernSettingsClasses = listOf("org.telegram.ui.SettingsActivity")
    private val legacyProfileClasses = listOf("org.telegram.ui.ProfileActivity")
    private val installedLoaders = Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    private val installedMethods = Collections.newSetFromMap(WeakHashMap<Method, Boolean>())
    private val pendingObjects = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val injectedLists = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val itemFactories = Collections.synchronizedMap(WeakHashMap<ClassLoader, Method>())
    private val itemIdReaders = Collections.synchronizedMap(WeakHashMap<ClassLoader, MemberReader>())
    private val clickedSettingsActivities = Collections.newSetFromMap(
        WeakHashMap<Activity, Boolean>(),
    )
    private val rememberedSettingsActivities = Collections.newSetFromMap(
        WeakHashMap<Activity, Boolean>(),
    )

    private sealed interface MemberReader {
        data class MethodReader(val method: Method) : MemberReader
        data class FieldReader(val field: Field) : MemberReader
    }

    fun install(xposed: XposedInterface, classLoader: ClassLoader) {
        synchronized(installedLoaders) { if (!installedLoaders.add(classLoader)) return }
        var count = 0
        modernSettingsClasses.forEach { name ->
            val type = runCatching { classLoader.loadClass(name) }.getOrNull() ?: return@forEach
            // Use the host's native SettingCell so the row is placed together with
            // NagramXF's own settings items. Do not add a second overlay/fallback row.
            count += installModernSettingsPath(xposed, classLoader, type)
        }
        // Do not install the legacy ProfileActivity fallback when the modern
        // SettingsActivity path is active; both can be present in NagramXF and
        // would create a second row/overlay at a conflicting position.
        if (count == 0) {
            legacyProfileClasses.forEach { name ->
                val type = runCatching { classLoader.loadClass(name) }.getOrNull() ?: return@forEach
                count += installLegacyProfilePath(xposed, classLoader, type)
            }
        }
        if (count == 0) synchronized(installedLoaders) { installedLoaders.remove(classLoader) }
        android.util.Log.i(TAG, "entry hooks registered=$count")
    }

    fun resetForHotReload() {
        synchronized(installedLoaders) { installedLoaders.clear() }
        synchronized(installedMethods) { installedMethods.clear() }
        synchronized(pendingObjects) { pendingObjects.clear() }
        synchronized(injectedLists) { injectedLists.clear() }
        itemFactories.clear()
        itemIdReaders.clear()
    }

    /** Telegram 12.4+: inject a native SettingCell into fillItems() and route onClick(). */
    private fun installModernSettingsPath(xposed: XposedInterface, loader: ClassLoader, activity: Class<*>): Int {
        val factory = runCatching { loader.loadClass(SETTINGS_FACTORY_CLASS) }.getOrNull() ?: return 0
        val factoryMethod = factory.declaredMethods.firstOrNull { method ->
            method.name == "of" && method.parameterTypes.size == 7 &&
                method.parameterTypes.take(4).all { it == Int::class.javaPrimitiveType } &&
                method.parameterTypes.drop(4).all { CharSequence::class.java.isAssignableFrom(it) }
        } ?: return 0
        factoryMethod.isAccessible = true
        itemFactories[loader] = factoryMethod
        val idReader = findItemIdReader(factoryMethod.returnType) ?: return 0
        itemIdReaders[loader] = idReader

        val methods = activity.declaredMethods
        installActivityTrackerHooks(xposed, activity)
        val fillItems = methods.firstOrNull { method ->
            method.name == "fillItems" && method.parameterTypes.size == 2 &&
                java.util.ArrayList::class.java.isAssignableFrom(method.parameterTypes[0])
        } ?: return 0
        val onClick = methods.firstOrNull { method -> method.name == "onClick" && method.parameterTypes.size == 5 } ?: return 0
        var count = 0
        if (markMethod(fillItems)) {
            hook(xposed, fillItems, "telegram.settings.fillItems") { chain ->
                val result = chain.proceed()
                val items = chain.getArg(0) as? MutableList<*>
                if (items != null) injectSettingsItem(items, factoryMethod, idReader)
                result
            }
            count++
        }
        if (markMethod(onClick)) {
            hook(xposed, onClick, "telegram.settings.onClick") { chain ->
                if (readItemId(chain.getArg(0), idReader) == SETTINGS_ENTRY_ID) {
                    val activityInstance = resolveClickActivity(chain)
                    android.util.Log.i(TAG, "DAuxiliary settings row clicked; activity=${activityInstance?.javaClass?.name}")
                    if (activityInstance != null) {
                        HostActivityTracker.remember(activityInstance)
                        TelegramInProcessSettings.open(activityInstance, loader)
                    } else {
                        android.util.Log.w(TAG, "Unable to resolve Telegram Activity from settings click")
                    }
                    null
                } else chain.proceed()
            }
            count++
        }
        return count
    }

    private fun installActivityTrackerHooks(xposed: XposedInterface, activity: Class<*>) {
        activity.declaredMethods.filter { method ->
            method.name in setOf("onCreate", "onStart", "onResume") &&
                method.parameterTypes.size <= 1
        }.forEach { method ->
            if (!markMethod(method)) return@forEach
            hook(xposed, method, "telegram.settings.activity.${method.name}") { chain ->
                val result = chain.proceed()
                (chain.getThisObject() as? Activity)?.let {
                    HostActivityTracker.remember(it)
                    synchronized(rememberedSettingsActivities) { rememberedSettingsActivities.add(it) }
                }
                result
            }
        }
    }

    private fun resolveClickActivity(chain: XposedInterface.Chain): Activity? {
        val thisObject = chain.getThisObject()
        fun fromContext(context: Context?): Activity? {
            var current: Context? = context
            while (current is android.content.ContextWrapper) {
                if (current is Activity) return current
                current = current.baseContext
            }
            return current as? Activity
        }
        fun findActivity(value: Any?, depth: Int = 0): Activity? {
            if (value == null || depth > 2) return null
            if (value is Activity) return value
            if (value is Context) {
                fromContext(value)?.let { return it }
                HostActivityTracker.register(value)
            }
            listOf("getActivity", "getContext", "getParentActivity").forEach { name ->
                runCatching {
                    val method = value.javaClass.methods.firstOrNull {
                        it.name == name && it.parameterTypes.isEmpty()
                    }
                    method?.let { findActivity(it.invoke(value), depth + 1) }?.let { return it }
                }
            }
            var type: Class<*>? = value.javaClass
            while (type != null) {
                type.declaredFields.forEach { field ->
                    if (Activity::class.java.isAssignableFrom(field.type) ||
                        Context::class.java.isAssignableFrom(field.type)
                    ) {
                        runCatching {
                            field.isAccessible = true
                            findActivity(field.get(value), depth + 1)?.let { return it }
                        }
                    }
                }
                type = type.superclass
            }
            return null
        }
        return findActivity(thisObject)
            ?: findActivity(chain.getArg(0))
            ?: HostActivityTracker.currentActivity()
            ?: HostActivityTracker.findLiveActivity()
            ?: synchronized(rememberedSettingsActivities) {
                rememberedSettingsActivities.lastOrNull { !it.isFinishing && !it.isDestroyed }
            }
    }

    private fun injectSettingsItem(items: MutableList<*>, factory: Method, idReader: MemberReader) {
        @Suppress("UNCHECKED_CAST")
        val mutable = items as? MutableList<Any?> ?: return
        synchronized(injectedLists) { if (!injectedLists.add(mutable)) return }
        // fillItems() is called repeatedly by NagramXF while rows are rebuilt. Do
        // not append another copy when the fork returns a fresh list whose reader
        // cannot expose the synthetic id reliably.
        if (mutable.any { readItemId(it, idReader) == SETTINGS_ENTRY_ID ||
                it?.toString()?.contains(TITLE, ignoreCase = true) == true }) return
        val index = mutable.indexOfFirst { readItemId(it, idReader) == SETTINGS_LANGUAGE_ITEM_ID }
        if (index < 0) {
            synchronized(injectedLists) { injectedLists.remove(mutable) }
            android.util.Log.w(TAG, "Telegram language setting row not found; native insertion skipped")
            return
        }
        val item = runCatching {
            factory.invoke(
                null,
                SETTINGS_ENTRY_ID,
                0,
                0xff486bd0.toInt(),
                0,
                TITLE,
                null,
                null,
            )
        }.getOrElse {
            synchronized(injectedLists) { injectedLists.remove(mutable) }
            android.util.Log.w(TAG, "Unable to create Telegram SettingCell", it)
            return
        }
        runCatching { mutable.add(index, item) }
            .onSuccess { android.util.Log.i(TAG, "native Telegram settings item inserted") }
            .onFailure {
                synchronized(injectedLists) { injectedLists.remove(mutable) }
                android.util.Log.w(TAG, "Unable to insert Telegram SettingCell", it)
            }
    }

    private fun installViewFallbackPath(
        xposed: XposedInterface,
        loader: ClassLoader,
        activityClass: Class<*>,
        hookPrefix: String,
    ): Int {
        var count = 0
        activityClass.declaredMethods.filter(::isViewCreationMethod).forEach { method ->
            if (!markMethod(method)) return@forEach
            hook(xposed, method, "$hookPrefix.view.${method.name}") { chain ->
                val result = chain.proceed()
                scheduleInject(chain.getThisObject(), result as? View, loader)
                result
            }
            count++
        }
        activityClass.declaredMethods.filter(::isResumeMethod).forEach { method ->
            if (!markMethod(method)) return@forEach
            hook(xposed, method, "$hookPrefix.resume") { chain ->
                val result = chain.proceed()
                scheduleInject(chain.getThisObject(), null, loader)
                result
            }
            count++
        }
        return count
    }

    /** Older clients use ProfileActivity rows; keep a conservative in-page fallback there. */
    private fun installLegacyProfilePath(xposed: XposedInterface, loader: ClassLoader, profile: Class<*>): Int {
        val adapter = runCatching { loader.loadClass(PROFILE_ADAPTER_CLASS) }.getOrNull() ?: profile
        var count = 0
        adapter.declaredMethods.filter { method ->
            method.returnType == Int::class.javaPrimitiveType && method.parameterTypes.any { it == Int::class.javaPrimitiveType }
        }.forEach { method ->
            if (!markMethod(method)) return@forEach
            hook(xposed, method, "telegram.profile.row.${method.name}") { chain ->
                val result = chain.proceed()
                if (result is Int && result == 6) SETTINGS_ENTRY_ID else result
            }
            count++
        }
        profile.declaredMethods.filter(::isViewCreationMethod).forEach { method ->
            if (!markMethod(method)) return@forEach
            hook(xposed, method, "telegram.profile.view.${method.name}") { chain ->
                val result = chain.proceed()
                scheduleInject(chain.getThisObject(), result as? View, loader)
                result
            }
            count++
        }
        profile.declaredMethods.filter(::isResumeMethod).forEach { method ->
            if (!markMethod(method)) return@forEach
            hook(xposed, method, "telegram.profile.resume") { chain ->
                val result = chain.proceed()
                scheduleInject(chain.getThisObject(), null, loader)
                result
            }
            count++
        }
        return count
    }

    private fun findItemIdReader(itemClass: Class<*>): MemberReader? {
        val method = (itemClass.declaredMethods.asList() + itemClass.methods.asList()).distinct()
            .firstOrNull { candidate ->
                candidate.parameterTypes.isEmpty() && (candidate.name == "getId" || candidate.name == "id") &&
                    (candidate.returnType == Int::class.javaPrimitiveType || Number::class.java.isAssignableFrom(candidate.returnType))
            }
        if (method != null) return MemberReader.MethodReader(method)
        val field = generateSequence(itemClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull { candidate ->
                candidate.name == "id" &&
                    (candidate.type == Int::class.javaPrimitiveType || Number::class.java.isAssignableFrom(candidate.type))
            } ?: return null
        return MemberReader.FieldReader(field)
    }

    private fun readItemId(item: Any?, reader: MemberReader): Int {
        if (item == null) return Int.MIN_VALUE
        return runCatching {
            when (reader) {
                is MemberReader.MethodReader -> {
                    reader.method.isAccessible = true
                    (reader.method.invoke(item) as? Number)?.toInt()
                }
                is MemberReader.FieldReader -> {
                    reader.field.isAccessible = true
                    (reader.field.get(item) as? Number)?.toInt()
                }
            } ?: Int.MIN_VALUE
        }.getOrDefault(Int.MIN_VALUE)
    }

    private fun isViewCreationMethod(method: Method): Boolean =
        View::class.java.isAssignableFrom(method.returnType) &&
            method.name in setOf("createView", "onCreateView", "doOnCreateView", "createContentView", "buildLayout") &&
            method.parameterTypes.size <= 3 && method.parameterTypes.all {
                Context::class.java.isAssignableFrom(it) || Bundle::class.java.isAssignableFrom(it) ||
                    ViewGroup::class.java.isAssignableFrom(it)
            }

    private fun isResumeMethod(method: Method): Boolean =
        method.name in setOf("onResume", "onStart", "onPostResume") &&
            method.parameterTypes.isEmpty() && method.returnType == Void.TYPE

    private fun hook(xposed: XposedInterface, method: Method, id: String, callback: (XposedInterface.Chain) -> Any?) {
        runCatching {
            method.isAccessible = true
            xposed.hook(method).setId(id)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(callback)
        }.onFailure {
            synchronized(installedMethods) { installedMethods.remove(method) }
            android.util.Log.w(TAG, "Hook skipped: ${method.toGenericString()}", it)
        }
    }

    private fun scheduleInject(instance: Any?, source: View?, loader: ClassLoader) {
        val key = instance ?: source ?: return
        synchronized(pendingObjects) { if (!pendingObjects.add(key)) return }
        val handler = Handler(Looper.getMainLooper())
        fun attempt(remaining: Int) {
            handler.post {
                val activity = when {
                    instance is Activity -> instance
                    source != null -> findActivity(source.context)
                    else -> HostActivityTracker.currentActivity()
                }
                val root = source ?: activity?.findViewById(android.R.id.content)
                val done = activity != null && root != null && injectViewFallback(root, activity, loader)
                if (!done && remaining > 0) handler.postDelayed({ attempt(remaining - 1) }, RETRY_DELAY_MS)
                else synchronized(pendingObjects) { pendingObjects.remove(key) }
            }
        }
        handler.postDelayed({ attempt(RETRIES) }, RETRY_DELAY_MS)
    }

    private fun injectViewFallback(root: View, activity: Activity, loader: ClassLoader): Boolean {
        val decor = activity.window?.decorView as? ViewGroup ?: return false
        if (decor.findViewWithTag<View>(ENTRY_TAG) != null || decor.findViewWithTag<View>(OVERLAY_TAG) != null) return true
        val entry = LinearLayout(activity).apply {
            tag = ENTRY_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 20), 0, dp(activity, 20), 0)
            addView(ImageView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)).apply {
                    marginEnd = dp(activity, 16)
                }
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                imageTintList = null
                setImageDrawable(runCatching {
                    activity.createPackageContext("com.dauxiliary", Context.CONTEXT_IGNORE_SECURITY)
                        .packageManager.getApplicationIcon("com.dauxiliary")
                }.getOrNull())
            })
            addView(TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = TITLE
                gravity = Gravity.CENTER_VERTICAL
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(resolveColor(activity, android.R.attr.textColorPrimary, Color.DKGRAY))
            })
            minimumHeight = dp(activity, 52)
            isClickable = true
            isFocusable = true
            contentDescription = TITLE
            background = selectableBackground(activity)
            setOnClickListener { TelegramInProcessSettings.open(activity, loader) }
        }
        val container = findSettingsContainer(root)
        if (container != null) return runCatching {
            container.addView(entry, ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 56)); true
        }.getOrDefault(false)
        return runCatching {
            val overlay = FrameLayout(activity).apply {
                tag = OVERLAY_TAG
                addView(entry, FrameLayout.LayoutParams.MATCH_PARENT, dp(activity, 56))
            }
            decor.addView(overlay, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(activity, 56), Gravity.BOTTOM))
            true
        }.getOrDefault(false)
    }

    private fun findSettingsContainer(root: View): ViewGroup? {
        if (root is LinearLayout && root.orientation == LinearLayout.VERTICAL && root.childCount > 0) return root
        val group = root as? ViewGroup ?: return null
        for (i in 0 until group.childCount) findSettingsContainer(group.getChildAt(i))?.let { return it }
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

    private fun resolveColor(context: Context, attr: Int, fallback: Int): Int =
        context.obtainStyledAttributes(intArrayOf(attr)).use { it.getColor(0, fallback) }

    private fun selectableBackground(context: Context) = runCatching {
        context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).use { it.getDrawable(0) }
    }.getOrElse { ColorDrawable(Color.TRANSPARENT) }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun markMethod(method: Method): Boolean = synchronized(installedMethods) { installedMethods.add(method) }
}