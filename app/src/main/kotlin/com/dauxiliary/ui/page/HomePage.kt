package com.dauxiliary.ui.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dauxiliary.BuildConfig
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.core.registry.AppTarget
import com.dauxiliary.core.update.ApkInstaller
import com.dauxiliary.core.update.UpdateChannel
import com.dauxiliary.core.update.UpdateChecker
import com.dauxiliary.core.update.UpdateInfo
import com.dauxiliary.ui.theme.isAppInDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Desktop overview with the module status and enabled host count. */
@Composable
fun HomePage(
    updateChannelIndex: Int,
    preservedUpdate: UpdateInfo?,
    hasCheckedUpdate: Boolean,
    onUpdateResult: (UpdateInfo?) -> Unit,
) {
    val context = LocalContext.current
    val updateScope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var showReleaseNotes by remember { mutableStateOf(false) }
    val updateChannel = UpdateChannel.entries.getOrElse(updateChannelIndex) { UpdateChannel.STABLE }
    var refresh by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    fun checkForUpdate() {
        if (updateChannel == UpdateChannel.DISABLED) return
        if (isRefreshing) return
        isRefreshing = true
        updateScope.launch {
            onUpdateResult(UpdateChecker.check(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, updateChannel))
            isRefreshing = false
        }
    }

    LaunchedEffect(updateChannel, hasCheckedUpdate) {
        if (!hasCheckedUpdate) checkForUpdate()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            refresh++
        }
    }

    val moduleActive = remember(refresh) {
        AppTarget.entries.any { ConfigStore.isHostActive(context, it) }
    }
    val enabledCount = remember(refresh) {
        ConfigStore.enabledApplicationCount(context)
    }
    val darkTheme = isAppInDarkTheme()
    val cardColor = if (moduleActive) {
        if (darkTheme) Color(0xFF1A3A2E) else Color(0xFFE8F5E9)
    } else {
        if (darkTheme) Color(0xFF3A2428) else Color(0xFFFCE4EC)
    }
    val accentColor = if (moduleActive) {
        if (darkTheme) Color(0xFF66BB6A) else Color(0xFF2E7D32)
    } else {
        if (darkTheme) Color(0xFFEF5350) else Color(0xFFC62828)
    }
    val titleColor = MiuixTheme.colorScheme.onSurface
    val summaryColor = if (darkTheme) {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    } else {
        Color(0xFF616161)
    }

    GroupedPage(
        title = "主页",
        isRefreshing = isRefreshing,
        onRefresh = ::checkForUpdate,
        pullToRefreshState = pullToRefreshState,
    ) {
        item(key = "module_status_title") {
            SmallTitle(text = "DAuxiliary")
        }
        item(key = "module_status_card") {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                cornerRadius = 20.dp,
                colors = CardDefaults.defaultColors(color = cardColor, contentColor = titleColor),
                pressFeedbackType = top.yukonga.miuix.kmp.utils.PressFeedbackType.Tilt,
                onClick = {},
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                    Text(
                        text = if (moduleActive) "模块已激活" else "模块未激活",
                        color = titleColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (moduleActive) {
                            "DAuxiliary 正在为 $enabledCount 个已启用宿主提供服务"
                        } else {
                            "模块尚未在已启用的宿主中加载，请检查 LSPosed/KernelSU 配置"
                        },
                        color = summaryColor,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        color = accentColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        item(key = "update_slot") {
            AnimatedVisibility(
                visible = preservedUpdate != null,
                enter = fadeIn(tween(350)) + expandVertically(tween(350)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
            ) {
                preservedUpdate?.let { update ->
                    UpdateCard(
                        update = update,
                        isDownloading = isDownloading,
                        onUpdateClick = {
                            if (!isDownloading) {
                                isDownloading = true
                                updateScope.launch {
                                    ApkInstaller.downloadAndInstall(context, update.downloadUrl)
                                    isDownloading = false
                                }
                            }
                        },
                        onLongClick = { showReleaseNotes = true },
                    )
                }
            }
        }
    }

    preservedUpdate?.let { update ->
        OverlayBottomSheet(
            show = showReleaseNotes,
            title = "${update.channel.label} ${update.latestVersion} 更新内容",
            onDismissRequest = { showReleaseNotes = false },
        ) {
            Text(
                text = update.releaseNotes,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}