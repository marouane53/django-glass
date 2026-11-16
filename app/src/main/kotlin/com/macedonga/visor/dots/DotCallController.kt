package com.macedonga.visor.dots

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import com.macedonga.visor.R
import com.macedonga.visor.core.assistant.DotCalls
import com.macedonga.visor.log.EventLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Owns only a dot call explicitly requested through Django Glasses. No account credentials or call audio. */
@Singleton
class DotCallController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: EventLog,
) : DotCalls {
    private val main = Handler(Looper.getMainLooper())
    private val _status = MutableStateFlow("Ready — say ‘Hey Meta, open ChatGPT’")
    val status = _status.asStateFlow()
    @Volatile override var active = false
        private set
    private var observedCall = false
    private var callNotification: String? = null
    private var hangup: PendingIntent? = null
    private var showCall: PendingIntent? = null
    private var generation = 0
    private val audio = context.getSystemService(AudioManager::class.java)
    private var sawCallAudio = false

    init {
        audio.addOnModeChangedListener(context.mainExecutor) { mode ->
            if (!active) return@addOnModeChangedListener
            if (mode == AudioManager.MODE_IN_COMMUNICATION) sawCallAudio = true
            if (mode == AudioManager.MODE_NORMAL && sawCallAudio && observedCall) {
                val request = generation
                main.postDelayed({
                    if (active && request == generation && audio.mode == AudioManager.MODE_NORMAL)
                        finish("Dot call ended. Your normal glasses assistant is ready.")
                }, 1_200)
            }
        }
    }

    override fun unavailableReason(): String? = when {
        active -> "A Django dot call is already open."
        runCatching { context.packageManager.getPackageInfo(CHATGPT, 0) }.isFailure -> "Install and sign in to the ChatGPT app first."
        DotCallAccessibilityService.current == null -> "Enable Django dot call controls in Android Accessibility settings first."
        context.getSystemService(KeyguardManager::class.java).isDeviceLocked -> "Your phone requires authentication. Unlock it once, then try again."
        else -> null
    }

    override suspend fun start(): String? = withContext(Dispatchers.Main) {
        unavailableReason()?.let { setStatus(it); return@withContext it }
        active = true
        observedCall = false
        sawCallAudio = false
        val request = ++generation
        setStatus("Opening your Django dot call…")
        controls()
        val service = DotCallAccessibilityService.current
        if (service == null || !service.openCallLauncher()) {
            finish("Could not open the dot call launcher. Try again with the glasses connected.")
            return@withContext _status.value
        }
        main.postDelayed({
            if (generation == request && active && !observedCall) {
                DotCallAccessibilityService.current?.cancelRequest()
                finish("Call did not start. Unlock the phone, check ChatGPT sign-in, and try again.")
            }
        }, 25_000)
        null
    }

    /** Called only by our private visible launcher after Android has dismissed any trusted keyguard. */
    fun openNativeCall() {
        if (!active) return
        if (context.getSystemService(KeyguardManager::class.java).isDeviceLocked) {
            failed("Your phone requires authentication. Unlock it once, then try again.")
            return
        }
        if (DotCallAccessibilityService.current?.startCall() != true)
            failed("Could not open ChatGPT. Check the dot call controls in Accessibility settings.")
    }

    fun callVisible() = main.post {
        if (active && !observedCall) {
            observedCall = true
            setStatus("Django dot call — ask Django to end the call when finished")
        }
    }

    /** Metadata/actions from ChatGPT's call notification; message contents are never read or logged. */
    fun posted(sbn: StatusBarNotification) {
        if (sbn.packageName != CHATGPT || sbn.notification.category != Notification.CATEGORY_CALL) return
        val end = sbn.notification.actions.orEmpty().firstOrNull {
            it.actionIntent?.creatorPackage == CHATGPT && it.title?.toString()?.lowercase() in END_LABELS
        }?.actionIntent
        main.post {
            if (!active) return@post
            if (callNotification != sbn.key || hangup != end)
                android.util.Log.i("DjangoDot", "ChatGPT call notification observed; end action available=${end != null}")
            callNotification = sbn.key
            hangup = end
            showCall = sbn.notification.contentIntent?.takeIf { it.creatorPackage == CHATGPT }
            observedCall = true
            setStatus("Django dot call — ask Django to end the call when finished")
        }
    }

    fun removed(sbn: StatusBarNotification) {
        if (sbn.packageName != CHATGPT) return
        main.post {
            if (active && callNotification == sbn.key) {
                val request = generation
                callNotification = null
                hangup = null
                main.postDelayed({
                    if (active && generation == request && callNotification == null) finish("Dot call ended. Your normal glasses assistant is ready.")
                }, 1_200)
            }
        }
    }

    fun endCall() = main.post {
        if (!active) return@post
        setStatus("Ending Django's voice call; assigned work can continue…")
        val sent = hangup?.let { runCatching { it.send() }.isSuccess } == true
        if (sent) {
            log.add("Dot call: sent ChatGPT's end-call action")
        } else {
            val service = DotCallAccessibilityService.current
            if (service != null) {
                service.endCall()
                showCall?.let { runCatching { it.send() } }
            } else {
                setStatus("Open ChatGPT and end the call there; call controls are unavailable.")
            }
        }
    }

    fun endedInUi() = main.post {
        if (active) setStatus("End call requested; waiting for ChatGPT to close the call…")
    }

    fun failed(reason: String) = main.post {
        if (active && !observedCall) finish(reason) else setStatus(reason)
    }

    private fun controls() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Django dot calls", NotificationManager.IMPORTANCE_LOW))
        val end = PendingIntent.getBroadcast(context, ID, Intent(context, DotCallReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(ID, Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_visor)
            .setContentTitle("Django dot call controls").setContentText("End the voice call; Django can keep working.")
            .setOngoing(true).addAction(Notification.Action.Builder(null, "End call", end).build()).build())
    }

    private fun finish(message: String) {
        active = false
        observedCall = false
        sawCallAudio = false
        generation++
        callNotification = null
        hangup = null
        showCall = null
        DotCallAccessibilityService.current?.cancelRequest()
        context.getSystemService(NotificationManager::class.java).cancel(ID)
        setStatus(message)
    }

    private fun setStatus(message: String) {
        if (_status.value == message) return
        _status.value = message
        log.add("Dot call: $message")
        android.util.Log.i("DjangoDot", message)
    }

    companion object {
        const val CHATGPT = "com.openai.chatgpt"
        private const val CHANNEL = "django_dot_calls"
        private const val ID = 3061
        private val END_LABELS = setOf("end", "end call", "hang up", "hangup", "raccrocher", "terminer", "انهاء", "إنهاء")
    }
}
