package com.dauxiliary.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.Sidebar
import com.dauxiliary.ui.page.HomePage
import com.dauxiliary.ui.page.FeaturesPage
import com.dauxiliary.ui.page.SettingsPage

/**
 * LSPosed 模块配置界面的根容器。这里只负责配置页之间的切换，
 * 不承载被注入应用的业务 UI。
 *
 * TODO: 使用 Miuix 当前版本官方悬浮底栏 API 替换临时 NavigationBar。
 * 当前依赖版本必须先确认该组件的准确名称和签名后再接入。
 */
@Composable
fun DAuxiliaryApp() {
    var selected by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            FloatingNavigationBar(
                horizontalOutSidePadding = 16.dp,
            ) {
                FloatingNavigationBarItem(
                    selected = selected == 0,
                    onClick = { selected = 0 },
                    icon = MiuixIcons.Basic.Check,
                    label = "首页",
                )
                FloatingNavigationBarItem(
                    selected = selected == 1,
                    onClick = { selected = 1 },
                    icon = MiuixIcons.Basic.Search,
                    label = "功能",
                )
                FloatingNavigationBarItem(
                    selected = selected == 2,
                    onClick = { selected = 2 },
                    icon = MiuixIcons.Basic.Sidebar,
                    label = "设置",
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selected) {
                0 -> HomePage()
                1 -> FeaturesPage()
                else -> SettingsPage()
            }
        }
    }
}
