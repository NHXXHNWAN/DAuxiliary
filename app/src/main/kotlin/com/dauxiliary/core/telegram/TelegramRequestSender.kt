package com.dauxiliary.core.telegram

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

/** Sends one learned Telegram callback through the host client's own account/network stack. */
internal class TelegramRequestSender(
    private val loader: ClassLoader,
) {
    data class Result(val text: String = "", val error: String = "")
    private val mainHandler = Handler(Looper.getMainLooper())


    /** Returns false if the host API or the request could not be constructed. */
    fun send(
        dialogId: String,
        account: Int,
        button: TelegramPrefs.LearnedButton,
        callback: (Result) -> Unit,
    ): Boolean {
        return try {
            require(account in 0..99) { "Telegram account index unavailable" }
            val peer = resolveInputPeer(dialogId, account)
                ?: throw IllegalStateException("InputPeer unavailable for dialog $dialogId")
            val requestType = loader.loadClass("org.telegram.tgnet.TLRPC\$TL_messages_getBotCallbackAnswer")
            val request = requestType.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            if (!setField(request, "peer", peer) || !setField(request, "data", button.data) ||
                !setField(request, "msg_id", button.messageId)) {
                throw IllegalStateException("Callback request is missing a required field")
            }
            // Telegram forks that still serialize the legacy flags field require bit 0 set.
            setField(request, "flags", 1)

            val delegateType = loader.loadClass("org.telegram.tgnet.RequestDelegate")
            val delegate = Proxy.newProxyInstance(delegateType.classLoader, arrayOf(delegateType)) { proxy, method, args ->
                when (method.name) {
                    "run" -> {
                        val response = args?.getOrNull(0)
                        val error = args?.getOrNull(1)
                        val result = if (error != null) {
                            Result(error = readText(error, "text", "message") ?: error.toString())
                        } else {
                            Result(text = response?.let { readText(it, "message", "alert", "text") }.orEmpty())
                        }
                        mainHandler.post { callback(result) }
                        null
                    }
                    "toString" -> "DAuxiliaryTelegramRequestDelegate"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    else -> null
                }
            }

            val managerType = loader.loadClass("org.telegram.tgnet.ConnectionsManager")
            val manager = managerType.methods.firstOrNull { method ->
                method.name == "getInstance" && Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.size == 1 && method.parameterTypes[0] == Int::class.javaPrimitiveType
            }?.invoke(null, account) ?: throw IllegalStateException("ConnectionsManager.getInstance unavailable")
            val sendMethod = (managerType.methods.asSequence() + managerType.declaredMethods.asSequence())
                .distinctBy { it.toGenericString() }
                .firstOrNull { method ->
                    method.name == "sendRequest" && method.parameterTypes.size == 2 &&
                        method.parameterTypes[0].isAssignableFrom(requestType) &&
                        method.parameterTypes[1].isAssignableFrom(delegateType)
                } ?: throw IllegalStateException("Compatible ConnectionsManager.sendRequest unavailable")
            sendMethod.isAccessible = true
            sendMethod.invoke(manager, request, delegate)
            Log.i(TAG, "callback request queued dialog=$dialogId msg=${button.messageId} account=$account")
            true
        } catch (error: Throwable) {
            Log.w(TAG, "callback request could not be sent for dialog=$dialogId", error)
            mainHandler.post { callback(Result(error = error.cause?.message ?: error.message ?: error.javaClass.simpleName)) }
            false
        }
    }

    private fun resolveInputPeer(dialogId: String, account: Int): Any? {
        val dialog = dialogId.toLongOrNull() ?: return null
        if (dialog == 0L) return null
        val managerType = loader.loadClass("org.telegram.messenger.MessagesController")
        val controller = managerType.methods.firstOrNull { method ->
            method.name == "getInstance" && Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.size == 1 && method.parameterTypes[0] == Int::class.javaPrimitiveType
        }?.invoke(null, account) ?: return null

        val isUser = dialog > 0
        val absolute = kotlin.math.abs(dialog)
        val isChannel = !isUser && absolute > 1_000_000_000_000L
        val entityId = if (isChannel) absolute - 1_000_000_000_000L else absolute
        val entity = if (isUser) {
            invokeLong(controller, "getUser", entityId)
        } else {
            val candidates = if (isChannel) listOf(entityId, absolute, dialog) else listOf(entityId, dialog)
            candidates.asSequence().mapNotNull { invokeLong(controller, "getChat", it) }.firstOrNull()
        }

        if (entity != null) {
            val convert = managerType.methods.firstOrNull { method ->
                method.name == "getInputPeer" && Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.size == 1 && method.parameterTypes[0].isAssignableFrom(entity.javaClass)
            } ?: managerType.declaredMethods.firstOrNull { method ->
                method.name == "getInputPeer" && Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.size == 1 && method.parameterTypes[0].isAssignableFrom(entity.javaClass)
            }
            if (convert != null) runCatching {
                convert.isAccessible = true
                convert.invoke(null, entity)
            }.getOrNull()?.let { return it }
        }
        val hash = entity?.let { readNumber(it, "access_hash") } ?: 0L

        val peerName = when {
            isUser -> "TL_inputPeerUser"
            isChannel -> "TL_inputPeerChannel"
            else -> "TL_inputPeerChat"
        }
        val peerType = loader.loadClass("org.telegram.tgnet.TLRPC\$$peerName")
        val peer = peerType.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val idField = when {
            isUser -> "user_id"
            isChannel -> "channel_id"
            else -> "chat_id"
        }
        val peerId = entityId
        if (!setField(peer, idField, peerId)) return null
        if ((isUser || isChannel) && (hash == 0L || !setField(peer, "access_hash", hash))) return null
        return peer

    }

    private fun invokeLong(receiver: Any, name: String, value: Long): Any? {
        val method = generateSequence(receiver.javaClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { candidate ->
                candidate.name == name && candidate.parameterTypes.size == 1 &&
                    candidate.parameterTypes[0] in setOf(Long::class.javaPrimitiveType, Long::class.javaObjectType)
            } ?: return null
        return runCatching {
            method.isAccessible = true
            method.invoke(receiver, value)
        }.getOrNull()
    }

    private fun setField(target: Any, name: String, value: Any): Boolean {
        val field = generateSequence(target.javaClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return false
        return runCatching {
            field.isAccessible = true
            val converted: Any = when {
                field.type == Int::class.javaPrimitiveType && value is Number -> value.toInt()
                field.type == Long::class.javaPrimitiveType && value is Number -> value.toLong()
                field.type == ByteArray::class.java && value is ByteArray -> value
                field.type.isInstance(value) -> value
                else -> return false
            }
            field.set(target, converted)
            true
        }.getOrDefault(false)
    }

    private fun readNumber(target: Any, name: String): Long? {
        val value = generateSequence(target.javaClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?.let { field -> runCatching { field.isAccessible = true; field.get(target) }.getOrNull() }
        return (value as? Number)?.toLong()
    }

    private fun readText(target: Any, vararg names: String): String? {
        names.forEach { name ->
            val value = generateSequence(target.javaClass as Class<*>?) { it.superclass }
                .flatMap { it.declaredFields.asSequence() }
                .firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?.let { field -> runCatching { field.isAccessible = true; field.get(target) }.getOrNull() }
            if (value is String && value.isNotBlank()) return value
        }
        return null
    }

    private companion object { const val TAG = "DAuxiliary-Telegram" }
}
