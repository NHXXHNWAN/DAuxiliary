package com.dauxiliary.core.xposed

import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.util.Collections
import java.util.WeakHashMap

/**
 * Conservative QQ anti-recall hook coordinator.
 *
 * QQ 9.3.60 analysis does not contain a verified Java recall callback or the
 * native kernel binary. Therefore this class only installs candidates whose
 * method signature and semantic markers are verified at runtime. It never
 * patches arbitrary methods and leaves QQ's original behavior untouched when
 * no trusted candidate is found.
 */
internal object QQRecallHook {
    private const val TAG = "DAuxiliary"
    const val FEATURE_ID = "qq.anti_recall"
    private val installedLoaders = Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())

    fun install(
        xposed: XposedInterface,
        classLoader: ClassLoader,
    ) {
        synchronized(installedLoaders) {
            if (!installedLoaders.add(classLoader)) return
        }

        // Keep the resolver warm, but do not claim a recall hook from an
        // unrelated settings processor candidate.
        QQDexKitResolver.warmUp(classLoader)
        Log.i(TAG, "QQ anti-recall initialization started; no verified target yet")

        // The 9.3.60 material currently has no verified Java/native recall
        // target. This explicit status prevents a false-positive hook.
        Log.w(TAG, "QQ anti-recall skipped: verified recall callback unavailable")
    }

}
