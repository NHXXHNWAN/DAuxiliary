package com.dauxiliary.ui.widgets

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.ui.page.AboutPage
import com.dauxiliary.ui.page.HomePage
import com.dauxiliary.ui.miuix.component.liquid.IosLiquidGlassNavigationBar
import com.dauxiliary.ui.page.SettingsPage
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Sidebar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Miuix 主页面：底栏与横向 Pager 共用状态；About 是独立二级页面。 */
@Composable
fun DAuxiliaryApp() {
    val context = LocalContext.current
    val view = LocalView.current
    var navigationBarStyle by rememberSaveable {
        mutableIntStateOf(
            ConfigStore.prefs(context)
                .getInt(ConfigStore.KEY_FLOATING_NAVIGATION_BAR_STYLE, 0),
        )
    }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val surfaceColor = MiuixTheme.colorScheme.background
    val contentBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val navigationItems = remember {
        listOf(
            NavigationItem("首页", MiuixIcons.Basic.Check),
            NavigationItem("设置", MiuixIcons.Basic.Sidebar),
        )
    }

    LaunchedEffect(pagerState.currentPage) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    BackHandler(enabled = showAbout) {
        showAbout = false
    }

    if (showAbout) {
        AboutPage(onBack = { showAbout = false })
        return
    }

    Scaffold(
        bottomBar = {
            if (!showAbout) {
                if (navigationBarStyle == 1) {
                    IosLiquidGlassNavigationBar(
                        items = navigationItems,
                        selectedIndex = pagerState.currentPage,
                        onItemClick = { page ->
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        },
                        backdrop = contentBackdrop,
                        isBlurActive = true,
                    )
                } else {
                    FloatingNavigationBar(horizontalOutSidePadding = 16.dp) {
                    FloatingNavigationBarItem(
                        selected = pagerState.currentPage == 0,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    0,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        },
                        icon = MiuixIcons.Basic.Check,
                        label = "首页",
                    )
                    FloatingNavigationBarItem(
                        selected = pagerState.currentPage == 1,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    1,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        },
                        icon = MiuixIcons.Basic.Sidebar,
                        label = "设置",
                    )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop),
        ) {
            if (showAbout) {
                AboutPage(onBack = { showAbout = false })
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
                    userScrollEnabled = true,
                ) { page ->
                    if (page == 0) {
                        HomePage()
                    } else {
                        SettingsPage(
                            onAboutClick = { showAbout = true },
                            floatingNavigationBarStyle = navigationBarStyle,
                            onFloatingNavigationBarStyleChange = {
                                navigationBarStyle = it
                                ConfigStore.prefs(context).edit()
                                    .putInt(ConfigStore.KEY_FLOATING_NAVIGATION_BAR_STYLE, it).apply()
                            },
                        )
                    }
                }
            }
        }
    }
}