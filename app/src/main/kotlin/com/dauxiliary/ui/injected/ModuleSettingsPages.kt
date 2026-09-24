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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun ModuleSettingsHome(host: AppTarget, onOpenFeatures: () -> Unit) {
    val context = LocalContext.current
    val enabled = FeatureRegistry.enabledCount(context, host)
    InjectedGroupedPage(title = "${host.displayName}模块设置") {
        item { SmallTitle(text = "${host.displayName} · DAuxiliary") }
        item {
            InjectedGroupCard {
                BasicComponent(
                    title = "已启动 ${enabled} 个功能",
                    summary = "当前设置仅作用于 ${host.displayName}。",
                )
                ArrowPreference(
                    title = "功能设置",
                    summary = "按分类管理 ${host.displayName} 可用的模块功能。",
                    onClick = onOpenFeatures,
                )
            }
        }
        item { SmallTitle(text = "模块信息") }
        item {
            InjectedGroupCard {
                BasicComponent(title = "DAuxiliary", summary = "多宿主 Android LSPosed 增强框架")
                BasicComponent(title = "作者", summary = "NHXXHNWAN")
                BasicComponent(title = "版本", summary = BuildConfig.VERSION_NAME)
            }
        }
    }
}

@Composable
internal fun ModuleFeaturePage(host: AppTarget, onBack: () -> Unit) {
    InjectedGroupedPage(title = "功能", onBack = onBack) {
        FeatureRegistry.categoriesFor(host).forEach { category ->
            item { SmallTitle(text = category.title) }
            item {
                InjectedGroupCard {
                    FeatureRegistry.featuresFor(host)
                        .filter { it.category == category }
                        .forEach { feature -> FeaturePreference(host, feature) }
                }
            }
        }
    }
}

@Composable
private fun FeaturePreference(host: AppTarget, feature: FeatureDefinition) {
    val context = LocalContext.current
    var enabled by rememberSaveable(host.name, feature.id) {
        mutableStateOf(FeatureRegistry.isEnabled(context, host, feature))
    }
    SwitchPreference(
        title = feature.title,
        summary = if (feature.implemented) feature.summary else "${feature.summary} · 当前为占位功能",
        checked = enabled,
        onCheckedChange = {
            enabled = it
            FeatureRegistry.setEnabled(context, host, feature, it)
        },
    )
}

@Composable
internal fun ModuleLogPage(host: AppTarget, onBack: () -> Unit) {
    InjectedGroupedPage(title = "日志", onBack = onBack) {
        item { SmallTitle(text = "运行记录") }
        item {
            InjectedGroupCard {
                BasicComponent(title = "宿主识别", summary = "已识别 ${host.displayName} (${host.packageName})")
                BasicComponent(title = "模块加载", summary = "配置入口已初始化；真实 Hook 将按功能逐步接入。")
            }
        }
    }
}

@Composable
internal fun ModuleSettingsPage(host: AppTarget, onBack: () -> Unit) {
    InjectedGroupedPage(title = "设置", onBack = onBack) {
        item { SmallTitle(text = "开源信息") }
        item {
            InjectedGroupCard {
                BasicComponent(title = "界面与功能参考", summary = "部分模块设置结构和功能方向参考 WeKit")
                BasicComponent(title = "WeKit 项目作者", summary = "Ujhhgtg / WeKit")
                BasicComponent(title = "WeKit 许可", summary = "GPL-3.0；移植代码需保留原始版权与许可声明。")
                BasicComponent(title = "DAuxiliary 作者", summary = "NHXXHNWAN")
            }
        }
    }
}

@Composable
internal fun InjectedGroupedPage(
    title: String,
    onBack: (() -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    com.dauxiliary.ui.page.GroupedPage(
        title = title,
        navigationIcon = onBack?.let {
            {
                IconButton(onClick = it) {
                    Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                }
            }
        },
        content = content,
    )
}

@Composable
internal fun InjectedGroupCard(content: @Composable () -> Unit) {
    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) { content() }
}
