package com.dauxiliary.core.xposed

import android.util.Log
import com.dauxiliary.core.registry.AppTarget
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/** Intercepts QQNT recall system pushes before QQ applies local deletion. */
internal object QQRecallHook {
    private const val TAG = "DAuxiliary"
    const val FEATURE_ID = "qq.anti_recall"
    private const val CALLBACK_CLASS = "com.tencent.qqnt.kernel.nativeinterface.IQQNTWrapperSession\$CppProxy"
    private const val MSG_PUSH_CMD = "trpc.msg.olpush.OlPushService.MsgPush"
    private val installedLoaders = Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    private val installedMethods = Collections.newSetFromMap(WeakHashMap<Method, Boolean>())

    fun install(xposed: XposedInterface, classLoader: ClassLoader) {
        synchronized(installedLoaders) {
            if (installedLoaders.contains(classLoader)) return
        }

        runCatching {
            val callbackClass = classLoader.loadClass(CALLBACK_CLASS)
            val candidates = callbackClass.declaredMethods.filter(::isMsfPushCallback)
            val method = candidates.singleOrNull()
            if (method == null) {
                Log.w(TAG, "QQ anti-recall skipped: onMsfPush callback unavailable or ambiguous (count=${candidates.size})")
                return
            }
            if (synchronized(installedMethods) { method in installedMethods }) return
            method.isAccessible = true
            xposed.hook(method)
                .setId("qq.recall.nt.msf_push")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val command = chain.getArg(0) as? String
                    val payload = chain.getArg(1) as? ByteArray
                    if (command == MSG_PUSH_CMD && payload != null && isRecallPush(payload)) {
                        // Returning without proceed suppresses only the recall system push.
                        // Ordinary messages and unrecognized payloads follow QQ's original path.
                        Log.i(TAG, "QQ anti-recall blocked a recognized C2C/group recall push")
                        null
                    } else {
                        chain.proceed()
                    }
                }
            synchronized(installedMethods) { installedMethods.add(method) }
            synchronized(installedLoaders) { installedLoaders.add(classLoader) }
            Log.i(TAG, "QQ anti-recall hook installed: ${callbackClass.name}.onMsfPush")
        }.onFailure { error ->
            Log.w(TAG, "QQ anti-recall hook installation failed; original QQ behavior retained", error)
        }
    }

    private fun isMsfPushCallback(method: Method): Boolean {
        val p = method.parameterTypes
        return method.name == "onMsfPush" &&
            method.returnType == Void.TYPE &&
            (p.size == 2 || p.size == 3) &&
            p[0] == String::class.java &&
            p[1] == ByteArray::class.java
    }

    /** MsgPush(1) -> Message(2) -> ContentHead(type=1, subType=2). */
    private fun isRecallPush(data: ByteArray): Boolean = runCatching {
        if (data.isEmpty() || data.size > MAX_PUSH_BYTES) return false
        val message = findLengthDelimitedField(data, 1) ?: return false
        val contentHead = findLengthDelimitedField(message, 2) ?: return false
        val type = findVarintField(contentHead, 1) ?: return false
        val subType = findVarintField(contentHead, 2) ?: return false
        (type == C2C_RECALL_TYPE && subType == C2C_RECALL_SUBTYPE) ||
            (type == GROUP_RECALL_TYPE && subType == GROUP_RECALL_SUBTYPE)
    }.getOrDefault(false)

    private fun findLengthDelimitedField(data: ByteArray, wantedField: Int): ByteArray? {
        var offset = 0
        while (offset < data.size) {
            val tag = readVarint(data, offset) ?: return null
            offset = tag.second
            val field = (tag.first ushr 3).toInt()
            val wire = (tag.first and 7).toInt()
            if (field == 0) return null
            if (wire == WIRE_LENGTH_DELIMITED) {
                val lengthValue = readVarint(data, offset) ?: return null
                offset = lengthValue.second
                val length = lengthValue.first
                if (length < 0 || length > data.size - offset) return null
                val end = offset + length.toInt()
                if (field == wantedField) return data.copyOfRange(offset, end)
                offset = end
            } else {
                offset = skipField(data, offset, wire) ?: return null
            }
        }
        return null
    }

    private fun findVarintField(data: ByteArray, wantedField: Int): Long? {
        var offset = 0
        while (offset < data.size) {
            val tag = readVarint(data, offset) ?: return null
            offset = tag.second
            val field = (tag.first ushr 3).toInt()
            val wire = (tag.first and 7).toInt()
            if (field == 0) return null
            if (wire == WIRE_VARINT) {
                val value = readVarint(data, offset) ?: return null
                offset = value.second
                if (field == wantedField) return value.first
            } else {
                offset = skipField(data, offset, wire) ?: return null
            }
        }
        return null
    }
    private fun skipField(data: ByteArray, offset: Int, wire: Int): Int? {
        return when (wire) {
            WIRE_VARINT -> readVarint(data, offset)?.second
            WIRE_FIXED64 -> (offset + 8).takeIf { it <= data.size }
            WIRE_LENGTH_DELIMITED -> {
                val length = readVarint(data, offset) ?: return null
                val remaining = data.size - length.second
                if (length.first < 0 || length.first > remaining) null
                else length.second + length.first.toInt()
            }
            WIRE_FIXED32 -> (offset + 4).takeIf { it <= data.size }
            else -> null
        }
    }


    private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var offset = start
        while (offset < data.size && shift < 64) {
            val byte = data[offset++].toInt() and 0xff
            result = result or ((byte and 0x7f).toLong() shl shift)
            if (byte and 0x80 == 0) return result to offset
            shift += 7
        }
        return null
    }

    private const val MAX_PUSH_BYTES = 1024 * 1024
    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_FIXED32 = 5
    private const val C2C_RECALL_TYPE = 528L
    private const val C2C_RECALL_SUBTYPE = 138L
    private const val GROUP_RECALL_TYPE = 732L
    private const val GROUP_RECALL_SUBTYPE = 17L
}
