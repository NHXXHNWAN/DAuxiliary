package com.dauxiliary.core.config

import android.content.Context
import com.dauxiliary.core.registry.AppTarget
import de.robv.android.xposed.XSharedPreferences

/** Shared module configuration and host heartbeat access. */
object ConfigStore {
    private const val PREFS_FILE = "daux_config"
    const val KEY_FLOATING_NAVIGATION_BAR_STYLE = "floating_navigation_bar_style"
    const val KEY_COLOR_MODE = "color_mode"
    const val KEY_ENABLED_APPLICATIONS = "enabled_applications"
    const val KEY_BACKGROUND_EFFECT_VARIANT = "background_effect_variant"
    const val KEY_DYNAMIC_BACKGROUND = "dynamic_background"
    const val KEY_FULLSCREEN_BACKGROUND = "fullscreen_background"
    const val KEY_UPDATE_CHANNEL = "update_channel"
    private const val KEY_HOST_FEATURE_PREFIX = "enabled_features_"
    private const val KEY_HOST_LAST_SEEN_PREFIX = "host_last_seen_"
    private const val HOST_ACTIVE_WINDOW_MS = 5 * 60 * 1000L
    private const val MODULE_PACKAGE = "com.dauxiliary"
    private val DEFAULT_ENABLED_APPLICATIONS = setOf("com.ss.android.ugc.aweme")

    fun enabledApplicationPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ENABLED_APPLICATIONS, DEFAULT_ENABLED_APPLICATIONS).orEmpty()

    fun enabledApplicationCount(context: Context): Int = enabledApplicationPackages(context).size

    fun enabledFeatureIds(context: Context, host: AppTarget): Set<String> =
        if (context.packageName == MODULE_PACKAGE) {
            prefs(context).getStringSet(featureKey(host), emptySet()).orEmpty()
        } else {
            hookedPreferences()?.getStringSet(featureKey(host), emptySet()).orEmpty()
        }

    fun setFeatureEnabled(context: Context, host: AppTarget, featureId: String, enabled: Boolean) {
        val current = enabledFeatureIds(context, host).toMutableSet()
        if (enabled) current += featureId else current -= featureId
        prefs(context).edit().putStringSet(featureKey(host), current).apply()
    }

    fun prefs(context: Context) =
        context.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** Host process reports through the module-owned provider; no local flag is fabricated. */
    fun recordHostLoaded(context: Context, host: AppTarget) {
        runCatching {
            context.contentResolver.call(
                android.net.Uri.parse("content://$MODULE_PACKAGE.status"),
                "record_host_loaded",
                host.name,
                null,
            )
        }
    }

    fun isHostActive(context: Context, host: AppTarget): Boolean {
        val seenAt = prefs(context).getLong(lastSeenKey(host), 0L)
        return seenAt > 0L && System.currentTimeMillis() - seenAt < HOST_ACTIVE_WINDOW_MS
    }

    fun setApplicationEnabled(context: Context, packageName: String, enabled: Boolean) {
        val current = enabledApplicationPackages(context).toMutableSet()
        if (enabled) current += packageName else current -= packageName
        prefs(context).edit().putStringSet(KEY_ENABLED_APPLICATIONS, current).apply()
    }

    fun isApplicationEnabled(context: Context, packageName: String): Boolean =
        enabledApplicationPackages(context).contains(packageName)

    fun readFromHookedProcess(key: String, default: Boolean): Boolean =
        hookedPreferences()?.getBoolean(key, default) ?: default

    fun readEnabledApplicationCount(): Int =
        hookedPreferences()?.getStringSet(KEY_ENABLED_APPLICATIONS, emptySet())?.size ?: 0

    fun isApplicationEnabledInHookedProcess(packageName: String): Boolean =
        hookedPreferences()?.getStringSet(KEY_ENABLED_APPLICATIONS, DEFAULT_ENABLED_APPLICATIONS)
            ?.contains(packageName) == true

    private fun featureKey(host: AppTarget) = KEY_HOST_FEATURE_PREFIX + host.name.lowercase()
    private fun lastSeenKey(host: AppTarget) = KEY_HOST_LAST_SEEN_PREFIX + host.name.lowercase()

    @Volatile
    private var cached: XSharedPreferences? = null

    private fun hookedPreferences(): XSharedPreferences? {
        cached?.reload()
        return cached ?: runCatching {
            XSharedPreferences(MODULE_PACKAGE, PREFS_FILE).apply {
                makeWorldReadable()
                reload()
            }.also { cached = it }
        }.getOrNull()
    }
}
