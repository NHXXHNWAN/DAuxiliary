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
    GroupedPage(title = "DAuxiliary") {
        item(key = "module_header") { SmallTitle(text = "模块") }
        item(key = "module_controls") {
            GroupCard {
                // A saved preference is not evidence that LSPosed loaded the module.
                BasicComponent(
                    title = "运行状态 · 尚未验证",
                    summary = "尚未接入宿主状态检测，请在 LSPosed 中确认模块与作用域。",
                )
                SwitchPreference(
                    title = "功能总开关",
                    summary = if (moduleEnabled) "已开启 · 实际生效需模块正确加载" else "已关闭 · 开启前请完成下方配置",
                    checked = moduleEnabled,
                    onCheckedChange = {
                        moduleEnabled = it
                        ConfigStore.setMasterSwitch(context, it)
                    },
                )
            }
        }
        item(key = "setup_header") { SmallTitle(text = "首次使用") }
        item(key = "setup_steps") {
            GroupCard {
                BasicComponent(
                    title = "1. 启用模块",
                    summary = "在 LSPosed 管理器中启用 DAuxiliary。",
                )
                BasicComponent(
                    title = "2. 配置作用域",
                    summary = "勾选抖音（com.ss.android.ugc.aweme）。",
                )
                BasicComponent(
                    title = "3. 重新启动抖音",
                    summary = "开启功能总开关后，完全结束抖音进程并重新打开。",
                )
            }
        }
    }
}
