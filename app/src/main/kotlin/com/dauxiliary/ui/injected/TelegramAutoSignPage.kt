package com.dauxiliary.ui.injected

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
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
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text

/** Telegram-only page. /code is self-service; /auth remains Bot-admin-only. */
@Composable
internal fun TelegramAutoSignPage() {
    val context = LocalContext.current
    val prefs = remember(context) { TelegramPrefs(context) }
    val scope = rememberCoroutineScope()
    var endpoint by remember { mutableStateOf(prefs.authEndpoint()) }
    var authCode by remember { mutableStateOf("") }
    var authorized by remember { mutableStateOf(prefs.authorized()) }
    var checking by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
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
                    decorationBox = { inner -> Box { if (endpoint.isEmpty()) Text("https://your-worker.example/auth/verify"); inner() } },
                )
                BasicComponent(title = "Telegram 模块授权码")
                BasicTextField(
                    value = authCode,
                    onValueChange = { authCode = it.filter(Char::isLetterOrDigit).uppercase().take(64) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    decorationBox = { inner -> Box { if (authCode.isEmpty()) Text("在 Bot 私聊发送 /code 获取"); inner() } },
                )
                BasicComponent(
                    title = if (authorized) "已授权" else "未授权",
                    summary = if (authorized) {
                        "Telegram 功能 Hook 将正常加载。已绑定 ID：${prefs.telegramUserId()}"
                    } else {
                        "未授权时仅保留入口，Telegram 功能 Hook 不会加载。"
                    },
                )
                BasicComponent(title = "授权接口", summary = endpoint.ifBlank { "未配置" })
                BasicComponent(
                    title = if (checking) "正在兑换…" else "兑换授权码",
                    summary = statusText.ifBlank { "在 Bot 私聊发送 /code，自助获取绑定本人 Telegram 的授权码。" },
                    onClick = {
                        prefs.setAuthEndpoint(endpoint)
                        checking = true
                        statusText = "正在连接授权服务…"
                        scope.launch {
                            authorized = TelegramAuthorization.redeemCode(context, authCode)
                            statusText = if (authorized) {
                                "授权成功，已绑定 Telegram ID ${prefs.telegramUserId()}。"
                            } else {
                                "兑换失败：授权码无效、已过期/已使用，或服务暂不可用。"
                            }
                            prefs.setAuthorized(authorized)
                            ConfigStore.setTelegramAuthorized(context, authorized)
                            checking = false
                        }
                    },
                )
                if (authorized) {
                    BasicComponent(
                        title = "重新校验授权",
                        summary = "服务端记录是最终状态；撤销后这里会显示未授权。",
                        onClick = {
                            checking = true
                            scope.launch {
                                authorized = TelegramAuthorization.verify(context)
                                prefs.setAuthorized(authorized)
                                ConfigStore.setTelegramAuthorized(context, authorized)
                                statusText = if (authorized) "远程校验通过。" else "远程校验失败或授权已撤销。"
                                checking = false
                            }
                        },
                    )
                }
            }
        }
        item { SmallTitle(text = "自动签到") }
        item {
            InjectedGroupCard {
                BasicComponent(title = "自动签到接入", summary = if (authorized) "授权通过，功能按模块配置运行。" else "需要 Telegram 模块授权后才会运行。")
                if (targets.isEmpty()) BasicComponent(title = "暂无目标", summary = "学习签到目标后会显示在这里。")
                targets.forEach { id -> BasicComponent(title = id, summary = if (prefs.signedToday(id)) "今日已签到" else "等待签到") }
            }
        }
        item { SmallTitle(text = "使用说明") }
        item {
            InjectedGroupCard {
                BasicComponent(title = "获取授权码", summary = "在 Telegram Bot 私聊发送 /code；每次生成的新码会使该账号之前未兑换的旧码失效。")
                BasicComponent(title = "一次性绑定", summary = "授权码完全随机、10 分钟过期、只能兑换一次，成功后绑定发码账号的 Telegram ID。")
                BasicComponent(title = "与 Bot 管理员授权分离", summary = "/auth 和 /revoke 只管理 Bot 管理员权限，不会授予或撤销模块授权。")
            }
        }
    }
}