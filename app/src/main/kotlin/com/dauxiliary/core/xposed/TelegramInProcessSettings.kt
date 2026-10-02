package com.dauxiliary.core.xposed

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dauxiliary.core.registry.AppTarget
import com.dauxiliary.ui.injected.InjectedModuleSettings
import com.dauxiliary.ui.theme.AppTheme

/** Opens Telegram-only DAuxiliary settings inside the current Telegram Activity. */
internal object TelegramInProcessSettings {
    private const val VIEW_TAG = "dauxiliary.telegram.settings.view"

    fun open(context: Context, classLoader: ClassLoader) {
        val activity = findActivity(context) ?: HostActivityTracker.currentActivity() ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        val root = activity.window?.decorView as? ViewGroup ?: return
        if (root.findViewWithTag<View>(VIEW_TAG) != null) return

        // Compose saveable state also requires a SavedStateRegistryOwner. NagramXF's
        // Activity is not an AndroidX owner, so provide an isolated owner on this
        // injected subtree instead of mutating only the ComposeView.
        val lifecycleOwner = ComposeHostOwner()
        lifecycleOwner.performAttach()
        root.setViewTreeLifecycleOwner(lifecycleOwner)
        root.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        val composeView = ComposeView(activity).apply {
            tag = VIEW_TAG
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setBackgroundColor(Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { AppTheme { InjectedModuleSettings(AppTarget.TELEGRAM) } }
        }
        root.addView(composeView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        fun close() {
            if (composeView.parent === root) root.removeView(composeView)
            lifecycleOwner.performDetach()
        }
        if (activity is OnBackPressedDispatcherOwner) {
            activity.onBackPressedDispatcher.addCallback(
                activity,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        isEnabled = false
                        close()
                    }
                },
            )
        } else if (Build.VERSION.SDK_INT >= 33) {
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            ) { close() }
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

    private class ComposeHostOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        init {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun performAttach() {
            runCatching { savedStateController.performAttach() }
        }

        fun performDetach() {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
        }
    }
}
