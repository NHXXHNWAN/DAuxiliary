package com.dauxiliary.admin

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Admin 版主页底栏：保留主页的 64dp 外壳、56dp 内容层和独立活动胶囊。
 * 选中背景不再直接挂在文字条目上，避免所有条目视觉上糊成一条。
 */
@Composable
internal fun AdminLiquidNavigationBar(
    items: List<NavigationItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val shape = remember { CircleShape }
    val bottomInset = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()
    val bottomPadding = if (bottomInset != 0.dp) 8.dp + bottomInset else 36.dp
    val surface = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
    val selectedColor = MiuixTheme.colorScheme.primary
    val contentColor = MiuixTheme.colorScheme.onSurface
    val safeSelectedIndex = selectedIndex.coerceIn(0, items.lastIndex)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .dropShadow(
                    shape = shape,
                    shadow = Shadow(
                        radius = 10.dp,
                        color = Color.Black,
                        alpha = 0.1f,
                    ),
                )
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = {
                                blur(4.dp.toPx(), 4.dp.toPx())
                            },
                            onDrawSurface = { drawRect(surface) },
                        )
                    } else {
                        Modifier.background(surface, shape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val tabWidth = (maxWidth - 8.dp) / items.size
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * safeSelectedIndex,
                label = "admin-navigation-indicator",
            )

            // 64dp 外壳的 4dp 内缩，得到主页同款 56dp 标签层。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
            ) {
                // 独立活动胶囊：它是选中态的唯一背景层，不包裹文字条目。
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .height(56.dp)
                        .clip(shape)
                        .background(selectedColor.copy(alpha = 0.15f), shape),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(shape)
                                .clickable { onItemClick(index) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                        ) {
                            CompositionLocalProvider(
                                LocalContentColor provides if (index == safeSelectedIndex) {
                                    selectedColor
                                } else {
                                    contentColor
                                },
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(text = item.label, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
