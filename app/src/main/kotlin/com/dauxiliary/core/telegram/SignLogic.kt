package com.dauxiliary.core.telegram

/**
 * Pure Telegram sign-in decisions, adapted from TGAutoSign's SignLogic.
 * This class deliberately has no Android/Xposed dependency so its behaviour can
 * be tested independently from Telegram's changing obfuscated classes.
 */
object SignLogic {
    const val SIGNED = 0
    const val FAILED = 1
    const val UNKNOWN = 2
    const val EXHAUSTED = 3

    const val SKIP_NONE = 0
    const val SKIP_SIGNED = 1
    const val SKIP_IN_FLIGHT = 2
    const val SKIP_PENDING = 3
    const val SKIP_BACKOFF = 4
    const val SKIP_DISABLED = 5

    private val permanentFailWords = arrayOf("请先关注", "未关注", "没有资格", "活动已结束", "已过期", "请先开始", "not allowed")
    private val exhaustedWords = arrayOf("已达上限", "今日上限", "本日上限", "次数用完", "请明日再试", "daily limit", "limit reached", "try tomorrow")
    private val duplicateWords = arrayOf("今日已签", "今天已签", "您已签", "已签到", "已打卡", "已签过", "already", "checked in", "signed today")
    private val successWords = arrayOf("签到成功", "打卡成功", "成功签到", "领取成功", "签到完成", "获得积分", "success", "claimed", "check-in complete")
    private val failWords = arrayOf("签到失败", "打卡失败", "未签到成功", "未成功", "请重新签到", "failed", "invalid", "rejected", "try again", "not signed", "请先加入", "无权限")

    fun parseHm(value: String?): Int {
        val parts = value?.trim()?.split(":") ?: return -1
        if (parts.size != 2) return -1
        val hour = parts[0].toIntOrNull() ?: return -1
        val minute = parts[1].toIntOrNull() ?: return -1
        return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else -1
    }

    /** Returns start, end, crossesMidnight, or null for malformed input. */
    fun window(value: String?): IntArray? {
        val parts = value?.trim()?.split("-") ?: return null
        if (parts.size != 2) return null
        val start = parseHm(parts[0]); val end = parseHm(parts[1])
        return if (start >= 0 && end >= 0) intArrayOf(start, end, if (end < start) 1 else 0) else null
    }

    fun inWindow(nowMinute: Int, range: IntArray?): Boolean {
        if (range == null) return true
        return if (range.getOrNull(2) == 1) nowMinute >= range[0] || nowMinute <= range[1]
        else nowMinute in range[0]..range[1]
    }

    fun normalizeId(raw: String?): String {
        if (raw == null) return ""
        val value = buildString {
            raw.forEach { ch ->
                when {
                    ch in '0'..'9' -> append(ch)
                    ch == '-' || ch == '−' || ch == '－' || ch == '–' || ch == '—' -> append('-')
                }
            }
        }
        val normalized = if (value.startsWith('-')) "-" + value.drop(1).replace("-", "") else value.replace("-", "")
        return normalized.takeUnless { it == "-" }.orEmpty()
    }

    fun backoffDelay(retry: Int): Long = when (retry.coerceAtLeast(0)) {
        0 -> 5 * 60_000L
        1 -> 15 * 60_000L
        2 -> 45 * 60_000L
        3 -> 2 * 60 * 60_000L
        else -> 4 * 60 * 60_000L
    }

    fun verdict(reply: String?): Int {
        val text = reply?.lowercase().orEmpty()
        if (text.isEmpty()) return UNKNOWN
        if (contains(text, failWords) || contains(text, permanentFailWords)) return FAILED
        if (contains(text, exhaustedWords)) return EXHAUSTED
        if (contains(text, duplicateWords) || contains(text, successWords)) return SIGNED
        return UNKNOWN
    }

    fun isNavigationButton(text: String?, data: String? = null): Boolean {
        val value = "${text.orEmpty()} ${data.orEmpty()}".lowercase()
        if (listOf("签到", "打卡", "领取", "check", "sign", "daily", "reward").any(value::contains)) return false
        return listOf("返回", "后退", "主菜单", "首页", "关闭", "取消", "退出", "back", "menu", "home", "cancel", "close").any(value::contains)
    }

    fun shouldSend(signedToday: Boolean, inFlight: Boolean, pending: Boolean, disabled: Boolean, retryAt: Long, now: Long): Int = when {
        inFlight -> SKIP_IN_FLIGHT
        signedToday -> SKIP_SIGNED
        pending -> SKIP_PENDING
        disabled -> SKIP_DISABLED
        now < retryAt -> SKIP_BACKOFF
        else -> SKIP_NONE
    }

    private fun contains(text: String, words: Array<String>) = words.any { text.contains(it.lowercase()) }
}
