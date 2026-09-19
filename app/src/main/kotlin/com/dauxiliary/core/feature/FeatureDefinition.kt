package com.dauxiliary.core.feature

import com.dauxiliary.core.registry.AppTarget

/** A host-aware setting shown by the injected module settings UI. */
data class FeatureDefinition(
    val id: String,
    val title: String,
    val summary: String,
    val category: FeatureCategory,
    val hosts: Set<AppTarget>,
    val implemented: Boolean = false,
)

enum class FeatureCategory(val title: String, val description: String) {
    HOME("主页", "主界面与入口增强"),
    CHAT("聊天", "聊天体验与消息辅助"),
    PRIVACY("隐私", "隐私保护与状态控制"),
    BEAUTIFY("界面美化", "界面布局与视觉增强"),
    DEBUG("调试", "日志、诊断与开发辅助"),
}
