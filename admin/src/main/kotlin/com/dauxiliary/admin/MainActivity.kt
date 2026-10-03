package com.dauxiliary.admin

import android.content.Context
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val PREFS = "admin_connection"
private data class AdminAccount(val name: String, val endpoint: String, val token: String)

private fun readAccounts(prefs: android.content.SharedPreferences): List<AdminAccount> {
    val json = runCatching { JSONObject(prefs.getString("accounts", "{}") ?: "{}") }.getOrDefault(JSONObject())
    return json.keys().asSequence().mapNotNull { name -> json.optJSONObject(name)?.let { AdminAccount(name, it.optString("endpoint"), it.optString("token")) } }.toList()
}

private fun saveAccount(prefs: android.content.SharedPreferences, account: AdminAccount) {
    val json = runCatching { JSONObject(prefs.getString("accounts", "{}") ?: "{}") }.getOrDefault(JSONObject())
    json.put(account.name, JSONObject().put("endpoint", account.endpoint).put("token", account.token))
    prefs.edit().putString("accounts", json.toString()).putString("active_account", account.name).apply()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MiuixTheme { AdminApp() } }
    }
}

@Composable
private fun AdminApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val storedAccounts = remember { readAccounts(prefs) }
    val legacyEndpoint = prefs.getString("endpoint", "").orEmpty()
    val legacyToken = prefs.getString("token", "").orEmpty()
    val initialAccounts = remember { if (storedAccounts.isNotEmpty()) storedAccounts else if (legacyEndpoint.isNotBlank() && legacyToken.isNotBlank()) listOf(AdminAccount("默认账号", legacyEndpoint, legacyToken)) else emptyList() }
    var accounts by remember { mutableStateOf(initialAccounts) }
    val activeName = prefs.getString("active_account", accounts.firstOrNull()?.name ?: "").orEmpty()
    var active by remember { mutableStateOf(accounts.firstOrNull { it.name == activeName } ?: accounts.firstOrNull()) }
    var endpoint by rememberSaveable { mutableStateOf(active?.endpoint.orEmpty()) }
    var token by rememberSaveable { mutableStateOf(active?.token.orEmpty()) }
    var accountName by rememberSaveable { mutableStateOf("") }
    var connected by rememberSaveable { mutableStateOf(false) }
    var showConfig by rememberSaveable { mutableStateOf(active == null) }
    var testing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf<JSONObject?>(null) }
    var targetId by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val base = endpoint.trim().trimEnd('/')
        require(base.startsWith("https://")) { "Worker 地址必须以 https:// 开头" }
        require(token.isNotBlank()) { "请填写管理 Token" }
        val connection = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 10_000; readTimeout = 10_000
            setRequestProperty("Accept", "application/json"); setRequestProperty("Authorization", "Bearer ${token.trim()}")
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json"); outputStream.use { it.write(body.toString().toByteArray()) } }
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val result = stream?.bufferedReader()?.use { JSONObject(it.readText()) } ?: JSONObject()
            if (code !in 200..299) error(result.optString("error", "连接失败（HTTP $code）"))
            result
        } finally { connection.disconnect() }
    }

    fun run(block: suspend () -> JSONObject) {
        scope.launch {
            busy = true; message = ""
            try { val result = block(); message = "操作成功"; connected = true; if (result.has("module_authorized_count")) summary = result }
            catch (e: Exception) { connected = false; message = e.message ?: "请求失败" }
            finally { busy = false }
        }
    }

    fun saveAndTest() {
        scope.launch {
            testing = true; message = ""
            try {
                summary = request("/admin/summary")
                val resolvedName = accountName.trim().ifBlank { active?.name ?: "默认账号" }
                val account = AdminAccount(resolvedName, endpoint.trim().trimEnd('/'), token.trim())
                saveAccount(prefs, account)
                accounts = (accounts.filterNot { it.name == resolvedName } + account)
                active = account; accountName = resolvedName
                connected = true; showConfig = false; message = "连接成功，账号已保存"
            } catch (e: Exception) { connected = false; message = e.message ?: "连接失败" }
            finally { testing = false }
        }
    }

    if (showConfig) ConnectionDialog(endpoint, token, accountName, accounts.map { it.name }, { endpoint = it }, { token = it }, { accountName = it }, testing, message, ::saveAndTest)

    Scaffold(topBar = { TopAppBar(title = "授权管理", largeTitle = "授权管理") }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallTitle(text = "当前账号")
            Card(Modifier.fillMaxWidth()) {
                BasicComponent(title = active?.name ?: "未连接", summary = if (connected) "已连接 · ${active?.endpoint}" else "已保存的连接配置可在此切换", onClick = { showConfig = true })
                accounts.filter { it != active }.forEach { account -> ArrowPreference(title = "切换到 ${account.name}", summary = account.endpoint, onClick = {
                    endpoint = account.endpoint; token = account.token; active = account; accountName = account.name
                    scope.launch { testing = true; try { summary = request("/admin/summary"); connected = true; message = "已切换到 ${account.name}" } catch (e: Exception) { connected = false; message = e.message ?: "连接失败" } finally { testing = false } }
                }) }
                ArrowPreference(title = "添加账号", summary = "新增 Worker 连接，不会移除已有账号", onClick = { endpoint = ""; token = ""; accountName = ""; message = ""; showConfig = true })
                if (active != null) ArrowPreference(title = "编辑当前账号", summary = "更新此账号的连接配置", onClick = { endpoint = active!!.endpoint; token = active!!.token; accountName = active!!.name; message = ""; showConfig = true })
                ArrowPreference(title = if (busy || testing) "正在刷新…" else "刷新数据", summary = "重新读取授权统计", onClick = { if (!busy && !testing) run { request("/admin/summary") } })
            }
            if (message.isNotBlank()) BasicComponent(title = message)
            SmallTitle(text = "授权概览")
            Card(Modifier.fillMaxWidth()) {
                BasicComponent(title = "模块授权用户", summary = summary?.optString("module_authorized_count", "—") ?: "—")
                BasicComponent(title = "有效授权码", summary = summary?.optString("active_code_count", "—") ?: "—")
                BasicComponent(title = "Bot 管理员", summary = summary?.optString("bot_admin_count", "—") ?: "—")
                BasicComponent(title = "维护者", summary = summary?.optString("maintainer_count", "—") ?: "—")
                BasicComponent(title = "发码 / 兑换 / 撤销", summary = summary?.let { "${it.optString("issued_count", "0")} / ${it.optString("redeemed_count", "0")} / ${it.optString("revoked_count", "0")}" } ?: "—")
            }
            SmallTitle(text = "权限操作")
            Card(Modifier.fillMaxWidth()) {
                BasicComponent(title = "目标 Telegram ID", summary = "只填写数字 ID")
                BasicTextField(targetId, { targetId = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().padding(16.dp))
                listOf("maintainer" to "维护者", "bot_admin" to "Bot 管理员").forEach { (role, label) ->
                    ArrowPreference(title = "授予$label", onClick = { if (targetId.isNotBlank()) run { request("/admin/bot/role/grant", "POST", JSONObject().put("telegram_id", targetId).put("role", role)) } })
                    ArrowPreference(title = "撤销$label", onClick = { if (targetId.isNotBlank()) run { request("/admin/bot/role/revoke", "POST", JSONObject().put("telegram_id", targetId).put("role", role)) } })
                }
            }
            SmallTitle(text = "模块授权管理")
            Card(Modifier.fillMaxWidth()) { BasicComponent(title = "撤销指定用户授权", summary = "清除该用户的授权状态和未兑换授权码", onClick = { if (targetId.isNotBlank()) run { request("/admin/revoke", "POST", JSONObject().put("telegram_id", targetId)) } }) }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ConnectionDialog(endpoint: String, token: String, accountName: String, existingNames: List<String>, onEndpoint: (String) -> Unit, onToken: (String) -> Unit, onName: (String) -> Unit, testing: Boolean, message: String, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = { }) {
        Card(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (accountName.isBlank()) "连接管理后台" else "账号连接配置")
                Text("连接 Cloudflare Worker，验证成功后进入管理主页。账号配置会保存在本机，之后无需退出登录。", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                SmallTitle(text = "账号名称")
                BasicTextField(accountName, onName, Modifier.fillMaxWidth().padding(12.dp))
                if (accountName.isBlank()) BasicComponent(title = "例如：正式环境、测试环境")
                if (accountName.isNotBlank() && accountName in existingNames) BasicComponent(title = "同名配置将更新已有账号")
                SmallTitle(text = "Worker 配置")
                BasicComponent(title = "Worker 地址", summary = "例如：https://your-worker.example.workers.dev")
                BasicTextField(endpoint, onEndpoint, Modifier.fillMaxWidth().padding(12.dp))
                BasicComponent(title = "管理 Token", summary = "对应 Cloudflare Production 中的 ADMIN_API_TOKEN")
                BasicTextField(token, onToken, Modifier.fillMaxWidth().padding(12.dp), visualTransformation = PasswordVisualTransformation())
                if (message.isNotBlank()) BasicComponent(title = message, summary = "请检查账号名称、HTTPS 地址和 Token")
                ArrowPreference(title = if (testing) "正在验证…" else "验证并保存账号", summary = "验证成功后进入管理主页", onClick = { if (!testing) onConfirm() })
            }
        }
    }
}