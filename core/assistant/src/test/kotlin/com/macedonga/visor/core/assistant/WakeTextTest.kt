package com.macedonga.visor.core.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeTextTest {
    @Test fun wakeOnlySegmentsDoNotBecomeQuestions() {
        for (text in listOf("Heme da.", "Hämeto.", "Hé meto.", "ヘメト。", "Hey Meta!")) assertEquals("", WakeText.strip(text))
    }
    @Test fun theQuestionAfterAMisheardWakePhraseIsPreserved() {
        assertEquals("Tell me your name.", WakeText.strip("Heme da. Tell me your name."))
        assertEquals("واش كتفهم الدارجة؟", WakeText.strip("Heme ta. واش كتفهم الدارجة؟"))
    }
    @Test fun aNameHeardInPlaceOfTheWakePhraseIsNotAQuestionOnItsOwn() {
        for (text in listOf("حمادة", "Muhammad", "Hamada.", "محمد؟")) assertTrue(text, WakeText.misheardAlone(text))
        for (text in listOf("Muhammad called me, call him back.", "حمادة جا البارح", "What's the weather like?")) {
            assertFalse(text, WakeText.misheardAlone(text))
        }
    }
    @Test fun ordinaryWordsAreNotRemoved() {
        assertEquals("What is hematology?", WakeText.strip("What is hematology?"))
        assertEquals("Meta makes these glasses.", WakeText.strip("Meta makes these glasses."))
    }
}
