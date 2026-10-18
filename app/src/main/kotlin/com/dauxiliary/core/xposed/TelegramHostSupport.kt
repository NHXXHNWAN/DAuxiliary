package com.dauxiliary.core.xposed

import android.content.Context
import com.dauxiliary.core.registry.AppTarget

/** Telegram-Android lineage detection for package-ready callbacks. */
object TelegramHostSupport {
    data class Detection(
        val packageKnown: Boolean,
        val markerMatches: Int,
        val telegramAndroidLineage: Boolean,
    )

    private val markerClasses = listOf(
        "org.telegram.tgnet.ConnectionsManager",
        "org.telegram.ui.Components.ChatActivityEnterView",
        "org.telegram.messenger.UserConfig",
    )

    fun detect(packageName: String, classLoader: ClassLoader?): Detection {
        val known = AppTarget.TELEGRAM.matchesPackage(packageName)
        val matches = markerClasses.count { className ->
            classLoader != null && runCatching { Class.forName(className, false, classLoader) }.isSuccess
        }
        return Detection(known, matches, known || matches >= 2)
    }

    fun isSupported(packageName: String, classLoader: ClassLoader?): Boolean {
        if (AppTarget.TELEGRAM.matchesPackage(packageName)) return true
        return hasTelegramMarkers(classLoader)
    }

    fun hasTelegramMarkers(classLoader: ClassLoader?): Boolean {
        if (classLoader == null) return false
        val matches = markerClasses.count { className ->
            runCatching { Class.forName(className, false, classLoader) }.isSuccess
        }
        return matches >= 2
    }

    fun hasTelegramMarkers(context: Context, packageName: String): Boolean = runCatching {
        val clientContext = context.createPackageContext(packageName, Context.CONTEXT_INCLUDE_CODE)
        hasTelegramMarkers(clientContext.classLoader)
    }.getOrDefault(false)
}