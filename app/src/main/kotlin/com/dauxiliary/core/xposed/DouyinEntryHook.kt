package com.dauxiliary.core.xposed

import android.app.Activity
import android.util.TypedValue

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.dauxiliary.core.config.ConfigStore
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember

/**
 * 抖音进程内的模块入口。
 *
 * 入口只挂载到抖音 Activity 的 decorView，不启动独立 Activity；这样用户可以
 * 在抖音内部直接打开模块面板。真正的功能 Hook 仍由 EntryHook/各 feature 负责。
 */
object DouyinEntryHook {
    private const val OVERLAY_TAG = "dauxiliary_douyin_entry"

    fun install() {
        XposedHelpers.findAndHookMethod(
            Activity::class.java,
            "onResume",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    installOverlay(activity)
                }
            },
        )
    }

    private fun installOverlay(activity: Activity) {
        val decor = activity.window?.decorView as? ViewGroup ?: return
        if (decor.findViewWithTag<View>(OVERLAY_TAG) != null) return

        val composeView = ComposeView(activity).apply {
            tag = OVERLAY_TAG
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MiuixTheme {
                    var expanded by remember { mutableStateOf(false) }
                    var enabled by remember {
                        mutableStateOf(
                            ConfigStore.readFromHookedProcess(
                                ConfigStore.KEY_MASTER_SWITCH,
                                false,
                            ),
                        )
                    }
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "关闭模块面板" else "DAuxiliary")
                        }
                        if (expanded) {
                            Card {
                                SwitchPreference(
                                    title = "抖音增强模块",
                                    summary = "在抖音内直接控制模块开关",
                                    checked = enabled,
                                    onCheckedChange = {
                                        enabled = it
                                        runCatching {
                                            val moduleContext = activity.createPackageContext(
                                                "com.dauxiliary",
                                                android.content.Context.CONTEXT_IGNORE_SECURITY,
                                            )
                                            ConfigStore.setMasterSwitch(moduleContext, it)
                                        }.onFailure { EntryHook.log("Unable to write module config", it) }
                                    },
                                )
                            }
                        }
                    }
                }
            }
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

    private fun dp(activity: Activity, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            activity.resources.displayMetrics,
        ).toInt()
}
