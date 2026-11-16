package com.macedonga.visor.notify

enum class ReadAmount(val label: String) { OFF("Off"), SENDER("Sender only"), FULL("Full message") }
enum class NoticeSource(val label: String) { DOTS("Django / Dots"), WHATSAPP("WhatsApp"), SMS("Texts"), CALL("Incoming calls"), OTHER("Other apps") }

/** Content stays in memory. No notification is an instruction to the assistant. */
data class SpokenNotice(
    val key: String,
    val pkg: String,
    val source: NoticeSource,
    val app: String,
    val sender: String,
    val body: String,
    val stamp: Long,
    val posted: Long,
    val channel: String = "",
    val outgoing: Boolean = false,
    val summary: Boolean = false,
    val ongoing: Boolean = false,
    val incomingCall: Boolean = false,
)

data class SpokenOptions(
    val dots: ReadAmount = ReadAmount.FULL,
    val whatsapp: ReadAmount = ReadAmount.SENDER,
    val sms: ReadAmount = ReadAmount.FULL,
    val calls: Boolean = true,
    val chime: Boolean = true,
    val other: Set<String> = emptySet(),
) {
    fun amount(n: SpokenNotice): ReadAmount = when (n.source) {
        NoticeSource.DOTS -> dots
        NoticeSource.WHATSAPP -> if (whatsapp == ReadAmount.OFF) ReadAmount.OFF else ReadAmount.SENDER
        NoticeSource.SMS -> sms
        NoticeSource.CALL -> if (calls) ReadAmount.SENDER else ReadAmount.OFF
        NoticeSource.OTHER -> if (n.pkg in other) ReadAmount.FULL else ReadAmount.OFF
    }
}

/** Ordinary ChatGPT promotions, streaming notices and voice-session controls are excluded. */
fun dotNotice(channel: String, title: String): Boolean =
    (channel == "codex" || channel.startsWith("codex_")) && channel !in setOf("codex_realtime_voice", "codex_remote_session") ||
        channel in setOf("chatgpt_messaging", "responses", "conversation_completion") && Regex("\\b(django|dots?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(title)

fun spokenLine(n: SpokenNotice, options: SpokenOptions): String? {
    if (n.outgoing || n.summary || n.ongoing && !n.incomingCall) return null
    val amount = options.amount(n)
    if (amount == ReadAmount.OFF) return null
    val sender = n.sender.trim().ifEmpty { "Someone" }
    if (n.source == NoticeSource.CALL) return if (n.incomingCall) "Incoming call from $sender." else null
    val prefix = when (n.source) {
        NoticeSource.WHATSAPP -> "WhatsApp message from $sender."
        NoticeSource.SMS -> "Text from $sender."
        NoticeSource.DOTS -> if (n.sender.isBlank() || n.sender == n.app) "Django Dots." else "Django Dots. $sender."
        else -> if (n.sender.isBlank() || n.sender == n.app) "${n.app}." else "${n.app}. $sender."
    }
    return if (amount == ReadAmount.SENDER) prefix else n.body.trim().takeIf { it.isNotEmpty() }?.let { "$prefix $it" }
}

/** Split long announcements without dropping any words. */
fun speechChunks(text: String, limit: Int = 500): List<String> {
    val chunks = mutableListOf<String>()
    var rest = text.trim()
    while (rest.length > limit) {
        val boundary = rest.lastIndexOf(' ', limit).takeIf { it > limit / 2 } ?: limit
        chunks += rest.take(boundary)
        rest = rest.substring(boundary).trimStart()
    }
    if (rest.isNotEmpty()) chunks += rest
    return chunks
}
