package com.macedonga.visor.core.assistant

import org.junit.Assert.*
import org.junit.Test

class DotCallsTest {
    @Test fun credentialLockedDevicesAlwaysRequireAuthentication() {
        assertEquals(DotCallAccess.AUTHENTICATION_REQUIRED, dotCallAccess(deviceLocked = true, keyguardLocked = true))
        assertEquals(DotCallAccess.AUTHENTICATION_REQUIRED, dotCallAccess(deviceLocked = true, keyguardLocked = false))
    }

    @Test fun onlyAlreadyAuthenticatedKeyguardsCanBeDismissed() {
        assertEquals(DotCallAccess.DISMISS_TRUSTED_KEYGUARD, dotCallAccess(deviceLocked = false, keyguardLocked = true))
        assertEquals(DotCallAccess.OPEN, dotCallAccess(deviceLocked = false, keyguardLocked = false))
    }

    @Test fun explicitCallCommandsWorkWithWakeWordAndPunctuation() {
        listOf("Call my dot.", "Hey Meta, call my dot!", "Please call Django", "Can you call my dot, please?",
            "Hey Meta, open ChatGPT.", "Open Chat GPT", "start a ChatGPT call", "Call my agent",
            "start a call with my dot", "Appelle mon dot.", "عيط لدوت ديالي").forEach { assertTrue(it, callsMyDot(it)) }
    }

    @Test fun ordinaryQuestionsAndContactCallsAreNeverIntercepted() {
        listOf("Hello Django", "What can my dot do?", "Call my doctor", "Call my dad", "Call Mom", "Call Django on WhatsApp",
            "Don't call my dot", "Cancel call my dot", "Tell me what call my dot means", "How do I call my dot?",
            "Call my dot and ask it to delete my files", "My dot told me to call Django", "What is your name?")
            .forEach { assertFalse(it, callsMyDot(it)) }
    }

    @Test fun everyRequestedAgentNameSupportsOpenCallAndTalkCommands() {
        assertTrue(callsMyDot("Hey Meta, can you please open Dots?"))
        for (name in listOf("ChatGPT", "Chat GPT", "Chat G P T", "Django", "Jango", "Dots", "Jarvis")) {
            for (command in listOf("Open $name", "Call $name", "Talk to $name", "Start a call with $name", "Connect me to $name"))
                assertTrue(command, callsMyDot("Hey Meta, $command!"))
        }
        for (question in listOf("Who is Jarvis?", "How do I open dots?", "Do not call ChatGPT", "Call Jarvis on WhatsApp",
            "Call my daughter", "Open Django documentation", "Open dots and delete my files"))
            assertFalse(question, callsMyDot(question))
    }
}
