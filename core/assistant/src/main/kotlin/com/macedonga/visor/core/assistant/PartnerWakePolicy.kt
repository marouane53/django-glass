package com.macedonga.visor.core.assistant

import com.macedonga.visor.core.protocol.ThriftWriter

private fun isDevicePartnerWake(start: Map<Int, Any?>, voice: String): Boolean =
    voice.startsWith("3p_") && start[5] == 7L && start[3] == 1L && start[55] != true

/** StartInteraction field 48 advertises whether a separate WW-verified signal will be sent. */
internal fun acceptsDevicePartnerWake(start: Map<Int, Any?>, voice: String): Boolean =
    isDevicePartnerWake(start, voice) && start[48] == false

/** The phone/server completes an advertised wake handshake for a device-initiated wake session. */
internal fun partnerWakeCompletion(start: Map<Int, Any?>, voice: String, session: String, interaction: String): ByteArray? {
    if (!isDevicePartnerWake(start, voice) || start[48] != true || session.isBlank() || interaction.isBlank() || session == interaction) return null
    // WwVerificationComplete: sessionId, interactionId, text. The question transcript is not known yet.
    return ThriftWriter().str(1, session).str(2, interaction).str(3, "").done()
}
