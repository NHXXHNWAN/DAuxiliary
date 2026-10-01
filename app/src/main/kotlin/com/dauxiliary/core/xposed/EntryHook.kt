package com.dauxiliary.core.xposed

import android.util.Log
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.core.feature.FeatureRegistry
import com.dauxiliary.core.registry.AppTarget
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Modern LibXposed API 102 entry point.
 *
 * The framework discovers this class from META-INF/xposed/java_init.list and
 * invokes the package lifecycle callbacks below. No legacy XposedBridge,
 * XposedHelpers, or XC_* callback is used by the module.
 */
class EntryHook : XposedModule() {
    @Volatile
    private var loadedProcess: String? = null

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        loadedProcess = param.processName
        log("Module loaded in ${param.processName}")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (loadedProcess?.let { process ->
                AppTarget.fromPackageName(process.substringBefore(':')) != null
            } == true) {
            HostActivityTracker.registerCurrentProcess()
        }
        val knownTarget = AppTarget.fromPackageName(param.packageName)
        val target = knownTarget
            ?: if (TelegramHostSupport.isSupported(param.packageName, param.classLoader)) AppTarget.TELEGRAM else return
        val process = loadedProcess
        val processMatchesTarget = if (knownTarget != null) {
            process == null || target.matchesProcess(process)
        } else {
            // A renamed Telegram-Android fork is accepted by marker detection.
            // Its base package must still match the package currently being readied.
            process == null || process.substringBefore(':') == param.packageName
        }
        if (!processMatchesTarget) {
            log("Skip ${target.displayName}: non-host process $process")
            return
        }

        val remotePreferences = getRemotePreferences(ConfigStore.REMOTE_PREFS_GROUP)
        ConfigStore.attachRemotePreferences(remotePreferences)

        // Keep native host entry hooks available even when host features are disabled.
        HostEntryHook.install(this, target, param.classLoader)

        val selectionInitialized = remotePreferences.getBoolean(
            ConfigStore.KEY_APPLICATION_SELECTION_INITIALIZED,
            false,
        )
        val enabledApplications = if (selectionInitialized) {
            remotePreferences
                .getStringSet(ConfigStore.KEY_ENABLED_APPLICATIONS, ConfigStore.defaultEnabledApplications())
                .orEmpty()
        } else {
            ConfigStore.defaultEnabledApplications()
        }
        log("Package ready: ${target.displayName} (${param.packageName}), selectionInitialized=$selectionInitialized, enabled=${target.packageName in enabledApplications}")
        if (target.packageName !in enabledApplications) {
            log("Skip ${target.displayName}: host is disabled in module settings")
            return
        }

        FeatureRegistry.dispatch(this, param, target)
        log("Feature dispatch completed for ${target.displayName}")
    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        FeatureRegistry.resetForHotReload()
        loadedProcess = null
        log("Hot reload state reset")
        return true
    }

    private fun log(message: String, throwable: Throwable? = null) {
        log(Log.INFO, message, throwable)
    }

    private fun log(priority: Int, message: String, throwable: Throwable? = null) {
        super.log(priority, "DAuxiliary", message, throwable)
    }
}
