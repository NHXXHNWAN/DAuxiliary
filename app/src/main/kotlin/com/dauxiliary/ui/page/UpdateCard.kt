package com.dauxiliary.ui.page

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dauxiliary.core.update.UpdateInfo
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun UpdateCard(
    update: UpdateInfo,
    darkTheme: Boolean,
    onUpdateClick: () -> Unit,
) {
    val accent = if (update.channel.name == "TEST") {
        if (darkTheme) Color(0xFFC9A8FF) else Color(0xFF7650B5)
    } else {
        if (darkTheme) Color(0xFFAFC8FF) else Color(0xFF4266A8)
    }
    val cardColor = if (darkTheme) Color(0xFF292D38) else Color(0xFFF7F8FC)
    val titleColor = MiuixTheme.colorScheme.onSurface
    val summaryColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val tagBackground = accent.copy(alpha = if (darkTheme) 0.22f else 0.12f)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(
            color = cardColor,
            contentColor = titleColor,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(accent, CircleShape),
                )
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "发现${update.channel.label}",
                        color = titleColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "DAuxiliary 更新",
                        color = summaryColor,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = update.latestVersion,
                    modifier = Modifier
                        .background(tagBackground, RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                colors = CardDefaults.defaultColors(
                    color = if (darkTheme) Color.White.copy(alpha = 0.055f) else Color.Black.copy(alpha = 0.035f),
                    contentColor = titleColor,
                ),
            ) {
                BasicComponent(
                    title = "更新内容",
                    summary = update.releaseNotes,
                    titleColor = BasicComponentDefaults.titleColor(titleColor),
                    summaryColor = BasicComponentDefaults.summaryColor(summaryColor),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "当前 v${update.currentVersion}",
                    modifier = Modifier.weight(1f),
                    color = summaryColor,
                    fontSize = 12.sp,
                )
                Button(onClick = onUpdateClick) {
                    Text("查看")
                }
            }
        }
    }
}