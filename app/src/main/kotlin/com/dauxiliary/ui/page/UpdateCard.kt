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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dauxiliary.core.update.UpdateInfo
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
    val accent = if (update.channel.name == "TEST") Color(0xFF9B6BFF) else Color(0xFF4D7CFF)
    val cardColor = if (darkTheme) Color(0xFF24283A) else Color(0xFFF1F4FF)
    val titleColor = if (darkTheme) MiuixTheme.colorScheme.onSurface else Color(0xFF1D2433)
    val summaryColor = if (darkTheme) Color(0xFFC2C8D9) else Color(0xFF58647A)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        cornerRadius = 24.dp,
        colors = CardDefaults.defaultColors(color = cardColor, contentColor = titleColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.20f), Color.Transparent),
                    ),
                )
                .padding(20.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(12.dp).background(accent, CircleShape),
                    )
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "发现${update.channel.label}",
                            color = titleColor,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "有新的 DAuxiliary 构建可用",
                            color = summaryColor,
                            fontSize = 13.sp,
                        )
                    }
                    Text(
                        text = "NEW",
                        modifier = Modifier
                            .background(accent.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VersionPill("当前", "v${update.currentVersion}", summaryColor)
                    Text("→", modifier = Modifier.align(Alignment.CenterVertically), color = accent)
                    VersionPill("最新", "v${update.latestVersion}", accent)
                }
                Spacer(Modifier.height(18.dp))
                Text("更新内容", color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(
                    text = update.releaseNotes,
                    color = summaryColor,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 6,
                )
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onUpdateClick) { Text("查看更新") }
                }
            }
        }
    }
}

@Composable
private fun VersionPill(label: String, version: String, color: Color) {
    Column(
        modifier = Modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = color, fontSize = 11.sp)
        Text(version, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}