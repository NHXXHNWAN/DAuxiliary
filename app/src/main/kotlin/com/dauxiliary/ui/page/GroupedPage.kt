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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

// The host supplies its bottom-bar inset to list content, not to the viewport.
internal val LocalNavigationPadding = compositionLocalOf { PaddingValues() }

/** Page insets and navigation insets are merged, never added twice. */
@Composable
fun GroupedPage(
    title: String,
    navigationIcon: (@Composable () -> Unit)? = null,
    containerColor: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.surface,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    pullToRefreshState: top.yukonga.miuix.kmp.basic.PullToRefreshState? = null,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val navigationBottom = LocalNavigationPadding.current.calculateBottomPadding()
    val layoutDirection = LocalLayoutDirection.current
    val listState = rememberLazyListState()
    val surface = MiuixTheme.colorScheme.surface
    val shaderSupported = remember { isRuntimeShaderSupported() }
    val pageBackdrop = rememberLayerBackdrop { drawRect(surface); drawContent() }
    val topBarBlurColors = BlurDefaults.blurColors(
        blendColors = listOf(BlendColorEntry(surface.copy(alpha = 0.82f))),
    )

    Scaffold(
        containerColor = containerColor,
        topBar = {
            Box(
                Modifier.then(
                    if (shaderSupported) Modifier.drawBackdrop(
                        backdrop = pageBackdrop,
                        shape = { RectangleShape },
                        effects = {
                            // Keep the progressive app bar translucent while the list moves below it.
                            blur(25f)
                            blendColors(topBarBlurColors)
                        },
                    ) else Modifier,
                ),
            ) {
                // Miuix's TopAppBar is the official progressive app bar: it expands to a
                // large title at the top and progressively collapses as the list scrolls.
                // Unlike the previous pinned SmallTopAppBar, its scroll range also leaves the
                // pull-to-refresh indicator visible instead of putting it behind the bar.
                TopAppBar(
                    title = title,
                    largeTitle = title,
                    titleColor = MiuixTheme.colorScheme.onSurface,
                    largeTitleColor = MiuixTheme.colorScheme.onSurface,
                    color = if (shaderSupported) Color.Transparent else surface,
                    navigationIcon = navigationIcon ?: {},
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
            val listContent: @Composable () -> Unit = {
                LazyColumn(
                state = listState,
                // Official order: boundary bounce wraps the app-bar scroll connection.
                // Explicit modifier also supports pages shorter than the viewport.
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .then(
                        if (navigationIcon == null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                        else Modifier,
                    ),
                overscrollEffect = null, // Never stack the theme factory with the modifier.
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                    top = padding.calculateTopPadding(),
                    bottom = maxOf(navigationBottom, padding.calculateBottomPadding()) + 24.dp,
                ),
                content = content,
                )
            }
            if (onRefresh != null && pullToRefreshState != null) {
                PullToRefresh(
                    modifier = Modifier.fillMaxSize(),
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    pullToRefreshState = pullToRefreshState,
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
