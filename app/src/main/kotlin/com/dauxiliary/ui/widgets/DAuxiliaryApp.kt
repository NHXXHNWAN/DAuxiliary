package com.dauxiliary.ui.widgets

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.ui.miuix.component.liquid.IosLiquidGlassNavigationBar
import com.dauxiliary.ui.page.FeaturesPage
import com.dauxiliary.ui.page.HomePage
import com.dauxiliary.ui.page.SettingsPage
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.Sidebar
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

private object HomeRoute : NavKey
private object FeaturesRoute : NavKey
private object SettingsRoute : NavKey

/** 页面转场由 Miuix NavDisplay 驱动，不再使用 AndroidX AnimatedContent。 */
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
    val backStack = remember { navBackStackOf(HomeRoute) }
    val contentBackdrop = rememberLayerBackdrop()
    val selected = when (backStack.lastOrNull()) {
        FeaturesRoute -> 1
        SettingsRoute -> 2
        else -> 0
    }
    val navigationItems = listOf(
        NavigationItem("首页", MiuixIcons.Basic.Check),
        NavigationItem("功能", MiuixIcons.Basic.Search),
        NavigationItem("设置", MiuixIcons.Basic.Sidebar),
    )

    fun selectPage(page: Int) {
        val route = when (page) {
            1 -> FeaturesRoute
            2 -> SettingsRoute
            else -> HomeRoute
        }
        if (backStack.lastOrNull() == route) return

        // Keep a real Miuix navigation stack so NavDisplay can animate push/pop and swipe-back.
        if (route == HomeRoute) {
            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        } else {
            val existingIndex = backStack.indexOf(route)
            if (existingIndex >= 0) {
                while (backStack.lastIndex > existingIndex) backStack.removeAt(backStack.lastIndex)
            } else {
                backStack.add(route)
            }
        }
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    Scaffold(
        bottomBar = {
            if (navigationBarStyle == 1) {
                IosLiquidGlassNavigationBar(
                    items = navigationItems,
                    selectedIndex = selected,
                    onItemClick = ::selectPage,
                    backdrop = contentBackdrop,
                    isBlurActive = true,
                )
            } else {
                FloatingNavigationBar(horizontalOutSidePadding = 16.dp) {
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
                .padding(padding)
                .layerBackdrop(contentBackdrop),
        ) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = {
                    if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                },
                transition = NavTransitions.MiuixDefault,
            ) {
                entry<HomeRoute> { HomePage() }
                entry<FeaturesRoute>(swipeDismiss = NavSwipeDirection.LeftToRight) { FeaturesPage() }
                entry<SettingsRoute>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                    SettingsPage(onFloatingNavigationBarStyleChange = { navigationBarStyle = it })
                }
            }
        }
    }
}