package com.dauxiliary.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 关于页参考 Miuix 示例的独立页面结构：顶部栏、应用信息卡和许可入口。 */
@Composable
fun AboutPage(onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
    ) {
        SmallTopAppBar(
            title = "关于",
            navigationIcon = {
                top.yukonga.miuix.kmp.basic.IconButton(onClick = onBack) {
                    top.yukonga.miuix.kmp.basic.Icon(
                        imageVector = top.yukonga.miuix.kmp.icon.MiuixIcons.Back,
                        contentDescription = "返回",
                    )
                }
            },
            scrollBehavior = scrollBehavior,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = "DAuxiliary", fontSize = 30.sp)
                    Text(text = "抖音增强模块", modifier = Modifier.padding(top = 8.dp))
                    Text(text = "版本 0.1.0", modifier = Modifier.padding(top = 4.dp))
                }
            }
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ArrowPreference(
                        title = "项目介绍",
                        summary = "基于 LSPosed 的抖音增强模块",
                        onClick = { },
                        enabled = false,
                    )
                    ArrowPreference(
                        title = "开源许可",
                        summary = "本项目遵循 Apache-2.0 许可",
                        onClick = { },
                        enabled = false,
                    )
                }
            }
            item {
                Text(
                    text = "DAuxiliary",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    fontSize = 13.sp,
                )
            }
        }
    }
}