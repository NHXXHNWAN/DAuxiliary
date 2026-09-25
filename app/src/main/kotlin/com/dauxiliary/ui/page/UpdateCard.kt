package com.dauxiliary.ui.page

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dauxiliary.core.update.UpdateInfo
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UpdateCard(
    update: UpdateInfo,
    isDownloading: Boolean,
    onUpdateClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val titleColor = MiuixTheme.colorScheme.onSurface
    val summaryColor = MiuixTheme.colorScheme.onSurfaceVariantSummary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .combinedClickable(
                role = Role.Button,
                onClick = onUpdateClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "发现新的${update.channel.label}",
                    color = titleColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(3.dp))
                Text(text = update.latestVersion, color = summaryColor, fontSize = 13.sp)
            }
            Spacer(Modifier.size(12.dp))
            Button(onClick = onUpdateClick, enabled = !isDownloading) {
                Text(if (isDownloading) "下载中" else "点击升级")
            }
        }
    }
}