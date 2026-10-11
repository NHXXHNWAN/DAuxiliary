package com.dauxiliary.admin

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
private const val ACCOUNTS_KEY = "accounts"
private const val ACTIVE_ACCOUNT_KEY = "active_account"

internal data class AdminAccount(val name: String, val endpoint: String, val token: String)

internal fun readAccounts(prefs: android.content.SharedPreferences): List<AdminAccount> {
    val json = runCatching { JSONObject(prefs.getString(ACCOUNTS_KEY, "{}") ?: "{}") }
        .getOrDefault(JSONObject())
    return json.keys().asSequence().mapNotNull { name ->
        json.optJSONObject(name)?.let { saved ->
            AdminAccount(
                name = name,
                endpoint = saved.optString("endpoint").trimEnd('/'),
                token = AdminSecretStore.decrypt(saved.optString("token")),
            )
        }
    }.toList()
}

internal fun persistAccounts(
    prefs: android.content.SharedPreferences,
    accounts: List<AdminAccount>,
    activeName: String?,
) {
    val json = JSONObject()
    accounts.forEach { account ->
        json.put(
            account.name,
            JSONObject()
                .put("endpoint", account.endpoint.trimEnd('/'))
                .put("token", AdminSecretStore.encrypt(account.token)),
        )
    }
    prefs.edit()
        .putString(ACCOUNTS_KEY, json.toString())
        .putString(ACTIVE_ACCOUNT_KEY, activeName.orEmpty())
        .remove("token")
        .apply()
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
    val initialAccounts = remember {
        val saved = readAccounts(prefs)
        if (saved.isNotEmpty()) saved else {
            // Migrate the legacy single-account format when the user next saves a connection.
            val endpoint = prefs.getString("endpoint", "").orEmpty()
            val token = prefs.getString("token", "").orEmpty()
            if (endpoint.isNotBlank() && token.isNotBlank()) {
                listOf(AdminAccount("默认账号", endpoint.trimEnd('/'), token))
            } else {
                emptyList()
            }
        }
    }
    val initialActive = remember(initialAccounts) {
        val name = prefs.getString(ACTIVE_ACCOUNT_KEY, "").orEmpty()
        initialAccounts.firstOrNull { it.name == name } ?: initialAccounts.firstOrNull()
    }
    var accounts by remember { mutableStateOf(initialAccounts) }
    var active by remember { mutableStateOf(initialActive) }
    var endpoint by rememberSaveable { mutableStateOf(initialActive?.endpoint.orEmpty()) }
    var token by rememberSaveable { mutableStateOf(initialActive?.token.orEmpty()) }
    var accountName by rememberSaveable { mutableStateOf(initialActive?.name.orEmpty()) }
    var tokenRevealed by rememberSaveable { mutableStateOf(false) }
    var showConfig by rememberSaveable { mutableStateOf(initialActive == null) }
    var connected by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf<JSONObject?>(null) }
    var roles by remember { mutableStateOf<JSONObject?>(null) }
    var roleTargetId by rememberSaveable { mutableStateOf("") }
    var revokeTargetId by rememberSaveable { mutableStateOf("") }
    var revokeConfirmation by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun request(
        baseEndpoint: String,
        bearerToken: String,
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val base = baseEndpoint.trim().trimEnd('/')
        require(base.startsWith("https://", ignoreCase = true)) { "Worker 地址必须以 https:// 开头" }
        require(bearerToken.isNotBlank()) { "请填写管理 Token" }
        val url = URL("$base$path")
        require(url.protocol.equals("https", ignoreCase = true)) { "仅允许 HTTPS 地址" }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${bearerToken.trim()}")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        try {
            val code = connection.responseCode
            if (code in 300..399) error("Worker 返回重定向；出于凭据安全考虑，已拒绝向重定向地址发送 Token")
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val result = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (code !in 200..299) {
                val detail = result.optString("error").ifBlank { raw.take(300) }
                error(detail.ifBlank { "请求失败（HTTP $code）" })
            }
            result
        } finally {
            connection.disconnect()
        }
    }

    suspend fun currentRequest(path: String, method: String = "GET", body: JSONObject? = null) =
        request(endpoint, token, path, method, body)

    fun refresh() {
        if (busy) return
        scope.launch {
            busy = true
            message = ""
            try {
                summary = currentRequest("/admin/summary")
                roles = runCatching { currentRequest("/admin/bot/roles") }.getOrNull()
                connected = true
                message = if (roles == null) "授权统计已更新；角色列表暂不可用" else "数据已更新"
            } catch (e: Exception) {
                connected = false
                message = e.message ?: "连接失败"
            } finally {
                busy = false
            }
        }
    }

    fun perform(path: String, body: JSONObject, successMessage: String) {
        if (busy) return
        val requestEndpoint = endpoint
        val requestToken = token
        scope.launch {
            busy = true
            message = ""
            try {
                request(requestEndpoint, requestToken, path, "POST", body)
                if (path == "/admin/revoke") revokeConfirmation = false
                summary = request(requestEndpoint, requestToken, "/admin/summary")
                roles = runCatching { request(requestEndpoint, requestToken, "/admin/bot/roles") }.getOrNull()
                connected = true
                message = successMessage
            } catch (e: Exception) {
                message = e.message ?: "请求失败"
            } finally {
                busy = false
            }
        }
    }

    fun switchAccount(account: AdminAccount) {
        if (busy || saving) return
        active = account
        accountName = account.name
        endpoint = account.endpoint
        token = account.token
        persistAccounts(prefs, accounts, account.name)
        summary = null
        roles = null
        connected = false
        message = ""
        revokeConfirmation = false
        showConfig = false
    }

    fun deleteAccount(account: AdminAccount) {
        val updated = accounts.filterNot { it.name == account.name }
        accounts = updated
        val newActiveName = if (active?.name == account.name) updated.firstOrNull()?.name else active?.name
        persistAccounts(prefs, updated, newActiveName)
        if (active?.name == account.name) {
            val next = updated.firstOrNull()
            active = next
            accountName = next?.name.orEmpty()
            endpoint = next?.endpoint.orEmpty()
            token = next?.token.orEmpty()
            summary = null
            roles = null
            connected = false
            message = ""
            showConfig = next == null
        }
    }

    fun verifyAndSave() {
        if (saving) return
        val candidateEndpoint = endpoint.trim().trimEnd('/')
        val candidateToken = token.trim()
        val name = accountName.trim().ifBlank { active?.name ?: "管理后台" }
        scope.launch {
            saving = true
            message = ""
            try {
                val verifiedSummary = request(candidateEndpoint, candidateToken, "/admin/summary")
                val account = AdminAccount(name, candidateEndpoint, candidateToken)
                val updated = accounts.filterNot { it.name == name } + account
                accounts = updated
                persistAccounts(prefs, updated, name)
                active = account
                accountName = name
                summary = verifiedSummary
                roles = runCatching { request(candidateEndpoint, candidateToken, "/admin/bot/roles") }.getOrNull()
                connected = true
                showConfig = false
                message = "已连接 · Token 已使用 Android Keystore 加密保存"
            } catch (e: Exception) {
                connected = false
                message = e.message ?: "连接失败，请检查地址和 Token"
            } finally {
                saving = false
            }
        }
    }

    LaunchedEffect(active?.name, showConfig) {
        if (active != null && !showConfig && summary == null && !connected && !busy) refresh()
    }

    if (showConfig) {
        ConnectionScreen(
            isEditing = active != null,
            canCancel = active != null,
            accountName = accountName,
            endpoint = endpoint,
            token = token,
            tokenRevealed = tokenRevealed,
            saving = saving,
            message = message,
            savedAccounts = accounts,
            activeAccountName = active?.name,
            onAccountNameChange = { accountName = it },
            onEndpointChange = { endpoint = it },
            onTokenChange = { token = it },
            onToggleTokenVisibility = { tokenRevealed = !tokenRevealed },
            onCancel = {
                tokenRevealed = false
                if (active != null) {
                    accountName = active?.name.orEmpty()
                    endpoint = active?.endpoint.orEmpty()
                    token = active?.token.orEmpty()
                    showConfig = false
                }
            },
            onSave = ::verifyAndSave,
            onSwitch = ::switchAccount,
            onDeleteAccount = ::deleteAccount,
        )
    } else {
        AdminDashboard(
            active = active,
            accounts = accounts,
            summary = summary,
            roles = roles,
            connected = connected,
            busy = busy,
            message = message,
            roleTargetId = roleTargetId,
            revokeTargetId = revokeTargetId,
            revokeConfirmation = revokeConfirmation,
            onRoleTargetIdChange = { roleTargetId = it.filter(Char::isDigit) },
            onRevokeTargetIdChange = { revokeTargetId = it.filter(Char::isDigit) },
            onUserSelect = { id ->
                roleTargetId = id
                message = "已选择 Telegram ID $id，用于 Bot 角色操作"
            },
            onEdit = {
                accountName = active?.name.orEmpty()
                endpoint = active?.endpoint.orEmpty()
                token = active?.token.orEmpty()
                tokenRevealed = false
                message = ""
                showConfig = true
            },
            onAdd = {
                accountName = ""
                endpoint = ""
                token = ""
                tokenRevealed = false
                message = ""
                showConfig = true
            },
            onSwitch = ::switchAccount,
            onRefresh = ::refresh,
            onRoleChange = { role, grant ->
                val id = roleTargetId
                if (id.isBlank() || !id.all(Char::isDigit)) {
                    message = "请输入有效的 Telegram 数字 ID"
                } else {
                    perform(
                        if (grant) "/admin/bot/role/grant" else "/admin/bot/role/revoke",
                        JSONObject().put("telegram_id", id).put("role", role),
                        if (grant) "角色已授予，并已刷新角色列表" else "角色已撤销，并已刷新角色列表",
                    )
                }
            },
            onAskRevoke = {
                if (revokeTargetId.isBlank() || !revokeTargetId.all(Char::isDigit)) {
                    message = "请输入有效的 Telegram 数字 ID"
                } else {
                    revokeConfirmation = true
                }
            },
            onCancelRevoke = { revokeConfirmation = false },
            onConfirmRevoke = {
                perform(
                    "/admin/revoke",
                    JSONObject().put("telegram_id", revokeTargetId),
                    "用户模块授权已撤销",
                )
            },
        )
    }
}
