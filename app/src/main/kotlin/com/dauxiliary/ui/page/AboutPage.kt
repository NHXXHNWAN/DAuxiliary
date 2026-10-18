// Copyright 2025-2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
// Layout and blend tokens adapted from the Miuix v0.9.4-rc01 About example.
package com.dauxiliary.ui.page

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dauxiliary.BuildConfig
import com.dauxiliary.R
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.ui.effect.BgEffectBackground
import com.dauxiliary.ui.theme.isAppInDarkTheme
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun AboutPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val isOs3Effect = ConfigStore.prefs(context).getInt(ConfigStore.KEY_BACKGROUND_EFFECT_VARIANT, 1) == 1
    val dynamicBackground = ConfigStore.prefs(context).getBoolean(ConfigStore.KEY_DYNAMIC_BACKGROUND, true)
    val fullScreenBackground = ConfigStore.prefs(context).getBoolean(ConfigStore.KEY_FULLSCREEN_BACKGROUND, true)
    var logoHeight by remember { mutableStateOf(180.dp) }
    val scrollProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f else {
                val spacer = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "logoSpacer" }
                if (spacer != null && spacer.size > 0) {
                    (listState.firstVisibleItemScrollOffset.toFloat() / spacer.size).coerceIn(0f, 1f)
                } else 0f
            }
        }
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val surface = MiuixTheme.colorScheme.surface
    val shaderSupported = remember { isRuntimeShaderSupported() }
    // Separate sources prevent a card from sampling its own rendered contents.
    val backgroundBackdrop = rememberLayerBackdrop { drawRect(surface); drawContent() }
    val pageBackdrop = rememberLayerBackdrop { drawRect(surface); drawContent() }
    val dark = isAppInDarkTheme()
    val cardBlend = remember(dark) {
        if (dark) listOf(
            BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
            BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
        ) else listOf(
            BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
            BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
        )
    }
    val cardColors = BlurDefaults.blurColors(blendColors = cardBlend)
    // Miuix v0.9.4 official progressive blur: progressiveTextureBlur + ProgressiveBlur.Top.

    val topBarBlurColors = BlurDefaults.blurColors(
        blendColors = listOf(BlendColorEntry(surface.copy(alpha = 0.3f))),
    )

    Scaffold(
        containerColor = surface,
        topBar = {
            // Keep the app bar itself transparent. The progressive blur is an overlay owned
            // by the app bar, rather than a permanently blurred measured wrapper around it.
            TopAppBar(
                title = "关于",
                largeTitle = "关于",
                bottomContent = {
                    Box(
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = (-topAppBarScrollBehavior.state.contentOffset /
                                        with(density) { 48.dp.toPx() }).coerceIn(0f, 1f)
                                }
                                .progressiveTextureBlur(
                                    backdrop = pageBackdrop,
                                    shape = RectangleShape,
                                    gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
                                    blurRadius = 10f,
                                    colors = topBarBlurColors,
                                ),
                        )
                },
                scrollBehavior = topAppBarScrollBehavior,
                titleColor = MiuixTheme.colorScheme.onSurface.copy(
                    alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
                ),
                color = Color.Transparent,
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
            BgEffectBackground(
                dynamicBackground = dynamicBackground,
                isOs3Effect = isOs3Effect,
                isFullSize = fullScreenBackground,
                modifier = Modifier.fillMaxSize().background(surface),
                bgModifier = Modifier.layerBackdrop(backgroundBackdrop),
                alpha = { 1f - scrollProgress },
            ) {
                // Fixed hero behind the transparent first list item, as in the upstream example.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = padding.calculateTopPadding() + 52.dp, start = 20.dp, end = 20.dp)
                        .onSizeChanged { with(density) { logoHeight = it.height.toDp() } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(88.dp).graphicsLayer {
                            val progress = ((scrollProgress - 0.35f) / 0.15f).coerceIn(0f, 1f)
                            alpha = 1f - progress
                            scaleX = 1f - progress * 0.05f
                            scaleY = scaleX
                        }.clip(RoundedCornerShape(24.dp)),
                    ) {
                        // painterResource cannot load the adaptive/layer-list launcher drawable.
                        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
                    }
                    Text(
                        text = "DAuxiliary",
                        modifier = Modifier.padding(top = 12.dp, bottom = 5.dp).graphicsLayer {
                            val progress = ((scrollProgress - 0.20f) / 0.15f).coerceIn(0f, 1f)
                            alpha = 1f - progress
                            scaleX = 1f - progress * 0.05f
                            scaleY = scaleX
                        },
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        modifier = Modifier.graphicsLayer {
                            val progress = ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                            alpha = 1f - progress
                            scaleX = 1f - progress * 0.05f
                            scaleY = scaleX
                        },
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                    overscrollEffect = null,
                    contentPadding = PaddingValues(top = padding.calculateTopPadding()),
                ) {
                    item(key = "logoSpacer") {
                        Spacer(Modifier.fillMaxWidth().height(logoHeight + 52.dp + 126.dp))
                    }
                    item(key = "aboutCards") {
                        Box {
                            // Guarantee enough scroll range to reach a fully collapsed bar on tall screens.
                            Spacer(Modifier.fillParentMaxHeight())
                            Column(
                                modifier = Modifier.padding(
                                    bottom = maxOf(padding.calculateBottomPadding(),
                                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()) + 24.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AboutGroup(backgroundBackdrop.takeIf { shaderSupported }, cardColors) {
                                    BasicComponent(title = "作者", summary = "NHXXHNWAN")
                                    ArrowPreference(
                                        title = "项目地址",
                                        endActions = { AboutValue("GitHub") },
                                        onClick = { uriHandler.openUri("https://github.com/NHXXHNWAN/DAuxiliary") },
                                    )
                                }
                                AboutGroup(backgroundBackdrop.takeIf { shaderSupported }, cardColors) {
                                    ArrowPreference(
                                        title = "界面组件",
                                        endActions = { AboutValue("Miuix") },
                                        onClick = { uriHandler.openUri("https://github.com/compose-miuix-ui/miuix") },
                                    )
                                    ArrowPreference(
                                        title = "Miuix 开源许可",
                                        endActions = { AboutValue("Apache-2.0") },
                                        onClick = { uriHandler.openUri("https://www.apache.org/licenses/LICENSE-2.0.txt") },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutValue(value: String) {
    Text(value, fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantActions)
}

@Composable
private fun AboutGroup(backdrop: LayerBackdrop?, blurColors: BlurColors, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).then(
            if (backdrop != null) Modifier.textureBlur(
                backdrop = backdrop,
                shape = RoundedCornerShape(16.dp),
                blurRadius = 60f,
                colors = blurColors,
            ) else Modifier,
        ),
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(
            color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
    ) { content() }
}
