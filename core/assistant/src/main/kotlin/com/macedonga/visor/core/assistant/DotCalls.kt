package com.macedonga.visor.core.assistant

import java.text.Normalizer
import java.util.Locale

/** The native ChatGPT dot call, separate from this app's model, diary and voice. */
interface DotCalls {
    val active: Boolean
    fun unavailableReason(): String?
    suspend fun start(): String?
}

enum class DotCallAccess { OPEN, DISMISS_TRUSTED_KEYGUARD, AUTHENTICATION_REQUIRED }

/** A visible keyguard can be dismissed without credentials only when Android still considers the device authenticated. */
fun dotCallAccess(deviceLocked: Boolean, keyguardLocked: Boolean): DotCallAccess = when {
    deviceLocked -> DotCallAccess.AUTHENTICATION_REQUIRED
    keyguardLocked -> DotCallAccess.DISMISS_TRUSTED_KEYGUARD
    else -> DotCallAccess.OPEN
}

/** Only complete, explicit commands are intercepted; questions and contact calls keep their existing path. */
internal fun callsMyDot(transcript: String): Boolean {
    val normalized = Normalizer.normalize(transcript, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("[\\p{P}\\p{S}]+"), " ").trim().replace(Regex("\\s+"), " ")
        .replace(Regex("^(?:hey meta|hi meta|okay meta|ok meta)\\s+"), "")
        .replace(Regex("^(?:please|(?:can you|could you)(?: please)?)\\s+"), "")
        .replace(Regex("\\s+(?:please|thanks)$"), "")
    return normalized in DOT_CALL_COMMANDS
}

private val DOT_CALL_NAMES = setOf("chatgpt", "chat gpt", "chat g p t", "django", "jango", "dots", "dot", "jarvis",
    "my dot", "my dots", "my agent")

private val DOT_CALL_COMMANDS = DOT_CALL_NAMES.flatMap { name ->
    listOf("open $name", "call $name", "talk to $name", "connect me to $name", "start a call with $name")
}.toSet() + setOf(
    "open chatgpt", "open chat gpt", "open chat g p t", "call chatgpt", "call chat gpt",
    "start a chatgpt call", "start chatgpt call", "start a chat gpt call", "start chat gpt call",
    "call my agent", "talk to my agent", "talk to my dot", "open my dot", "open my dots",
    "call my dot", "call my dots", "call django", "call my django", "call my dot django",
    "call django dot", "call my openai dot", "call my chatgpt dot", "connect me to my dot",
    "start a call with my dot", "start a call with django", "start my dot call",
    "appelle mon dot", "appelle django", "appeler mon dot", "appeler django",
    "عيط لدجانغو", "عيط لدجانجو", "عيط لدوت ديالي", "عيط لدوت", "اتصل بدجانغو", "اتصل بدجانجو",
)
