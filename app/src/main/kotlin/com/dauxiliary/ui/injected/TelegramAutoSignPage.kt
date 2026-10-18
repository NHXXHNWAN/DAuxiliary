package com.dauxiliary.ui.injected

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.core.telegram.TelegramAuthorization
import com.dauxiliary.core.telegram.TelegramPrefs
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TelegramAuthorizationSheet() {
    val context = LocalContext.current
    val prefs = remember(context) { TelegramPrefs(context) }
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(!prefs.authorized()) }
    var endpoint by remember { mutableStateOf(prefs.authEndpoint()) }
    var code by remember { mutableStateOf(prefs.authCode()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (prefs.authorized()) visible = false
    }

    OverlayBottomSheet(
        show = visible,
        title = "Telegram 功能授权",
        onDismissRequest = { if (!busy) visible = false },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "首次使用需要授权。填入授权码后，DAuxiliary 将兑换为模块凭据用于 Telegram 自动签到等功能。",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            TextField(
                value = code,
                onValueChange = { code = it.filter(Char::isLetterOrDigit).uppercase().take(64) },
                modifier = Modifier.fillMaxWidth(),
                label = "授权码",
                enabled = !busy,
            )
            TextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                modifier = Modifier.fillMaxWidth(),
                label = "授权接口（可选）",
                enabled = !busy,
            )
            if (message.isNotBlank()) {
                Text(
                    text = message,
                    color = if (message.contains("失败")) 
                        MiuixTheme.colorScheme.error 
                    else 
                        MiuixTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Button(
                onClick = {
                    if (code.isBlank() || endpoint.isBlank()) {
                        message = "请填写完整"
                        return@Button
                    }
                    busy = true
                    scope.launch {
                        prefs.setAuthEndpoint(endpoint)
                        prefs.setAuthCode(code)
                        val ok = TelegramAuthorization.redeemCode(context, code)
                        prefs.setAuthorized(ok)
                        ConfigStore.setTelegramAuthorized(context, ok)
                        message = if (ok) "授权成功" else "授权失败，请检查授权码"
                        busy = false
                        if (ok) visible = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(if (busy) "验证中" else "确认授权") }
        }
    }
}
