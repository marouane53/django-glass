package com.macedonga.visor.language

import com.macedonga.visor.core.assistant.AssistantConfigStore
import com.macedonga.visor.core.assistant.TtsPath
import com.macedonga.visor.core.connection.GlassesConnection
import com.macedonga.visor.core.media.GlassesConfig
import com.macedonga.visor.core.session.LinkState
import com.macedonga.visor.core.settings.GlassesSettings
import com.macedonga.visor.log.EventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Meta's switch for its session ("SCI") assistant path, on for English. There the glasses never speak Visor's text. */
const val SESSION_PATH_FLAG = "wearables_ai_phone_call.session_centric_air_infra"

/** How long the glasses wait for an answer before they speak their own error; 20 s from the factory. */
const val PROCESSING_TIMEOUT = "assistant_on_sn.default_processing_timeout_s"
private const val ASR_TIMEOUT = "assistant_on_sn.default_asr_timeout_s"
private const val PHONE_ASR_TIMEOUT_S = 90L
private const val PHONE_SESSION_TIMEOUT = "wearables_ai_phone_call.session_timeout_seconds"
private const val PHONE_PAYLOAD_TIMEOUT = "wearables_ai_phone_call.server_payload_ack_timeout_ms"
private val DEADLINE_KEYS = setOf(PROCESSING_TIMEOUT, ASR_TIMEOUT, PHONE_SESSION_TIMEOUT, PHONE_PAYLOAD_TIMEOUT)

private data class WantedAssistantConfig(val enabled: Boolean, val own: Boolean, val wait: Long, val phone: Boolean)

private const val CAPTION_VOICE = "live_caption_voice_id"

/** Keeps the answer timeout chosen in the app and, for answers in the glasses' own voice, Meta's session flag off. */
fun keepAssistantConfig(
    scope: CoroutineScope,
    connection: GlassesConnection,
    config: GlassesConfig,
    settings: GlassesSettings,
    store: AssistantConfigStore,
    log: EventLog,
) {
    scope.launch {
        combine(
            connection.state.map { it.link == LinkState.READY }.distinctUntilChanged(),
            store.config.map { Triple(it.enabled, it.tts == TtsPath.GLASSES, it.answerWaitSeconds.toLong()) }.distinctUntilChanged(),
            // A phone language has no glasses voice to keep. Settings come in on connect, well inside the settle below.
            settings.state.map { isPhoneVoice(settings.value(VOICE_KEY)) }.distinctUntilChanged(),
        ) { ready, wanted, partner ->
            WantedAssistantConfig(wanted.first, wanted.second && !partner, wanted.third, partner).takeIf { ready }
        }.collectLatest { wanted ->
            val (enabled, own, wait, phone) = wanted ?: return@collectLatest
            if (!(enabled || own)) return@collectLatest
            // A non-default answer timeout needs the glasses' parameter map too. Keepalives alone do not
            // change their factory 20-second processing deadline. readOnce reuses the pair's saved map.
            if (!own && !phone && wait == 20L && config.state.value.params.isEmpty()) return@collectLatest
            // Let the link settle first.
            delay(SETTLE_MS)
            if (!config.readOnce(READ_MS) { log.add("Assistant: reading the glasses' configuration once") }) return@collectLatest
            val state = config.state.first { it.busy == null }
            val oldAsr = (state.baseline?.get(ASR_TIMEOUT) as? Number)?.toLong()
            val asr = if (enabled && phone) PHONE_ASR_TIMEOUT_S
                else oldAsr?.takeIf { (state.values[ASR_TIMEOUT] as? Number)?.toLong() == PHONE_ASR_TIMEOUT_S }
            val changes = buildMap<String, Any> {
                if (enabled && PROCESSING_TIMEOUT in state.params &&
                    ((state.values[PROCESSING_TIMEOUT] as? Number)?.toLong() != wait ||
                        (state.reported[PROCESSING_TIMEOUT] as? Number)?.toLong() != wait)) put(PROCESSING_TIMEOUT, wait)
                if (asr != null && ASR_TIMEOUT in state.params &&
                    ((state.values[ASR_TIMEOUT] as? Number)?.toLong() != asr ||
                        (state.reported[ASR_TIMEOUT] as? Number)?.toLong() != asr)) put(ASR_TIMEOUT, asr)
                for ((key, phoneValue) in mapOf(PHONE_SESSION_TIMEOUT to 90L, PHONE_PAYLOAD_TIMEOUT to wait * 1000L)) {
                    val value = if (enabled && phone) phoneValue else
                        (state.baseline?.get(key) as? Number)?.toLong()?.takeIf { (state.values[key] as? Number)?.toLong() == phoneValue }
                    if (value != null && key in state.params &&
                        ((state.values[key] as? Number)?.toLong() != value ||
                            (state.reported[key] as? Number)?.toLong() != value)) put(key, value)
                }
                if (own && SESSION_PATH_FLAG in state.params && state.values[SESSION_PATH_FLAG] == true) put(SESSION_PATH_FLAG, false)
            }
            if (changes.isEmpty()) return@collectLatest
            if (PROCESSING_TIMEOUT in changes) log.add("Assistant: letting the glasses wait $wait s for an answer, not ${state.values[PROCESSING_TIMEOUT]}")
            if (ASR_TIMEOUT in changes) log.add("Assistant: setting the recognition timeout to $asr s, not ${state.values[ASR_TIMEOUT]}")
            for (key in listOf(PHONE_SESSION_TIMEOUT, PHONE_PAYLOAD_TIMEOUT)) changes[key]?.let { value ->
                log.add("Assistant: setting $key to $value, not ${state.values[key]}")
            }
            if (SESSION_PATH_FLAG in changes) log.add("Own voice: turning Meta's session path off, so the glasses speak answers in every language")
            config.apply(changes)
            val written = withTimeoutOrNull(WRITE_MS) {
                config.state.first { s -> s.busy == null && changes.all { (key, value) -> s.values[key] == value } }
            }
            if (written == null) return@collectLatest log.add("Assistant: the glasses did not take the change: ${config.state.value.note?.why.orEmpty()}")
            val restarted = restartGlassesAssistant(settings)
            log.add(if (restarted) "Assistant: restarted the glasses' assistant to apply its configuration" else "Assistant: soft restart unavailable; checking the reported configuration")
            val deadlines = changes.filterKeys { it in DEADLINE_KEYS }
            if (deadlines.isNotEmpty()) {
                val readBack = withTimeoutOrNull(READ_MS) { config.readNow() } == true
                for ((key, value) in deadlines) {
                    val reported = (config.state.value.reported[key] as? Number)?.toLong()
                    val unit = if (key.endsWith("_ms")) "ms" else "s"
                    log.add(if (readBack && reported == value) "Assistant: verified $key is $value $unit" else "Assistant: timeout not verified for $key; requested $value $unit, glasses reported $reported")
                }
            }
            if (SESSION_PATH_FLAG in changes && !restarted) log.add("Own voice: restart the glasses to apply its session-path setting")
        }
    }
}

/**
 * Restart only the glasses' assistant, which reads MobileConfig afresh when it starts. It exits on any change of
 * live_caption_voice_id, so that voice goes to another value and straight back.
 */
suspend fun restartGlassesAssistant(settings: GlassesSettings): Boolean {
    // The value to put back must be the glasses' own, so read them first if this run has not.
    if (CAPTION_VOICE !in settings.state.value.stored) {
        settings.read()
        withTimeoutOrNull(SETTINGS_MS) { settings.state.first { CAPTION_VOICE in it.stored } } ?: return false
    }
    val was = settings.state.value.stored[CAPTION_VOICE].orEmpty()
    val other = if (was == "en_US.mai_03") "en_US.mai_01" else "en_US.mai_03"
    if (!settings.writeQuietly(CAPTION_VOICE, other)) return false
    delay(1_500)
    return settings.writeQuietly(CAPTION_VOICE, was)
}

private const val SETTLE_MS = 20_000L
private const val READ_MS = 5 * 60_000L
private const val WRITE_MS = 2 * 60_000L
private const val SETTINGS_MS = 20_000L
