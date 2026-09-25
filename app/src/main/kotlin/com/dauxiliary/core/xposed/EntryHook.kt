package com.dauxiliary.core.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.core.feature.FeatureRegistry
import com.dauxiliary.core.registry.AppTarget

/**
 * Xposed entry point. Registered in assets/xposed_init.
 *
 * Architecture notes:
 * - Keep this class ultra-thin: resolve config, then dispatch to feature hooks.
 * - All feature hooks live in com.dauxiliary.core.xposed.hooks.* and are
 *   registered through a FeatureRegistry so they can be toggled at runtime.
 */
class EntryHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val target = AppTarget.fromPackageName(lpparam.packageName) ?: return

        log("Loaded into ${target.displayName} (${lpparam.packageName}, pid=${android.os.Process.myPid()})")

        val hostContext = runCatching {
            val activityThread = XposedHelpers.findClass("android.app.ActivityThread", null)
            XposedHelpers.callStaticMethod(activityThread, "currentApplication") as? android.content.Context
        }.getOrNull()
        hostContext?.let { ConfigStore.recordHostLoaded(it, target) }
        if (!ConfigStore.isApplicationEnabledInHookedProcess(target.packageName)) return

        FeatureRegistry.dispatch(hostContext, lpparam, target)
    }
    companion object {
        fun log(msg: String, throwable: Throwable? = null) {
            XposedBridge.log("[DAuxiliary] $msg")
            throwable?.let { XposedBridge.log(it) }
        }
    }
}
