package com.macedonga.visor.core.assistant

/** A native wake-only success may finish before the phone's real-language transcript arrives. */
internal class ClosedWakeRecovery {
    private val recovered = mutableSetOf<String>()

    @Synchronized
    fun claim(
        interaction: String, microphone: String, currentMicrophone: String?,
        nativeWakeOnly: Boolean, phoneText: String?, completionReason: Long?,
    ): Boolean {
        // 12 is the observed successful native completion, never an interruption, timeout or unknown close.
        if (completionReason != 12L || !nativeWakeOnly || microphone.isBlank() || microphone != currentMicrophone) return false
        val question = WakeText.strip(phoneText.orEmpty())
        if (question.none { it.isLetterOrDigit() }) return false
        return recovered.add(interaction)
    }
}
