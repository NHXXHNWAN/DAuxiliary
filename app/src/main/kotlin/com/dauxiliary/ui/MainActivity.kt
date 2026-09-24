package com.dauxiliary.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.dauxiliary.core.config.ConfigStore
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import com.dauxiliary.ui.theme.AppTheme
import com.dauxiliary.ui.widgets.DAuxiliaryApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var modeIndex by rememberSaveable {
                mutableIntStateOf(
                    ConfigStore.prefs(this).getInt(ConfigStore.KEY_COLOR_MODE, 0)
                        .takeIf { it in ColorSchemeMode.entries.indices } ?: 0,
                )
            }
            val modes = ColorSchemeMode.entries
            AppTheme(mode = modes.getOrElse(modeIndex) { ColorSchemeMode.System }) {
                DAuxiliaryApp(
                    colorMode = modeIndex,
                    onColorModeChange = { index ->
                        modeIndex = index.coerceIn(0, modes.lastIndex)
                        ConfigStore.prefs(this).edit()
                            .putInt(ConfigStore.KEY_COLOR_MODE, modeIndex).apply()
                        // AppTheme observes mode changes; no Activity restart is needed.
                    },
                )
            }
        }
    }
}
