package com.dauxiliary.ui.page

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.dauxiliary.BuildConfig
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.ui.effect.BgEffectBackground
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** Compact Miuix-style About page with the upstream Background Effect controls. */
@Composable
fun AboutPage(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showBackgroundSettings by rememberSaveable { mutableStateOf(false) }
    var isOs3Effect by rememberSaveable {
        mutableStateOf(
            ConfigStore.prefs(context).getInt(ConfigStore.KEY_BACKGROUND_EFFECT_VARIANT, 1) == 1,
        )
    }
    var dynamicBackground by rememberSaveable {
        mutableStateOf(
            ConfigStore.prefs(context).getBoolean(ConfigStore.KEY_DYNAMIC_BACKGROUND, true),
        )
    }
    var fullScreenBackground by rememberSaveable {
        mutableStateOf(
            ConfigStore.prefs(context).getBoolean(ConfigStore.KEY_FULLSCREEN_BACKGROUND, true),
        )
    }

    BgEffectBackground(
        dynamicBackground = dynamicBackground,
        isOs3Effect = isOs3Effect,
        isFullSize = fullScreenBackground,
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
    ) {
        GroupedPage(
            title = "关于",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                }
            },
            containerColor = Color.Transparent,
        ) {
            item { SmallTitle(text = "应用") }
            item {
                GroupCard {
                    InformationRow("DAuxiliary", "面向多个 Android 应用的 LSPosed 增强框架")
                    InformationRow("版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    InformationRow("作者", "NHXXHNWAN")
                }
            }
            item { SmallTitle(text = "Background Effect") }
            item {
                GroupCard {
                    ArrowPreference(
                        title = "Background Effect",
                        summary = "Effect Variant、动态背景与全屏背景",
                        onClick = { showBackgroundSettings = true },
                    )
                }
            }
            item { SmallTitle(text = "开源信息") }
            item {
                GroupCard {
                    InformationRow("项目许可", "Apache-2.0")
                    InformationRow("界面组件", "Miuix · Compose Multiplatform")
                }
            }
        }
    }

    OverlayBottomSheet(
        show = showBackgroundSettings,
        title = "Background Effect",
        onDismissRequest = { showBackgroundSettings = false },
        insideMargin = DpSize(0.dp, 0.dp),
    ) {
        LazyColumn {
            item {
                OverlayDropdownPreference(
                    title = "Effect Variant",
                    items = listOf("OS2", "OS3"),
                    selectedIndex = if (isOs3Effect) 1 else 0,
                    onSelectedIndexChange = { index ->
                        isOs3Effect = index == 1
                        ConfigStore.prefs(context).edit()
                            .putInt(ConfigStore.KEY_BACKGROUND_EFFECT_VARIANT, index)
                            .apply()
                    },
                )
                SwitchPreference(
                    title = "Dynamic Background",
                    checked = dynamicBackground,
                    onCheckedChange = { enabled ->
                        dynamicBackground = enabled
                        ConfigStore.prefs(context).edit()
                            .putBoolean(ConfigStore.KEY_DYNAMIC_BACKGROUND, enabled)
                            .apply()
                    },
                )
                SwitchPreference(
                    title = "Full Screen Background",
                    checked = fullScreenBackground,
                    onCheckedChange = { enabled ->
                        fullScreenBackground = enabled
                        ConfigStore.prefs(context).edit()
                            .putBoolean(ConfigStore.KEY_FULLSCREEN_BACKGROUND, enabled)
                            .apply()
                    },
                )
            }
            item {
                androidx.compose.foundation.layout.Spacer(
                    modifier = androidx.compose.ui.Modifier
                        .height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                )
            }
        }
    }
}
