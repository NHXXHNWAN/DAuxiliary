package com.dauxiliary.core.xposed

import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/** Runtime scope requests for Telegram clients whose package names are not known at build time. */
object XposedScopeManager {
    private const val TAG = "DAuxiliary-Scope"
    private val pendingPackages = linkedSetOf<String>()
    @Volatile
    private var service: XposedService? = null

    private val listener = object : XposedServiceHelper.OnServiceListener {
        override fun onServiceBind(service: XposedService) {
            this@XposedScopeManager.service = service
            flushPending(service)
        }

        override fun onServiceDied(service: XposedService) {
            if (this@XposedScopeManager.service === service) {
                this@XposedScopeManager.service = null
            }
        }
    }

    @Volatile
    private var listenerRegistered = false

    @Synchronized
    fun requestScope(packages: Set<String>) {
        if (packages.isEmpty()) return
        if (!listenerRegistered) {
            listenerRegistered = true
            runCatching { XposedServiceHelper.registerListener(listener) }
                .onFailure {
                    listenerRegistered = false
                    Log.w(TAG, "Unable to register LSPosed service listener", it)
                }
        }
        val bound = service
        if (bound == null) {
            pendingPackages += packages
            return
        }
        request(bound, packages)
    }

    @Synchronized
    private fun flushPending(service: XposedService) {
        if (pendingPackages.isEmpty()) return
        val request = pendingPackages.toList()
        pendingPackages.clear()
        request(service, request)
    }

    private fun request(service: XposedService, packages: Collection<String>) {
        val requested = packages.distinct()
        runCatching {
            service.requestScope(requested, object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) {
                    Log.i(TAG, "Scope approved: ${approved.joinToString()}")
                }

                override fun onScopeRequestFailed(message: String) {
                    Log.w(TAG, "Scope request failed: $message")
                }
            })
        }.onFailure { error ->
            Log.w(TAG, "Unable to request scope for ${requested.joinToString()}", error)
            synchronized(this) { pendingPackages += requested }
        }
    }
}