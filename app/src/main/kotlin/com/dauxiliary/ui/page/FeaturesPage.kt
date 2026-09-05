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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.preference.SwitchPreference
import com.dauxiliary.core.config.ConfigStore

/**
 * Features page: per-feature switches grouped by category.
 * Feature definitions will be migrated to a FeatureRegistry as the project grows.
 */
@Composable
fun FeaturesPage() {
    val scrollBehavior = MiuixScrollBehavior()
    val context = androidx.compose.ui.platform.LocalContext.current
    var masterSwitch by rememberSaveable { mutableStateOf(ConfigStore.isMasterEnabled(context)) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = "功能",
            scrollBehavior = scrollBehavior,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SmallTitle(text = "总开关")
            }
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SwitchPreference(
                        title = "启用模块",
                        summary = "关闭后所有功能停止生效（写入抖音进程）",
                        checked = masterSwitch,
                        onCheckedChange = {
                            masterSwitch = it
                            ConfigStore.setMasterSwitch(context, it)
                        },
                    )
                }
            }
            item {
                SmallTitle(text = "功能列表")
            }
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Placeholder: real feature entries will be registered here.
                    SwitchPreference(
                        title = "敬请期待",
                        summary = "功能开发中，等待后续版本接入",
                        checked = false,
                        onCheckedChange = { },
                        enabled = false,
                    )
                }
            }
        }
    }
}
