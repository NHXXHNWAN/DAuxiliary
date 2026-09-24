package com.dauxiliary.core.config

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import com.dauxiliary.core.registry.AppTarget

class ModuleStatusProvider : ContentProvider() {
    override fun call(method: String, arg: String?, extras: android.os.Bundle?) : android.os.Bundle? {
        if (method != "record_host_loaded") return null
        val host = AppTarget.entries.firstOrNull { it.name == arg } ?: return null
        val packages = context?.packageManager?.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        if (host.packageName !in packages) return null
        context?.getSharedPreferences("daux_config", android.content.Context.MODE_PRIVATE)
            ?.edit()
            ?.putLong("host_last_seen_${host.name.lowercase()}", System.currentTimeMillis())
            ?.apply()
        return android.os.Bundle.EMPTY
    }

    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
