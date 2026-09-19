package com.dauxiliary.core.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.dauxiliary.core.config.ConfigStore
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

        // Host integrations are selected through the central registry. The shared
        // in-app panel is intentionally small until host-specific features land.
        DouyinEntryHook.install(target)

        if (!ConfigStore.readFromHookedProcess(ConfigStore.KEY_MASTER_SWITCH, false)) {
            log("Module switch is disabled; feature hooks are skipped")
            return
        }
        if (!ConfigStore.isApplicationEnabledInHookedProcess(target.packageName)) {
            log("Host ${target.displayName} is not enabled; feature hooks are skipped")
            return
        }

        // TODO: 在这里注册实际的抖音功能 Hook；配置入口与 Hook 分发必须保持独立。
        // FeatureRegistry.dispatch(lpparam)
    }
    companion object {
        fun log(msg: String, throwable: Throwable? = null) {
            XposedBridge.log("[DAuxiliary] $msg")
            throwable?.let { XposedBridge.log(it) }
        }
    }
}
