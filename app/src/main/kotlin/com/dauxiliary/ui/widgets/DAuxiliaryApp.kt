package com.dauxiliary.ui.widgets

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.ui.miuix.component.liquid.IosLiquidGlassNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.Sidebar
import com.dauxiliary.ui.page.FeaturesPage
import com.dauxiliary.ui.page.HomePage
import com.dauxiliary.ui.page.SettingsPage

/**
 * LSPosed 模块配置界面的根容器。这里只负责配置页之间的切换，
 * 不承载被注入应用的业务 UI。
 */
@Composable
fun DAuxiliaryApp() {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var navigationBarStyle by rememberSaveable {
        mutableIntStateOf(
            ConfigStore.prefs(context)
                .getInt(ConfigStore.KEY_FLOATING_NAVIGATION_BAR_STYLE, 0),
        )
    }
    val view = LocalView.current
    val navigationItems = listOf(
        NavigationItem("首页", MiuixIcons.Basic.Check),
        NavigationItem("功能", MiuixIcons.Basic.Search),
        NavigationItem("设置", MiuixIcons.Basic.Sidebar),
    )

    fun selectPage(page: Int) {
        if (selected == page) return
        selected = page
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    Scaffold(
        bottomBar = {
            if (navigationBarStyle == 1) {
                // Miuix official example's iOS-like liquid-glass navigation implementation.
                IosLiquidGlassNavigationBar(
                    items = navigationItems,
                    selectedIndex = selected,
                    onItemClick = { selectPage(it) },
                    backdrop = null,
                    isBlurActive = false,
                )
            } else {
                // Miuix official default floating navigation component.
                FloatingNavigationBar(
                    horizontalOutSidePadding = 16.dp,
                ) {
                    FloatingNavigationBarItem(
                        selected = selected == 0,
                        onClick = { selectPage(0) },
                        icon = MiuixIcons.Basic.Check,
                        label = "首页",
                    )
                    FloatingNavigationBarItem(
                        selected = selected == 1,
                        onClick = { selectPage(1) },
                        icon = MiuixIcons.Basic.Search,
                        label = "功能",
                    )
                    FloatingNavigationBarItem(
                        selected = selected == 2,
                        onClick = { selectPage(2) },
                        icon = MiuixIcons.Basic.Sidebar,
                        label = "设置",
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedContent(
                targetState = selected,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val forward = targetState > initialState
                    if (forward) {
                        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 4 } + fadeOut())
                    }
                },
                label = "page_transition",
            ) { page ->
                when (page) {
                    0 -> HomePage()
                    1 -> FeaturesPage()
                    else -> SettingsPage(onFloatingNavigationBarStyleChange = { navigationBarStyle = it })
                }
            }
        }
    }
}
