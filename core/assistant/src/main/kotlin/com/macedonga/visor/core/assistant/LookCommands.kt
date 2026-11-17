package com.macedonga.visor.core.assistant

/** An explicit photo-analysis prefix; ordinary uses of "look" leave the camera alone. */
internal fun lookPrompt(transcript: String): String? {
    val text = transcript.trim().replace(Regex("[\\s\\p{Z}]+"), " ")
        .replace(Regex("^(?:hey|hi|okay|ok) meta(?:[,.:!]?\\s+)", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^(?:(?:can|could|would) you\\s+)?(?:please\\s+)?", RegexOption.IGNORE_CASE), "")
    return LOOK.matchEntire(text)?.groupValues?.get(1)?.trim()?.takeIf { it.any(Char::isLetterOrDigit) }
}

private val LOOK = Regex("(?:look(?:\\s+again)?|take\\s+(?:another|a new)\\s+(?:photo|picture))\\s*[,.:]?\\s+and\\s+(.+)", RegexOption.IGNORE_CASE)
