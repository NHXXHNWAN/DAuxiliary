package com.dauxiliary.core.feature

import android.content.Context
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.core.registry.AppTarget
import com.dauxiliary.core.xposed.HostEntryHook
import com.dauxiliary.core.xposed.QQRecallHook
import com.dauxiliary.core.xposed.QQPokeEffectHook
import com.dauxiliary.core.xposed.QQDexKitResolver
import com.dauxiliary.core.xposed.HostActivityTracker
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
            summary = "观察型接入与管理页已加入；自动发送、结果闭环和调度尚未实现。",
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

    fun isEnabled(context: Context, host: AppTarget, feature: FeatureDefinition): Boolean =
        ConfigStore.enabledFeatureIds(context, host).contains(feature.id)

    fun isEnabled(context: Context, host: AppTarget, featureId: String): Boolean =
        ConfigStore.enabledFeatureIds(context, host).contains(featureId)

    fun enabledCount(context: Context, host: AppTarget): Int =
        featuresFor(host).count { it.implemented && isEnabled(context, host, it) }

    fun dispatch(
        xposed: XposedInterface,
        packageParam: XposedModuleInterface.PackageReadyParam,
        host: AppTarget,
    ) {
        // Keep the in-host entry available as the recovery path for feature configuration.
        HostEntryHook.install(xposed, host, packageParam.classLoader)

        if (host == AppTarget.TELEGRAM && ConfigStore.isFeatureEnabledInHookedProcess(host, TelegramAutoSignHook.FEATURE_ID)) {
            TelegramAutoSignHook.install(xposed, packageParam.classLoader, packageParam.packageName)
        }

        if (host == AppTarget.QQ) {
            if (ConfigStore.isFeatureEnabledInHookedProcess(host, QQRecallHook.FEATURE_ID)) {
                QQRecallHook.install(xposed, packageParam.classLoader)
            }
            if (ConfigStore.isFeatureEnabledInHookedProcess(host, QQPokeEffectHook.FEATURE_ID)) {
                QQPokeEffectHook.install(xposed, packageParam.classLoader)
            }
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

    fun setEnabled(context: Context, host: AppTarget, feature: FeatureDefinition, enabled: Boolean) {
        ConfigStore.setFeatureEnabled(context, host, feature.id, enabled)
    }
}