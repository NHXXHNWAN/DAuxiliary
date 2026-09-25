package com.dauxiliary.ui.page

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
// Layout spacing is expressed through explicit card padding.

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.core.registry.AppTarget
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Sidebar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Host application selection and quick launcher used by the module. */
@Composable
fun ManagePage() {
    val context = LocalContext.current
    GroupedPage(title = "管理") {
        item(key = "manage_title") {
            SmallTitle(text = "宿主应用")
        }
        AppTarget.entries.forEach { target ->
            item(key = "host_${target.packageName}") {
                HostApplicationCard(context, target)
            }
        }
    }
}

@Composable
private fun HostApplicationCard(context: Context, target: AppTarget) {
    val hostInfo = remember(target.packageName) { context.packageManager.hostApplicationInfo(target.packageName) }
    var enabled by rememberSaveable(target.packageName) {
        mutableStateOf(ConfigStore.enabledApplicationPackages(context).contains(target.packageName))
    }
    val isInstalled = hostInfo != null
    val summary = if (isInstalled) "点击卡片打开应用" else "未安装，暂不可打开"
    val iconPainter = hostInfo?.let { rememberApplicationIcon(it) }
    val cardColor = if (isInstalled) {
        MiuixTheme.colorScheme.surfaceContainer
    } else {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { context.launchHost(target) },
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(
            color = cardColor,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HostIcon(target, iconPainter, isInstalled)
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = target.displayName,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = summary,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    ConfigStore.setApplicationEnabled(context, target.packageName, checked)
                },
            )
        }
    }
}

@Composable
private fun HostIcon(target: AppTarget, iconPainter: Painter?, installed: Boolean) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                if (installed) Color.Transparent else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (iconPainter != null) {
            Image(
                painter = iconPainter,
                contentDescription = "${target.displayName}图标",
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)),
            )
        } else {
            Icon(
                imageVector = MiuixIcons.Basic.Sidebar,
                contentDescription = "未安装应用",
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(26.dp),
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

private fun android.content.pm.PackageManager.hostApplicationInfo(packageName: String): ApplicationInfo? =
    runCatching { getApplicationInfo(packageName, 0) }.getOrNull()

private fun Context.launchHost(target: AppTarget) {
    val launchIntent = runCatching {
        packageManager.getLaunchIntentForPackage(target.packageName)
    }.getOrNull()
    if (launchIntent == null) {
        Toast.makeText(this, "${target.displayName}未安装或没有可用入口", Toast.LENGTH_SHORT).show()
        return
    }
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(launchIntent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "无法打开${target.displayName}", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(this, "没有权限打开${target.displayName}", Toast.LENGTH_SHORT).show()
    }
}