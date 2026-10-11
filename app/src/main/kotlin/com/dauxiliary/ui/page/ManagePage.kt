package com.dauxiliary.ui.page

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
    /** Loaded once during the background scan; card composition never calls PackageManager. */
    val iconBitmap: android.graphics.Bitmap?,
)

/** Process-local snapshot: switching pager pages must not rescan PackageManager. */
private object ManagedAppsCache {
    @Volatile var apps: List<ManagedHostApp> = emptyList()
    @Volatile var loaded: Boolean = false

    fun invalidate() {
        loaded = false
    }
}

/** Shows all detected applications in one unified, always-enabled list. */
@Composable
fun ManagePage() {
    val context = LocalContext.current.applicationContext
    var refreshKey by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf(ManagedAppsCache.apps) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Invalidate before changing the key so the next effect really rescans.
                ManagedAppsCache.invalidate()
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
        if (ManagedAppsCache.loaded) {
            apps = ManagedAppsCache.apps
            XposedScopeManager.requestScope(apps.filter { it.isTelegramClient }.mapTo(linkedSetOf()) { it.packageName })
            return@LaunchedEffect
        }

        val detected = withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val fixed = AppTarget.entries
                .filter { it != AppTarget.TELEGRAM }
                .mapNotNull { target ->
                    val info = packageManager.hostApplicationInfo(target.packageName) ?: return@mapNotNull null
                    info.toManagedHost(packageManager, isTelegramClient = false)
                }
            val telegram = TelegramClientDiscovery.findInstalledClients(context).map { client ->
                ManagedHostApp(
                    packageName = client.packageName,
                    displayName = client.displayName,
                    applicationInfo = client.applicationInfo,
                    isTelegramClient = true,
                    iconBitmap = client.applicationInfo.loadIcon(packageManager)
                        .toBitmap(width = 128, height = 128),
                )
            }
            (fixed + telegram).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
        }
        ManagedAppsCache.apps = detected
        ManagedAppsCache.loaded = true
        apps = detected
        XposedScopeManager.requestScope(detected.filter { it.isTelegramClient }.mapTo(linkedSetOf()) { it.packageName })
    }

    GroupedPage(title = "管理") {
        item(key = "manage_apps_title") { SmallTitle(text = "已识别应用") }
        if (apps.isEmpty()) {
            item(key = "manage_apps_empty") {
                BasicComponent(
                    title = "暂无已安装的支持应用",
                    summary = "安装支持的应用后会自动识别；所有已识别应用均为常驻启用。",
                )
            }
        } else {
            apps.forEach { app ->
                item(key = "managed_${app.packageName}") { HostApplicationCard(app) }
            }
        }
    }
}

private fun ApplicationInfo.toManagedHost(
    packageManager: PackageManager,
    isTelegramClient: Boolean,
): ManagedHostApp = ManagedHostApp(
    packageName = packageName,
    displayName = packageManager.getApplicationLabel(this).toString(),
    applicationInfo = this,
    isTelegramClient = isTelegramClient,
    iconBitmap = loadIcon(packageManager).toBitmap(width = 128, height = 128),
)

@Composable
private fun HostApplicationCard(host: ManagedHostApp) {
    val iconPainter = remember(host.packageName, host.iconBitmap) {
        host.iconBitmap?.let { BitmapPainter(it.asImageBitmap()) }
    }
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
private fun HostIcon(host: ManagedHostApp, iconPainter: BitmapPainter?) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center,
    ) {
        iconPainter?.let {
            Image(
                painter = it,
                contentDescription = "${host.displayName}图标",
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)),
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun PackageManager.hostApplicationInfo(packageName: String): ApplicationInfo? =
    runCatching { getApplicationInfo(packageName, 0) }.getOrNull()
