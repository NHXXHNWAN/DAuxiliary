package com.dauxiliary.ui.injected

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.core.registry.AppTarget
import com.dauxiliary.ui.miuix.component.liquid.IosLiquidGlassNavigationBar
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun InjectedModuleSettings(host: AppTarget) {
    val scope = rememberCoroutineScope()
    val pageCount = 3
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val surfaceColor = MiuixTheme.colorScheme.surface
    val contentBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val navigationItems = remember {
        listOf(
            NavigationItem("首页", MiuixIcons.Home),
            NavigationItem("功能", MiuixIcons.Layers),
            NavigationItem("设置", MiuixIcons.Settings),
        )
    }

    fun selectPage(index: Int) {
        scope.launch { pagerState.animateScrollToPage(index) }
    }

    Scaffold(
        bottomBar = {
            IosLiquidGlassNavigationBar(
                items = navigationItems,
                selectedIndex = pagerState.currentPage,
                onItemClick = ::selectPage,
                backdrop = contentBackdrop,
                isBlurActive = true,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                userScrollEnabled = true,
            ) { page ->
                when (page) {
                    0 -> TelegramHomePage()
                    1 -> HostFeaturePage(host)
                    2 -> HostSettingsPage(host)
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

@Composable
private fun HostSettingsPage(host: AppTarget) {
    val context = androidx.compose.ui.platform.LocalContext.current
    InjectedGroupedPage(title = "设置") {
        item {
            InjectedGroupCard {
                top.yukonga.miuix.kmp.basic.BasicComponent(
                    title = "宿主应用",
                    summary = host.displayName,
                )
                top.yukonga.miuix.kmp.basic.BasicComponent(
                    title = "模块版本",
                    summary = com.dauxiliary.BuildConfig.VERSION_NAME,
                )
                top.yukonga.miuix.kmp.basic.BasicComponent(
                    title = "已启用功能",
                    summary = "${ConfigStore.enabledFeatureIds(context, host).size} 项",
                )
            }
        }
    }
}