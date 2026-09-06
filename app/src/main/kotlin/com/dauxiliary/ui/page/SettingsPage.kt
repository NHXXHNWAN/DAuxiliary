package com.dauxiliary.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dauxiliary.core.config.ConfigStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * Settings page: app-level preferences (theme, language, about).
 */
@Composable
fun SettingsPage(onFloatingNavigationBarStyleChange: (Int) -> Unit = {}) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = androidx.compose.ui.platform.LocalContext.current
    var darkModeFollowSystem by rememberSaveable { mutableStateOf(true) }
    var floatingNavigationBarStyle by rememberSaveable {
        mutableStateOf(
            ConfigStore.prefs(context).getInt(ConfigStore.KEY_FLOATING_NAVIGATION_BAR_STYLE, 0),
        )
    }
    val floatingNavigationBarStyles = listOf("Default", "iOS-like")

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = "设置",
            scrollBehavior = scrollBehavior,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SmallTitle(text = "外观")
            }
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SwitchPreference(
                        title = "跟随系统深色模式",
                        summary = "关闭后可手动指定浅色/深色",
                        checked = darkModeFollowSystem,
                        onCheckedChange = { darkModeFollowSystem = it },
                    )
                    OverlayDropdownPreference(
                        title = "FloatingNavigationBar 样式",
                        summary = "",
                        items = floatingNavigationBarStyles,
                        selectedIndex = floatingNavigationBarStyle,
                        onSelectedIndexChange = { style ->
                            floatingNavigationBarStyle = style
                            onFloatingNavigationBarStyleChange(style)
                            ConfigStore.prefs(context).edit()
                                .putInt(ConfigStore.KEY_FLOATING_NAVIGATION_BAR_STYLE, style)
                                .apply()
                        },
                    )
                }
            }
            item {
                SmallTitle(text = "关于")
            }
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SwitchPreference(
                        title = "版本",
                        summary = "0.1.0 (骨架)",
                        checked = false,
                        onCheckedChange = { },
                        enabled = false,
                    )
                }
            }
        }
    }
}
