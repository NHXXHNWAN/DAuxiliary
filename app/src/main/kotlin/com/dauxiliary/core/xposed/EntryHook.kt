package com.dauxiliary.core.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.dauxiliary.core.config.ConfigStore

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
        // Only hook the Douyin package
        if (lpparam.packageName != DOUYIN_PACKAGE) return

        log("Loaded into ${lpparam.packageName} (pid=${android.os.Process.myPid()})")

        // 入口必须始终安装，否则模块关闭后用户无法在抖音内重新打开它。
        // 具体功能 Hook 再根据 ConfigStore.KEY_MASTER_SWITCH 判断是否执行。
        DouyinEntryHook.install()

        if (!ConfigStore.readFromHookedProcess(ConfigStore.KEY_MASTER_SWITCH, false)) {
            log("Module switch is disabled; feature hooks are skipped")
            return
        }

        // TODO: 在这里注册实际的抖音功能 Hook；配置入口与 Hook 分发必须保持独立。
        // FeatureRegistry.dispatch(lpparam)
    }

    companion object {
        private const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"

        fun log(msg: String, throwable: Throwable? = null) {
            XposedBridge.log("[DAuxiliary] $msg")
            throwable?.let { XposedBridge.log(it) }
        }
    }
}
