package com.dauxiliary.core.xposed

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.dauxiliary.core.registry.AppTarget
import com.dauxiliary.ui.injected.InjectedModuleSettings
import io.github.libxposed.api.XposedInterface
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/** Module settings entry implemented with the modern LibXposed interceptor API. */
object HostEntryHook {
    private const val OVERLAY_TAG_PREFIX = "dauxiliary_host_entry_"
    private val installedTargets = mutableSetOf<AppTarget>()

    fun install(xposed: XposedInterface, target: AppTarget) {
        if (!installedTargets.add(target)) return
        val onResume = Activity::class.java.getDeclaredMethod("onResume")
        xposed.hook(onResume)
            .setId("${target.name.lowercase(Locale.ROOT)}.activity.on_resume")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? Activity)?.let { activity ->
                    activity.runOnUiThread { installOverlay(activity, target) }
                }
                result
            }
    }

    private fun installOverlay(activity: Activity, target: AppTarget) {
        val decor = activity.window?.decorView as? ViewGroup ?: return
        val tag = OVERLAY_TAG_PREFIX + target.name.lowercase(Locale.ROOT)
        if (decor.findViewWithTag<View>(tag) != null) return
        val composeView = ComposeView(activity).apply {
            this.tag = tag
            setBackgroundColor(Color.TRANSPARENT)
            setContent { MiuixTheme { HostEntryButton(activity, target) } }
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dp(activity, 48)
            marginEnd = dp(activity, 8)
        }
        decor.addView(composeView, params)
    }

    @Composable
    private fun HostEntryButton(activity: Activity, target: AppTarget) {
        var opened by remember { mutableStateOf(false) }
        Button(
            modifier = Modifier.padding(8.dp),
            onClick = {
                opened = true
                HostSettingsDialog.show(activity, target)
            },
        ) { Text(if (opened) "${target.displayName}设置" else "DAuxiliary") }
    }

    private fun dp(activity: Activity, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            activity.resources.displayMetrics,
        ).toInt()
}

private object HostSettingsDialog {
    fun show(activity: Activity, target: AppTarget) {
        val dialog = Dialog(activity)
        val view = ComposeView(activity).apply {
            setContent { MiuixTheme { InjectedModuleSettings(target) } }
        }
        dialog.setContentView(view)
        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
}