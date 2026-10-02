package com.dauxiliary.core.telegram

import android.app.Activity
import android.content.Context
import android.util.Log
import com.dauxiliary.core.registry.AppTarget
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/** Safe structural hooks shared by Telegram-Android and compatible forks. */
object TelegramAutoSignHook {
    const val FEATURE_ID = "telegram.auto_sign"
    private const val TAG = "DAuxiliary-Telegram"
    private val installedLoaders = Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    private val installedMethods = Collections.newSetFromMap(WeakHashMap<Method, Boolean>())

    fun install(xposed: XposedInterface, classLoader: ClassLoader, packageName: String) {
        // EntryHook validates known packages and marker-class-detected Telegram forks.
        synchronized(installedLoaders) { if (!installedLoaders.add(classLoader)) return }
        val state = runCatching { TelegramRuntimeState.current(classLoader, packageName) }.getOrElse {
            synchronized(installedLoaders) { installedLoaders.remove(classLoader) }
            Log.w(TAG, "runtime state unavailable; hooks deferred", it)
            return
        }

        var count = 0
        count += hookButtons(xposed, classLoader, state)
        count += hookSendRequest(xposed, classLoader, state)
        count += hookUpdates(xposed, classLoader, state)
        count += hookResume(xposed, classLoader, state)
        log("hooks registered=$count package=$packageName")
        if (count == 0) synchronized(installedLoaders) { installedLoaders.remove(classLoader) }
    }

    fun resetForHotReload() {
        synchronized(installedLoaders) { installedLoaders.clear() }
        synchronized(installedMethods) { installedMethods.clear() }
    }

    private fun hookButtons(xposed: XposedInterface, loader: ClassLoader, state: TelegramRuntimeState): Int {
        val names = listOf("org.telegram.ui.Components.ChatActivityEnterView", "org.telegram.ui.ChatActivity", "org.telegram.ui.ChatActivity\$ChatMessageCellDelegate")
        var count = 0
        names.mapNotNull { runCatching { loader.loadClass(it) }.getOrNull() }.forEach { type ->
            type.declaredMethods.filter { method ->
                method.name == "didPressedBotButton" || method.parameterTypes.any { it.name.contains("KeyboardButton") }
            }.forEach { method ->
                if (hookOnce(xposed, method, "telegram.button.${type.simpleName}") { chain ->
                        runCatching { state.learnButton(Array(method.parameterTypes.size) { index -> chain.getArg(index) }) }
                        chain.proceed()
                    }) count++
            }
        }
        return count
    }

    private fun hookSendRequest(xposed: XposedInterface, loader: ClassLoader, state: TelegramRuntimeState): Int {
        val type = runCatching { loader.loadClass("org.telegram.tgnet.ConnectionsManager") }.getOrNull() ?: return 0
        return type.declaredMethods.filter { it.name == "sendRequest" }.count { method ->
            hookOnce(xposed, method, "telegram.network.${method.parameterTypes.size}") { chain ->
                runCatching { state.observeOutgoingRequest(Array(method.parameterTypes.size) { index -> chain.getArg(index) }) }
                chain.proceed()
            }
        }
    }

    private fun hookUpdates(xposed: XposedInterface, loader: ClassLoader, state: TelegramRuntimeState): Int {
        val type = runCatching { loader.loadClass("org.telegram.messenger.MessagesController") }.getOrNull() ?: return 0
        return type.declaredMethods.filter { it.name.startsWith("processUpdate") }.count { method ->
            hookOnce(xposed, method, "telegram.update.${method.name}.${method.parameterTypes.size}") { chain ->
                runCatching { state.observeIncomingUpdate(Array(method.parameterTypes.size) { index -> chain.getArg(index) }) }
                chain.proceed()
            }
        }
    }

    private fun hookResume(xposed: XposedInterface, loader: ClassLoader, state: TelegramRuntimeState): Int {
        var count = 0
        listOf("org.telegram.ui.ChatActivity", "org.telegram.ui.LaunchActivity").forEach { name ->
            val type = runCatching { loader.loadClass(name) }.getOrNull() ?: return@forEach
            val method = type.declaredMethods.firstOrNull { it.name == "onResume" && it.parameterTypes.isEmpty() } ?: return@forEach
            if (hookOnce(xposed, method, "telegram.resume.$name") { chain ->
                    (chain.getThisObject() as? Activity)?.let(state::noteActivity)
                    chain.proceed()
                }) count++
        }
        return count
    }

    private fun hookOnce(xposed: XposedInterface, method: Method, id: String, callback: (XposedInterface.Chain) -> Any?): Boolean {
        synchronized(installedMethods) { if (!installedMethods.add(method)) return false }
        runCatching {
            method.isAccessible = true
            xposed.hook(method).setId(id).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(callback)
        }.onFailure {
            synchronized(installedMethods) { installedMethods.remove(method) }
            Log.w(TAG, "hook skipped: ${method.toGenericString()}", it)
        }
        return true
    }

    private fun log(message: String) = Log.i(TAG, message)
}

private class TelegramRuntimeState private constructor(
    private val context: Context,
    private val loader: ClassLoader,
    packageName: String,
) {
    private val prefs = TelegramPrefs(context)
    private val accountId = AccountIdentityResolver.resolve(loader, packageName)

    fun learnButton(args: Array<Any?>) {
        val button = args.firstOrNull { it?.javaClass?.name?.contains("KeyboardButton") == true } ?: return
        val text = ReflectionText.find(button, "text", "label") ?: return
        val data = ReflectionText.find(button, "data", "callbackData").orEmpty()
        if (SignLogic.isNavigationButton(text, data)) return
        val message = args.firstOrNull { it?.javaClass?.name?.contains("MessageObject") == true }
        val id = SignLogic.normalizeId(ReflectionText.find(message, "dialogId", "dialog_id").orEmpty()).ifEmpty { "learned" }
        prefs.addTarget(id, accountId)
        prefs.saveLearnedButton(accountId, id, text, data)
        Log.i("DAuxiliary-Telegram", "learned button target=$id account=$accountId")
    }

    fun observeOutgoingRequest(args: Array<Any?>) {
        val request = args.firstOrNull() ?: return
        if (request.javaClass.name.contains("Messages") || request.javaClass.name.contains("SendMessage")) {
            Log.d("DAuxiliary-Telegram", "outgoing request=${request.javaClass.name}")
        }
    }

    fun observeIncomingUpdate(args: Array<Any?>) {
        val update = args.firstOrNull() ?: return
        val text = ReflectionText.find(update, "message", "text", "messageText") ?: return
        val verdict = SignLogic.verdict(text)
        if (verdict != SignLogic.UNKNOWN) Log.i("DAuxiliary-Telegram", "reply verdict=$verdict text=${text.take(80)}")
    }

    fun noteActivity(activity: Activity) = Log.d("DAuxiliary-Telegram", "active=${activity.javaClass.name}")

    companion object {
        private val states = Collections.synchronizedMap(WeakHashMap<ClassLoader, TelegramRuntimeState>())
        fun current(loader: ClassLoader, packageName: String): TelegramRuntimeState = states.getOrPut(loader) {
            val app = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as Context
            TelegramRuntimeState(app, loader, packageName)
        }
    }
}

private object ReflectionText {
    fun find(value: Any?, vararg preferred: String): String? {
        if (value == null) return null
        preferred.forEach { name ->
            var type: Class<*>? = value.javaClass
            while (type != null && type != Any::class.java) {
                val field = type.declaredFields.firstOrNull { it.name.equals(name, true) }
                if (field != null) return runCatching { field.isAccessible = true; field.get(value)?.toString() }.getOrNull()
                type = type.superclass
            }
        }
        return null
    }
}