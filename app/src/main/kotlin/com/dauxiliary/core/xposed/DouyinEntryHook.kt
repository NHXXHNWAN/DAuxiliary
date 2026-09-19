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
import com.dauxiliary.core.registry.AppTarget
import java.util.Locale

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
 * Host process module entry.
 *
 * The shared panel is attached to the host Activity decorView instead of starting
 * another Activity. Host-specific hooks remain owned by the feature registry.
 */
object DouyinEntryHook {
    private const val OVERLAY_TAG_PREFIX = "dauxiliary_host_entry_"
    private val installedTargets = mutableSetOf<AppTarget>()

    fun install(target: AppTarget) {
        if (!installedTargets.add(target)) return
        XposedHelpers.findAndHookMethod(
            Activity::class.java,
            "onResume",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    installOverlay(activity, target)
                }
            },
        )
    }

    private fun installOverlay(activity: Activity, target: AppTarget) {
        val decor = activity.window?.decorView as? ViewGroup ?: return
        val overlayTag = OVERLAY_TAG_PREFIX + target.name.lowercase(Locale.ROOT)
        if (decor.findViewWithTag<View>(overlayTag) != null) return

        val composeView = ComposeView(activity).apply {
            tag = overlayTag
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
                    val enabledFeatures = ConfigStore.readEnabledFeatureCount()
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
                                    title = "${target.displayName}增强模块",
                                    summary = "在宿主应用内直接控制模块开关 · 已启动 ${enabledFeatures} 个功能",
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
