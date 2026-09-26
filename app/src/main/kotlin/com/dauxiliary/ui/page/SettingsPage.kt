package com.dauxiliary.ui.page
import androidx.compose.runtime.Composable

import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun SettingsPage(
    onAboutClick: () -> Unit,
    colorMode: Int,
    onColorModeChange: (Int) -> Unit,
    floatingNavigationBarStyle: Int,
    onFloatingNavigationBarStyleChange: (Int) -> Unit,
    updateChannel: Int,
    onUpdateChannelChange: (Int) -> Unit,
) {
    GroupedPage(title = "设置") {
        item { SmallTitle(text = "外观") }
        item {
            GroupCard {
                OverlayDropdownPreference(
                    title = "Color Mode",
                    items = listOf("System", "Light", "Dark", "MonetSystem", "MonetLight", "MonetDark"),
                    selectedIndex = colorMode.coerceIn(0, 5),
                    onSelectedIndexChange = onColorModeChange,
                )
                OverlayDropdownPreference(
                    title = "底栏样式",
                    items = listOf("Default", "iOS-like"),
                    selectedIndex = floatingNavigationBarStyle.coerceIn(0, 1),
                    onSelectedIndexChange = onFloatingNavigationBarStyleChange,
                )
            }
        }
        item { SmallTitle(text = "更新") }
        item {
            GroupCard {
                OverlayDropdownPreference(
                    title = "更新通道",
                    items = listOf("稳定版（Release）", "测试版（Test）", "不检查更新"),
                    selectedIndex = updateChannel.coerceIn(0, 2),
                    onSelectedIndexChange = onUpdateChannelChange,
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
