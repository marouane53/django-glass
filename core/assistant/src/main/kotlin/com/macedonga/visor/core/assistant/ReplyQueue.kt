package com.macedonga.visor.core.assistant

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Replies share one audio stream. A new wake request may cancel the current reply without wedging the queue. */
internal class ReplyQueue {
    private val lock = Mutex()
    @Volatile private var active: Job? = null
    fun interrupt() { active?.cancel() }
    suspend fun reply(prepare: suspend () -> Boolean = { true }, block: suspend () -> Unit) {
        lock.withLock {
            val job = currentCoroutineContext()[Job]
            active = job
            // Protocol setup also owns the stream: starting a new remote interaction closes the previous one.
            try { if (prepare()) block() } finally { if (active === job) active = null }
        }
    }
}
