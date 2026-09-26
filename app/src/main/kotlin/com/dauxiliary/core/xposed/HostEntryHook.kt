package com.dauxiliary.core.xposed

import com.dauxiliary.core.registry.AppTarget
import io.github.libxposed.api.XposedInterface

/** Dispatches only host-native entry hooks. No Activity or cross-app navigation is used. */
object HostEntryHook {
    fun install(xposed: XposedInterface, target: AppTarget, classLoader: ClassLoader) {
        if (target == AppTarget.QQ) {
            QQSettingsEntryHook.install(xposed, classLoader)
        }
    }
}