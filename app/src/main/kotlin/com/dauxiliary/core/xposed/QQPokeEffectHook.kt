package com.dauxiliary.core.xposed

import android.util.Log
import com.dauxiliary.core.registry.AppTarget
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

/**
 * Clean-room implementation inspired by QAuxiliary's DisablePokeEffect.
 * It only targets known QQ builder candidates and changes a boolean decision
 * to false; all unresolved classes and unexpected calls retain QQ behavior.
 */
internal object QQPokeEffectHook {
    const val FEATURE_ID = "qq.disable_poke_effect"
    private const val TAG = "DAuxiliary"
    private val installedLoaders = Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    private val candidateNames = listOf(
        "com.tencent.mobileqq.troopgift.GivingHeartItemBuilder",
        "com.tencent.mobileqq.activity.aio.item.GivingHeartItemBuilder",
        "com.tencent.mobileqq.aio.item.GivingHeartItemBuilder",
    )

    fun resetForHotReload() {
        synchronized(installedLoaders) { installedLoaders.clear() }
    }

    fun install(xposed: XposedInterface, classLoader: ClassLoader) {
        synchronized(installedLoaders) {
            if (!installedLoaders.add(classLoader)) return
        }
        var installed = false
        runCatching {
            candidateNames.forEach { name ->
                val type = runCatching { classLoader.loadClass(name) }.getOrNull() ?: return@forEach
                type.declaredMethods.filter(::isPokeDecision).forEach { method ->
                    method.isAccessible = true
                    xposed.hook(method)
                        .setId("qq.poke.disable.${type.name}.${method.name}")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept { chain -> false }
                    installed = true
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "QQ poke-effect hook installation failed; original behavior retained", error)
        }
        if (installed) Log.i(TAG, "QQ poke-effect hook installed")
        else Log.i(TAG, "QQ poke-effect hook skipped: compatible builder not found")
    }

    private fun isPokeDecision(method: Method): Boolean {
        val params = method.parameterTypes
        return method.name == "a" &&
            method.returnType == Boolean::class.javaPrimitiveType &&
            params.size == 3 &&
            !Modifier.isStatic(method.modifiers)
    }
}
