package com.dauxiliary.core.telegram

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Telegram-only authorization check. Other hosts never call this gate. */
object TelegramAuthorization {
    suspend fun verify(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = TelegramPrefs(context)
        val endpoint = prefs.authEndpoint()
        val userId = prefs.telegramUserId()
        if (endpoint.isBlank() || userId.isBlank()) return@withContext false
        runCatching {
            val separator = if (endpoint.contains("?")) "&" else "?"
            val connection = (URL("$endpoint${separator}telegram_id=$userId").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                useCaches = false
            }
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()).optBoolean("authorized", false) }
        }.getOrDefault(false)
    }
}
