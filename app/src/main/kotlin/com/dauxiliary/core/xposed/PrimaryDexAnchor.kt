package com.dauxiliary.core.xposed

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor

/**
 * A harmless app-process component that makes the LibXposed entry class a primary-dex root.
 * Some XP102-compatible loaders resolve java_init.list only from classes.dex.
 */
public class PrimaryDexAnchor : ContentProvider() {
    // Keep a direct primary-dex reference to the LibXposed entry class.
    @Suppress("unused")
    private val entryClass = EntryHook::class.java

    override fun onCreate(): Boolean = true

    @Suppress("OVERRIDE_DEPRECATION")
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: android.os.Bundle?,
        cancellationSignal: CancellationSignal?,
    ): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = null
}