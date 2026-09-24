package com.dauxiliary.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.core.update.UpdateInfo
import com.dauxiliary.ui.miuix.component.liquid.IosLiquidGlassNavigationBar
import com.dauxiliary.ui.page.AboutPage
import com.dauxiliary.ui.page.HomePage
import com.dauxiliary.ui.page.LocalNavigationPadding
import com.dauxiliary.ui.page.ManagePage
import com.dauxiliary.ui.page.SettingsPage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Sidebar
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DAuxiliaryApp(
    colorMode: Int = 0,
    onColorModeChange: (Int) -> Unit = {},
) {
    val backStack = rememberNavBackStack<AppRoute>(AppRoute.Home)
    NavDisplay(backStack = backStack, transition = NavTransitions.MiuixDefault) {
        entry<AppRoute.Home> {
            MainNavigation(
                colorMode = colorMode,
                onColorModeChange = onColorModeChange,
                onAboutClick = { backStack.add(AppRoute.About) },
            )
        }
        entry<AppRoute.About>(swipeDismiss = NavSwipeDirection.LeftToRight) {
            AboutPage(onBack = { backStack.removeLastOrNull() })
        }
    }
}

@Composable
private fun MainNavigation(
    colorMode: Int,
    onColorModeChange: (Int) -> Unit,
    onAboutClick: () -> Unit,
) {
    val context = LocalContext.current
    var navigationBarStyle by rememberSaveable {
        mutableIntStateOf(
            ConfigStore.prefs(context).getInt(ConfigStore.KEY_FLOATING_NAVIGATION_BAR_STYLE, 0),
        )
    }
    var updateChannel by rememberSaveable {
        mutableIntStateOf(
            ConfigStore.prefs(context).getInt(ConfigStore.KEY_UPDATE_CHANNEL, 0),
        )
    }
    var homeUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val contentBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val navigationItems = remember {
        listOf(
            NavigationItem("首页", MiuixIcons.Basic.Check),
            NavigationItem("管理", MiuixIcons.Basic.Sidebar),
            NavigationItem("设置", MiuixIcons.Basic.Sidebar),
        )
    }

    fun selectPage(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    Scaffold(
        bottomBar = {
            if (navigationBarStyle == 1) {
                IosLiquidGlassNavigationBar(
                    items = navigationItems,
                    selectedIndex = pagerState.currentPage,
                    onItemClick = ::selectPage,
                    backdrop = contentBackdrop,
                    isBlurActive = true,
                )
            } else {
                FloatingNavigationBar(horizontalOutSidePadding = 16.dp) {
                    FloatingNavigationBarItem(
                        selected = pagerState.currentPage == 0,
                        onClick = { selectPage(0) },
                        icon = MiuixIcons.Basic.Check,
                        label = "首页",
                    )
                    FloatingNavigationBarItem(
                        selected = pagerState.currentPage == 1,
                        onClick = { selectPage(1) },
                        icon = MiuixIcons.Basic.Sidebar,
                        label = "管理",
                    )
                    FloatingNavigationBarItem(
                        selected = pagerState.currentPage == 2,
                        onClick = { selectPage(2) },
                        icon = MiuixIcons.Basic.Sidebar,
                        label = "设置",
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop)) {
            CompositionLocalProvider(LocalNavigationPadding provides padding) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true,
                ) { page ->
                    when (page) {
                        0 -> HomePage(
                            updateChannelIndex = updateChannel,
                            preservedUpdate = homeUpdate,
                            onUpdateResult = { homeUpdate = it },
                        )
                        1 -> ManagePage()
                        2 -> SettingsPage(
                            onAboutClick = onAboutClick,
                            colorMode = colorMode,
                            onColorModeChange = onColorModeChange,
                            floatingNavigationBarStyle = navigationBarStyle,
                            onFloatingNavigationBarStyleChange = {
                                navigationBarStyle = it
                                ConfigStore.prefs(context).edit()
                                    .putInt(ConfigStore.KEY_FLOATING_NAVIGATION_BAR_STYLE, it)
                                    .apply()
                            },
                            updateChannel = updateChannel,
                            onUpdateChannelChange = {
                                updateChannel = it
                                homeUpdate = null
                                ConfigStore.prefs(context).edit()
                                    .putInt(ConfigStore.KEY_UPDATE_CHANNEL, it)
                                    .apply()
                            },
                        )
                    }
                }
            }
        }
    }
}