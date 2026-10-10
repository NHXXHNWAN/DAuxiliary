package com.dauxiliary.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

@Composable
internal fun AdminLiquidNavigationBar(
    items: List<NavigationItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    val shape = remember { CircleShape }
    val bottomInset = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()
    val surface = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f)
    val selectedColor = MiuixTheme.colorScheme.primary
    val contentColor = MiuixTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 8.dp + bottomInset),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .dropShadow(
                    shape = shape,
                    shadow = Shadow(
                        radius = 12.dp,
                        color = Color.Black,
                        alpha = 0.14f,
                    ),
                )
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = {
                                blur(5.dp.toPx(), 5.dp.toPx())
                            },
                            onDrawSurface = { drawRect(surface) },
                        )
                    } else {
                        Modifier.background(surface, shape)
                    },
                )
                .padding(5.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                AdminLiquidNavigationItem(
                    item = item,
                    itemWidthFraction = 1f / items.size,
                    selected = index == selectedIndex,
                    selectedColor = selectedColor,
                    contentColor = contentColor,
                    onClick = { onItemClick(index) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.AdminLiquidNavigationItem(
    item: NavigationItem,
    itemWidthFraction: Float,
    selected: Boolean,
    selectedColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    val itemModifier = Modifier
        .fillMaxWidth(itemWidthFraction)
        .clip(CircleShape)
        .then(if (selected) Modifier.background(selectedColor.copy(alpha = 0.16f), CircleShape) else Modifier)
        .padding(vertical = 7.dp)
        .graphicsLayer { alpha = 1f }
        .clickable(onClick = onClick)

    Column(
        modifier = itemModifier,

        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (selected) selectedColor else contentColor,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(21.dp),
            )
            Text(text = item.label, fontSize = 11.sp)
        }
    }
}
