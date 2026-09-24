package com.dauxiliary.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.core.registry.AppTarget
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
fun HomePage() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            refresh++
        }
    }
    val enabledApps = ConfigStore.enabledApplicationPackages(context)

    GroupedPage(title = "DAuxiliary") {
        item(key = "host_status_title") { SmallTitle(text = "宿主状态") }
        AppTarget.entries.forEach { host ->
            item(key = "host_status_${host.name}") {
                val active = remember(refresh) { ConfigStore.isHostActive(context, host) }
                Card(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.defaultColors(
                        color = if (active) Color(0xFF1F7A46) else Color(0xFFB3261E),
                        contentColor = Color.White,
                    ),
                ) {
                    BasicComponent(
                        title = host.displayName,
                        summary = if (active) "已激活" else "未激活",
                        titleColor = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.titleColor(
                            color = Color.White,
                        ),
                        summaryColor = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.summaryColor(
                            color = Color.White.copy(alpha = 0.86f),
                        ),
                    )
                }
            }
        }
        item(key = "enabled_apps") {
            GroupCard { EnabledAppsCard(enabledApps) }
        }
    }
}
