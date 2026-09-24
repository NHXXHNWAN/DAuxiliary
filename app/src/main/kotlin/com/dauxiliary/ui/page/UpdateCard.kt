package com.dauxiliary.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val accent = if (update.channel.name == "TEST") Color(0xFF8A63D2) else Color(0xFF4D72C8)
    val cardColor = if (darkTheme) Color(0xFF303644) else Color(0xFFE8EDF6)
    val titleColor = if (darkTheme) MiuixTheme.colorScheme.onSurface else Color(0xFF20242B)
    val summaryColor = if (darkTheme) Color(0xFFC0CAD8) else Color(0xFF5B6572)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        cornerRadius = 18.dp,
        colors = CardDefaults.defaultColors(color = cardColor, contentColor = titleColor),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "发现${update.channel.label}",
                    color = titleColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = update.latestVersion,
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = update.releaseNotes,
                color = summaryColor,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 3,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "当前 v${update.currentVersion}",
                    color = summaryColor,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
                Button(onClick = onUpdateClick) {
                    Text("查看")
                }
            }
        }
    }
}