package com.dauxiliary.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dauxiliary.core.telegram.TelegramPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

@Composable
fun AuthorizationAdminPage(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var endpoint by remember { mutableStateOf(TelegramPrefs(context).authEndpoint().removeSuffix("/auth/verify")) }
    var token by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf<JSONObject?>(null) }
    var targetId by remember { mutableStateOf("") }
    var roleTargetId by remember { mutableStateOf("") }
    var roles by remember { mutableStateOf<JSONObject?>(null) }

    suspend fun loadSummary() {
        loading = true
        error = ""
        try {
            summary = withContext(Dispatchers.IO) {
                val connection = java.net.URL("${endpoint.trimEnd('/')}/admin/summary").openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("Authorization", "Bearer $token")
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val result = stream?.bufferedReader()?.use { JSONObject(it.readText()) } ?: JSONObject()
                connection.disconnect()
                if (code !in 200..299) throw IllegalStateException(result.optString("error", "HTTP $code"))
                result
            }
        } catch (exception: Exception) { error = exception.message ?: "读取统计失败" }
        finally { loading = false }
    }

    suspend fun loadRoles() {
        try {
            roles = withContext(Dispatchers.IO) {
                val connection = java.net.URL("${endpoint.trimEnd('/')}/admin/bot/roles").openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("Authorization", "Bearer $token")
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val result = stream?.bufferedReader()?.use { JSONObject(it.readText()) } ?: JSONObject()
                connection.disconnect()
                if (code !in 200..299) throw IllegalStateException(result.optString("error", "HTTP $code"))
                result
            }
        } catch (exception: Exception) { error = exception.message ?: "读取角色失败" }
    }

    suspend fun changeRole(role: String, grant: Boolean) {
        if (roleTargetId.isBlank() || roleTargetId.any { !it.isDigit() }) { error = "请输入有效的 Telegram 数字 ID"; return }
        error = ""
        try {
            withContext(Dispatchers.IO) {
                val action = if (grant) "grant" else "revoke"
                val connection = java.net.URL("${endpoint.trimEnd('/')}/admin/bot/role/$action").openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                val payload = JSONObject().put("telegram_id", roleTargetId).put("role", role)
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val result = stream?.bufferedReader()?.use { JSONObject(it.readText()) } ?: JSONObject()
                connection.disconnect()
                if (code !in 200..299) throw IllegalStateException(result.optString("error", "HTTP $code"))
            }
            loadRoles()
            loadSummary()
        } catch (exception: Exception) { error = exception.message ?: "角色操作失败" }
    }

    suspend fun revoke(userId: String) {
        error = ""
        try {
            withContext(Dispatchers.IO) {
                val connection = java.net.URL("${endpoint.trimEnd('/')}/admin/revoke").openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.outputStream.use { it.write(JSONObject().put("telegram_id", userId).toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val result = stream?.bufferedReader()?.use { JSONObject(it.readText()) } ?: JSONObject()
                connection.disconnect()
                if (code !in 200..299) throw IllegalStateException(result.optString("error", "HTTP $code"))
            }
            loadSummary()
        } catch (exception: Exception) { error = exception.message ?: "撤销失败" }
    }

    GroupedPage(title = "授权管理", navigationIcon = {
        top.yukonga.miuix.kmp.basic.IconButton(onClick = onBack) {
            top.yukonga.miuix.kmp.basic.Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
        }
    }) {
        item { SmallTitle(text = "管理 API") }
        item {
            GroupCard {
                BasicComponent(title = "Worker 地址")
                androidx.compose.foundation.text.BasicTextField(value = endpoint, onValueChange = { endpoint = it.trimEnd('/') }, modifier = Modifier.fillMaxWidth().padding(16.dp))
                BasicComponent(title = "管理 Token（ADMIN_API_TOKEN）")
                androidx.compose.foundation.text.BasicTextField(value = token, onValueChange = { token = it }, modifier = Modifier.fillMaxWidth().padding(16.dp))
                ArrowPreference(title = if (loading) "正在加载…" else "刷新统计", summary = "模块用户、发码/兑换/撤销次数", onClick = { scope.launch { loadSummary() } })
            }
        }
        if (error.isNotBlank()) item { BasicComponent(title = "请求失败", summary = error) }
        summary?.let { data ->
            item { SmallTitle(text = "授权统计") }
            item {
                GroupCard {
                    BasicComponent(title = "模块授权用户", summary = data.optString("module_authorized_count", "0"))
                    BasicComponent(title = "有效授权码", summary = data.optString("active_code_count", "0"))
                    BasicComponent(title = "Bot 管理员", summary = data.optString("bot_admin_count", "0"))
                    BasicComponent(title = "维护者", summary = data.optString("maintainer_count", "0"))
                    BasicComponent(title = "累计发码 / 兑换 / 撤销", summary = "${data.optString("issued_count", "0")} / ${data.optString("redeemed_count", "0")} / ${data.optString("revoked_count", "0")}")
                }
            }
            item { SmallTitle(text = "已授权用户（点选后可撤销）") }
            val users = data.optJSONArray("users")
            if (users != null) for (index in 0 until users.length()) {
                val user = users.optJSONObject(index) ?: continue
                val id = user.optString("telegram_id")
                item(key = "auth_user_$id") { BasicComponent(title = "Telegram ID $id", summary = "授权：${user.optString("granted_at")} · 最近校验：${user.optString("last_verified_at")}", onClick = { targetId = id }) }
            }
        }
        item { SmallTitle(text = "Bot 角色管理") }
        item {
            GroupCard {
                BasicComponent(title = "目标 Telegram ID")
                androidx.compose.foundation.text.BasicTextField(value = roleTargetId, onValueChange = { roleTargetId = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth().padding(16.dp))
                ArrowPreference(title = "刷新角色列表", summary = "读取 Bot 管理员和维护者", onClick = { scope.launch { loadRoles() } })
                ArrowPreference(title = "授予维护者", onClick = { scope.launch { changeRole("maintainer", true) } })
                ArrowPreference(title = "撤销维护者", onClick = { scope.launch { changeRole("maintainer", false) } })
                ArrowPreference(title = "授予 Bot 管理员", onClick = { scope.launch { changeRole("bot_admin", true) } })
                ArrowPreference(title = "撤销 Bot 管理员", onClick = { scope.launch { changeRole("bot_admin", false) } })
                roles?.let { data ->
                    BasicComponent(title = "当前维护者", summary = data.optJSONArray("maintainers")?.length()?.toString() ?: "0")
                    BasicComponent(title = "当前 Bot 管理员", summary = data.optJSONArray("admins")?.length()?.toString() ?: "0")
                }
            }
        }
        item { SmallTitle(text = "撤销 Telegram 模块授权") }
        item {
            GroupCard {
                BasicComponent(title = "目标 Telegram ID", summary = targetId.ifBlank { "从上方选择，或手动填写" })
                androidx.compose.foundation.text.BasicTextField(value = targetId, onValueChange = { targetId = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth().padding(16.dp))
                ArrowPreference(title = "立即撤销授权", summary = "撤销模块凭据并清除未兑换授权码", onClick = { if (targetId.isNotBlank()) scope.launch { revoke(targetId) } })
            }
        }
    }
}