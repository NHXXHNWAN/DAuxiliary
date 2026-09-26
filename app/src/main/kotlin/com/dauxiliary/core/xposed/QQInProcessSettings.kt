package com.dauxiliary.core.xposed

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import com.dauxiliary.ui.injected.HostSettingsFragment

/** Opens the module UI inside QQ's current native Activity back stack. */
internal object QQInProcessSettings {
    private const val TAG = "DAuxiliary"
    private const val TAG_FRAGMENT = "dauxiliary.qq.settings"

    fun open(context: Context, classLoader: ClassLoader) {
        val activity = findActivity(context)
        if (activity !is FragmentActivity) {
            Log.w(TAG, "QQ settings Activity is not FragmentActivity; refusing cross-app navigation")
            return
        }
        runCatching {
            if (activity.supportFragmentManager.findFragmentByTag(TAG_FRAGMENT) != null) return
            activity.supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(android.R.id.content, HostSettingsFragment.newInstance(), TAG_FRAGMENT)
                addToBackStack(TAG_FRAGMENT)
            }
        }.onFailure { error ->
            Log.e(TAG, "Unable to open in-process QQ settings fragment", error)
        }
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is android.content.ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }
}