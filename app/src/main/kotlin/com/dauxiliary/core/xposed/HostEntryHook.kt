package com.dauxiliary.core.xposed

import android.app.Activity
import android.app.Dialog
import android.app.Instrumentation
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import com.dauxiliary.core.registry.AppTarget
import com.dauxiliary.ui.injected.InjectedModuleSettings
import io.github.libxposed.api.XposedInterface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/** Stable in-host entry. Uses a native view so it remains visible before Compose is ready. */
object HostEntryHook {
    private const val TAG_PREFIX = "dauxiliary_host_entry_"
    private val installedTargets = mutableSetOf<AppTarget>()

    fun install(xposed: XposedInterface, target: AppTarget, classLoader: ClassLoader) {
        if (target == AppTarget.QQ) {
            QQSettingsEntryHook.install(xposed, classLoader)
            return
        }
        synchronized(installedTargets) {
            if (!installedTargets.add(target)) return
        }
        runCatching {
            val method = Instrumentation::class.java.getDeclaredMethod(
                "callActivityOnResume",
                Activity::class.java,
            )
            xposed.hook(method)
                .setId("${target.name.lowercase(Locale.ROOT)}.instrumentation.call_activity_on_resume")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    val activity = chain.args.firstOrNull() as? Activity
                    if (activity != null) {
                        activity.runOnUiThread {
                            activity.window?.decorView?.postDelayed(
                                { installOverlay(activity, target) },
                                120L,
                            )
                        }
                    }
                    result
                }
        }.onFailure { error ->
            Log.e("DAuxiliary", "Failed to install ${target.displayName} entry hook", error)
        }
    }

    private fun installOverlay(activity: Activity, target: AppTarget) {
        if (activity.isFinishing || activity.isDestroyed) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val tag = TAG_PREFIX + target.name.lowercase(Locale.ROOT)
        if (root.findViewWithTag<View>(tag) != null) return

        val button = TextView(activity).apply {
            this.tag = tag
            text = "DAuxiliary"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(activity, 18).toFloat()
                setColor(Color.rgb(35, 35, 38))
                setStroke(dp(activity, 1), Color.argb(90, 255, 255, 255))
            }
            elevation = dp(activity, 6).toFloat()
            contentDescription = "打开 DAuxiliary 模块设置"
            setOnClickListener { HostSettingsDialog.show(activity, target) }
        }

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dp(activity, 18)
            marginEnd = dp(activity, 14)
        }
        root.addView(button, params)
    }

    private fun dp(activity: Activity, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            activity.resources.displayMetrics,
        ).toInt()
}

internal object HostSettingsDialog {
    fun show(activity: Activity, target: AppTarget) {
        if (activity.isFinishing || activity.isDestroyed) return
        val dialog = Dialog(activity)
        val view = ComposeView(activity).apply {
            setContent { MiuixTheme { InjectedModuleSettings(target) } }
        }
        dialog.setContentView(view)
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.35f)
                setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        }
        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
}
