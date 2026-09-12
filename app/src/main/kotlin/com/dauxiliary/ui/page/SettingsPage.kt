package com.dauxiliary.ui.page

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun SettingsPage(
    onAboutClick: () -> Unit,
    floatingNavigationBarStyle: Int,
    onFloatingNavigationBarStyleChange: (Int) -> Unit,
) {
    GroupedPage(title = "设置") {
        item { SmallTitle(text = "外观") }
        item {
            GroupCard {
                InformationRow("主题", "跟随系统切换浅色与深色外观")
                OverlayDropdownPreference(
                    title = "底栏样式",
                    items = listOf("Default", "iOS-like"),
                    selectedIndex = floatingNavigationBarStyle.coerceIn(0, 1),
                    onSelectedIndexChange = onFloatingNavigationBarStyleChange,
                )
            }
        }
        item { SmallTitle(text = "应用信息") }
        item {
            GroupCard {
                ArrowPreference(
                    title = "关于 DAuxiliary",
                    summary = "版本、项目介绍与开源许可",
                    onClick = onAboutClick,
                )
            }
        }
    }
}
