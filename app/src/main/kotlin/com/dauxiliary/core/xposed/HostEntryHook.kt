package com.dauxiliary.core.xposed

import com.dauxiliary.core.registry.AppTarget
import io.github.libxposed.api.XposedInterface
import com.dauxiliary.core.telegram.TelegramAutoSignHook

/** Dispatches only host-native entry hooks. No Activity or cross-app navigation is used. */
object HostEntryHook {
    fun resetForHotReload() {
        QQSettingsEntryHook.resetForHotReload()
        TelegramAutoSignHook.resetForHotReload()
    }

    fun install(xposed: XposedInterface, target: AppTarget, classLoader: ClassLoader) {
        when (target) {
            AppTarget.QQ -> QQSettingsEntryHook.install(xposed, classLoader)
            // Telegram feature hooks are installed from FeatureRegistry only when enabled.
            AppTarget.TELEGRAM -> Unit
            else -> Unit
        }
    }
}