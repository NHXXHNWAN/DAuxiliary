package com.dauxiliary.ui.injected

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dauxiliary.BuildConfig
import com.dauxiliary.core.feature.FeatureDefinition
import com.dauxiliary.core.feature.FeatureRegistry
import com.dauxiliary.core.registry.AppTarget
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun HostOverviewPage(host: AppTarget) {
    val enabled = FeatureRegistry.enabledCount(host)
    InjectedGroupedPage(title = "${host.displayName}设置") {
        item { SmallTitle(text = host.displayName) }
        item {
            InjectedGroupCard {
                BasicComponent(title = "DAuxiliary", summary = "${host.displayName}专属功能")
                BasicComponent(title = "已启用功能", summary = "$enabled 项（常驻启用）")
            }
        }
        item { SmallTitle(text = "模块") }
        item {
            InjectedGroupCard {
                BasicComponent(title = "版本", summary = BuildConfig.VERSION_NAME)
            }
        }
    }
}

@Composable
internal fun HostFeaturePage(host: AppTarget) {
    InjectedGroupedPage(title = "${host.displayName}功能") {
        FeatureRegistry.categoriesFor(host).forEach { category ->
            val features = FeatureRegistry.featuresFor(host)
                .filter { it.implemented && it.category == category }
            if (features.isNotEmpty()) {
                item { SmallTitle(text = category.title) }
                item {
                    InjectedGroupCard {
                        features.forEach { feature -> HostFeatureItem(feature) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostFeatureItem(feature: FeatureDefinition) {
    BasicComponent(
        title = feature.title,
        summary = "${feature.summary} · 常驻启用",
    )
}

@Composable
internal fun InjectedGroupedPage(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    com.dauxiliary.ui.page.GroupedPage(title = title, content = content)
}

@Composable
internal fun InjectedGroupCard(content: @Composable () -> Unit) {
    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) { content() }
}
