package com.dauxiliary.ui.injected

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dauxiliary.core.telegram.TelegramPrefs
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** Telegram-only Miuix management surface; no cross-app navigation. */
@Composable
internal fun TelegramAutoSignPage() {
    val context = LocalContext.current
    val prefs = remember(context) { TelegramPrefs(context) }
    var enabled by remember { mutableStateOf(prefs.enabled()) }
    val targets = remember { prefs.targets().toList().sorted() }

    InjectedGroupedPage(title = "Telegram 自动签到") {
        item { SmallTitle(text = "自动签到") }
        item {
            InjectedGroupCard {
                SwitchPreference(
                    title = "启用自动签到",
                    summary = "适用于已适配的 Telegram-Android 客户端；功能仍在接入中。",
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        prefs.setEnabled(it)
                    },
                )
            }
        }
        item { SmallTitle(text = "签到目标") }
        item {
            InjectedGroupCard {
                if (targets.isEmpty()) {
                    BasicComponent(title = "暂无目标", summary = "在 Telegram 客户端中学习或添加签到目标后会显示在这里。")
                } else {
                    targets.forEach { id -> BasicComponent(title = id, summary = if (prefs.signedToday(id)) "今日已签到" else "等待签到 Hook 接入") }
                }
            }
        }
        item { SmallTitle(text = "支持客户端") }
        item {
            InjectedGroupCard {
                BasicComponent(title = "Telegram 官方版、官网版及兼容 fork", summary = "客户端通过 Telegram-Android 标志类识别；每个客户端使用独立进程状态。")
            }
        }
    }
}