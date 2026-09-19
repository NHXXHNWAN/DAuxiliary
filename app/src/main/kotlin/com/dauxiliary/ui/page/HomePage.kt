package com.dauxiliary.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.basic.BasicComponent
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.dauxiliary.core.config.ConfigStore
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun HomePage() {
    val context = LocalContext.current
    var moduleEnabled by rememberSaveable { mutableStateOf(ConfigStore.isMasterEnabled(context)) }
    val enabledApps = ConfigStore.enabledApplicationPackages(context)

    GroupedPage(title = "DAuxiliary") {
        item(key = "module_header") { SmallTitle(text = "模块") }
        item(key = "module_controls") {
            GroupCard {
                EnabledAppsCard(enabledApps)
                // A saved preference is not evidence that LSPosed loaded the module.
                BasicComponent(
                    title = "运行状态 · 尚未验证",
                    summary = "尚未接入宿主状态检测，请在 LSPosed 中确认模块与作用域。",
                )
                SwitchPreference(
                    title = "功能总开关",
                    summary = if (moduleEnabled) "已开启 · 实际生效需模块正确加载" else "已关闭",
                    checked = moduleEnabled,
                    onCheckedChange = {
                        moduleEnabled = it
                        ConfigStore.setMasterSwitch(context, it)
                    },
                )
            }
        }
        item(key = "module_note") {
            BasicComponent(
                title = "状态以 LSPosed 加载结果为准",
                summary = "开关仅保存模块配置，不代表宿主进程已经加载。",
            )
        }
    }
}
