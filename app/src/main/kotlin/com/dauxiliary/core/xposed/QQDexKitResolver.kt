package com.dauxiliary.core.xposed

import android.content.Context
import android.util.Log
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * Resolves QQ's obfuscated SimpleItemProcessor without blocking the settings UI.
 *
 * DexKit creation/query is deliberately performed on a daemon worker. The QQ hook
 * can still use a known candidate immediately while DexKit prepares a version-
 * independent result for later settings screens.
 */
internal object QQDexKitResolver {
    private const val TAG = "DAuxiliary"
    private const val FUNCTION0 = "kotlin.jvm.functions.Function0"

    private val started = Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    private val resolvedNames = WeakHashMap<ClassLoader, String>()

    fun warmUp(classLoader: ClassLoader) {
        synchronized(started) {
            if (!started.add(classLoader)) return
        }
        Thread({ resolve(classLoader) }, "DAuxiliary-DexKit").apply {
            isDaemon = true
            start()
        }
    }

    fun resolveOrNull(classLoader: ClassLoader): Class<*>? {
        synchronized(resolvedNames) {
            resolvedNames[classLoader]?.let { name ->
                return runCatching { classLoader.loadClass(name) }.getOrNull()
            }
        }
        // Do not run a native scan on the UI thread. The warm-up worker owns it.
        return null
    }

    private fun resolve(classLoader: ClassLoader) {
        runCatching {
            // DexKit ships its native library in the module APK; load it before creating the bridge.
            System.loadLibrary("dexkit")
            // Inspect dex elements visible to the host class loader.
            DexKitBridge.create(classLoader, true).use { bridge ->
                val result = bridge.findMethod(
                    FindMethod.create().matcher(
                        MethodMatcher.create().addEqString("SimpleItemProcessor"),
                    ),
                )
                val processor = result.asSequence()
                    .mapNotNull { data -> runCatching { data.getClassInstance(classLoader) }.getOrNull() }
                    .firstOrNull { type ->
                        type.name.startsWith("com.tencent.mobileqq.setting.") && isSimpleProcessor(type)
                    }
                if (processor != null) {
                    synchronized(resolvedNames) {
                        resolvedNames[classLoader] = processor.name
                    }
                    Log.i(TAG, "DexKit resolved QQ simple processor: ${processor.name}")
                } else {
                    Log.w(TAG, "DexKit found no compatible QQ SimpleItemProcessor")
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "DexKit QQ lookup unavailable; fallback candidates remain active", error)
        }
    }

    fun isSimpleProcessor(type: Class<*>): Boolean {
        val hasConstructor = type.declaredConstructors.any(::isSimpleProcessorConstructor)
        val hasCallback = type.declaredMethods.any(::isFunction0Setter)
        return hasConstructor && hasCallback
    }

    fun isSimpleProcessorConstructor(constructor: Constructor<*>): Boolean {
        val p = constructor.parameterTypes
        return p.size == 5 &&
            Context::class.java.isAssignableFrom(p[0]) &&
            p[1] == Int::class.javaPrimitiveType &&
            CharSequence::class.java.isAssignableFrom(p[2]) &&
            p[3] == Int::class.javaPrimitiveType &&
            p[4] == String::class.java
    }

    fun isFunction0Setter(method: Method): Boolean =
        method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0].name == FUNCTION0
}
