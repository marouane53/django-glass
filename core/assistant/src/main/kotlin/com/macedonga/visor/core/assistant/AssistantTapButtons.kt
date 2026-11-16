package com.macedonga.visor.core.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTrack
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/** A headset-button target only for the lifetime of one normal assistant microphone session. */
internal class AssistantTapButtons(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var owner: String? = null
    private var media: MediaSession? = null
    private var routingTrack: AudioTrack? = null

    private fun releaseRoutingTrack() {
        routingTrack?.let {
            runCatching { it.stop() }
            it.release()
        }
        routingTrack = null
    }

    /**
     * Speech goes over DataX, bypassing Android's player history. An active MediaSession alone
     * therefore leaves headset buttons routed to the previous player. Brief silent local playback
     * registers this session as the current button target without changing the glasses' speech path.
     * It never loops, requests audio focus, or survives this session's cleanup.
     */
    private fun selectButtonTarget(id: String) {
        releaseRoutingTrack()
        runCatching {
            val speaker = context.getSystemService(AudioManager::class.java)
                ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                ?.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?: error("No phone speaker for local button registration")
            val silence = ShortArray(2400) // 150 ms, mono at 16 kHz.
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(silence.size * 2)
                .build()
            routingTrack = track
            check(track.setPreferredDevice(speaker)) { "Phone speaker routing was rejected" }
            track.addOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener { routed ->
                if (routingTrack === track) {
                    val type = routed.routedDevice?.type
                    android.util.Log.i("DjangoTouch", "Button-registration output device type=$type; phone speaker=${type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER}")
                    if (type != null && type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) releaseRoutingTrack()
                }
            }, main)
            check(track.write(silence, 0, silence.size) == silence.size)
            track.play()
            main.postDelayed({ if (owner == id) releaseRoutingTrack() }, 450)
            android.util.Log.i("DjangoTouch", "Registered phone-speaker playback for headset-button routing")
        }.onFailure {
            releaseRoutingTrack()
            android.util.Log.w("DjangoTouch", "Headset-button routing playback failed: ${it.javaClass.simpleName}")
        }
    }

    fun begin(id: String, stop: (String) -> Unit) = main.post {
        media?.release()
        owner = id
        fun end(action: String) {
            if (owner == id) {
                android.util.Log.i("DjangoTouch", "Headset media control: $action")
                stop(action)
            }
        }
        media = MediaSession(context, "DjangoAssistantTouchStop").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(intent: Intent): Boolean {
                    val key = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) ?: return false
                    val action = when (key.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> "PLAY_PAUSE"
                        KeyEvent.KEYCODE_MEDIA_PLAY -> "PLAY"
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> "PAUSE"
                        KeyEvent.KEYCODE_MEDIA_STOP -> "STOP"
                        KeyEvent.KEYCODE_MEDIA_NEXT -> "NEXT"
                        else -> return false
                    }
                    if (key.action == KeyEvent.ACTION_UP && key.repeatCount == 0) end(action)
                    return true
                }
                override fun onPlay() { end("PLAY") }
                override fun onPause() { end("PAUSE") }
                override fun onStop() { end("STOP") }
                override fun onSkipToNext() { end("NEXT") }
            }, main)
            setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Glasses assistant").build())
            setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_NEXT)
                // The actual reply travels over DataX. Reporting PLAYING here also makes the
                // notification bridge tell the glasses that music is playing, disrupting follow-ups.
                // Local player history selects this session even while its transport state is paused.
                .setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
            isActive = true
        }
        selectButtonTarget(id)
        android.util.Log.i("DjangoTouch", "Headset media-button stop armed for a normal session")
    }

    fun end(id: String) = main.post {
        if (owner == id) {
            owner = null
            releaseRoutingTrack()
            media?.isActive = false
            media?.release()
            media = null
            android.util.Log.i("DjangoTouch", "Headset media-button stop released")
        }
    }
}
