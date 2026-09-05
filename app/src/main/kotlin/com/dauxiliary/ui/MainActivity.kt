package com.dauxiliary.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dauxiliary.ui.theme.AppTheme
import com.dauxiliary.ui.widgets.DAuxiliaryApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                DAuxiliaryApp()
            }
        }
    }
}
