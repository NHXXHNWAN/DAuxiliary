package com.dauxiliary.ui.injected

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dauxiliary.BuildConfig
import com.dauxiliary.core.feature.FeatureDefinition
import com.dauxiliary.core.feature.FeatureRegistry
import com.dauxiliary.core.registry.AppTarget
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun HostOverviewPage(host: AppTarget) {
    val context = LocalContext.current
    val enabled = FeatureRegistry.enabledCount(context, host)
    InjectedGroupedPage(title = "${host.displayName}设置") {
        item { SmallTitle(text = host.displayName) }
        item {
            InjectedGroupCard {
                BasicComponent(title = "DAuxiliary", summary = "${host.displayName}专属模块设置")
                BasicComponent(title = "已启用功能", summary = "$enabled 项")
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
                        features.forEach { feature -> HostFeaturePreference(host, feature) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostFeaturePreference(host: AppTarget, feature: FeatureDefinition) {
    val context = LocalContext.current
    var enabled by rememberSaveable(host.name, feature.id) {
        mutableStateOf(FeatureRegistry.isEnabled(context, host, feature))
    }
    SwitchPreference(
        title = feature.title,
        summary = feature.summary,
        checked = enabled,
        onCheckedChange = {
            enabled = it
            FeatureRegistry.setEnabled(context, host, feature, it)
        },
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