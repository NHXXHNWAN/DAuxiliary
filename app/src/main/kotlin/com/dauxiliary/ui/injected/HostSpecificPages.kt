package com.dauxiliary.ui.injected

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dauxiliary.BuildConfig
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.core.feature.FeatureCategory
import com.dauxiliary.core.feature.FeatureDefinition
import com.dauxiliary.core.feature.FeatureRegistry
import com.dauxiliary.core.registry.AppTarget
import com.dauxiliary.ui.page.LocalNavigationPadding
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune

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
    val features = remember(host) {
        FeatureRegistry.featuresFor(host).filter { it.implemented }
    }
    val groupedFeatures = remember(features) {
        features.groupBy { it.category }
    }

    InjectedGroupedPage(title = "功能") {
        if (features.isEmpty()) {
            item {
                InjectedGroupCard {
                    BasicComponent(
                        title = "暂无功能",
                        summary = "该宿主应用的功能正在开发中",
                    )
                }
            }
        } else {
            groupedFeatures.forEach { (category, categoryFeatures) ->
                item {
                    SmallTitle(
                        text = category.title,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                item {
                    InjectedGroupCard {
                        categoryFeatures.forEach { feature ->
                            HostFeatureItem(context, host, feature)
                        }
                    }
                }
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
    // 在宿主进程内，必须读写 remotePreferences
    val remotePrefs = remember { ConfigStore.remotePreferences }
    var enabled by remember(host, feature.id, remotePrefs) {
        if (remotePrefs != null) {
            mutableStateOf(
                remotePrefs.getStringSet("enabled_features_${host.name.lowercase()}", emptySet())
                    ?.contains(feature.id) == true
            )
        } else {
            mutableStateOf(ConfigStore.enabledFeatureIds(context, host).contains(feature.id))
        }
    }

    val categoryIcon = when (feature.category) {
        FeatureCategory.HOME -> MiuixIcons.Home
        FeatureCategory.CHAT -> MiuixIcons.Messages
        FeatureCategory.PRIVACY -> MiuixIcons.Lock
        FeatureCategory.BEAUTIFY -> MiuixIcons.Tune
        FeatureCategory.DEBUG -> MiuixIcons.Info
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = categoryIcon,
            contentDescription = null,
            modifier = Modifier.size(24.dp).padding(end = 12.dp),
        )
        BasicComponent(
            modifier = Modifier.weight(1f),
            title = feature.title,
            summary = feature.summary,
            endActions = {
                top.yukonga.miuix.kmp.basic.Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        // 宿主进程内通过 ContentProvider 写入模块 prefs
                        ConfigStore.setFeatureEnabled(context, host, feature.id, it)
                    },
                )
            },
        )
    }
}

@Composable
internal fun InjectedGroupedPage(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    // 注入导航栏高度，用于 GroupedPage 底部 padding
    val navigationPadding = remember {
        PaddingValues(bottom = 64.dp + 12.dp) // 底栏高度 64dp + 悬浮间距 12dp
    }
    CompositionLocalProvider(LocalNavigationPadding provides navigationPadding) {
        com.dauxiliary.ui.page.GroupedPage(title = title, content = content)
    }
}

@Composable
internal fun InjectedGroupCard(content: @Composable () -> Unit) {
    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) { content() }
}
