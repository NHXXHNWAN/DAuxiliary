package com.dauxiliary.core.config

import android.content.Context
import de.robv.android.xposed.XSharedPreferences

/**
 * Bridge between the module UI process and the hooked Douyin process.
 *
 * Reading strategy:
 * 1. In the module app: plain SharedPreferences backed by MODE_WORLD_READABLE
 *    fallback (LSPosed rewires this on modern Android).
 * 2. In the hooked process: XSharedPreferences (LSPosed grants read access
 *    when "opt-in" is enabled for this module).
 *
 * A remote config service (content provider or file observer) can replace
 * this later if world-readable prefs become unreliable.
 */
object ConfigStore {
    private const val PREFS_FILE = "daux_config"

    const val KEY_MASTER_SWITCH = "master_switch"
    const val KEY_FLOATING_NAVIGATION_BAR_STYLE = "floating_navigation_bar_style"

    // ---- Module UI side (write) ----

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun setMasterSwitch(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MASTER_SWITCH, enabled).apply()
    }

    fun isMasterEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MASTER_SWITCH, false)

    // ---- Hooked process side (read) ----

    @Volatile
    private var cached: XSharedPreferences? = null

    fun readFromHookedProcess(key: String, default: Boolean): Boolean {
        cached?.let { return it.getBoolean(key, default) }
        return runCatching {
            XSharedPreferences(MODULE_PACKAGE, PREFS_FILE).apply {
                makeWorldReadable()
            }.also { cached = it }
        }.getOrDefault(null)?.getBoolean(key, default) ?: default
    }

    private const val MODULE_PACKAGE = "com.dauxiliary"
}
