package com.macedonga.visor.core.assistant

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withTimeoutOrNull

/** Microphone end precedes native session teardown; recording must wait for both acknowledgements. */
internal class RecordingRelease {
    private val nativeStopped = CompletableDeferred<Unit>()

    fun onNativeState(state: Any?, mode: Any?) {
        // Session COMPLETED (5) or STOPPED (6), with mode NONE (0). STOPPING (10) is not enough.
        if ((state == 5L || state == 6L) && mode == 0L) nativeStopped.complete(Unit)
    }

    suspend fun await(microphone: Deferred<Unit>?, timeoutMs: Long = 1500): Boolean =
        microphone != null && withTimeoutOrNull(timeoutMs) {
            microphone.await()
            nativeStopped.await()
            true
        } == true
}
