package com.dauxiliary.core.telegram

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Telegram-only authorization. The remote record is authoritative; local state is only a cache. */
object TelegramAuthorization {
    suspend fun redeemCode(context: Context, code: String): Boolean = withContext(Dispatchers.IO) {
        val prefs = TelegramPrefs(context)
        val endpoint = prefs.authEndpoint()
        if (endpoint.isBlank() || code.isBlank()) return@withContext false
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpointFor(endpoint, "auth/redeem")).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 8_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                useCaches = false
            }
            connection.outputStream.use {
                it.write(JSONObject().put("code", code.trim().uppercase()).toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val result = stream?.bufferedReader()?.use { JSONObject(it.readText()) } ?: JSONObject()
            val userId = result.optString("telegram_id")
            val token = result.optString("auth_token")
            val authorized = status in 200..299 && result.optBoolean("module_authorized", result.optBoolean("authorized", false)) && userId.isNotBlank() && token.isNotBlank()
            if (authorized) {
                prefs.setTelegramUserId(userId)
                prefs.setAuthToken(token)
                prefs.setAuthCode("")
                prefs.setAuthorized(true)
            } else {
                prefs.setAuthorized(false)
            }
            authorized
        } catch (_: Exception) {
            prefs.setAuthorized(false)
            false
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun verify(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = TelegramPrefs(context)
        val endpoint = prefs.authEndpoint()
        val userId = prefs.telegramUserId()
        val token = prefs.authToken()
        if (endpoint.isBlank() || userId.isBlank() || token.isBlank()) return@withContext false
        var connection: HttpURLConnection? = null
        try {
            val url = URL(endpointFor(endpoint, "auth/verify") + "?telegram_id=$userId")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Authorization", "Bearer $token")
                useCaches = false
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val result = stream?.bufferedReader()?.use { JSONObject(it.readText()) } ?: JSONObject()
            val authorized = status in 200..299 && result.optBoolean("module_authorized", result.optBoolean("authorized", false))
            prefs.setAuthorized(authorized)
            authorized
        } catch (_: Exception) {
            prefs.setAuthorized(false)
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun endpointFor(configured: String, path: String): String {
        val trimmed = configured.trim().trimEnd('/')
        val base = trimmed.removeSuffix("/auth/verify").removeSuffix("/auth/redeem")
        return "$base/$path"
    }
}
