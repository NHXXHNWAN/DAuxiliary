package com.dauxiliary.core.xposed

import com.dauxiliary.core.registry.AppTarget

/** Telegram-Android lineage detection used to safely support renamed client forks. */
object TelegramHostSupport {
    private val markerClasses = listOf(
        "org.telegram.tgnet.ConnectionsManager",
        "org.telegram.ui.Components.ChatActivityEnterView",
        "org.telegram.messenger.UserConfig",
    )

    fun isSupported(packageName: String, classLoader: ClassLoader?): Boolean {
        if (AppTarget.TELEGRAM.matchesPackage(packageName)) return true
        if (classLoader == null) return false
        return markerClasses.all { name ->
            runCatching { Class.forName(name, false, classLoader) }.isSuccess
        }
    }

    fun supportedPackageNames(): Set<String> =
        setOf(AppTarget.TELEGRAM.packageName) + AppTarget.TELEGRAM.aliasesForListing()
}
