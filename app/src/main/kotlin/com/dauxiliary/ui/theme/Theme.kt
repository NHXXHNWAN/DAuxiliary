package com.dauxiliary.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * App-wide theme wrapper. Follows system light/dark mode with Miuix color schemes.
 * TODO: expose Monet dynamic colors (MiuixTheme(controller = ...)) in settings page later.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) darkColorScheme() else lightColorScheme()
    MiuixTheme(colors = colors) {
        content()
    }
}
