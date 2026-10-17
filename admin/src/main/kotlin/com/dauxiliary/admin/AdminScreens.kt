package com.dauxiliary.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableIntStateOf
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
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Settings
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
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    "管理 Worker 使用 HTTPS 连接，凭据仅保存在本机。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                SmallTitle(text = "连接信息")
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
                item { SmallTitle(text = "已保存连接") }
                item {
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
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val scrollBehavior = MiuixScrollBehavior()
    val users = summary?.optJSONArray("users")?.let { array ->
        (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }
    val pageTitles = listOf("管理概览", "用户授权", "Bot 角色")
    val currentTab = selectedTab.coerceIn(0, 2)
    val navigationItems = remember {
        listOf(
            NavigationItem("总览", MiuixIcons.Home),
            NavigationItem("用户授权", MiuixIcons.Layers),
            NavigationItem("Bot 角色", MiuixIcons.Settings),
        )
    }
    val surfaceColor = MiuixTheme.colorScheme.surface
    val contentBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = pageTitles[currentTab],
                largeTitle = pageTitles[currentTab],
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            FloatingNavigationBar(defaultWindowInsetsPadding = false) {
                navigationItems.forEachIndexed { index, item ->
                    FloatingNavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = item.icon,
                        label = item.label,
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .layerBackdrop(contentBackdrop),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (message.isNotBlank()) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            BasicComponent(title = message)
                        }
                    }
                }

                when (currentTab) {
                    0 -> {
                        item {
                            SmallTitle(text = "当前连接")
                            Card(Modifier.fillMaxWidth()) {
                                Column {
                                    val connectionState = when {
                                        busy -> "正在同步"
                                        connected -> "服务在线"
                                        active != null -> "连接不可用"
                                        else -> "尚未配置"
                                    }
                                    BasicComponent(
                                        title = active?.name ?: "尚未配置管理连接",
                                        summary = "$connectionState · ${active?.endpoint?.substringAfter("://")?.substringBefore('/') ?: "添加 Worker 地址以开始管理"}",
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Button(
                                            onClick = onRefresh,
                                            enabled = active != null && !busy,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColorsPrimary(),
                                        ) { Text(if (busy) "同步中" else "刷新数据") }
                                        TextButton(
                                            text = "连接设置",
                                            onClick = onEdit,
                                            enabled = !busy,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            SmallTitle(text = "授权概览")
                            if (summary == null) {
                                Card(Modifier.fillMaxWidth()) {
                                    BasicComponent(
                                        title = if (busy) "正在加载授权数据" else "还没有授权数据",
                                        summary = if (active == null) "先配置管理连接" else "点击上方刷新数据以获取最新统计",
                                    )
                                }
                            } else {
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(vertical = 4.dp)) {
                                        Column(
                                            Modifier.fillMaxWidth().padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                MetricCard("模块授权", summary.optString("module_authorized_count", "0"), Modifier.weight(1f))
                                                MetricCard("有效授权码", summary.optString("active_code_count", "0"), Modifier.weight(1f))
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                MetricCard("Bot 管理员", summary.optString("bot_admin_count", "0"), Modifier.weight(1f))
                                                MetricCard("维护者", summary.optString("maintainer_count", "0"), Modifier.weight(1f))
                                            }
                                        }
                                        BasicComponent(
                                            title = "授权码统计",
                                            summary = "已发 ${summary.optString("issued_count", "0")}  ·  已兑换 ${summary.optString("redeemed_count", "0")}  ·  已撤销 ${summary.optString("revoked_count", "0")}",
                                        )
                                    }
                                }
                                summary.optString("generated_at").takeIf { it.isNotBlank() }?.let { generatedAt ->
                                    Text(
                                        text = "更新于 ${formatTimestamp(generatedAt)}",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                }
                            }
                        }
                        item {
                            SmallTitle(text = "连接列表")
                            Card(Modifier.fillMaxWidth()) {
                                Column {
                                    accounts.filter { it.name != active?.name }.forEach { account ->
                                        ArrowPreference(
                                            title = account.name,
                                            summary = account.endpoint,
                                            onClick = { if (!busy) onSwitch(account) },
                                        )
                                    }
                                    ArrowPreference(
                                        title = "添加连接",
                                        summary = "添加另一个 Worker",
                                        onClick = onAdd,
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        item {
                            SmallTitle(text = "已授权用户")
                            TextField(
                                value = userFilter,
                                onValueChange = { userFilter = it.filter(Char::isDigit) },
                                label = "搜索 Telegram ID",
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            Text(
                                text = if (users == null) "尚未加载" else "${users.count { it.optString("telegram_id").contains(userFilter) }} / ${users.size} 人",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            )
                        }
                        if (users == null) {
                            item {
                                Card(Modifier.fillMaxWidth()) {
                                    BasicComponent(
                                        title = if (busy) "正在加载用户" else "暂无用户数据",
                                        summary = if (connected) "刷新后重试" else "请先连接管理 Worker",
                                    )
                                }
                            }
                        } else {
                            val matchingUsers = users.filter { it.optString("telegram_id").contains(userFilter) }
                            if (matchingUsers.isEmpty()) {
                                item {
                                    Card(Modifier.fillMaxWidth()) {
                                        BasicComponent(title = if (users.isEmpty()) "暂无已授权用户" else "没有匹配的用户")
                                    }
                                }
                            } else {
                                item {
                                    Card(Modifier.fillMaxWidth()) {
                                        Column {
                                            matchingUsers.forEach { user ->
                                                val id = user.optString("telegram_id")
                                                val selected = id == revokeTargetId
                                                val method = user.optString("method").takeIf { it.isNotBlank() && it != "unknown" }
                                                val detail = buildString {
                                                    append("授权于 ${formatTimestamp(user.optString("granted_at"))}")
                                                    method?.let { append(" · $it") }
                                                }
                                                BasicComponent(
                                                    title = "Telegram ID  $id${if (selected) " · 模块操作对象" else ""}",
                                                    summary = detail,
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.End,
                                                ) {
                                                    TextButton(
                                                        text = "Bot 角色",
                                                        enabled = !busy,
                                                        onClick = {
                                                            onUserSelect(id)
                                                            selectedTab = 2
                                                        },
                                                    )
                                                    TextButton(
                                                        text = "撤销授权",
                                                        enabled = !busy,
                                                        onClick = {
                                                            onRevokeTargetIdChange(id)
                                                            onAskRevoke()
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        summary?.optString("generated_at")?.takeIf { it.isNotBlank() }?.let { generatedAt ->
                            item {
                                Text(
                                    text = "数据更新于 ${formatTimestamp(generatedAt)}",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }

                    2 -> {
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
                        }
                        item {
                            SmallTitle(text = "调整角色")
                            Card(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    TextField(
                                        value = roleTargetId,
                                        onValueChange = { onRoleTargetIdChange(it.filter(Char::isDigit)) },
                                        label = "Telegram 用户 ID",
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    )
                                    if (roleTargetId.isBlank()) {
                                        Text(
                                            text = "从用户列表点选 ID，或在此输入数字 ID。",
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                    RoleActionRow(
                                        title = "维护者",
                                        telegramId = roleTargetId,
                                        values = roles?.optJSONArray("maintainers"),
                                        busy = busy,
                                        onGrant = { pendingRoleAction = "maintainer:grant" },
                                        onRevoke = { pendingRoleAction = "maintainer:revoke" },
                                    )
                                    RoleActionRow(
                                        title = "Bot 管理员",
                                        telegramId = roleTargetId,
                                        values = roles?.optJSONArray("admins"),
                                        busy = busy,
                                        onGrant = { pendingRoleAction = "bot_admin:grant" },
                                        onRevoke = { pendingRoleAction = "bot_admin:revoke" },
                                    )
                                }
                            }
                            Text(
                                text = "Bot 角色与 DAuxiliary 模块授权互相独立。",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }

                }
            }
        }
    }

    if (revokeConfirmation) {
        ConfirmDialog(
            title = "撤销模块授权？",
            message = "Telegram ID $revokeTargetId 的模块凭据将失效，未兑换授权码会被清除。Bot 角色不受影响。",
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
        val targetId = roleTargetId
        val roleTitle = if (role == "maintainer") "维护者" else "Bot 管理员"
        ConfirmDialog(
            title = if (grant) "授予$roleTitle？" else "移除$roleTitle？",
            message = "${if (grant) "授予" else "移除"} Telegram ID $targetId 的$roleTitle？",
            confirmLabel = if (grant) "确认授予" else "确认移除",
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
private fun RoleActionRow(
    title: String,
    telegramId: String,
    values: JSONArray?,
    busy: Boolean,
    onGrant: () -> Unit,
    onRevoke: () -> Unit,
) {
    val assigned = values?.let { array ->
        (0 until array.length()).any { array.optJSONObject(it)?.optString("telegram_id") == telegramId }
    } ?: false
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicComponent(
            title = title,
            summary = when {
                telegramId.isBlank() -> "先选择用户 ID"
                values == null -> "角色列表尚未加载"
                assigned -> "该用户已有此角色"
                else -> "该用户未设置此角色"
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onGrant,
                enabled = !busy && telegramId.isNotBlank() && values != null && !assigned,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("授予") }
            Button(
                onClick = onRevoke,
                enabled = !busy && telegramId.isNotBlank() && values != null && assigned,
                modifier = Modifier.weight(1f),
            ) { Text("移除") }
        }
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
                summary = "授予 ${formatTimestamp(record.optString("granted_at"))} · ${record.optString("granted_by").ifBlank { "未知" }} · 点按后转到 Bot 角色页",
                onClick = {
                    onUserSelect(id)
                },
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
    Card(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
        Text(
            text = value,
            style = MiuixTheme.textStyles.title1,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        }
    }
}
private fun formatTimestamp(value: String): String = when {
    value.isBlank() || value == "null" -> "暂无"
    else -> value.replace("T", " ").removeSuffix(".000Z").removeSuffix("Z")
}
