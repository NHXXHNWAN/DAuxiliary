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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ConnectionScreen(
    isEditing: Boolean,
    canCancel: Boolean,
    accountName: String,
    endpoint: String,
    token: String,
    saving: Boolean,
    message: String,
    onAccountNameChange: (String) -> Unit,
    onEndpointChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = if (isEditing) "连接设置" else "授权管理",
                largeTitle = if (isEditing) "连接设置" else "授权管理",
                navigationIcon = {
                    if (canCancel) {
                        IconButton(onClick = onCancel) {
                            Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (isEditing) "管理连接" else "连接管理后台",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "使用 Cloudflare Worker 管理授权与 Bot 角色。连接信息保存在本机应用私有存储中。",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            item {
                SmallTitle(text = "账号")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        TextField(
                            value = accountName,
                            onValueChange = onAccountNameChange,
                            label = "账号名称，例如：正式环境",
                            singleLine = true,
                        )
                    }
                }
            }
            item {
                SmallTitle(text = "Worker 配置")
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextField(
                            value = endpoint,
                            onValueChange = onEndpointChange,
                            label = "Worker HTTPS 地址",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        Text(
                            text = "示例：https://your-worker.example.workers.dev",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        TextField(
                            value = token,
                            onValueChange = onTokenChange,
                            label = "管理 Token（ADMIN_API_TOKEN）",
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
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
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Button(
                        onClick = onSave,
                        enabled = !saving && endpoint.isNotBlank() && token.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(if (saving) "正在验证…" else "验证并保存")
                    }
                    if (canCancel) {
                        TextButton(
                            text = "取消",
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AdminDashboard(
    active: AdminAccount?,
    accounts: List<AdminAccount>,
    summary: JSONObject?,
    connected: Boolean,
    busy: Boolean,
    message: String,
    roleTargetId: String,
    revokeTargetId: String,
    revokeConfirmation: Boolean,
    onRoleTargetIdChange: (String) -> Unit,
    onRevokeTargetIdChange: (String) -> Unit,
    onEdit: () -> Unit,
    onAdd: () -> Unit,
    onSwitch: (AdminAccount) -> Unit,
    onRefresh: () -> Unit,
    onRoleChange: (role: String, grant: Boolean) -> Unit,
    onAskRevoke: () -> Unit,
    onCancelRevoke: () -> Unit,
    onConfirmRevoke: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = "授权管理", largeTitle = "授权管理") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SmallTitle(text = "连接")
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        BasicComponent(
                            title = active?.name ?: "尚未配置后台",
                            summary = when {
                                connected -> "已连接 · ${active?.endpoint.orEmpty()}"
                                busy -> "正在读取后台数据…"
                                else -> active?.endpoint ?: "添加 Cloudflare Worker 连接"
                            },
                        )
                        if (accounts.size > 1) {
                            accounts.filter { it.name != active?.name }.forEach { account ->
                                ArrowPreference(
                                    title = "切换到 ${account.name}",
                                    summary = account.endpoint,
                                    onClick = { onSwitch(account) },
                                )
                            }
                        }
                        ArrowPreference(
                            title = if (active == null) "连接管理后台" else "连接设置",
                            summary = "管理 Worker 地址和本机 Token",
                            onClick = onEdit,
                        )
                        ArrowPreference(
                            title = "添加账号",
                            summary = "添加另一个 Worker 环境",
                            onClick = onAdd,
                        )
                        ArrowPreference(
                            title = if (busy) "正在刷新…" else "刷新统计",
                            summary = "重新读取授权数据",
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
                        BasicComponent(
                            title = if (busy) "正在加载统计" else "暂无统计数据",
                            summary = if (connected) "点击上方刷新统计" else "请先连接管理后台",
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(
                                "模块授权用户",
                                summary.optString("module_authorized_count", "—"),
                                Modifier.weight(1f),
                            )
                            MetricCard(
                                "有效授权码",
                                summary.optString("active_code_count", "—"),
                                Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(
                                "Bot 管理员",
                                summary.optString("bot_admin_count", "—"),
                                Modifier.weight(1f),
                            )
                            MetricCard(
                                "维护者",
                                summary.optString("maintainer_count", "—"),
                                Modifier.weight(1f),
                            )
                        }
                        Card(Modifier.fillMaxWidth()) {
                            BasicComponent(
                                title = "授权码记录",
                                summary = "发放 ${summary.optString("issued_count", "0")}  ·  兑换 ${summary.optString("redeemed_count", "0")}  ·  撤销 ${summary.optString("revoked_count", "0")}",
                            )
                        }
                    }
                }
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
                            onValueChange = onRoleTargetIdChange,
                            label = "角色管理 · Telegram 数字 ID",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onRoleChange("maintainer", true) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) { Text("设为维护者") }
                            Button(
                                onClick = { onRoleChange("maintainer", false) },
                                modifier = Modifier.weight(1f),
                            ) { Text("移除维护者") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onRoleChange("bot_admin", true) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) { Text("设为 Bot 管理员") }
                            Button(
                                onClick = { onRoleChange("bot_admin", false) },
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
                        Text("撤销指定 Telegram 用户的模块授权，并清除其未兑换授权码。")
                        TextField(
                            value = revokeTargetId,
                            onValueChange = onRevokeTargetIdChange,
                            label = "授权管理 · Telegram 数字 ID",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        if (!revokeConfirmation) {
                            Button(
                                onClick = onAskRevoke,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("撤销用户授权…") }
                        } else {
                            Text(
                                text = "确认撤销 ID 为 $revokeTargetId 的授权？此操作无法直接恢复。",
                                color = MiuixTheme.colorScheme.error,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onCancelRevoke,
                                    modifier = Modifier.weight(1f),
                                ) { Text("取消") }
                                Button(
                                    onClick = onConfirmRevoke,
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f),
                                ) { Text(if (busy) "处理中…" else "确认撤销") }
                            }
                        }
                    }
                }
            }
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
            Text(label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}