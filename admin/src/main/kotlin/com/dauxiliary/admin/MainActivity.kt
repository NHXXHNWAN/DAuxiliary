package com.dauxiliary.admin

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

private const val PREFS = "admin_connection"

internal data class AdminAccount(val name: String, val endpoint: String, val token: String)

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
        setContent {
            val themeController = remember { ThemeController(ColorSchemeMode.MonetSystem) }
            MiuixTheme(controller = themeController) { AdminApp() }
        }
    }
}

@Composable
private fun AdminApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val initialAccounts = remember { readAccounts(prefs).ifEmpty {
        val endpoint = prefs.getString("endpoint", "").orEmpty()
        val token = prefs.getString("token", "").orEmpty()
        if (endpoint.isNotBlank() && token.isNotBlank()) listOf(AdminAccount("默认账号", endpoint, token)) else emptyList()
    } }
    var accounts by remember { mutableStateOf(initialAccounts) }
    val activeName = prefs.getString("active_account", "").orEmpty()
    var active by remember { mutableStateOf(initialAccounts.firstOrNull { it.name == activeName } ?: initialAccounts.firstOrNull()) }
    var endpoint by rememberSaveable { mutableStateOf(active?.endpoint.orEmpty()) }
    var token by rememberSaveable { mutableStateOf(active?.token.orEmpty()) }
    var accountName by rememberSaveable { mutableStateOf(active?.name.orEmpty()) }
    var showConfig by rememberSaveable { mutableStateOf(active == null) }
    var connected by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf<JSONObject?>(null) }
    var targetId by rememberSaveable { mutableStateOf("") }
    var revokeConfirmation by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val base = endpoint.trim().trimEnd('/')
        require(base.startsWith("https://", ignoreCase = true)) { "Worker 地址必须以 https:// 开头" }
        require(token.isNotBlank()) { "请填写管理 Token" }
        val connection = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 10_000; readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${token.trim()}")
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json; charset=utf-8"); outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) } }
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val result = stream?.bufferedReader()?.use { JSONObject(it.readText()) } ?: JSONObject()
            if (code !in 200..299) error(result.optString("error").ifBlank { "请求失败（HTTP $code）" })
            result
        } finally { connection.disconnect() }
    }

    fun refresh() {
        if (busy) return
        scope.launch {
            busy = true; message = ""
            try { summary = request("/admin/summary"); connected = true; message = "数据已更新" }
            catch (e: Exception) { connected = false; message = e.message ?: "连接失败" }
            finally { busy = false }
        }
    }

    fun perform(path: String, method: String, body: JSONObject, successMessage: String) {
        if (busy) return
        scope.launch {
            busy = true; message = ""
            try { val result = request(path, method, body); if (result.has("module_authorized_count")) summary = result; message = successMessage; connected = true; if (path == "/admin/revoke") revokeConfirmation = false }
            catch (e: Exception) { connected = false; message = e.message ?: "请求失败" }
            finally { busy = false }
        }
    }

    fun verifyAndSave() {
        if (saving) return
        scope.launch {
            saving = true; message = ""
            try {
                val result = request("/admin/summary")
                val name = accountName.trim().ifBlank { active?.name ?: "管理后台" }
                val account = AdminAccount(name, endpoint.trim().trimEnd('/'), token.trim())
                saveAccount(prefs, account); accounts = accounts.filterNot { it.name == name } + account
                active = account; accountName = name; summary = result; connected = true; showConfig = false; message = "已连接 · 配置保存在本机"
            } catch (e: Exception) { connected = false; message = e.message ?: "连接失败，请检查地址和 Token" }
            finally { saving = false }
        }
    }

    LaunchedEffect(active?.name, showConfig) { if (active != null && !showConfig && summary == null && !connected && !busy) refresh() }
    if (showConfig) {
        ConnectionScreen(isEditing = active != null, canCancel = active != null, accountName = accountName, endpoint = endpoint, token = token, saving = saving, message = message, onAccountNameChange = { accountName = it }, onEndpointChange = { endpoint = it }, onTokenChange = { token = it }, onCancel = { showConfig = false }, onSave = ::verifyAndSave)
    } else {
        AdminDashboard(active = active, accounts = accounts, summary = summary, connected = connected, busy = busy, message = message, targetId = targetId, revokeConfirmation = revokeConfirmation, onTargetIdChange = { targetId = it.filter(Char::isDigit) }, onEdit = { accountName = active?.name.orEmpty(); endpoint = active?.endpoint.orEmpty(); token = active?.token.orEmpty(); message = ""; showConfig = true }, onAdd = { accountName = ""; endpoint = ""; token = ""; message = ""; showConfig = true }, onSwitch = { account -> active = account; accountName = account.name; endpoint = account.endpoint; token = account.token; prefs.edit().putString("active_account", account.name).apply(); summary = null; connected = false; message = "" }, onRefresh = ::refresh, onRoleChange = { role, grant -> if (targetId.isBlank()) message = "请先填写 Telegram 数字 ID" else perform(if (grant) "/admin/bot/role/grant" else "/admin/bot/role/revoke", "POST", JSONObject().put("telegram_id", targetId).put("role", role), if (grant) "角色已授予" else "角色已撤销") }, onAskRevoke = { if (targetId.isBlank()) message = "请先填写 Telegram 数字 ID" else revokeConfirmation = true }, onCancelRevoke = { revokeConfirmation = false }, onConfirmRevoke = { perform("/admin/revoke", "POST", JSONObject().put("telegram_id", targetId), "用户授权已撤销") })
    }
}
