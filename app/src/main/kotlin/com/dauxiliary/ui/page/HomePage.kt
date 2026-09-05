package com.dauxiliary.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

/**
 * Home page: module status overview.
 * TODO: live LSPosed activation status via XSharedPreferences / API check.
 */
@Composable
fun HomePage() {
    val scrollBehavior = MiuixScrollBehavior()
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = "DAuxiliary",
            scrollBehavior = scrollBehavior,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SmallTitle(text = "状态")
            }
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(text = "欢迎使用 DAuxiliary", modifier = Modifier.padding(16.dp))
                }
            }
            item {
                SmallTitle(text = "关于")
            }
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(text = "抖音 LSPosed 增强模块 · 骨架阶段", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

private fun skeletonPadding() = androidx.compose.foundation.layout.PaddingValues(16.dp)
