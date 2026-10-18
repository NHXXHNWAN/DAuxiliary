package com.dauxiliary.ui.page

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun UpdateLoadingCard(darkTheme: Boolean) {
    val transition = rememberInfiniteTransition(label = "update-check")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "update-check-alpha",
    )
    val surface = if (darkTheme) Color(0xFF292D38) else Color(0xFFF7F8FC)
    val placeholder = MiuixTheme.colorScheme.onSurface.copy(alpha = alpha)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(color = surface),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(10.dp).background(placeholder, CircleShape))
                Spacer(Modifier.size(10.dp))
                Column {
                    Text("检查更新", color = placeholder, fontSize = 17.sp)
                    Text("正在获取最新版本", color = placeholder, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(placeholder.copy(alpha = alpha * 0.45f), RoundedCornerShape(6.dp)),
            )
        }
    }
}