package com.dauxiliary.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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

/** Pages own their top insets; the main navigation host reserves bottom-bar space. */
@Composable
internal fun GroupedPage(
    title: String,
    navigationIcon: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            if (navigationIcon == null) {
                TopAppBar(title = title, scrollBehavior = scrollBehavior)
            } else {
                SmallTopAppBar(title = title, navigationIcon = navigationIcon, scrollBehavior = scrollBehavior)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = title, style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface)
        Text(text = summary, style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
