package com.dauxiliary.core.xposed

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dauxiliary.core.registry.AppTarget
import io.github.libxposed.api.XposedInterface

/** Launches the module-owned settings Activity from a host-native settings item. */
internal object HostSettingsLauncher {
    private const val TAG = "DAuxiliary"
    private const val MODULE_PACKAGE = "com.dauxiliary"
    private const val SETTINGS_ACTIVITY = "com.dauxiliary.ui.HostSettingsActivity"
    const val EXTRA_HOST = "com.dauxiliary.extra.HOST"

    fun open(context: Context, target: AppTarget) {
        runCatching {
            val intent = Intent().apply {
                setPackage(MODULE_PACKAGE)
                component = ComponentName(MODULE_PACKAGE, SETTINGS_ACTIVITY)
                putExtra(EXTRA_HOST, target.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }.onFailure { error ->
            Log.e(TAG, "Failed to open ${target.displayName} settings Activity", error)
        }
    }
}

/** Installs only host-native settings entries; no floating or injected View entry is used. */
object HostEntryHook {
    fun install(xposed: XposedInterface, target: AppTarget, classLoader: ClassLoader) {
        if (target == AppTarget.QQ) {
            QQSettingsEntryHook.install(xposed, classLoader)
        }
    }
}