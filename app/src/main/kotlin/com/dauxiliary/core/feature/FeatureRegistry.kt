package com.dauxiliary.core.feature

import com.dauxiliary.core.registry.AppTarget
import com.dauxiliary.core.xposed.HostActivityTracker
import com.dauxiliary.core.xposed.HostEntryHook
import com.dauxiliary.core.xposed.QQDexKitResolver
import com.dauxiliary.core.xposed.QQPokeEffectHook
import com.dauxiliary.core.xposed.QQRecallHook
import com.dauxiliary.core.telegram.TelegramAutoSignHook
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface

/** Central feature catalogue and LibXposed API 102 dispatch point. */
object FeatureRegistry {
    private val allFeatures = listOf(
        FeatureDefinition(
            id = "home.module_settings",
            title = "模块设置入口",
            summary = "通过宿主原生设置项进入模块自有设置页面。",
            category = FeatureCategory.HOME,
            hosts = AppTarget.entries.toSet(),
            implemented = true,
        ),
        FeatureDefinition(
            id = "telegram.host_support",
            title = "Telegram 宿主支持",
            summary = "识别 Telegram 官方版、官网版及已适配的第三方客户端。",
            category = FeatureCategory.DEBUG,
            hosts = setOf(AppTarget.TELEGRAM),
            implemented = true,
        ),
        FeatureDefinition(
            id = TelegramAutoSignHook.FEATURE_ID,
            title = "Telegram 自动签到",
            summary = "Telegram-Android 客户端的签到观察与接入能力。",
            category = FeatureCategory.CHAT,
            hosts = setOf(AppTarget.TELEGRAM),
            implemented = false,
        ),
        FeatureDefinition(
            id = QQRecallHook.FEATURE_ID,
            title = "QQ 防撤回",
            summary = "拦截已识别的 QQNT 私聊与群聊撤回推送；其他消息保持 QQ 原始行为。",
            category = FeatureCategory.CHAT,
            hosts = setOf(AppTarget.QQ),
            implemented = true,
        ),
        FeatureDefinition(
            id = QQPokeEffectHook.FEATURE_ID,
            title = "关闭戳一戳动画",
            summary = "关闭已识别的 QQ 戳一戳效果；未匹配版本保持原始行为。",
            category = FeatureCategory.CHAT,
            hosts = setOf(AppTarget.QQ),
            implemented = true,
        ),
    )

    fun featuresFor(host: AppTarget): List<FeatureDefinition> = allFeatures.filter { host in it.hosts }

    fun categoriesFor(host: AppTarget): List<FeatureCategory> =
        featuresFor(host).map { it.category }.distinct()

    /** Every registered feature is treated as permanently enabled. */
    fun enabledCount(host: AppTarget): Int =
        featuresFor(host).count { it.implemented }

    fun dispatch(
        xposed: XposedInterface,
        packageParam: XposedModuleInterface.PackageReadyParam,
        host: AppTarget,
    ) {
        // All recognized hosts are active; there are no user-controlled feature gates.
        HostEntryHook.install(xposed, host, packageParam.classLoader)

        if (host == AppTarget.TELEGRAM) {
            TelegramAutoSignHook.install(xposed, packageParam.classLoader, packageParam.packageName)
        }

        if (host == AppTarget.QQ) {
            QQRecallHook.install(xposed, packageParam.classLoader)
            QQPokeEffectHook.install(xposed, packageParam.classLoader)
        }
    }

    fun resetForHotReload() {
        HostEntryHook.resetForHotReload()
        QQRecallHook.resetForHotReload()
        QQPokeEffectHook.resetForHotReload()
        QQDexKitResolver.resetForHotReload()
        HostActivityTracker.resetForHotReload()
        TelegramAutoSignHook.resetForHotReload()
    }
}