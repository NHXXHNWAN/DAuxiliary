package com.dauxiliary.core.telegram

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Telegram-only state namespace; it never reads QQ/WeChat preferences. */
class TelegramPrefs(context: Context) {
    data class LearnedButton(val text: String, val data: ByteArray, val messageId: Int)

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
    fun authToken(): String = prefs.getString("auth_token", "")?.trim().orEmpty()
    fun setAuthToken(value: String) = prefs.edit().putString("auth_token", value.trim()).apply()
    fun authCode(): String = prefs.getString("auth_code", "")?.trim().orEmpty()
    fun setAuthCode(value: String) = prefs.edit().putString("auth_code", value.trim()).apply()
    fun clearAuthorization() = prefs.edit()
        .putBoolean("authorized", false)
        .remove("telegram_user_id")
        .remove("auth_token")
        .remove("auth_code")
        .apply()

    /** Account prefix is deliberately explicit so multiple Telegram accounts cannot share state. */
    private fun key(account: String, name: String, id: String) =
        "acc_${sanitize(account)}_${name}_${sanitize(id)}"

    fun targets(account: String = DEFAULT_ACCOUNT): Set<String> =
        prefs.getStringSet("targets_${sanitize(account)}", emptySet()).orEmpty()
    @Synchronized
    fun addTarget(id: String, account: String = DEFAULT_ACCOUNT) {
        val value = id.trim().takeIf(String::isNotEmpty) ?: return
        prefs.edit().putStringSet("targets_${sanitize(account)}", targets(account) + value).commit()
    }

    @Synchronized
    fun removeTarget(id: String, account: String = DEFAULT_ACCOUNT) {
        prefs.edit().putStringSet("targets_${sanitize(account)}", targets(account) - id).commit()
        listOf("button_text", "button_data", "button_message", "last", "pending", "inflight", "inflight_at", "retry", "retry_at").forEach { name ->
            prefs.edit().remove(key(account, name, id)).commit()
        }
    }

    fun signedToday(id: String, account: String = DEFAULT_ACCOUNT): Boolean =
        prefs.getString(key(account, "last", id), "") == today()
    fun markSigned(id: String, account: String = DEFAULT_ACCOUNT) = prefs.edit()
        .putString(key(account, "last", id), today())
        .remove(key(account, "pending", id))
        .remove(key(account, "inflight", id))
        .remove(key(account, "inflight_at", id))
        .remove(key(account, "retry", id))
        .remove(key(account, "retry_at", id))
        .commit()
    fun markPending(id: String, account: String = DEFAULT_ACCOUNT) = prefs.edit()
        .putString(key(account, "pending", id), today())
        .remove(key(account, "inflight", id))
        .remove(key(account, "inflight_at", id))
        .remove(key(account, "retry", id))
        .remove(key(account, "retry_at", id))
        .commit()
    fun pendingToday(id: String, account: String = DEFAULT_ACCOUNT): Boolean =
        prefs.getString(key(account, "pending", id), "") == today()
    fun inFlight(id: String, account: String = DEFAULT_ACCOUNT): Boolean {
        if (prefs.getString(key(account, "inflight", id), "") != today()) return false
        val sentAt = prefs.getLong(key(account, "inflight_at", id), 0L)
        if (sentAt > 0L && System.currentTimeMillis() - sentAt < IN_FLIGHT_TTL_MS) return true
        clearInFlight(id, account)
        return false
    }
    @Synchronized
    fun markInFlight(id: String, account: String = DEFAULT_ACCOUNT): Boolean {
        if (inFlight(id, account) || signedToday(id, account) || pendingToday(id, account)) return false
        return prefs.edit()
            .putString(key(account, "inflight", id), today())
            .putLong(key(account, "inflight_at", id), System.currentTimeMillis())
            .commit()
    }
    fun clearInFlight(id: String, account: String = DEFAULT_ACCOUNT) =
        prefs.edit().remove(key(account, "inflight", id)).remove(key(account, "inflight_at", id)).commit()

    fun retryAt(id: String, account: String = DEFAULT_ACCOUNT): Long =
        prefs.getLong(key(account, "retry_at", id), 0L)
    fun setRetryAt(id: String, atMillis: Long, account: String = DEFAULT_ACCOUNT): Boolean = prefs.edit()
        .putInt(key(account, "retry", id), retryCount(id, account) + 1)
        .putLong(key(account, "retry_at", id), atMillis)
        .remove(key(account, "pending", id))
        .remove(key(account, "inflight", id))
        .remove(key(account, "inflight_at", id))
        .commit()
    fun setRetry(id: String, count: Int, account: String = DEFAULT_ACCOUNT) = prefs.edit()
        .putInt(key(account, "retry", id), count)
        .putLong(key(account, "retry_at", id), System.currentTimeMillis() + SignLogic.backoffDelay(count))
        .remove(key(account, "pending", id))
        .remove(key(account, "inflight", id))
        .remove(key(account, "inflight_at", id))
        .commit()
    fun retryCount(id: String, account: String = DEFAULT_ACCOUNT): Int =
        prefs.getInt(key(account, "retry", id), 0)
    fun clearRetry(id: String, account: String = DEFAULT_ACCOUNT) = prefs.edit()
        .remove(key(account, "retry", id)).remove(key(account, "retry_at", id)).commit()

    fun learnedButton(account: String, id: String): LearnedButton? {
        val text = prefs.getString(key(account, "button_text", id), null) ?: return null
        val encoded = prefs.getString(key(account, "button_data", id), null) ?: return null
        val data = runCatching { android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP) }.getOrNull() ?: return null
        val messageId = prefs.getInt(key(account, "button_message", id), 0)
        return LearnedButton(text, data, messageId).takeIf { messageId > 0 && data.isNotEmpty() }
    }

    fun saveLearnedButton(account: String, id: String, text: String, data: ByteArray, messageId: Int) = prefs.edit()
        .putString(key(account, "button_text", id), text)
        .putString(key(account, "button_data", id), android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP))
        .putInt(key(account, "button_message", id), messageId)
        .commit()

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun sanitize(value: String): String = value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
    companion object {
        const val DEFAULT_ACCOUNT = "default"
        const val IN_FLIGHT_TTL_MS = 3 * 60_000L
    }
}