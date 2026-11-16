package com.macedonga.visor.core.assistant

/** One spoken follow-up can be reported by both the glasses and phone ASR. Only one owns the reply. */
internal class FollowupCoordinator {
    data class Native(val id: String, val text: String)
    private var pending: Native? = null
    private val phoneOwners = mutableSetOf<String>()
    private var lastPhoneFinished = Long.MIN_VALUE
    private var nativeFallbackOwner: String? = null
    private var lastNativeFinished = Long.MIN_VALUE

    @Synchronized
    fun offerNative(id: String, text: String, now: Long): Boolean {
        if (phoneOwners.isNotEmpty() || nativeFallbackOwner != null || (lastPhoneFinished != Long.MIN_VALUE && now - lastPhoneFinished < 1_500)) return false
        pending = Native(id, text)
        return true
    }

    @Synchronized
    fun claimPhone(fallbackId: String, now: Long): String? {
        if (nativeFallbackOwner != null || (lastNativeFinished != Long.MIN_VALUE && now - lastNativeFinished < 1_500)) return null
        val id = pending?.id ?: fallbackId
        pending = null
        phoneOwners += id
        return id
    }

    @Synchronized
    fun finishPhone(id: String, now: Long) {
        phoneOwners -= id
        lastPhoneFinished = now
    }

    @Synchronized
    fun claimNativeFallback(id: String): Native? = pending?.takeIf { it.id == id }?.also {
        pending = null
        nativeFallbackOwner = id
    }

    @Synchronized
    fun finishNativeFallback(id: String, now: Long) {
        if (nativeFallbackOwner == id) { nativeFallbackOwner = null; lastNativeFinished = now }
    }

    @Synchronized
    fun isPending(id: String): Boolean = pending?.id == id

    @Synchronized
    fun currentPhone(): String? = phoneOwners.firstOrNull()
}
