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

/** Host application selection used by the module. */
@Composable
fun ManagePage() {
    val context = LocalContext.current
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
                        checked = enabled,
                        onCheckedChange = { checked ->
                            enabled = checked
                            ConfigStore.setApplicationEnabled(context, target.packageName, checked)
                        },
                    )
                }
            }
        }
    }
}