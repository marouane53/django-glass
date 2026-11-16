package com.macedonga.visor.core.assistant

import com.macedonga.visor.core.protocol.Thrift
import org.junit.Assert.*
import org.junit.Test

class TouchStopTest {
    @Test fun headsetControlsStopOnlyTheNormalConversation() {
        for (action in listOf("PLAY_PAUSE", "PLAY", "PAUSE", "STOP", "NEXT")) {
            assertTrue(action, stopsAssistant("media_key", action, normalSession = true, dotCall = false))
            assertFalse(action, stopsAssistant("media_key", action, normalSession = false, dotCall = false))
            assertFalse(action, stopsAssistant("media_key", action, normalSession = true, dotCall = true))
        }
        assertFalse(stopsAssistant("media_key", "VOLUME_DOWN", normalSession = true, dotCall = false))
        assertFalse(stopsAssistant("media_key", "PREVIOUS", normalSession = true, dotCall = false))
    }

    @Test fun singleTapEndsOnlyAnActiveNormalSession() {
        assertTrue(stopsAssistant("captouch", "SINGLE_TAP", normalSession = true, dotCall = false))
        assertFalse(stopsAssistant("captouch", "SINGLE_TAP", normalSession = false, dotCall = false))
        assertFalse(stopsAssistant("captouch", "SINGLE_TAP", normalSession = true, dotCall = true))
    }

    @Test fun musicVolumeAndCameraInputsKeepTheirOwnBehavior() {
        for (action in listOf("DOUBLE_TAP", "TAP_HOLD", "SWIPE_FORWARD", "SWIPE_BACKWARD", "PRESENCE_TOUCHED", "PRESENCE_CLEAR"))
            assertFalse(action, stopsAssistant("captouch", action, normalSession = true, dotCall = false))
        assertFalse(stopsAssistant("capture_button", "SINGLE_TAP", normalSession = true, dotCall = false))
        assertFalse(stopsAssistant("action_button", "SINGLE_TAP", normalSession = true, dotCall = false))
    }

    @Test fun completionTargetsTheSessionAndNeverRequestsSpeechOrFollowup() {
        val event = parseNetEvent(touchStopCompletion("microphone-session"))
        assertEquals(EV_MWA_VOICE_INTERACTION_COMPLETE, event.id)
        val fields = Thrift.loads(event.body)
        assertEquals("microphone-session", fields[1])
        val result = fields[4] as Map<*, *>
        assertEquals("", result[1])
        assertEquals(false, result[3])
        assertEquals(false, result[7])
        assertEquals(false, result[11])
    }
}
