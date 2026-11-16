package com.macedonga.visor.core.connection

import com.macedonga.visor.core.session.LinkState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull

/** What the glasses film when asked from the phone: the capture types their assistant's start_recording takes. */
enum class VideoKind(val wire: String) {
    VIDEO("video"),
    HYPERLAPSE("hyperlapse"),
    ;

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.wire == name } ?: VIDEO
    }
}

/**
 * The glasses' camera, worked from the phone as their capture button would: svc28 SNIntent (2300) {1 action, 2 extras}.
 * Normal captures use action.robusta.capture with a launch type. The agentic recording broadcast has no receivers on
 * this Skyler firmware; it remains the hyperlapse path. CLOSE_APP_CAPTURE is their "stop recording".
 * Each answer is the camera state the glasses push on svc28.
 */
class Shutter(private val connection: GlassesConnection) {
    private val _notes = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val notes: SharedFlow<String> = _notes.asSharedFlow()

    val recording: Flow<Boolean> = connection.state.map { it.device?.camera == CAMERA_VIDEO_STARTED }.distinctUntilChanged()

    /** Take a photo. True once the glasses say the capture started. */
    suspend fun photo(): Boolean = ask("photo", { it == CAMERA_PHOTO_STARTED }) {
        SnApp.intent(
            ROBUSTA_CAPTURE,
            listOf(SnApp.data(LAUNCH_TYPE, "CAPTURE_EXTRA_LAUNCH_TYPE_PHOTO"), SnApp.data(REFERRER, "SHORT_BUTTON"), SnApp.data(IS_MODAL, true)),
        )
    }

    /** Start filming. True once the glasses say the video started. */
    suspend fun video(kind: VideoKind = VideoKind.VIDEO): Boolean = ask(kind.wire, { it == CAMERA_VIDEO_STARTED }) {
        if (kind == VideoKind.VIDEO) SnApp.intent(ROBUSTA_CAPTURE, listOf(
            SnApp.data(LAUNCH_TYPE, "CAPTURE_EXTRA_LAUNCH_TYPE_VIDEO"),
            SnApp.data(REFERRER, "SHORT_BUTTON"), SnApp.data(IS_MODAL, true),
        )) else SnApp.intent(AGENTIC_START_RECORDING, listOf(SnApp.data(CAPTURE_TYPE, kind.wire)))
    }

    /** Stop the video. True once the glasses say it stopped, or at once when none is running. */
    suspend fun stop(): Boolean {
        if (connection.state.value.device?.camera != CAMERA_VIDEO_STARTED) return true
        return ask("stop", { it != CAMERA_VIDEO_STARTED }) { SnApp.intent(CLOSE_APP_CAPTURE) }
    }

    /** Stop a running video, or start one. */
    suspend fun toggle(kind: VideoKind = VideoKind.VIDEO): Boolean =
        if (connection.state.value.device?.camera == CAMERA_VIDEO_STARTED) stop() else video(kind)

    private suspend fun ask(what: String, done: (String?) -> Boolean, intent: () -> ByteArray): Boolean {
        if (connection.state.value.link != LinkState.READY) {
            _notes.tryEmit("$what: not connected")
            return false
        }
        val observed = mutableListOf<String>()
        val answered = coroutineScope {
            // Watching before sending, so a quick answer is not missed; the state at hand is not one.
            val seen = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(ANSWER_MS) {
                connection.state.map { it.device?.camera }.distinctUntilChanged().drop(1)
                    .onEach { if (it != null) observed += it }.first(done)
            } }
            connection.submit { it.sendOnService(SVC_STATUS, SnApp.MT_SNINTENT, intent()) }
            seen.await()
        }
        _notes.tryEmit(if (answered != null) "$what: $answered" else "$what: no answer from the glasses")
        android.util.Log.i("DjangoCommands", "Camera state confirmation: $what; confirmed=${answered != null}; states=${observed.joinToString(",")}")
        return answered != null
    }

    private companion object {
        const val ANSWER_MS = 5_000L
        const val ROBUSTA_CAPTURE = "action.robusta.capture"
        const val AGENTIC_START_RECORDING = "com.meta.smartglass.app.intent.action.AGENTIC_START_RECORDING"
        const val CLOSE_APP_CAPTURE = "com.meta.smartglass.app.intent.action.CLOSE_APP_CAPTURE"
        const val LAUNCH_TYPE = "com.meta.smartglass.intent.action.LAUNCH_TYPE"
        const val REFERRER = "android.intent.extra.REFERRER_NAME"
        const val IS_MODAL = "IS_MODAL"
        const val CAPTURE_TYPE = "capture_type"
    }
}
