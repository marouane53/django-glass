package com.macedonga.visor.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.macedonga.visor.core.assistant.Assistant
import com.macedonga.visor.core.assistant.AssistantConfigStore
import com.macedonga.visor.core.assistant.AssistantPhase
import com.macedonga.visor.core.assistant.LiveAiPhase
import com.macedonga.visor.core.assistant.SpeechPcm
import com.macedonga.visor.core.assistant.cloudSpeech
import com.macedonga.visor.core.connection.GlassesConnection
import com.macedonga.visor.core.connection.GlassesLink
import com.macedonga.visor.core.session.LinkState
import com.macedonga.visor.dots.DotCallController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Serial, cancellable Finn announcements, only through this pair of glasses. */
@Singleton
class SpokenReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connection: GlassesConnection,
    private val assistant: Assistant,
    private val config: AssistantConfigStore,
    private val choice: NotifyChoice,
    private val settings: SpokenSettings,
    private val dots: DotCallController,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = LinkedHashMap<String, SpokenNotice>()
    private val seen = LinkedHashSet<String>()
    private val removed = LinkedHashSet<String>()
    @Volatile private var currentInterruptionMatch: ((String) -> Boolean?)? = null
    @Volatile private var playing: Job? = null
    @Volatile private var currentKey: String? = null
    @Volatile private var currentNotice: SpokenNotice? = null
    @Volatile private var track: AudioTrack? = null
    private val _status = MutableStateFlow("Ready for selected notifications")
    val status = _status.asStateFlow()

    init {
        assistant.beforeListening = ::interrupt
        scope.launch {
            while (isActive) {
                if (playing?.isActive == true && (!choice.enabled.value || busy(currentNotice))) interrupt()
                if (currentInterruptionMatch != null && playing?.isActive != true) next()?.let { n ->
                    playing = launch { announce(n) }
                }
                delay(100)
            }
        }
    }

    fun offer(n: SpokenNotice, matchesInterruptionFilter: Boolean?) {
        if (!choice.allowsDelivery(matchesInterruptionFilter, n.preview) || spokenLine(n, settings.options.value) == null || !fresh(n)) return
        // Reposts and progress updates of the same message should not talk again.
        val fingerprint = "${n.key}|${n.stamp}|${n.sender.hashCode()}|${n.body.hashCode()}"
        synchronized(queue) {
            removed.remove(n.key)
            if (!seen.add(fingerprint)) return
            while (seen.size > 256) seen.remove(seen.first())
            queue.remove(n.key)
            queue[n.key] = n
            while (queue.size > 25) queue.remove(queue.keys.first())
        }
        if (n.incomingCall && currentNotice?.incomingCall == false) interrupt()
        android.util.Log.i("DjangoNotify", "Queued source=${n.source}; senderOnly=${settings.options.value.amount(n) == ReadAmount.SENDER}")
        _status.value = if (busy(n)) "Waiting until your conversation ends" else "Notification queued for glasses"
    }

    fun remove(key: String) {
        synchronized(queue) {
            queue.remove(key)
            removed.add(key)
            while (removed.size > 256) removed.remove(removed.first())
        }
        if (currentKey == key) interrupt()
    }

    /** Called synchronously before the assistant opens its microphone, and on focus/route loss. */
    fun interrupt() {
        playing?.cancel()
        runCatching { track?.pause() }
    }

    fun connected(interruptionMatch: (String) -> Boolean?) { currentInterruptionMatch = interruptionMatch }
    fun disconnected() {
        currentInterruptionMatch = null
        interrupt()
        synchronized(queue) { queue.clear() }
    }

    private fun fresh(n: SpokenNotice) = System.currentTimeMillis() - n.posted in 0..(if (n.incomingCall) 12_000L else 120_000L)

    private fun allowsDelivery(n: SpokenNotice): Boolean {
        val match = currentInterruptionMatch ?: return false
        return choice.allowsDelivery(if (n.preview) null else match(n.key), n.preview)
    }

    private fun next(): SpokenNotice? = synchronized(queue) {
        val n = queue.values.firstOrNull { it.incomingCall } ?: queue.values.firstOrNull() ?: return null
        if (!fresh(n) || !allowsDelivery(n) || spokenLine(n, settings.options.value) == null) {
            queue.remove(n.key)
            return null
        }
        if (busy(n) || sink() == null) return null
        queue.remove(n.key)
        n
    }

    private fun busy(n: SpokenNotice?): Boolean {
        val s = connection.state.value
        return s.link != LinkState.READY || s.device?.worn == false || s.device?.hinge == "CLOSED" ||
            assistant.conversationActive || assistant.phase.value in setOf(AssistantPhase.THINKING, AssistantPhase.SPEAKING) ||
            assistant.liveAiPhase.value != LiveAiPhase.OFF || dots.active ||
            s.device?.assistantAttention in setOf("WAKE", "LISTENING", "PROCESSING", "RESPONDING", "LONG_RUNNING_SESSION_ACTIVATED") ||
            s.device?.camera?.contains("VIDEO_STARTED") == true ||
            audio.mode != AudioManager.MODE_NORMAL && !(n?.incomingCall == true && audio.mode == AudioManager.MODE_RINGTONE)
    }

    private fun sink(): AudioDeviceInfo? {
        val s = connection.state.value
        val addresses = setOfNotNull(s.info?.btcAddress?.takeIf { it.isNotBlank() }, (s.target?.link as? GlassesLink.Bluetooth)?.address)
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { d ->
            d.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET) && addresses.any { it.equals(d.address, true) }
        }
    }

    private suspend fun announce(n: SpokenNotice) {
        currentKey = n.key
        currentNotice = n
        val preparing = "Preparing ${n.source.label} announcement"
        try {
            val line = spokenLine(n, settings.options.value) ?: return
            _status.value = preparing
            for ((index, chunk) in speechChunks(line).withIndex()) {
                if (!canAnnounce(n, line)) return
                val pcm = withTimeout(35_000) { cloudSpeech(config.config.value, chunk) }
                currentCoroutineContext().ensureActive()
                if (!canAnnounce(n, line) || !play(pcm, n, index == 0 && settings.options.value.chime)) return
            }
            _status.value = "Last announcement played through glasses"
            android.util.Log.i("DjangoNotify", "Played source=${n.source}; route=glasses")
        } catch (e: CancellationException) {
            _status.value = "Announcement stopped; assistant has priority"
            throw e
        } catch (_: Exception) {
            // Exception messages from remote services can contain personal content: never log them.
            _status.value = "Could not play announcement; check Glasses Speech and Bluetooth audio"
            android.util.Log.w("DjangoNotify", "Announcement unavailable; no speaker fallback")
        } finally {
            _status.compareAndSet(preparing, "Ready for selected notifications")
            currentKey = null
            currentNotice = null
        }
    }

    private fun canAnnounce(n: SpokenNotice, line: String): Boolean =
        allowsDelivery(n) && fresh(n) && !busy(n) && !synchronized(queue) { n.key in removed } &&
            spokenLine(n, settings.options.value) == line

    private suspend fun play(pcm: SpeechPcm, n: SpokenNotice, chime: Boolean): Boolean {
        val device = sink() ?: return false
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change -> if (change < 0) interrupt() }, Handler(Looper.getMainLooper())).build()
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) error("Audio busy")
        var local: AudioTrack? = null
        try {
            // Verify routing during a silent prefix before any name/message is audible.
            val silence = pcm.rate / 2
            val tone = if (chime) pcm.rate / 5 else 0
            val samples = ShortArray(silence + tone + pcm.samples.size)
            for (i in 0 until tone) {
                val envelope = sin(PI * i / tone)
                samples[silence + i] = (sin(2 * PI * 740 * i / pcm.rate) * envelope * 2500).toInt().toShort()
            }
            pcm.samples.copyInto(samples, silence + tone)
            val t = AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(pcm.rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
            local = t
            track = t
            check(t.setPreferredDevice(device))
            check(t.write(samples, 0, samples.size) == samples.size)
            if (!allowsDelivery(n)) return false
            t.play()
            delay(180)
            check(t.routedDevice?.id == device.id) { "Wrong audio route" }
            android.util.Log.i("DjangoNotify", "Verified glasses audio route; type=${device.type}")
            return withTimeout(samples.size * 1000L / pcm.rate + 3_000L) {
                val amount = settings.options.value.amount(n)
                while (t.playbackHeadPosition < samples.size) {
                    check(t.routedDevice?.id == device.id && !busy(n))
                    if (!allowsDelivery(n) || !fresh(n) || settings.options.value.amount(n) != amount) return@withTimeout false
                    delay(20)
                }
                true
            }
        } finally {
            track = null
            local?.let { runCatching { it.stop() }; it.release() }
            audio.abandonAudioFocusRequest(focus)
        }
    }

    /** Local preview; no message is sent and no real notification is consumed. */
    fun test() = offer(SpokenNotice("django-test-${System.nanoTime()}", context.packageName, NoticeSource.DOTS,
        "Django", "", "Notifications are ready. WhatsApp announcements will say only who sent the message.",
        System.currentTimeMillis(), System.currentTimeMillis(), preview = true), matchesInterruptionFilter = null)
}
