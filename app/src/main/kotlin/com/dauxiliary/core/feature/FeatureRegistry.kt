package com.dauxiliary.core.feature

import android.util.Log
import com.dauxiliary.core.config.ConfigStore
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
            summary = "仅观察已学习的签到目标与回复；自动发送尚未启用，未知 fork 安全降级。",
            category = FeatureCategory.CHAT,
            hosts = setOf(AppTarget.TELEGRAM),
            implemented = true,
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
        // Entry injection is independent from feature implementations and must not
        // be blocked by one failed feature hook.
        runHook("${host.name}.entry") { HostEntryHook.install(xposed, host, packageParam.classLoader) }

        if (host == AppTarget.TELEGRAM && enabled(host, TelegramAutoSignHook.FEATURE_ID)) {
            runHook(TelegramAutoSignHook.FEATURE_ID) {
                TelegramAutoSignHook.install(xposed, packageParam.classLoader, packageParam.packageName)
            }
        }

        if (host == AppTarget.QQ && enabled(host, QQRecallHook.FEATURE_ID)) {
            runHook(QQRecallHook.FEATURE_ID) { QQRecallHook.install(xposed, packageParam.classLoader) }
        }
        if (host == AppTarget.QQ && enabled(host, QQPokeEffectHook.FEATURE_ID)) {
            runHook(QQPokeEffectHook.FEATURE_ID) { QQPokeEffectHook.install(xposed, packageParam.classLoader) }
        }
    }

    private fun enabled(host: AppTarget, featureId: String): Boolean =
        ConfigStore.isFeatureEnabledInHookedProcess(host, featureId)

    private inline fun runHook(id: String, block: () -> Unit) {
        runCatching { block() }.onFailure { error ->
            Log.e("DAuxiliary", "Feature hook failed: $id", error)
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