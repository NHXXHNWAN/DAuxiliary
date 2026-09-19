package com.dauxiliary.core.config

import android.content.Context
import com.dauxiliary.core.registry.AppTarget
import de.robv.android.xposed.XSharedPreferences

/**
 * Bridge between the module UI process and all hooked host processes.
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
    private const val KEY_HOST_FEATURE_PREFIX = "enabled_features_"

    private val DEFAULT_ENABLED_APPLICATIONS = setOf("com.ss.android.ugc.aweme")

    fun enabledApplicationPackages(context: Context): Set<String> =
        prefs(context)
            .getStringSet(KEY_ENABLED_APPLICATIONS, DEFAULT_ENABLED_APPLICATIONS)
            .orEmpty()

    /** Number of host applications currently enabled in the desktop configuration. */
    fun enabledApplicationCount(context: Context): Int =
        enabledApplicationPackages(context).size

    fun enabledFeatureIds(context: Context, host: AppTarget): Set<String> =
        if (context.packageName == MODULE_PACKAGE) {
            prefs(context).getStringSet(KEY_HOST_FEATURE_PREFIX + host.name.lowercase(), emptySet()).orEmpty()
        } else {
            hookedPreferences()
                ?.getStringSet(KEY_HOST_FEATURE_PREFIX + host.name.lowercase(), emptySet())
                .orEmpty()
        }

    fun setFeatureEnabled(
        context: Context,
        host: AppTarget,
        featureId: String,
        enabled: Boolean,
    ) {
        val key = KEY_HOST_FEATURE_PREFIX + host.name.lowercase()
        val current = enabledFeatureIds(context, host).toMutableSet()
        if (enabled) current += featureId else current -= featureId
        prefs(context).edit().putStringSet(key, current).apply()
    }

    // ---- Module UI side (write) ----

    fun moduleContext(context: Context): Context =
        context.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)

    fun prefs(context: Context) =
        context.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

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
