package com.dauxiliary.ui.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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
    val cardColor = if (darkTheme) Color(0xFF3E4654) else Color(0xFFE8EDF5)
    val titleColor = if (darkTheme) MiuixTheme.colorScheme.onSurface else Color(0xFF20242B)
    val summaryColor = if (darkTheme) Color(0xFFC0CAD8) else Color(0xFF5B6572)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(
            color = cardColor,
            contentColor = titleColor,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "发现新版本",
                color = titleColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "v${update.currentVersion} → v${update.latestVersion}",
                color = summaryColor,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "更新内容",
                color = titleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = update.releaseNotes,
                color = summaryColor,
                fontSize = 14.sp,
                maxLines = 8,
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Button(onClick = onUpdateClick) {
                    Text("更新")
                }
            }
        }
    }
}