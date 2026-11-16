package com.macedonga.visor.core.assistant

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ReplyQueueTest {
    @Test fun theNextRemoteInteractionCannotStartWhileTheFirstReplyIsGenerating() = runBlocking {
        val queue = ReplyQueue()
        val generating = CompletableDeferred<Unit>()
        val finishPlayback = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val first = launch {
            queue.reply(prepare = { events += "start-1"; true }) {
                generating.complete(Unit)
                finishPlayback.await()
                events += "played-1"
            }
        }
        generating.await()
        val next = launch { queue.reply(prepare = { events += "start-2"; true }) { events += "played-2" } }
        yield()
        assertEquals(listOf("start-1"), events)
        finishPlayback.complete(Unit)
        first.join(); next.join()
        assertEquals(listOf("start-1", "played-1", "start-2", "played-2"), events)
    }

    @Test fun aStaleQueuedTurnDoesNotOpenARemoteInteraction() = runBlocking {
        val queue = ReplyQueue()
        var played = false
        queue.reply(prepare = { false }) { played = true }
        assertFalse(played)
        queue.reply { played = true }
        assertTrue(played)
    }

    @Test fun aSecondReplyWaitsUntilTheFirstPlaybackEnds() = runBlocking {
        val queue = ReplyQueue()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        val firstJob = launch { queue.reply { started.complete(Unit); finish.await() } }
        started.await()
        val secondJob = launch { queue.reply { second.complete(Unit) } }
        yield()
        assertFalse(second.isCompleted)
        finish.complete(Unit)
        firstJob.join(); secondJob.join()
        assertTrue(second.isCompleted)
    }

    @Test fun aNewWakeRequestInterruptsTheOldReplyAndReleasesTheStream() = runBlocking {
        val queue = ReplyQueue()
        val started = CompletableDeferred<Unit>()
        val blocked = CompletableDeferred<Unit>()
        val completedOld = CompletableDeferred<Unit>()
        val old = launch { queue.reply { started.complete(Unit); blocked.await(); completedOld.complete(Unit) } }
        started.await()
        queue.interrupt()
        old.join()
        var nextPlayed = false
        queue.reply { nextPlayed = true }
        assertTrue(old.isCancelled)
        assertFalse(completedOld.isCompleted)
        assertTrue(nextPlayed)
    }
}
