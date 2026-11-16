package com.macedonga.visor.core.assistant

import org.junit.Assert.*
import org.junit.Test

class ClosedWakeRecoveryTest {
    @Test fun theObservedWakeOnlyCloseCanRecoverTheDarijaQuestionOnce() {
        val recovery = ClosedWakeRecovery()
        val question = "شنو دار المغرب في الماتش ديال هذه السيمانة؟"
        assertTrue(recovery.claim("native", "mic", "mic", true, question, 12))
        assertFalse(recovery.claim("native", "mic", "mic", true, question, 12))
    }

    @Test fun aNewWakeRequestMakesTheOldTranscriptStale() {
        assertFalse(ClosedWakeRecovery().claim("old", "old-mic", "new-mic", true, "a question", 12))
    }

    @Test fun interruptedOrUnknownClosesCannotBeReopened() {
        for (reason in listOf(null, 2L, 36L, 48L)) {
            assertFalse(ClosedWakeRecovery().claim("native", "mic", "mic", true, "a question", reason))
        }
    }

    @Test fun aNativeQuestionWithActualWordsIsNotTreatedAsAnEmptyWake() {
        assertFalse(ClosedWakeRecovery().claim("native", "mic", "mic", false, "a question", 12))
    }

    @Test fun silencePunctuationAndAnotherWakePhraseCannotCreateAReply() {
        for (text in listOf(null, "", "...", "Hey Meta!", "ヘメト。")) {
            assertFalse(ClosedWakeRecovery().claim("native", "mic", "mic", true, text, 12))
        }
    }

    @Test fun anEligibleLaterQuestionIsNotBlockedByAnEarlierIneligibleOne() {
        val recovery = ClosedWakeRecovery()
        assertFalse(recovery.claim("native", "mic", "mic", true, "", 12))
        assertTrue(recovery.claim("native", "mic", "mic", true, "Bonjour, comment ça va ?", 12))
    }
}
