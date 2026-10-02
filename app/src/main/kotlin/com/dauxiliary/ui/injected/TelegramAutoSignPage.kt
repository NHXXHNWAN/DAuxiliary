package com.dauxiliary.ui.injected
// Telegram authorization page

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
//

import com.dauxiliary.core.telegram.TelegramAuthorization
import com.dauxiliary.core.telegram.TelegramPrefs
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import top.yukonga.miuix.kmp.basic.Text

/** Telegram-only management page. Authorization is the only host-specific gate. */
@Composable
internal fun TelegramAutoSignPage() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { TelegramPrefs(context) }
    val scope = rememberCoroutineScope()
    var endpoint by remember { mutableStateOf(prefs.authEndpoint()) }
    var userId by remember { mutableStateOf(prefs.telegramUserId()) }
    var authorized by remember { mutableStateOf(prefs.authorized()) }
    var checking by remember { mutableStateOf(false) }
    val targets = remember(context) { prefs.targets().toList().sorted() }

    InjectedGroupedPage(title = "Telegram 授权与签到") {
        item { SmallTitle(text = "授权状态") }
        item {
            InjectedGroupCard {
                BasicComponent(title = "授权接口地址")
                BasicTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    decorationBox = { inner -> androidx.compose.foundation.layout.Box { if (endpoint.isEmpty()) Text("https://your-worker.example/auth/verify"); inner() } },
                )
                BasicComponent(title = "Telegram 数字用户 ID")
                BasicTextField(
                    value = userId,
                    onValueChange = { userId = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    decorationBox = { inner -> androidx.compose.foundation.layout.Box { if (userId.isEmpty()) Text("例如：123456789"); inner() } },
                )
                BasicComponent(
                    title = if (authorized) "已授权" else "未授权",
                    summary = if (authorized) "Telegram 功能 Hook 将正常加载。" else "未授权时仅保留入口，Telegram 功能 Hook 不会加载。",
                )
                BasicComponent(title = "授权接口", summary = endpoint.ifBlank { "未配置" })
                BasicComponent(title = "Telegram 用户 ID", summary = userId.ifBlank { "未配置" })
                BasicComponent(
                    title = if (checking) "正在验证…" else "保存并验证授权",
                    summary = "请由管理员在 Bot 中回复你的消息发送 /auth；撤销使用 /revoke。",
                    onClick = {
                        prefs.setAuthEndpoint(endpoint)
                        prefs.setTelegramUserId(userId)
                        checking = true
                        scope.launch {
                            authorized = TelegramAuthorization.verify(context)
                            prefs.setAuthorized(authorized)
                            checking = false
                        }
                    },
                )
            }
        }
        item { SmallTitle(text = "自动签到") }
        item {
            InjectedGroupCard {
                BasicComponent(title = "自动签到接入", summary = if (authorized) "授权通过，功能按模块配置运行。" else "需要 Telegram 授权后才会运行。")
                if (targets.isEmpty()) BasicComponent(title = "暂无目标", summary = "学习签到目标后会显示在这里。")
                targets.forEach { id -> BasicComponent(title = id, summary = if (prefs.signedToday(id)) "今日已签到" else "等待签到") }
            }
        }
        item { SmallTitle(text = "配置说明") }
        item {
            InjectedGroupCard {
                BasicComponent(title = "授权接口地址", summary = "在模块构建配置或页面输入 Bot Worker 的 /auth/verify 地址。")
                BasicComponent(title = "仅 Telegram 生效", summary = "QQ、微信、抖音不会读取授权状态，也不会被此授权机制阻断。")
            }
        }
    }
}