package com.dauxiliary.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.dauxiliary.core.registry.AppTarget
import com.dauxiliary.ui.injected.InjectedModuleSettings
import com.dauxiliary.ui.theme.AppTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

/** Module-owned settings screen launched from a host-native settings item. */
class HostSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val host = intent.getStringExtra("com.dauxiliary.extra.HOST")
            ?.let { runCatching { AppTarget.valueOf(it) }.getOrNull() }
            ?: AppTarget.QQ
        setContent {
            val modeIndex = rememberSaveable {
                mutableIntStateOf(0)
            }
            val modes = ColorSchemeMode.entries
            AppTheme(mode = modes.getOrElse(modeIndex.intValue) { ColorSchemeMode.System }) {
                InjectedModuleSettings(host)
            }
        }
    }
}
