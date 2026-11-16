package com.macedonga.visor.core.assistant

/** Ignore a wake phrase reported as a separate ASR segment, including observed multilingual spellings. */
internal object WakeText {
    private val prefix = Regex(
        "^(?:(?:hey|hi|hé|hei|heme)[ ,.!?]+(?:meta|meto|metta|mata|meda|da|ta)|hämeto|hemeta|hemeda|hemeto|hemata|hemato|ヘメト|هاي[ ]*ميتا|هي[ ]*ميتا)(?=$|[ ,.!?:;؟。])[ ,.!?:;؟。]*",
        RegexOption.IGNORE_CASE,
    )
    fun strip(text: String): String = text.trim().replace(prefix, "").trim()
}
