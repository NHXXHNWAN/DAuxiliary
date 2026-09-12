package com.dauxiliary.ui.page

import androidx.compose.runtime.Composable
import com.dauxiliary.BuildConfig
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

@Composable
fun AboutPage(onBack: () -> Unit) {
    GroupedPage(
        title = "关于",
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
            }
        },
    ) {
        item { SmallTitle(text = "应用") }
        item {
            GroupCard {
                InformationRow("DAuxiliary", "基于 LSPosed 的抖音增强模块")
                InformationRow("版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            }
        }
        item { SmallTitle(text = "开源信息") }
        item {
            GroupCard {
                InformationRow("项目许可", "Apache-2.0")
                InformationRow("界面组件", "Miuix · Compose Multiplatform")
                InformationRow("液态玻璃底栏", "基于 Miuix 官方示例适配，来源记录见项目 MIUIX_UPSTREAM.md。")
            }
        }
    }
}
