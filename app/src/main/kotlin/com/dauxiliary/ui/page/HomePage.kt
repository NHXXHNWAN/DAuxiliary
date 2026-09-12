package com.dauxiliary.ui.page

import androidx.compose.runtime.*
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
        item { SmallTitle(text = "模块状态") }
        item {
            GroupCard {
                InformationRow("运行状态待确认", "请在 LSPosed 中启用模块并勾选抖音作用域。当前页面尚未接入宿主运行状态回报。")
            }
        }
        item { SmallTitle(text = "模块控制") }
        item {
            GroupCard {
                SwitchPreference(
                    title = "启用模块",
                    summary = if (moduleEnabled) "总开关已开启，不代表模块已加载" else "总开关已关闭",
                    checked = moduleEnabled,
                    onCheckedChange = {
                        moduleEnabled = it
                        ConfigStore.setMasterSwitch(context, it)
                    },
                )
            }
        }
        item { SmallTitle(text = "使用说明") }
        item {
            GroupCard {
                InformationRow("首次启用", "安装后在 LSPosed 中启用 DAuxiliary，确认作用域，再重新启动抖音。")
            }
        }
    }
}
