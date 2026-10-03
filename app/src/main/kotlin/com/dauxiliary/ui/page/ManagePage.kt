package com.dauxiliary.ui.page

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.dauxiliary.core.registry.AppTarget
import com.dauxiliary.core.telegram.TelegramClientDiscovery
import com.dauxiliary.core.xposed.XposedScopeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class ManagedHostApp(
    val packageName: String,
    val displayName: String,
    val applicationInfo: ApplicationInfo,
    val isTelegramClient: Boolean,
)

/** Shows installed host apps only. Integrations are always on; no host enable switches. */
@Composable
fun ManagePage() {
    val context = LocalContext.current.applicationContext
    var refreshKey by remember { mutableIntStateOf(0) }
    var fixedHosts by remember { mutableStateOf<List<ManagedHostApp>>(emptyList()) }
    var telegramClients by remember { mutableStateOf<List<ManagedHostApp>>(emptyList()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refreshKey++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    LaunchedEffect(context, refreshKey) {
        val detected = withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val fixed = AppTarget.entries
                .filter { it != AppTarget.TELEGRAM }
                .mapNotNull { target ->
                    val info = packageManager.hostApplicationInfo(target.packageName) ?: return@mapNotNull null
                    ManagedHostApp(
                        packageName = target.packageName,
                        displayName = packageManager.getApplicationLabel(info).toString(),
                        applicationInfo = info,
                        isTelegramClient = false,
                    )
                }
            val telegram = TelegramClientDiscovery.findInstalledClients(context).map { client ->
                ManagedHostApp(
                    packageName = client.packageName,
                    displayName = client.displayName,
                    applicationInfo = client.applicationInfo,
                    isTelegramClient = true,
                )
            }
            fixed to telegram
        }
        fixedHosts = detected.first
        telegramClients = detected.second.sortedBy { it.displayName.lowercase() }
        XposedScopeManager.requestScope(telegramClients.mapTo(linkedSetOf()) { it.packageName })
    }

    GroupedPage(title = "管理") {
        item(key = "manage_hosts_title") { SmallTitle(text = "宿主应用") }
        if (fixedHosts.isEmpty()) {
            item(key = "manage_fixed_empty") {
                BasicComponent(title = "暂无已安装的宿主应用", summary = "安装支持的应用后会自动识别并常驻启用。")
            }
        } else {
            fixedHosts.forEach { host ->
                item(key = "host_${host.packageName}") { HostApplicationCard(host) }
            }
        }

        item(key = "manage_telegram_title") { SmallTitle(text = "Telegram 客户端") }
        if (telegramClients.isEmpty()) {
            item(key = "manage_telegram_empty") {
                BasicComponent(
                    title = "未检测到 Telegram 客户端",
                    summary = "会扫描已安装应用中的 Telegram-Android 标志类；识别后显示客户端自己的名称和图标。",
                )
            }
        } else {
            telegramClients.forEach { client ->
                item(key = "telegram_${client.packageName}") { HostApplicationCard(client) }
            }
        }
    }
}

@Composable
private fun HostApplicationCard(host: ManagedHostApp) {
    val iconPainter = rememberApplicationIcon(host.applicationInfo)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HostIcon(host, iconPainter)
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = host.displayName, color = MiuixTheme.colorScheme.onSurface)
                Spacer(Modifier.size(4.dp))
                Text(
                    text = if (host.isTelegramClient) "Telegram 客户端 · 常驻启用" else "常驻启用",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun HostIcon(host: ManagedHostApp, iconPainter: Painter?) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (iconPainter != null) {
            Image(
                painter = iconPainter,
                contentDescription = "${host.displayName}图标",
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)),
            )
        }
    }
}

@Composable
private fun rememberApplicationIcon(info: ApplicationInfo): Painter? {
    val context = LocalContext.current
    val icon = remember(info.packageName) {
        runCatching { info.loadIcon(context.packageManager) }.getOrNull()
    }
    return icon?.let { drawable: Drawable ->
        remember(drawable) {
            BitmapPainter(drawable.toBitmap(width = 128, height = 128).asImageBitmap())
        }
    }
}

@Suppress("DEPRECATION")
private fun PackageManager.hostApplicationInfo(packageName: String): ApplicationInfo? =
    runCatching { getApplicationInfo(packageName, 0) }.getOrNull()
