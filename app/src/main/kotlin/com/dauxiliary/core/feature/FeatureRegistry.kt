package com.dauxiliary.core.feature

import android.content.Context
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.core.registry.AppTarget
import com.dauxiliary.core.xposed.HostEntryHook
import com.dauxiliary.core.xposed.QQRecallHook

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface

/** Central feature catalogue and LibXposed API 102 dispatch point. */
object FeatureRegistry {
    private val allFeatures = listOf(
        FeatureDefinition(
            id = "home.module_settings",
            title = "模块设置入口",
            summary = "在宿主应用内显示轻量模块设置入口。",
            category = FeatureCategory.HOME,
            hosts = AppTarget.entries.toSet(),
            implemented = true,
        ),
        FeatureDefinition(
            id = "privacy.hide_online_status",
            title = "隐私状态增强",
            summary = "隐私状态增强。",
            category = FeatureCategory.PRIVACY,
            hosts = AppTarget.entries.toSet(),
        ),
        FeatureDefinition(
            id = "beautify.clean_home",
            title = "主页界面整理",
            summary = "按宿主应用提供界面整理与入口优化。",
            category = FeatureCategory.BEAUTIFY,
            hosts = AppTarget.entries.toSet(),
        ),
        FeatureDefinition(
            id = "debug.verbose_log",
            title = "详细日志",
            summary = "记录模块加载、宿主识别和功能状态变化。",
            category = FeatureCategory.DEBUG,
            hosts = AppTarget.entries.toSet(),
        ),
        FeatureDefinition(
            id = "wechat.chat_tools",
            title = "微信聊天辅助",
            summary = "微信聊天辅助。",
            category = FeatureCategory.CHAT,
            hosts = setOf(AppTarget.WECHAT),
        ),
        FeatureDefinition(
            id = "douyin.content_tools",
            title = "抖音内容辅助",
            summary = "抖音内容辅助。",
            category = FeatureCategory.HOME,
            hosts = setOf(AppTarget.DOUYIN),
        ),
        FeatureDefinition(
            id = "qq.chat_tools",
            title = "QQ 聊天辅助",
            summary = "QQ 聊天辅助功能集合。",
            category = FeatureCategory.CHAT,
            hosts = setOf(AppTarget.QQ),
        ),
        FeatureDefinition(
            id = QQRecallHook.FEATURE_ID,
            title = "QQ 防撤回（实验性）",
            summary = "实验性拦截 QQNT 私聊与群聊撤回推送；仅识别已知 Protobuf 类型，运行效果尚待真机验证。",
            category = FeatureCategory.CHAT,
            hosts = setOf(AppTarget.QQ),
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
        val entryEnabled = ConfigStore.isFeatureEnabledInHookedProcess(host, "home.module_settings")
        if (entryEnabled) {
            HostEntryHook.install(xposed, host, packageParam.classLoader)
        }
        if (host == AppTarget.QQ &&
            ConfigStore.isFeatureEnabledInHookedProcess(host, QQRecallHook.FEATURE_ID)
        ) {
            QQRecallHook.install(xposed, packageParam.classLoader)
        }
    }

    fun setEnabled(context: Context, host: AppTarget, feature: FeatureDefinition, enabled: Boolean) {
        ConfigStore.setFeatureEnabled(context, host, feature.id, enabled)
    }
}