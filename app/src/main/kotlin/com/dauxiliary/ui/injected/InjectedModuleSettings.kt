package com.dauxiliary.ui.injected

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.dauxiliary.core.registry.AppTarget
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Sidebar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.unit.dp

@Composable
fun InjectedModuleSettings(host: AppTarget) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val page = pagerState.currentPage
    fun select(index: Int) {
        scope.launch { pagerState.animateScrollToPage(index) }
    }
    MiuixTheme {
        Scaffold(
            bottomBar = {
                FloatingNavigationBar(horizontalOutSidePadding = 16.dp) {
                    FloatingNavigationBarItem(
                        selected = page == 0,
                        onClick = { select(0) },
                        icon = MiuixIcons.Basic.Check,
                        label = "主页",
                    )
                    FloatingNavigationBarItem(
                        selected = page == 1,
                        onClick = { select(1) },
                        icon = MiuixIcons.Basic.Sidebar,
                        label = "功能",
                    )
                    FloatingNavigationBarItem(
                        selected = page == 2,
                        onClick = { select(2) },
                        icon = MiuixIcons.Basic.Sidebar,
                        label = "日志",
                    )
                    FloatingNavigationBarItem(
                        selected = page == 3,
                        onClick = { select(3) },
                        icon = MiuixIcons.Basic.Sidebar,
                        label = "设置",
                    )
                }
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true,
                ) { index ->
                    when (index) {
                        0 -> ModuleSettingsHome(host, onOpenFeatures = { select(1) })
                        1 -> ModuleFeaturePage(host, onBack = { select(0) })
                        2 -> ModuleLogPage(host, onBack = { select(0) })
                        3 -> ModuleSettingsPage(host, onBack = { select(0) })
                    }
                }
            }
        }
    }
}