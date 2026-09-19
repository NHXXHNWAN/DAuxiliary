package com.dauxiliary.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.core.registry.AppTarget
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** Cross-app host management. Feature settings will be added below this registry. */
@Composable
fun ManagePage() {
    val context = LocalContext.current
    var enabledApps by rememberSaveable {
        mutableStateOf(ConfigStore.enabledApplicationPackages(context))
    }
    GroupedPage(title = "管理") {
        item { SmallTitle(text = "宿主应用") }
        item {
            GroupCard {
                AppTarget.entries.forEach { target ->
                    var enabled by rememberSaveable(target.packageName) {
                        mutableStateOf(
                            ConfigStore.enabledApplicationPackages(context).contains(target.packageName),
                        )
                    }
                    SwitchPreference(
                        title = target.displayName,
                        summary = target.packageName,
                        checked = enabled,
                        onCheckedChange = { checked ->
                            enabled = checked
                            val current = enabledApps.toMutableSet()
                            if (checked) current += target.packageName else current -= target.packageName
                            enabledApps = current
                            ConfigStore.setApplicationEnabled(context, target.packageName, checked)
                        },
                    )
                }
            }
        }
        item { SmallTitle(text = "功能") }
        item {
            GroupCard {
                EnabledAppsCard(enabledApps)
            }
        }
    }
}