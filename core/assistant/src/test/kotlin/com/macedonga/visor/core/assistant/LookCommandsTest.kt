package com.macedonga.visor.core.assistant

import org.junit.Assert.*
import org.junit.Test

class LookCommandsTest {
    @Test fun capturesOnlyAnExplicitLeadingCommand() {
        assertEquals("tell me what this is", lookPrompt("Look and tell me what this is"))
        assertEquals("read the label", lookPrompt("Hey Meta, look and read the label"))
    }

    @Test fun supportsPoliteWakePunctuationAndTranscriptionSpacing() {
        assertEquals("describe this room.", lookPrompt("Hey Meta. Could you please LOOK, and describe this room."))
        assertEquals("translate this", lookPrompt("Please look and\ntranslate this"))
    }

    @Test fun preservesFrenchAndDarijaQuestions() {
        assertEquals("شنو مكتوب هنا؟", lookPrompt("Hey Meta, look and شنو مكتوب هنا؟"))
        assertEquals("explique ce qui est écrit", lookPrompt("look and explique ce qui est écrit"))
    }

    @Test fun newViewCommandsAreExplicit() {
        assertEquals("describe the new object", lookPrompt("Look again and describe the new object"))
        assertEquals("read it", lookPrompt("Take another picture and read it"))
    }

    @Test fun webSearchesExplanationsAndNegationsDoNotCapture() {
        for (question in listOf("Look up the weather", "Look for a restaurant", "How do I say look and describe it?",
            "Don't look and describe this", "Do not look and take a picture", "What does look and mean?", "Take a picture")) {
            assertNull(question, lookPrompt(question))
        }
    }

    @Test fun anIncompleteCommandDoesNotTakeAPhoto() {
        for (question in listOf("look", "look and", "Hey Meta, look and", "look and ...")) assertNull(question, lookPrompt(question))
    }
}
