package com.dauxiliary.core.xposed

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner

import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.dauxiliary.core.registry.AppTarget
import com.dauxiliary.ui.injected.InjectedModuleSettings
import com.dauxiliary.ui.theme.AppTheme

/** Opens the QQ-specific module UI inside the current QQ Activity. */
internal object QQInProcessSettings {
    private const val VIEW_TAG = "dauxiliary.qq.settings.view"

    fun open(context: Context, classLoader: ClassLoader) {
        HostActivityTracker.register(context)
        val activity = HostActivityTracker.currentActivity() ?: findActivity(context) ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        val root = activity.window?.decorView as? ViewGroup ?: return
        if (root.findViewWithTag<View>(VIEW_TAG) != null) return

        // QQ's Activity is not an AndroidX LifecycleOwner. The Compose window
        // recomposer searches from DecorView, so assigning the owner only to the
        // new child view is too late and causes a crash during the first measure.
        val lifecycleOwner = AlwaysResumedLifecycleOwner()
        root.setViewTreeLifecycleOwner(lifecycleOwner)

        val composeView = ComposeView(activity).apply {
            // Keep the owner on the ComposeView as well as DecorView. Some QQ
            // versions replace the decor root's tag during window setup.
            setViewTreeLifecycleOwner(lifecycleOwner)
            tag = VIEW_TAG
            setBackgroundColor(Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { AppTheme { InjectedModuleSettings(AppTarget.QQ) } }
        }
        root.addView(composeView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        if (activity is OnBackPressedDispatcherOwner) {
            activity.onBackPressedDispatcher.addCallback(
                activity,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        isEnabled = false
                        root.removeView(composeView)
                    }
                },
            )
        } else if (Build.VERSION.SDK_INT >= 33) {
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            ) { root.removeView(composeView) }
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

    private class AlwaysResumedLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply {
            handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            handleLifecycleEvent(Lifecycle.Event.ON_START)
            handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        override val lifecycle: Lifecycle
            get() = registry
    }
}