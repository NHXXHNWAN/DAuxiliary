package com.dauxiliary.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MiuixTheme { AdminScreen() } }
    }
}

@Composable
private fun AdminScreen() {
    val scope = rememberCoroutineScope()
    var endpoint by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var targetId by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("maintainer") }
    var summary by remember { mutableStateOf<JSONObject?>(null) }
    var roles by remember { mutableStateOf<JSONObject?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun runRequest(block: suspend () -> JSONObject) {
        scope.launch {
            busy = true
            message = ""
            try {
                val result = block()
                if (result.has("module_authorized_count")) summary = result
                if (result.has("admins") || result.has("maintainers")) roles = result
                message = "操作成功"
            } catch (error: Exception) {
                message = error.message ?: "请求失败"
            } finally { busy = false }
        }
    }

    suspend fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val base = endpoint.trim().trimEnd('/')
        require(base.startsWith("https://")) { "Worker 地址必须使用 HTTPS" }
        val connection = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${token.trim()}")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val result = stream?.bufferedReader()?.use { JSONObject(it.readText()) } ?: JSONObject()
        connection.disconnect()
        if (code !in 200..299) error(result.optString("error", "HTTP $code"))
        result
    }

    fun roleChange(grant: Boolean) = runRequest {
        request("/admin/bot/role/${if (grant) "grant" else "revoke"}", "POST", JSONObject()
            .put("telegram_id", targetId)
            .put("role", role))
    }

    Scaffold(topBar = { TopAppBar(title = "DAuxiliary 授权管理", largeTitle = "DAuxiliary 授权管理") }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallTitle(text = "独立后台应用")
            itemCard {
                BasicComponent(title = "Worker 地址")
                BasicTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth().padding(16.dp))
                BasicComponent(title = "管理 Token（ADMIN_API_TOKEN）")
                BasicTextField(token, { token = it }, Modifier.fillMaxWidth().padding(16.dp))
                ArrowPreference(title = if (busy) "正在请求…" else "刷新授权统计", onClick = { if (!busy) runRequest { request("/admin/summary") } })
                ArrowPreference(title = "刷新角色列表", onClick = { if (!busy) runRequest { request("/admin/bot/roles") } })
            }
            if (message.isNotBlank()) BasicComponent(title = message)
            summary?.let { data ->
                SmallTitle(text = "授权统计")
                itemCard {
                    BasicComponent(title = "模块授权用户", summary = data.optString("module_authorized_count", "0"))
                    BasicComponent(title = "有效授权码", summary = data.optString("active_code_count", "0"))
                    BasicComponent(title = "Bot 管理员", summary = data.optString("bot_admin_count", "0"))
                    BasicComponent(title = "维护者", summary = data.optString("maintainer_count", "0"))
                    BasicComponent(title = "发码 / 兑换 / 撤销", summary = "${data.optString("issued_count", "0")} / ${data.optString("redeemed_count", "0")} / ${data.optString("revoked_count", "0")}")
                }
            }
            SmallTitle(text = "Bot 角色管理")
            itemCard {
                BasicComponent(title = "目标 Telegram ID")
                BasicTextField(targetId, { targetId = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().padding(16.dp))
                ArrowPreference(title = "当前角色：$role", summary = "点击切换维护者或 Bot 管理员", onClick = { role = if (role == "maintainer") "bot_admin" else "maintainer" })
                ArrowPreference(title = "授予 $role", onClick = { if (!busy && targetId.isNotBlank()) roleChange(true) })
                ArrowPreference(title = "撤销 $role", onClick = { if (!busy && targetId.isNotBlank()) roleChange(false) })
                roles?.let { data ->
                    BasicComponent(title = "维护者数量", summary = data.optJSONArray("maintainers")?.length()?.toString() ?: "0")
                    BasicComponent(title = "Bot 管理员数量", summary = data.optJSONArray("admins")?.length()?.toString() ?: "0")
                }
            }
            SmallTitle(text = "撤销 Telegram 模块授权")
            itemCard {
                BasicComponent(title = "目标 Telegram ID")
                BasicTextField(targetId, { targetId = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().padding(16.dp))
                ArrowPreference(title = "立即撤销授权", summary = "撤销凭据并清除未兑换授权码", onClick = { if (!busy && targetId.isNotBlank()) runRequest { request("/admin/revoke", "POST", JSONObject().put("telegram_id", targetId)) } })
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun itemCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) { content() }
}