package com.dauxiliary.core.xposed

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.lang.ref.WeakReference

/** Tracks the current host Activity without retaining an Activity instance. */
internal object HostActivityTracker : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var current = WeakReference<Activity>(null)
    @Volatile
    private var registered = false

    fun resetForHotReload() {
        synchronized(this) {
            current.clear()
            registered = false
        }
    }

    fun registerCurrentProcess() {
        val application = runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Application
        }.getOrNull() ?: return
        register(application)
    }

    fun register(context: Context) {
        val application = context.applicationContext as? Application ?: return
        if (registered) return
        synchronized(this) {
            if (!registered) {
                application.registerActivityLifecycleCallbacks(this)
                registered = true
            }
        }
    }

    fun currentActivity(): Activity? = current.get()

    fun remember(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (current.get() === activity) current.clear()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        current = WeakReference(activity)
    }
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}