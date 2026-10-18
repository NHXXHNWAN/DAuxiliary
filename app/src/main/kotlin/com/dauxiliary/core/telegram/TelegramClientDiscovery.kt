package com.dauxiliary.core.telegram

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.dauxiliary.core.registry.AppTarget
import com.dauxiliary.core.xposed.TelegramHostSupport

/** Discovers installed Telegram-Android clients from shared runtime code markers. */
object TelegramClientDiscovery {
    data class Client(
        val packageName: String,
        val displayName: String,
        val applicationInfo: ApplicationInfo,
    )

    fun findInstalledClients(context: Context): List<Client> {
        val packageManager = context.packageManager
        return launcherApplications(packageManager)
            .asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .filter { candidate ->
                AppTarget.TELEGRAM.matchesPackage(candidate.packageName) ||
                    TelegramHostSupport.hasTelegramMarkers(context, candidate.packageName)
            }
            .map { applicationInfo ->
                Client(
                    packageName = applicationInfo.packageName,
                    displayName = packageManager.getApplicationLabel(applicationInfo).toString(),
                    applicationInfo = applicationInfo,
                )
            }
            .toList()
    }

    @Suppress("DEPRECATION")
    private fun launcherApplications(packageManager: PackageManager): List<ResolveInfo> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            packageManager.queryIntentActivities(launcherIntent, 0)
        }
    }
}
