package com.dauxiliary.ui.injected

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.dauxiliary.core.telegram.TelegramPrefs
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle

/** Telegram-only Miuix management surface; no cross-app navigation or enable switch. */
@Composable
internal fun TelegramAutoSignPage() {
    val context = LocalContext.current
    val prefs = androidx.compose.runtime.remember(context) { TelegramPrefs(context) }
    val targets = androidx.compose.runtime.remember(context) { prefs.targets().toList().sorted() }

    InjectedGroupedPage(title = "Telegram 自动签到") {
        item { SmallTitle(text = "自动签到") }
        item {
            InjectedGroupCard {
                BasicComponent(
                    title = "自动签到接入",
                    summary = "观察模式已启用：学习按钮、记录目标和识别回复；不会自动发送消息或回调。", 
                )
            }
        }
        item { SmallTitle(text = "签到目标") }
        item {
            InjectedGroupCard {
                if (targets.isEmpty()) {
                    BasicComponent(title = "暂无目标", summary = "学习或添加签到目标后会显示在这里。")
                } else {
                    targets.forEach { id ->
                        BasicComponent(title = id, summary = if (prefs.signedToday(id)) "今日已签到" else "等待签到 Hook 接入")
                    }
                }
            }
        }
        item { SmallTitle(text = "支持客户端") }
        item {
            InjectedGroupCard {
                BasicComponent(
                    title = "Telegram-Android 及兼容 fork",
                    summary = "按客户端代码标志识别；每个客户端的 Hook 按独立进程运行。",
                )
            }
        }
    }
}