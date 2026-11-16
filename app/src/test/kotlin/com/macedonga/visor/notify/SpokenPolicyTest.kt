package com.macedonga.visor.notify

import org.junit.Assert.*
import org.junit.Test

class SpokenPolicyTest {
    private fun notice(source: NoticeSource = NoticeSource.WHATSAPP) = SpokenNotice(
        "key", "com.whatsapp", source, "WhatsApp", "Amine", "Private message with a verification code 123456.", 1, 1,
    )

    @Test fun whatsappNeverSendsMessageBodyToSpeechEvenWithFullSetting() {
        for (amount in listOf(ReadAmount.SENDER, ReadAmount.FULL)) {
            val line = spokenLine(notice(), SpokenOptions(whatsapp = amount))!!
            assertEquals("WhatsApp message from Amine.", line)
            assertFalse(line.contains("123456"))
        }
    }

    @Test fun whatsappOffSuppressesEverything() {
        assertNull(spokenLine(notice(), SpokenOptions(whatsapp = ReadAmount.OFF)))
    }

    @Test fun dotsReadsWholeMessageByDefault() {
        val n = notice(NoticeSource.DOTS).copy(app = "ChatGPT", sender = "Django", body = "I finished your task. Here are the results.")
        assertEquals("Django Dots. Django. I finished your task. Here are the results.", spokenLine(n, SpokenOptions()))
    }

    @Test fun textsReadFullMessageOrOnlySenderAsChosen() {
        val n = notice(NoticeSource.SMS)
        assertEquals("Text from Amine. ${n.body}", spokenLine(n, SpokenOptions()))
        assertEquals("Text from Amine.", spokenLine(n, SpokenOptions(sms = ReadAmount.SENDER)))
        assertNull(spokenLine(n, SpokenOptions(sms = ReadAmount.OFF)))
    }

    @Test fun everyOtherAppStartsOff() {
        val n = notice(NoticeSource.OTHER).copy(pkg = "new.app")
        assertNull(spokenLine(n, SpokenOptions()))
        assertNotNull(spokenLine(n, SpokenOptions(other = setOf("new.app"))))
        assertNull(spokenLine(n, SpokenOptions(other = setOf("different.app"))))
    }

    @Test fun outgoingRepliesAndSummariesNeverAnnounce() {
        assertNull(spokenLine(notice().copy(outgoing = true), SpokenOptions()))
        assertNull(spokenLine(notice().copy(summary = true), SpokenOptions()))
    }

    @Test fun ongoingProgressAndCallsNeverAnnounceAsMessages() {
        assertNull(spokenLine(notice(NoticeSource.DOTS).copy(ongoing = true), SpokenOptions()))
        assertNull(spokenLine(notice(NoticeSource.CALL), SpokenOptions()))
    }

    @Test fun explicitIncomingCallAnnouncesCallerAndHonorsToggle() {
        val n = notice(NoticeSource.CALL).copy(incomingCall = true, ongoing = true)
        assertEquals("Incoming call from Amine.", spokenLine(n, SpokenOptions()))
        assertNull(spokenLine(n, SpokenOptions(calls = false)))
    }

    @Test fun missingWhatsappSenderNeverFallsBackToMessageBody() {
        assertEquals("WhatsApp message from Someone.", spokenLine(notice().copy(sender = ""), SpokenOptions()))
    }

    @Test fun onlyAgentChannelsOrExplicitlyNamedDotRepliesMatch() {
        assertTrue(dotNotice("codex", "Finished"))
        assertTrue(dotNotice("codex_remote_avatar", "Django"))
        assertTrue(dotNotice("chatgpt_messaging", "Django"))
        assertTrue(dotNotice("conversation_completion", "Django has an update"))
        for (channel in listOf("marketing", "recommendations", "responses", "conversation_completion", "voice_mode_ongoing", "codex_realtime_voice", "codex_remote_session")) {
            assertFalse(channel, dotNotice(channel, "New update"))
        }
        assertFalse(dotNotice("marketing", "Django"))
        assertFalse(dotNotice("chatgpt_messaging", "A friend"))
    }

    @Test fun longSpeechKeepsEveryWord() {
        val words = (1..1000).map { "word$it" }
        val chunks = speechChunks(words.joinToString(" "))
        assertTrue(chunks.all { it.length <= 500 })
        assertEquals(words, chunks.flatMap { it.split(' ') })
    }

    @Test fun unbrokenTextAlsoSplitsWithoutLoss() {
        val text = "a".repeat(1200)
        assertEquals(text, speechChunks(text).joinToString(""))
    }
}
