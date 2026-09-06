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
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** 首页只保留模块总开关，具体功能从后续版本逐步接入。 */
@Composable
fun HomePage() {
    val scrollBehavior = MiuixScrollBehavior()
    val context = androidx.compose.ui.platform.LocalContext.current
    var moduleEnabled by rememberSaveable { mutableStateOf(ConfigStore.isMasterEnabled(context)) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = "DAuxiliary",
            largeTitle = "DAuxiliary",
            subtitle = "抖音增强模块",
            scrollBehavior = scrollBehavior,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SmallTitle(text = "模块状态")
            }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    SwitchPreference(
                        title = "启用模块",
                        summary = "关闭后所有功能停止生效",
                        checked = moduleEnabled,
                        onCheckedChange = {
                            moduleEnabled = it
                            ConfigStore.setMasterSwitch(context, it)
                        },
                    )
                }
            }
        }
    }
}
