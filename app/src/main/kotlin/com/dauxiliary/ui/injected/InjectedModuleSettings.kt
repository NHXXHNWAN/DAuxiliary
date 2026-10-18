package com.dauxiliary.ui.injected

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dauxiliary.core.registry.AppTarget
import com.dauxiliary.ui.miuix.component.liquid.IosLiquidGlassNavigationBar
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.basic.Sidebar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun InjectedModuleSettings(host: AppTarget) {
    val scope = rememberCoroutineScope()
    val pageCount = 3
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val surface = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
    var selectedPage by remember { mutableIntStateOf(0) }
    val items = remember {
        listOf(
            NavigationItem("首页", MiuixIcons.Basic.Check),
            NavigationItem("功能", MiuixIcons.Basic.Sidebar),
            NavigationItem("设置", MiuixIcons.Settings),
        )
    }

    fun selectPage(index: Int) {
        selectedPage = index
        scope.launch { pagerState.animateScrollToPage(index) }
    }

    MiuixTheme {
        Scaffold(
            bottomBar = {
                IosLiquidGlassNavigationBar(
                    items = items,
                    selectedIndex = selectedPage,
                    onItemClick = ::selectPage,
                    backdrop = backdrop,
                    isBlurActive = true,
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true,
                ) { page ->
                    when (page) {
                        0 -> TelegramHomePage()
                        1 -> HostFeaturePage(host)
                        2 -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun TelegramHomePage() {
    TelegramAuthorizationSheet()
    HostOverviewPage(AppTarget.TELEGRAM)
}