package com.macedonga.visor.core.assistant

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RecordingReleaseTest {
    @Test fun microphoneEndDoesNotPermitRecordingWhileNativeSessionIsStopping() = runBlocking {
        val release = RecordingRelease()
        val mic = CompletableDeferred<Unit>().apply { complete(Unit) }
        val ready = async(start = CoroutineStart.UNDISPATCHED) { release.await(mic) }
        assertFalse(ready.isCompleted)
        release.onNativeState(10L, 1L)
        assertFalse(ready.isCompleted)
        release.onNativeState(6L, 1L)
        assertFalse(ready.isCompleted)
        release.onNativeState(6L, 0L)
        assertTrue(ready.await())
    }

    @Test fun nativeCompletionDoesNotPermitRecordingUntilTheMicrophoneEnds() = runBlocking {
        val release = RecordingRelease()
        release.onNativeState(5L, 0L)
        val mic = CompletableDeferred<Unit>()
        val ready = async(start = CoroutineStart.UNDISPATCHED) { release.await(mic) }
        assertFalse(ready.isCompleted)
        mic.complete(Unit)
        assertTrue(ready.await())
    }

    @Test fun missingAcknowledgementsCannotLaunchRecording() = runBlocking {
        val release = RecordingRelease()
        assertFalse(release.await(null))
        release.onNativeState(10L, 0L)
        val mic = CompletableDeferred<Unit>().apply { complete(Unit) }
        assertFalse(release.await(mic, timeoutMs = 5))
    }
}
