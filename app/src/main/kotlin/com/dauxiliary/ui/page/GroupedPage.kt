package com.dauxiliary.ui.page

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

// The host supplies its bottom-bar inset to list content, not to the viewport.
internal val LocalNavigationPadding = compositionLocalOf { PaddingValues() }

/** Page insets and navigation insets are merged, never added twice. */
@Composable
internal fun GroupedPage(
    title: String,
    navigationIcon: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val navigationBottom = LocalNavigationPadding.current.calculateBottomPadding()
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            if (navigationIcon == null) {
                TopAppBar(title = title, scrollBehavior = scrollBehavior)
            } else {
                SmallTopAppBar(title = title, navigationIcon = navigationIcon)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().then(
                if (navigationIcon == null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                else Modifier,
            ),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                end = padding.calculateEndPadding(layoutDirection),
                top = padding.calculateTopPadding(),
                bottom = maxOf(navigationBottom, padding.calculateBottomPadding()) + 24.dp,
            ),
            content = content,
        )
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
