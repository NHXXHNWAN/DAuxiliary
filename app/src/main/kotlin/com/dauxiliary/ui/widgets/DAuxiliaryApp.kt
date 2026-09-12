package com.dauxiliary.ui.widgets

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.ui.miuix.component.liquid.IosLiquidGlassNavigationBar
import com.dauxiliary.ui.page.AboutPage
import com.dauxiliary.ui.page.HomePage
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

/** Miuix 主页面使用底栏切换，二级页面独立于底栏内容显示。 */
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
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val contentBackdrop = rememberLayerBackdrop()
    val navigationItems = listOf(
        NavigationItem("首页", MiuixIcons.Basic.Check),
        NavigationItem("设置", MiuixIcons.Basic.Sidebar),
    )

    fun selectPage(page: Int) {
        if (selected == page || showAbout) return
        selected = page
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    BackHandler(enabled = showAbout) {
        showAbout = false
    }

    Scaffold(
        bottomBar = {
            if (!showAbout) {
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
                .padding(padding)
                .background(MiuixTheme.colorScheme.background)
                .layerBackdrop(contentBackdrop),
        ) {
            if (showAbout) {
                AboutPage(onBack = { showAbout = false })
            } else {
                AnimatedContent(
                    targetState = selected,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { it } + fadeIn() togetherWith
                                slideOutHorizontally { -it / 3 } + fadeOut()
                        } else {
                            slideInHorizontally { -it } + fadeIn() togetherWith
                                slideOutHorizontally { it / 3 } + fadeOut()
                        }
                    },
                    label = "mainTabTransition",
                ) { page ->
                    if (page == 0) {
                        HomePage()
                    } else {
                        SettingsPage(
                            onAboutClick = { showAbout = true },
                            onFloatingNavigationBarStyleChange = { navigationBarStyle = it },
                        )
                    }
                }
            }
        }
    }
}