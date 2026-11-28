package com.macedonga.visor.core.assistant

import kotlinx.coroutines.channels.Channel

/**
 * An answer still being written, handed out in pieces to speak: the first whole sentence as soon as it is there, then
 * every whole sentence written while the piece before was being fetched. Written from the model's thread, read from
 * the speech's.
 */
internal class Phrases {
    private val pending = StringBuilder()
    private val taken = StringBuilder()
    private var ended = false
    private val changed = Channel<Unit>(Channel.CONFLATED)

    fun add(delta: String) {
        synchronized(this) { if (!ended) pending.append(delta) }
        changed.trySend(Unit)
    }

    /** The answer is whole: what is left is the last piece. */
    fun end() {
        synchronized(this) { ended = true }
        changed.trySend(Unit)
    }

    /** The answer broke off: what was not handed out yet is never spoken. */
    fun fail() {
        synchronized(this) {
            pending.setLength(0)
            ended = true
        }
        changed.trySend(Unit)
    }

    /** Everything handed out so far. */
    fun spoken(): String = synchronized(this) { taken.toString() }

    /** The next piece once one is whole; null when the answer ended and all of it was handed out. */
    suspend fun await(): String? {
        while (true) {
            val (piece, over) = synchronized(this) { take() to ended }
            if (piece != null) return piece
            if (over) return null
            changed.receive()
        }
    }

    /** The next piece now, null while no sentence is whole. */
    fun take(): String? = synchronized(this) {
        val end = if (ended) pending.length else cut(pending, first = taken.isEmpty())
        if (end <= 0) return null
        val piece = pending.substring(0, end)
        pending.delete(0, end)
        taken.append(piece)
        piece
    }

    companion object {
        // A first piece shorter than this, such as "Sure.", waits for the sentence after it.
        const val MIN_FIRST = 20
        private const val ENDS = ".!?…؟。！？"
        private const val CLOSERS = "\"'”’)»]"
        private val ABBREVIATIONS = setOf("mr", "mrs", "ms", "dr", "st", "vs", "etc", "no", "e.g", "i.e", "m", "mme", "mlle")

        /**
         * Where the next piece of [text] ends: after its first whole sentence when [first], else after its last one;
         * 0 when none is whole. A sentence is whole only once the space after it has been written, so "3." is never
         * cut from "3.5".
         */
        fun cut(text: CharSequence, first: Boolean): Int {
            var best = 0
            var i = 0
            while (i < text.length) {
                val c = text[i]
                var after = i + 1
                val breaks = when {
                    c == '\n' -> true
                    c in ENDS -> {
                        while (after < text.length && text[after] in CLOSERS) after++
                        after < text.length && text[after].isWhitespace() && !(c == '.' && abbreviated(text, i))
                    }
                    else -> false
                }
                if (breaks) {
                    while (after < text.length && text[after].isWhitespace()) after++
                    if (text.subSequence(0, i).isNotBlank()) {
                        if (first && after >= MIN_FIRST) return after
                        if (!first) best = after
                    }
                    i = after
                } else i++
            }
            return best
        }

        // "Dr.", "e.g.", an initial or a list number before the full stop: not the end of a sentence.
        private fun abbreviated(text: CharSequence, dot: Int): Boolean {
            var start = dot
            while (start > 0 && !text[start - 1].isWhitespace()) start--
            val word = text.subSequence(start, dot).toString().trimStart('(', '"', '\'', '“')
            return word.length == 1 && word[0].isLetter() || word.isNotEmpty() && word.all { it.isDigit() } ||
                word.lowercase() in ABBREVIATIONS
        }
    }
}
