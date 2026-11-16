package com.macedonga.visor.core.assistant

import com.macedonga.visor.core.protocol.Thrift
import org.junit.Assert.*
import org.junit.Test

class AssistantKeepAliveTest {
    @Test fun cloudSpeechKeepsTheAudioSessionAliveAsWellAsThePendingReply() {
        val events = assistantKeepAlive("turn", partial = true, sequence = 7).toMap()
        assertTrue(events.containsKey(EV_PARTIAL_VOICE_INTERACTION_FULFILLMENT_RESPONSE))
        val lease = Thrift.loads(checkNotNull(events[EV_KEEP_AIR_SESSION_ALIVE]))
        assertEquals("AwaitingResponse", lease[1])
        assertEquals(20_000L, lease[2])
        assertEquals(7L, lease[3])
    }

    @Test fun phoneListeningCanRenewTheLeaseWithoutPretendingAnAnswerIsPending() {
        val events = assistantKeepAlive("session", partial = false, sequence = 8, reason = "AsrStartListening").toMap()
        assertFalse(events.containsKey(EV_PARTIAL_VOICE_INTERACTION_FULFILLMENT_RESPONSE))
        assertEquals("AsrStartListening", Thrift.loads(checkNotNull(events[EV_KEEP_AIR_SESSION_ALIVE]))[1])
    }
}
