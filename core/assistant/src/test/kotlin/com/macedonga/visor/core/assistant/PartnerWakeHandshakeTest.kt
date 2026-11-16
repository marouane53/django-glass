package com.macedonga.visor.core.assistant

import com.macedonga.visor.core.protocol.Thrift
import org.junit.Assert.*
import org.junit.Test

class PartnerWakeHandshakeTest {
    private fun observedStart() = mutableMapOf<Int, Any?>(3 to 1L, 5 to 7L, 48 to true, 55 to false)

    @Test fun theObservedEnabledFlagProducesTheSchemaCorrectCompletion() {
        val packet = partnerWakeCompletion(observedStart(), "3p_en_US.0", "session-id", "initial-turn")!!
        val fields = Thrift.loads(packet)
        assertEquals(mapOf(1 to "session-id", 2 to "initial-turn", 3 to ""), fields)
        assertFalse(acceptsDevicePartnerWake(observedStart(), "3p_en_US.0"))
    }

    @Test fun disabledAndUnknownCapabilitiesDoNotSendACompletion() {
        assertNull(partnerWakeCompletion(observedStart().apply { put(48, false) }, "3p_en_US.0", "session", "turn"))
        assertNull(partnerWakeCompletion(observedStart().apply { remove(48) }, "3p_en_US.0", "session", "turn"))
    }

    @Test fun typedNonWakeAndNativeStartsCannotTriggerThisHandshake() {
        assertNull(partnerWakeCompletion(observedStart().apply { put(55, true) }, "3p_en_US.0", "session", "turn"))
        assertNull(partnerWakeCompletion(observedStart().apply { put(3, 5L) }, "3p_en_US.0", "session", "turn"))
        assertNull(partnerWakeCompletion(observedStart(), "en_US.mai_03", "session", "turn"))
    }

    @Test fun missingOrCollidingCorrelationIdsCannotBeAcknowledged() {
        for ((session, turn) in listOf("" to "turn", "session" to "", "same" to "same")) {
            assertNull(partnerWakeCompletion(observedStart(), "3p_en_US.0", session, turn))
        }
    }
}
