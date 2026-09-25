package com.dauxiliary.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

// The host supplies its bottom-bar inset to list content, not to the viewport.
internal val LocalNavigationPadding = compositionLocalOf { PaddingValues() }

/** A single Miuix progressive page scaffold shared by the main pages. */
@Composable
fun GroupedPage(
    title: String,
    navigationIcon: (@Composable () -> Unit)? = null,
    containerColor: Color = MiuixTheme.colorScheme.surface,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    pullToRefreshState: PullToRefreshState? = null,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val navigationBottom = LocalNavigationPadding.current.calculateBottomPadding()
    val layoutDirection = LocalLayoutDirection.current
    val listState = rememberLazyListState()
    val surface = MiuixTheme.colorScheme.surface
    val shaderSupported = remember { isRuntimeShaderSupported() }
    val pageBackdrop = rememberLayerBackdrop { drawRect(surface); drawContent() }
    val density = LocalDensity.current
    val topBarBlurColors = BlurDefaults.blurColors(
        blendColors = listOf(BlendColorEntry(surface.copy(alpha = 0.3f))),
    )
    // Miuix v0.9.4 official progressive blur: progressiveTextureBlur + ProgressiveBlur.Top.

    Scaffold(
        containerColor = containerColor,
        topBar = {
            // Do not wrap TopAppBar in another measured app-bar container. TopAppBar itself is
            // the only top bar, and its MiuixScrollBehavior owns the progressive title layout.
            TopAppBar(
                modifier = Modifier,
                // Progressive blur remains clear at the expanded end and appears as the bar collapses.
                bottomContent = {
                    Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = (-scrollBehavior.state.contentOffset / with(density) { 48.dp.toPx() })
                                        .coerceIn(0f, 1f)
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
                title = title,
                largeTitle = title,
                titleColor = MiuixTheme.colorScheme.onSurface,
                largeTitleColor = MiuixTheme.colorScheme.onSurface,
                color = Color.Transparent,
                navigationIcon = navigationIcon ?: {},
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        // Scaffold's padding is passed to the scroll content, exactly as in the Miuix examples.
        // It must not be applied to the PullToRefresh viewport itself: PullToRefresh uses this
        // same top padding to place its indicator below the app bar.
        Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
            val contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                end = padding.calculateEndPadding(layoutDirection),
                top = padding.calculateTopPadding(),
                bottom = maxOf(navigationBottom, padding.calculateBottomPadding()) + 24.dp,
            )
            val listContent: @Composable () -> Unit = {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    overscrollEffect = null,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = contentPadding,
                    content = content,
                )
            }
            if (onRefresh != null && pullToRefreshState != null) {
                PullToRefresh(
                    modifier = Modifier.fillMaxSize(),
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    pullToRefreshState = pullToRefreshState,
                    contentPadding = contentPadding,
                    // This is the official Miuix coordination point. Without it, the refresh
                    // connection and the app-bar connection compete, leaving the indicator under
                    // the progressive bar and making the title appear duplicated.
                    topAppBarScrollBehavior = scrollBehavior,
                    refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新", "刷新成功"),
                ) { listContent() }
            } else {
                listContent()
            }
        }
    }
}

@Composable
internal fun GroupCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        content()
    }
}

/** Read-only information has no disabled arrow or fake click action. */
@Composable
internal fun InformationRow(title: String, summary: String) {
    BasicComponent(title = title, summary = summary)
}
