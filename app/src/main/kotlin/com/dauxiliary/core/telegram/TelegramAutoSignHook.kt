package com.dauxiliary.core.telegram

import android.app.Activity
import android.content.Context
import android.util.Log
import com.dauxiliary.core.config.ConfigStore
import com.dauxiliary.core.registry.AppTarget
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
private const val TAG = "DAuxiliary-Telegram"
private const val MAX_RETRIES = 5


/** Safe structural hooks shared by Telegram-Android and compatible forks. */
object TelegramAutoSignHook {
    const val FEATURE_ID = "telegram.auto_sign"
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
                val result = chain.proceed()
                runCatching { state.observeIncomingUpdate(Array(method.parameterTypes.size) { index -> chain.getArg(index) }) }
                    .onFailure { Log.w(TAG, "update observer failed for ${method.name}", it) }
                result
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
    private val packageName: String,
) {
    private val prefs = TelegramPrefs(context)

    private fun accountId(): String = AccountIdentityResolver.resolve(loader, packageName)

    private fun authorized(): Boolean = runCatching {
        ConfigStore.isTelegramAuthorized(context) || prefs.authorized()
    }.getOrDefault(false)

    fun learnButton(args: Array<Any?>) {
        val button = args.firstOrNull { it?.javaClass?.name?.contains("KeyboardButton", ignoreCase = true) == true } ?: return
        val text = ReflectionText.find(button, "text", "label")?.trim().orEmpty()
        val rawData = ReflectionText.findObject(button, "data", "callbackData")
        val data: ByteArray? = when (rawData) {
            is ByteArray -> rawData
            is String -> rawData.toByteArray(Charsets.UTF_8)
            else -> null
        }
        val callbackData = data ?: return
        val dataText = callbackData.toString(Charsets.UTF_8)
        if (callbackData.isEmpty() || !SignLogic.looksLikeSignButton(text, dataText)) return

        val message = args.firstOrNull { it?.javaClass?.name?.contains("MessageObject", ignoreCase = true) == true }
            ?: args.firstOrNull { candidate -> candidate != null && ReflectionText.findObject(candidate, "messageOwner") != null }
            ?: return
        val dialogId = SignLogic.normalizeId(
            ReflectionText.invokeNumber(message, "getDialogId")
                ?: ReflectionText.find(message, "dialogId", "dialog_id"),
        ).takeIf(String::isNotBlank) ?: return
        val messageOwner = ReflectionText.findObject(message, "messageOwner") ?: message
        val messageId = (ReflectionText.invokeNumber(message, "getId")
            ?: ReflectionText.find(messageOwner, "id", "messageId"))?.toIntOrNull() ?: 0
        if (messageId <= 0) return

        val account = accountId()
        prefs.addTarget(dialogId, account)
        prefs.saveLearnedButton(account, dialogId, text, callbackData, messageId)
        // This hook runs because the user already pressed the button; do not send a second callback here.
        Log.i(TAG, "learned explicit sign callback target=$dialogId account=$account")
    }

    fun observeOutgoingRequest(args: Array<Any?>) {
        val request = args.firstOrNull() ?: return
        if (request.javaClass.name.contains("Messages") || request.javaClass.name.contains("SendMessage")) {
            Log.d(TAG, "outgoing request observed=${request.javaClass.name}")
        }
    }

    fun observeIncomingUpdate(args: Array<Any?>) {
        runCatching { processIncomingUpdates(args) }
            .onFailure { Log.w(TAG, "incoming update inspection failed", it) }
    }

    private fun processIncomingUpdates(args: Array<Any?>) {
        val accountKey = accountId()
        for (message in ReflectionText.findMessages(args)) {
            val dialogId = ReflectionText.dialogId(message) ?: continue
            if (dialogId !in prefs.targets(accountKey)) continue
            val messageOwner = ReflectionText.findObject(message, "messageOwner") ?: message
            if (ReflectionText.boolean(message, "out", "isOut") || ReflectionText.boolean(messageOwner, "out", "isOut")) continue
            val text = ReflectionText.find(message, "message", "text", "messageText")
                ?: ReflectionText.find(messageOwner, "message", "text", "messageText").orEmpty()
            when (SignLogic.verdict(text)) {
                SignLogic.SIGNED -> {
                    prefs.markSigned(dialogId, accountKey)
                    Log.i(TAG, "target confirmed signed dialog=$dialogId")
                }
                SignLogic.FAILED, SignLogic.EXHAUSTED -> {
                    prefs.markPending(dialogId, accountKey)
                    Log.i(TAG, "target response needs review dialog=$dialogId verdict=${SignLogic.verdict(text)}")
                }
            }

            val messageId = ReflectionText.number(message, "id", "messageId")?.toInt() ?: 0
            if (messageId <= 0) continue
            val markup = ReflectionText.findObject(message, "reply_markup", "replyMarkup") ?: continue
            val learned = ReflectionText.findButtons(markup).asSequence().mapNotNull { button ->
                val label = ReflectionText.find(button, "text", "label")?.trim().orEmpty()
                val data: ByteArray? = when (val raw = ReflectionText.findObject(button, "data", "callbackData")) {
                    is ByteArray -> raw
                    is String -> raw.toByteArray(Charsets.UTF_8)
                    else -> null
                }
                data?.takeIf { it.isNotEmpty() && SignLogic.looksLikeSignButton(label, it.toString(Charsets.UTF_8)) }
                    ?.let { TelegramPrefs.LearnedButton(label, it, messageId) }
            }.firstOrNull() ?: continue

            prefs.saveLearnedButton(accountKey, dialogId, learned.text, learned.data, learned.messageId)
            Log.i(TAG, "fresh sign button observed dialog=$dialogId msg=$messageId")
            attemptSign(dialogId, accountKey, learned)
        }
    }

    private fun attemptSign(dialogId: String, accountKey: String, learned: TelegramPrefs.LearnedButton) {
        if (!prefs.enabled() || !authorized()) return
        val accountIndex = AccountIdentityResolver.resolveIndex(loader) ?: run {
            Log.w(TAG, "cannot send callback without an exact Telegram account index")
            return
        }
        val decision = SignLogic.shouldSend(
            signedToday = prefs.signedToday(dialogId, accountKey),
            inFlight = prefs.inFlight(dialogId, accountKey),
            pending = prefs.pendingToday(dialogId, accountKey),
            disabled = !prefs.enabled() || !authorized(),
            retryAt = prefs.retryAt(dialogId, accountKey),
            now = System.currentTimeMillis(),
        )
        if (decision != SignLogic.SKIP_NONE) return
        if (!prefs.markInFlight(dialogId, accountKey)) return

        val sender = TelegramRequestSender(loader)
        val queued = sender.send(dialogId, accountIndex, learned) { result ->
            runCatching { completeSign(dialogId, accountKey, result) }
                .onFailure { Log.w(TAG, "sign completion handling failed dialog=$dialogId", it) }
        }
        if (!queued) Log.w(TAG, "callback request was not queued dialog=$dialogId")
    }

    private fun completeSign(dialogId: String, accountKey: String, result: TelegramRequestSender.Result) {
        if (result.error.isBlank()) {
            when (SignLogic.verdict(result.text)) {
                SignLogic.SIGNED -> {
                    prefs.markSigned(dialogId, accountKey)
                    Log.i(TAG, "callback answer confirmed signed dialog=$dialogId")
                }
                SignLogic.FAILED -> {
                    prefs.setRetry(dialogId, prefs.retryCount(dialogId, accountKey) + 1, accountKey)
                    Log.w(TAG, "callback answer reported failure dialog=$dialogId")
                }
                else -> {
                    prefs.markPending(dialogId, accountKey)
                    Log.i(TAG, "callback answer has no definitive verdict dialog=$dialogId")
                }
            }
            return
        }

        val error = result.error.uppercase()
        val floodSeconds = Regex("FLOOD_WAIT[_ ]?(\\d+)").find(error)?.groupValues?.getOrNull(1)?.toLongOrNull()
        when {
            floodSeconds != null -> {
                val waitMs = floodSeconds.coerceIn(1L, 86_400L) * 1_000L
                prefs.setRetryAt(dialogId, System.currentTimeMillis() + waitMs, accountKey)
                Log.w(TAG, "Telegram flood wait dialog=$dialogId seconds=$floodSeconds")
            }
            listOf("PEER_ID_INVALID", "USER_BOT_INVALID", "CHAT_WRITE_FORBIDDEN", "USER_PRIVACY_RESTRICTED", "USER_DEACTIVATED").any(error::contains) -> {
                prefs.markPending(dialogId, accountKey)
                Log.w(TAG, "permanent Telegram request failure dialog=$dialogId error=$error")
            }
            listOf("DATA_INVALID", "MESSAGE_ID_INVALID", "MESSAGE_NOT_FOUND", "BUTTON").any(error::contains) -> {
                if (prefs.retryCount(dialogId, accountKey) >= MAX_RETRIES) prefs.markPending(dialogId, accountKey)
                else prefs.setRetry(dialogId, prefs.retryCount(dialogId, accountKey) + 1, accountKey)
                Log.w(TAG, "callback panel may be stale dialog=$dialogId error=$error")
            }
            else -> {
                if (prefs.retryCount(dialogId, accountKey) >= MAX_RETRIES) prefs.markPending(dialogId, accountKey)
                else prefs.setRetry(dialogId, prefs.retryCount(dialogId, accountKey) + 1, accountKey)
                Log.w(TAG, "callback request failed dialog=$dialogId error=$error")
            }
        }
    }

    fun noteActivity(activity: Activity) = Log.d(TAG, "active=${activity.javaClass.name}")

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

    fun findObject(value: Any?, vararg preferred: String): Any? {
        if (value == null) return null
        preferred.forEach { name ->
            var type: Class<*>? = value.javaClass
            while (type != null && type != Any::class.java) {
                val field = type.declaredFields.firstOrNull { it.name.equals(name, true) }
                if (field != null) return runCatching { field.isAccessible = true; field.get(value) }.getOrNull()
                type = type.superclass
            }
        }
        return null
    }

    fun number(value: Any?, vararg names: String): Number? {
        names.forEach { name ->
            val fieldValue = findObject(value, name)
            if (fieldValue is Number) return fieldValue
            val text = fieldValue?.toString()?.trim()?.toLongOrNull()
            if (text != null) return text
        }
        return null
    }

    fun invokeNumber(value: Any?, name: String): String? {
        if (value == null) return null
        val method = generateSequence(value.javaClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { it.name.equals(name, true) && it.parameterTypes.isEmpty() } ?: return null
        return runCatching {
            method.isAccessible = true
            method.invoke(value)?.toString()
        }.getOrNull()
    }

    fun boolean(value: Any?, vararg names: String): Boolean {
        names.forEach { name ->
            val candidate = findObject(value, name)
            if (candidate is Boolean) return candidate
            if (candidate?.toString()?.equals("true", true) == true) return true
        }
        return false
    }

    fun dialogId(message: Any?): String? {
        if (message == null) return null
        invokeNumber(message, "getDialogId")?.let { value ->
            SignLogic.normalizeId(value).takeIf(String::isNotBlank)?.let { return it }
        }
        find(message, "dialogId", "dialog_id")?.let { value ->
            SignLogic.normalizeId(value).takeIf(String::isNotBlank)?.let { return it }
        }
        val owner = findObject(message, "messageOwner") ?: message
        find(owner, "dialogId", "dialog_id")?.let { value ->
            SignLogic.normalizeId(value).takeIf(String::isNotBlank)?.let { return it }
        }
        val peer = findObject(owner, "peer_id", "to_id", "peerId") ?: return null
        val peerClass = peer.javaClass.simpleName.lowercase()
        val id = when {
            "peerchannel" in peerClass -> number(peer, "channel_id", "channelId")?.toLong()
                ?.let { -(1_000_000_000_000L + it) }
            "peerchat" in peerClass -> number(peer, "chat_id", "chatId")?.toLong()?.unaryMinus()
            "peeruser" in peerClass -> number(peer, "user_id", "userId")?.toLong()
            else -> null
        }
        return id?.toString()
    }

    fun findMessages(args: Array<Any?>): List<Any> {
        val result = ArrayList<Any>()
        fun visit(value: Any?, depth: Int) {
            if (value == null || depth > 4) return
            when (value) {
                is Iterable<*> -> value.forEach { visit(it, depth + 1) }
                is Array<*> -> value.forEach { visit(it, depth + 1) }
                else -> {
                    val name = value.javaClass.name
                    if (name.contains("Message", true) && (find(value, "message", "text", "messageText") != null || findObject(value, "messageOwner") != null)) {
                        result += value
                    } else {
                        findObject(value, "message", "messages", "updates")?.let { visit(it, depth + 1) }
                    }
                }
            }
        }
        args.forEach { visit(it, 0) }
        return result.distinctBy { System.identityHashCode(it) }
    }

    fun findButtons(markup: Any?): List<Any> {
        val result = ArrayList<Any>()
        fun visit(value: Any?, depth: Int) {
            if (value == null || depth > 4) return
            when (value) {
                is Iterable<*> -> value.forEach { visit(it, depth + 1) }
                is Array<*> -> value.forEach { visit(it, depth + 1) }
                else -> {
                    if (value.javaClass.name.contains("KeyboardButton", true)) result += value
                    else findObject(value, "rows", "buttons")?.let { visit(it, depth + 1) }
                }
            }
        }
        visit(markup, 0)
        return result.distinctBy { System.identityHashCode(it) }
    }
}