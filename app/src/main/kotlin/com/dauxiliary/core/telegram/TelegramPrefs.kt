package com.dauxiliary.core.telegram

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Telegram-only state namespace; it never reads QQ/WeChat preferences. */
class TelegramPrefs(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("telegram_autosign", Context.MODE_PRIVATE)
    fun enabled(): Boolean = prefs.getBoolean("enabled", true)
    fun setEnabled(value: Boolean) = prefs.edit().putBoolean("enabled", value).apply()

    /** Telegram-only authorization state. Other hosts deliberately do not use this gate. */
    fun authorized(): Boolean = prefs.getBoolean("authorized", false)
    fun setAuthorized(value: Boolean) = prefs.edit().putBoolean("authorized", value).apply()
    fun authEndpoint(): String = prefs.getString("auth_endpoint", "")?.trim().orEmpty()
    fun setAuthEndpoint(value: String) = prefs.edit().putString("auth_endpoint", value.trim()).apply()

    fun telegramUserId(): String = prefs.getString("telegram_user_id", "")?.trim().orEmpty()
    fun setTelegramUserId(value: String) = prefs.edit().putString("telegram_user_id", value.trim()).apply()


    /** Account prefix is deliberately explicit so multiple Telegram accounts cannot share state. */
    private fun key(account: String, name: String, id: String) =
        "acc_${sanitize(account)}_${name}_${sanitize(id)}"

    fun targets(account: String = DEFAULT_ACCOUNT): Set<String> =
        prefs.getStringSet("targets_${sanitize(account)}", emptySet()).orEmpty()
    fun addTarget(id: String, account: String = DEFAULT_ACCOUNT) = prefs.edit()
        .putStringSet("targets_${sanitize(account)}", targets(account) + id).apply()
    fun removeTarget(id: String, account: String = DEFAULT_ACCOUNT) = prefs.edit()
        .putStringSet("targets_${sanitize(account)}", targets(account) - id).apply()

    fun signedToday(id: String, account: String = DEFAULT_ACCOUNT): Boolean =
        prefs.getString(key(account, "last", id), "") == today()
    fun markSigned(id: String, account: String = DEFAULT_ACCOUNT) = prefs.edit()
        .putString(key(account, "last", id), today())
        .remove(key(account, "pending", id))
        .remove(key(account, "inflight", id)).apply()
    fun markPending(id: String, account: String = DEFAULT_ACCOUNT) =
        prefs.edit().putString(key(account, "pending", id), today()).apply()
    fun pendingToday(id: String, account: String = DEFAULT_ACCOUNT): Boolean =
        prefs.getString(key(account, "pending", id), "") == today()
    fun inFlight(id: String, account: String = DEFAULT_ACCOUNT): Boolean =
        prefs.getString(key(account, "inflight", id), "") == today()
    fun markInFlight(id: String, account: String = DEFAULT_ACCOUNT) =
        prefs.edit().putString(key(account, "inflight", id), today()).apply()
    fun clearInFlight(id: String, account: String = DEFAULT_ACCOUNT) =
        prefs.edit().remove(key(account, "inflight", id)).apply()

    fun retryAt(id: String, account: String = DEFAULT_ACCOUNT): Long =
        prefs.getLong(key(account, "retry_at", id), 0L)
    fun setRetry(id: String, count: Int, account: String = DEFAULT_ACCOUNT) = prefs.edit()
        .putInt(key(account, "retry", id), count)
        .putLong(key(account, "retry_at", id), System.currentTimeMillis() + SignLogic.backoffDelay(count))
        .apply()
    fun retryCount(id: String, account: String = DEFAULT_ACCOUNT): Int =
        prefs.getInt(key(account, "retry", id), 0)
    fun clearRetry(id: String, account: String = DEFAULT_ACCOUNT) = prefs.edit()
        .remove(key(account, "retry", id)).remove(key(account, "retry_at", id)).apply()

    fun learnedButton(account: String, id: String): String? =
        prefs.getString(key(account, "button", id), null)
    fun saveLearnedButton(account: String, id: String, text: String, data: String) =
        prefs.edit().putString(key(account, "button", id), "$text|$data").apply()

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun sanitize(value: String): String = value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
    companion object { const val DEFAULT_ACCOUNT = "default" }
}