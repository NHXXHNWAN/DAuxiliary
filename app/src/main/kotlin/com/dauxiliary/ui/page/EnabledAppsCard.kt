package com.dauxiliary.ui.page

import androidx.compose.runtime.Composable
import com.dauxiliary.core.registry.AppTarget
import top.yukonga.miuix.kmp.basic.BasicComponent

/** Shared status language used by the desktop home and future in-host settings pages. */
@Composable
internal fun EnabledAppsCard(enabledApps: Set<String>) {
    val count = enabledApps.size
    val names = AppTarget.entries
        .filter { it.packageName in enabledApps }
        .joinToString("、") { it.displayName }

    BasicComponent(
        title = "已启用 ${count} 个应用",
        summary = if (names.isEmpty()) {
            "暂无已启用的宿主应用"
        } else {
            names
        },
    )
}