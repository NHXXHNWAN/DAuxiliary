package com.dauxiliary.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
internal fun ConnectionScreen(
    isEditing: Boolean,
    canCancel: Boolean,
    accountName: String,
    endpoint: String,
    token: String,
    tokenRevealed: Boolean,
    saving: Boolean,
    message: String,
    savedAccounts: List<AdminAccount>,
    activeAccountName: String?,
    onAccountNameChange: (String) -> Unit,
    onEndpointChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onToggleTokenVisibility: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onSwitch: (AdminAccount) -> Unit,
    onDeleteAccount: (AdminAccount) -> Unit,
) {
    var deleteCandidate by remember { mutableStateOf<AdminAccount?>(null) }

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (isEditing) "连接设置" else "连接管理",
                largeTitle = if (isEditing) "连接设置" else "连接管理",
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SmallTitle(text = "连接")
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextField(
                            value = accountName,
                            onValueChange = onAccountNameChange,
                            label = "连接名称",
                            singleLine = true,
                        )
                        TextField(
                            value = endpoint,
                            onValueChange = onEndpointChange,
                            label = "Worker 地址（HTTPS）",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        TextField(
                            value = token,
                            onValueChange = onTokenChange,
                            label = "管理 Token",
                            singleLine = true,
                            visualTransformation = if (tokenRevealed) VisualTransformation.None else PasswordVisualTransformation(),
                        )
                        TextButton(
                            text = if (tokenRevealed) "隐藏" else "显示",
                            onClick = onToggleTokenVisibility,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (message.isNotBlank()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        BasicComponent(title = message)
                    }
                }
            }
            item {
                Button(
                    onClick = onSave,
                    enabled = !saving && endpoint.isNotBlank() && token.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(if (saving) "验证中" else "保存")
                }
                if (canCancel) {
                    TextButton(
                        text = "取消",
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (savedAccounts.isNotEmpty()) {
                item {
                    SmallTitle(text = "已保存")
                    Card(Modifier.fillMaxWidth()) {
                        Column {
                            savedAccounts.forEach { account ->
                                val isActive = account.name == activeAccountName
                                BasicComponent(
                                    title = account.name + if (isActive) " · 当前" else "",
                                    summary = account.endpoint,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    if (!isActive) {
                                        TextButton(
                                            text = "切换",
                                            onClick = { onSwitch(account) },
                                        )
                                    }
                                    TextButton(
                                        text = "移除",
                                        onClick = { deleteCandidate = account },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteCandidate?.let { account ->
        ConfirmDialog(
            title = "移除连接？",
            message = "将删除本机连接及 Token。",
            confirmLabel = "移除连接",
            destructive = true,
            onDismiss = { deleteCandidate = null },
            onConfirm = {
                deleteCandidate = null
                onDeleteAccount(account)
            },
        )
    }
}

@Composable
internal fun AdminDashboard(
    active: AdminAccount?,
    accounts: List<AdminAccount>,
    summary: JSONObject?,
    roles: JSONObject?,
    connected: Boolean,
    busy: Boolean,
    message: String,
    roleTargetId: String,
    revokeTargetId: String,
    revokeConfirmation: Boolean,
    onRoleTargetIdChange: (String) -> Unit,
    onRevokeTargetIdChange: (String) -> Unit,
    onUserSelect: (String) -> Unit,
    onEdit: () -> Unit,
    onAdd: () -> Unit,
    onSwitch: (AdminAccount) -> Unit,
    onRefresh: () -> Unit,
    onRoleChange: (role: String, grant: Boolean) -> Unit,
    onAskRevoke: () -> Unit,
    onCancelRevoke: () -> Unit,
    onConfirmRevoke: () -> Unit,
) {
    var userFilter by rememberSaveable { mutableStateOf("") }
    var pendingRoleAction by remember { mutableStateOf<String?>(null) }
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "授权管理",
                largeTitle = "授权管理",
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SmallTitle(text = "连接")
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        BasicComponent(
                            title = active?.name ?: "未配置",
                            summary = if (busy) "处理中" else active?.endpoint.orEmpty(),
                        )
                        if (accounts.size > 1) {
                            accounts.filter { it.name != active?.name }.forEach { account ->
                                ArrowPreference(
                                    title = "切换到 ${account.name}",
                                    onClick = { onSwitch(account) },
                                )
                            }
                        }
                        ArrowPreference(
                            title = if (active == null) "配置连接" else "编辑连接",
                            summary = "地址与 Token",
                            onClick = onEdit,
                        )
                        ArrowPreference(
                            title = "添加连接",
                            summary = "新增 Worker",
                            onClick = onAdd,
                        )
                        ArrowPreference(
                            title = if (busy) "处理中" else "刷新",
                            summary = "更新数据",
                            onClick = { if (!busy) onRefresh() },
                        )
                    }
                }
            }

            if (message.isNotBlank()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        BasicComponent(title = message)
                    }
                }
            }

            item {
                SmallTitle(text = "授权概览")
                if (summary == null) {
                    Card(Modifier.fillMaxWidth()) {
                        if (busy) {
                            BasicComponent(title = "加载中")
                        } else {
                            BasicComponent(
                                title = "暂无数据",
                                summary = if (connected) "点击刷新" else "先配置连接",
                            )
                        }
                    }

                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("模块授权", summary.optString("module_authorized_count", "0"), Modifier.weight(1f))
                            MetricCard("有效授权码", summary.optString("active_code_count", "0"), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("Bot 管理员", summary.optString("bot_admin_count", "0"), Modifier.weight(1f))
                            MetricCard("维护者", summary.optString("maintainer_count", "0"), Modifier.weight(1f))
                        }
                        Card(Modifier.fillMaxWidth()) {
                            BasicComponent(
                                title = "授权码",
                                summary = "发 ${summary.optString("issued_count", "0")} · 换 ${summary.optString("redeemed_count", "0")} · 撤 ${summary.optString("revoked_count", "0")}",
                            )
                        }
                    }
                }
            }

            item {
                SmallTitle(text = "已授权用户")
                val users = summary?.optJSONArray("users")
                if (users == null) {
                    Card(Modifier.fillMaxWidth()) {
                        BasicComponent(title = "未加载")
                    }
                } else if (users.length() == 0) {
                    Card(Modifier.fillMaxWidth()) {
                        BasicComponent(title = "暂无用户")
                    }
                } else {
                    Card(Modifier.fillMaxWidth()) {
                        Column {
                            TextField(
                                value = userFilter,
                                onValueChange = { userFilter = it.filter(Char::isDigit) },
                                label = "Telegram ID",
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            val matchingUsers = (0 until users.length())
                                .mapNotNull { users.optJSONObject(it) }
                                .filter { it.optString("telegram_id").contains(userFilter) }
                            Text(
                                text = "${matchingUsers.size}/${users.length()}",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            if (matchingUsers.isEmpty()) {
                                BasicComponent(title = "无匹配用户")
                            }
                            matchingUsers.forEach { user ->
                                val id = user.optString("telegram_id")
                                val isSelected = id == roleTargetId || id == revokeTargetId
                                val method = user.optString("method").takeIf { it.isNotBlank() && it != "unknown" }
                                val detail = buildString {
                                    append("授权 ${formatTimestamp(user.optString("granted_at"))}")
                                    append(" · 校验 ${formatTimestamp(user.optString("last_verified_at"))}")
                                    if (method != null) append(" · $method")
                                    if (isSelected) append(" · 已选")
                                }
                                BasicComponent(
                                    title = "Telegram ID $id",
                                    summary = detail,
                                    onClick = { onUserSelect(id) },
                                )
                            }
                        }
                    }
                }
                summary?.optString("generated_at")?.takeIf { it.isNotBlank() }?.let { generatedAt ->
                    Text(
                        text = formatTimestamp(generatedAt),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }
            }

            item {
                SmallTitle(text = "Bot 角色")
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        RoleList(
                            title = "维护者",
                            values = roles?.optJSONArray("maintainers"),
                            onUserSelect = onUserSelect,
                        )
                        RoleList(
                            title = "Bot 管理员",
                            values = roles?.optJSONArray("admins"),
                            onUserSelect = onUserSelect,
                        )

                    }
                }
                Text(
                    text = "角色记录可点选 ID · 与模块授权独立",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }

            item {
                SmallTitle(text = "Bot 角色管理")
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextField(
                            value = roleTargetId,
                            onValueChange = { onRoleTargetIdChange(it.filter(Char::isDigit)) },
                            label = "ID",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { pendingRoleAction = "maintainer:grant" },
                                enabled = !busy && roleTargetId.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) { Text("设为维护者") }
                            Button(
                                onClick = { pendingRoleAction = "maintainer:revoke" },
                                enabled = !busy && roleTargetId.isNotBlank(),
                                modifier = Modifier.weight(1f),
                            ) { Text("移除维护者") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { pendingRoleAction = "bot_admin:grant" },
                                enabled = !busy && roleTargetId.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) { Text("设为 Bot 管理员") }
                            Button(
                                onClick = { pendingRoleAction = "bot_admin:revoke" },
                                enabled = !busy && roleTargetId.isNotBlank(),
                                modifier = Modifier.weight(1f),
                            ) { Text("移除管理员") }
                        }
                    }
                }
            }

            item {
                SmallTitle(text = "模块授权")
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("撤销后凭据失效，未兑换授权码清除。")
                        TextField(
                            value = revokeTargetId,
                            onValueChange = { onRevokeTargetIdChange(it.filter(Char::isDigit)) },
                            label = "ID",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Button(
                            onClick = onAskRevoke,
                            enabled = !busy && revokeTargetId.isNotBlank() && !revokeConfirmation,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (busy) "处理中…" else "撤销模块授权…") }
                    }
                }
            }
            item {
                Text(
                    text = "HTTPS · Bearer Token",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    if (revokeConfirmation) {
        ConfirmDialog(
            title = "撤销模块授权？",
            message = "撤销 $revokeTargetId 的模块授权？",
            confirmLabel = "确认撤销",
            destructive = true,
            onDismiss = onCancelRevoke,
            onConfirm = onConfirmRevoke,
        )
    }

    pendingRoleAction?.let { action ->
        val parts = action.split(':', limit = 2)
        val role = parts.getOrNull(0).orEmpty()
        val grant = parts.getOrNull(1) == "grant"
        val roleTitle = if (role == "maintainer") "维护者" else "Bot 管理员"
        ConfirmDialog(
            title = if (grant) "授予$roleTitle？" else "撤销$roleTitle？",
            message = "${if (grant) "授予" else "撤销"} $roleTargetId 的$roleTitle？",
            confirmLabel = if (grant) "确认授予" else "确认撤销",
            destructive = !grant,
            onDismiss = { pendingRoleAction = null },
            onConfirm = {
                pendingRoleAction = null
                onRoleChange(role, grant)
            },
        )
    }
}

@Composable
private fun RoleList(
    title: String,
    values: JSONArray?,
    onUserSelect: (String) -> Unit,
) {
    val records = values?.let { array ->
        (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }.orEmpty()
    BasicComponent(
        title = title,
        summary = if (values == null) "未加载" else "${records.size} 个",
    )
    if (values != null && records.isEmpty()) {

        Text(
            text = "暂无$title",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        )
    } else {
        records.forEach { record ->
            val id = record.optString("telegram_id")
            BasicComponent(
                title = "Telegram ID $id",
                summary = "授予 ${formatTimestamp(record.optString("granted_at"))} · ${record.optString("granted_by").ifBlank { "未知" }}",
                onClick = { onUserSelect(id) },
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = title,
        summary = message,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = if (destructive) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary(),
            ) { Text(confirmLabel) }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(value, style = MiuixTheme.textStyles.title2)
        }
    }
}

private fun formatTimestamp(value: String): String = when {
    value.isBlank() || value == "null" -> "暂无"
    else -> value.replace("T", " ").removeSuffix(".000Z").removeSuffix("Z")
}
