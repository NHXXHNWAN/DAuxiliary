package com.dauxiliary.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** App theme backed by Miuix Color Mode and Android Monet. */
@Composable
fun AppTheme(
    mode: ColorSchemeMode = ColorSchemeMode.System,
    content: @Composable () -> Unit,
) {
    val controller = remember(mode) { ThemeController(mode) }
    MiuixTheme(controller = controller, content = content)
}

/** Resolves explicit light/dark modes for the Miuix background effect. */
@Composable
fun isAppInDarkTheme(): Boolean = when (MiuixTheme.colorSchemeMode) {
    ColorSchemeMode.Light, ColorSchemeMode.MonetLight -> false
    ColorSchemeMode.Dark, ColorSchemeMode.MonetDark -> true
    else -> isSystemInDarkTheme()
}
