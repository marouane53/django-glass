package com.macedonga.visor.core.assistant

/** Ignore a wake phrase reported as a separate ASR segment, including observed multilingual spellings. */
internal object WakeText {
    private val prefix = Regex(
        "^(?:(?:hey|hi|hé|hei|heme)[ ,.!?]+(?:meta|meto|metta|mata|meda|da|ta)|hämeto|hemeta|hemeda|hemeto|hemata|hemato|ヘメト|هاي[ ]*ميتا|هي[ ]*ميتا)(?=$|[ ,.!?:;؟。])[ ,.!?:;؟。]*",
        RegexOption.IGNORE_CASE,
    )
    fun strip(text: String): String = text.trim().replace(prefix, "").trim()

    // "Hey Meta" and nothing after it comes back from transcription as a name that sounds like it. Only an utterance
    // that is nothing else counts: the same name in a sentence is the wearer's own word.
    private val alone = Regex(
        "^(?:hamada|hamad|hameda|hamida|mohammed|mohamed|mohamad|muhammad|muhammed|حمادة|حماده|حمادا|حميدة|محمد|ميتا)[ .!?,؟،]*$",
        RegexOption.IGNORE_CASE,
    )
    fun misheardAlone(text: String): Boolean = alone.matches(text.trim())
}
