package com.dauxiliary.core.xposed

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
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
    private const val OVERLAY_TAG = "dauxiliary.telegram.settings.overlay"

    fun open(context: Context, classLoader: ClassLoader) {
        HostActivityTracker.register(context)
        val activity = findActivity(context) ?: HostActivityTracker.currentActivity() ?: HostActivityTracker.findLiveActivity() ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        val root = activity.window?.decorView as? ViewGroup ?: return
        if (root.findViewWithTag<View>(VIEW_TAG) != null) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        android.util.Log.i("DAuxiliary-TelegramEntry", "Opening injected settings in ${activity.javaClass.name}")

        // Compose saveable state also requires a SavedStateRegistryOwner. NagramXF's
        // Activity is not an AndroidX owner, so provide an isolated owner on this
        // injected subtree instead of mutating only the ComposeView.
        val lifecycleOwner = ComposeHostOwner()
        // Compose must see an attached, CREATED owner. Do not advance the
        // isolated owner to RESUMED before setContent; NagramXF's obfuscated
        // saveable-state code can consume restored state during composition.
        val composeView = ComposeView(activity).apply {
            tag = VIEW_TAG
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setBackgroundColor(Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        }
        val overlay = FrameLayout(activity).apply {
            tag = OVERLAY_TAG
            setBackgroundColor(Color.TRANSPARENT)
            // Compose and NagramXF resolve tree owners from the nearest parent during measure.
            // Installing them only on ComposeView is too late for the injected FrameLayout path.
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            addView(composeView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        // Attach the owner and the ComposeView to the real window before starting
        // composition. Starting composition while the view is detached can make
        // Compose consume saved state before the registry is in CREATED state.
        content.addView(overlay, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        var closed = false
        var dispatcherCallback: OnBackPressedCallback? = null
        var platformCallback: OnBackInvokedCallback? = null
        fun close() {
            if (closed) return
            closed = true
            dispatcherCallback?.remove()
            if (Build.VERSION.SDK_INT >= 33) {
                platformCallback?.let { activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
            }
            if (overlay.parent === content) content.removeView(overlay)
            lifecycleOwner.performDetach()
        }
        composeView.setContent { AppTheme { InjectedModuleSettings(AppTarget.TELEGRAM) } }
        composeView.post {
            if (!closed && composeView.isAttachedToWindow) lifecycleOwner.performStart()
        }

        if (activity is OnBackPressedDispatcherOwner) {
            val callback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = close()
            }
            dispatcherCallback = callback
            activity.onBackPressedDispatcher.addCallback(activity, callback)
        } else if (Build.VERSION.SDK_INT >= 33) {
            val callback = OnBackInvokedCallback { close() }
            platformCallback = callback
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback,
            )
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
            // The registry must be attached before Compose starts reading saveable state.
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            // Keep the owner at CREATED while Compose registers saveable state.
            // It is advanced after the view has been attached.
        }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun performAttach() {
            // Kept for binary/source compatibility with older callers.
            runCatching { savedStateController.performAttach() }
        }

        fun performStart() {
            if (lifecycle.currentState == Lifecycle.State.CREATED) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
        }

        fun performDetach() {
            when (lifecycle.currentState) {
                Lifecycle.State.RESUMED -> {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                }
                Lifecycle.State.STARTED -> {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                }
                Lifecycle.State.CREATED -> lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                else -> Unit
            }
        }
    }
}
