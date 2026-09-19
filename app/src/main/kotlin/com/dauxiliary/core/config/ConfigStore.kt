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
    const val KEY_COLOR_MODE = "color_mode"
    const val KEY_ENABLED_APPLICATIONS = "enabled_applications"
    const val KEY_ENABLED_FEATURES = "enabled_features"

    private val DEFAULT_ENABLED_APPLICATIONS = setOf("com.ss.android.ugc.aweme")

    fun enabledApplicationPackages(context: Context): Set<String> =
        prefs(context)
            .getStringSet(KEY_ENABLED_APPLICATIONS, DEFAULT_ENABLED_APPLICATIONS)
            .orEmpty()

    /** Number of host applications currently enabled in the desktop configuration. */
    fun enabledApplicationCount(context: Context): Int =
        enabledApplicationPackages(context).size

    /** Number of enabled feature registrations, reserved for the feature registry. */
    fun enabledFeatureCount(context: Context): Int =
        prefs(context).getStringSet(KEY_ENABLED_FEATURES, emptySet())?.size ?: 0

    // ---- Module UI side (write) ----

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun setMasterSwitch(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MASTER_SWITCH, enabled).apply()
    }

    fun setApplicationEnabled(context: Context, packageName: String, enabled: Boolean) {
        val current = prefs(context)
            .getStringSet(KEY_ENABLED_APPLICATIONS, emptySet())
            .orEmpty()
            .toMutableSet()
        if (enabled) current += packageName else current -= packageName
        prefs(context).edit().putStringSet(KEY_ENABLED_APPLICATIONS, current).apply()
    }

    fun isApplicationEnabled(context: Context, packageName: String): Boolean =
        prefs(context)
            .getStringSet(KEY_ENABLED_APPLICATIONS, emptySet())
            .orEmpty()
            .contains(packageName)

    fun isMasterEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MASTER_SWITCH, false)

    // ---- Hooked process side (read) ----

    @Volatile
    private var cached: XSharedPreferences? = null

    fun readFromHookedProcess(key: String, default: Boolean): Boolean {
        return hookedPreferences()?.getBoolean(key, default) ?: default
    }

    fun readEnabledFeatureCount(): Int =
        hookedPreferences()?.getStringSet(KEY_ENABLED_FEATURES, emptySet())?.size ?: 0

    fun readEnabledApplicationCount(): Int =
        hookedPreferences()?.getStringSet(KEY_ENABLED_APPLICATIONS, emptySet())?.size ?: 0

    fun isApplicationEnabledInHookedProcess(packageName: String): Boolean =
        hookedPreferences()
            ?.getStringSet(KEY_ENABLED_APPLICATIONS, DEFAULT_ENABLED_APPLICATIONS)
            ?.contains(packageName) == true

    private fun hookedPreferences(): XSharedPreferences? {
        cached?.reload()
        return cached ?: runCatching {
            XSharedPreferences(MODULE_PACKAGE, PREFS_FILE).apply {
                makeWorldReadable()
                reload()
            }.also { cached = it }
        }.getOrNull()
    }

    private const val MODULE_PACKAGE = "com.dauxiliary"
}
