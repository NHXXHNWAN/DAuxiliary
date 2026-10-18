package com.dauxiliary.ui.injected

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.core.telegram.TelegramAuthorization
import com.dauxiliary.core.telegram.TelegramPrefs
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

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
        title = "Telegram 授权",
        onDismissRequest = { if (!busy) visible = false },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isLetterOrDigit).uppercase().take(64) },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                decorationBox = { inner ->
                    Box {
                        if (code.isBlank()) Text("授权码")
                        inner()
                    }
                },
            )
            BasicTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                decorationBox = { inner ->
                    Box {
                        if (endpoint.isBlank()) Text("授权接口")
                        inner()
                    }
                },
            )
            if (message.isNotBlank()) Text(message)
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
                        message = if (ok) "授权成功" else "授权失败"
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
