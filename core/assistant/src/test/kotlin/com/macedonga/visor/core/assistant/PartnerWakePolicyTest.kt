package com.macedonga.visor.core.assistant

import org.junit.Assert.*
import org.junit.Test

class PartnerWakePolicyTest {
    private fun observedStart() = mutableMapOf<Int, Any?>(3 to 1L, 5 to 7L, 48 to false, 55 to false)

    @Test fun aPhoneModeStartCanExplicitlyDisableTheSeparateSignal() {
        assertTrue(acceptsDevicePartnerWake(observedStart(), "3p_en_US.0"))
    }

    @Test fun firmwareAdvertisingTheSignalMustStillSendIt() {
        assertFalse(acceptsDevicePartnerWake(observedStart().apply { put(48, true) }, "3p_en_US.0"))
    }

    @Test fun anUnknownSignalCapabilityIsNotTreatedAsDisabled() {
        assertFalse(acceptsDevicePartnerWake(observedStart().apply { remove(48) }, "3p_en_US.0"))
    }

    @Test fun typedAndNonWakeInvocationsDoNotUseThisCompatibilityPath() {
        assertFalse(acceptsDevicePartnerWake(observedStart().apply { put(55, true) }, "3p_en_US.0"))
        assertFalse(acceptsDevicePartnerWake(observedStart().apply { put(3, 5L) }, "3p_en_US.0"))
    }

    @Test fun nativeVoiceAndNonSessionStartsRemainUnchanged() {
        assertFalse(acceptsDevicePartnerWake(observedStart(), "en_US.mai_03"))
        assertFalse(acceptsDevicePartnerWake(observedStart().apply { put(5, 3L) }, "3p_en_US.0"))
    }
}
