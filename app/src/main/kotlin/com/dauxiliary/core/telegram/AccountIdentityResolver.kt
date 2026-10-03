package com.dauxiliary.core.telegram

import android.util.Log

/** Resolves a Telegram account slot without assuming a particular fork's field layout. */
internal object AccountIdentityResolver {
    private const val TAG = "DAuxiliary-Telegram"

    fun resolve(loader: ClassLoader, packageName: String): String {
        val userConfig = runCatching { loader.loadClass("org.telegram.messenger.UserConfig") }.getOrNull()
        if (userConfig != null) {
            val names = listOf("selectedAccount", "selectedAccountIndex", "currentAccount")
            for (name in names) {
                val value = readStaticInt(userConfig, name) ?: continue
                if (value in 0..99) return "slot_$value"
            }
        }
        // A process-scoped fallback is deliberately explicit; it avoids merging data
        // between clients while diagnostics can still report that true account identity is unknown.
        Log.d(TAG, "Telegram account slot unavailable; using process fallback")
        return "process_${packageName.replace(Regex("[^A-Za-z0-9_.-]"), "_")}"
    }

    private fun readStaticInt(type: Class<*>, name: String): Int? = runCatching {
        generateSequence(type) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull { it.name.equals(name, ignoreCase = true) && it.type == Int::class.javaPrimitiveType }
            ?.also { it.isAccessible = true }
            ?.getInt(null)
    }.getOrNull()
}
