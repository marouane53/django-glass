package com.macedonga.visor.core.assistant

/** Only a deliberate single tap during this phone's normal assistant session ends listening. */
internal fun stopsAssistant(source: String, action: String, normalSession: Boolean, dotCall: Boolean): Boolean =
    normalSession && !dotCall && ((source == "captouch" && action == "SINGLE_TAP") ||
        (source == "media_key" && action in setOf("PLAY_PAUSE", "PLAY", "PAUSE", "STOP", "NEXT")))

/** Close the whole microphone session, not its last answered sub-interaction, with no follow-up or speech. */
internal fun touchStopCompletion(session: String): ByteArray = netEvent(
    EV_MWA_VOICE_INTERACTION_COMPLETE,
    interactionComplete(session, "", expectsAnswer = false, followUp = false),
)
