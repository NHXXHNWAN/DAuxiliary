package com.dauxiliary.ui.injected

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dauxiliary.BuildConfig
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.core.feature.FeatureDefinition
import com.dauxiliary.core.feature.FeatureRegistry
import com.dauxiliary.core.registry.AppTarget
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun HostOverviewPage(host: AppTarget) {
    InjectedGroupedPage(title = "首页") {
        item {
            InjectedGroupCard {
                BasicComponent(title = "版本", summary = BuildConfig.VERSION_NAME)
            }
        }
    }
}

@Composable
internal fun HostFeaturePage(host: AppTarget) {
    val context = LocalContext.current
    InjectedGroupedPage(title = "功能") {
        item {
            InjectedGroupCard {
                FeatureRegistry.featuresFor(host)
                    .filter { it.implemented }
                    .forEach { feature -> HostFeatureItem(context, host, feature) }
            }
        }
    }
}

@Composable
private fun HostFeatureItem(
    context: android.content.Context,
    host: AppTarget,
    feature: FeatureDefinition,
) {
    var enabled by remember(host, feature.id) {
        mutableStateOf(ConfigStore.enabledFeatureIds(context, host).contains(feature.id))
    }
    BasicComponent(
        title = feature.title,
        endActions = {
            top.yukonga.miuix.kmp.basic.Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    ConfigStore.setFeatureEnabled(context, host, feature.id, it)
                },
            )
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
