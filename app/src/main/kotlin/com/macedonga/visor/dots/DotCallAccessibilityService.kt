package com.macedonga.visor.dots

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Only ChatGPT's Call/End controls, only after an explicit request. Never types or sends a message. */
@AndroidEntryPoint
class DotCallAccessibilityService : AccessibilityService() {
    @Inject lateinit var controller: DotCallController
    private val main = Handler(Looper.getMainLooper())
    private var request: String? = null
    private var deadline = 0L
    private val poll = object : Runnable {
        override fun run() {
            if (request == null) return
            if (SystemClock.elapsedRealtime() >= deadline) {
                val was = request
                request = null
                controller.failed(if (was == "end") "Could not end the call. Open ChatGPT and tap End." else
                    "Could not find Django's Call button. Unlock the phone and open your dot in ChatGPT.")
                return
            }
            inspect()
            if (request != null) main.postDelayed(this, 250)
        }
    }

    override fun onServiceConnected() { current = this }

    fun openCallLauncher(): Boolean = runCatching {
        startActivity(Intent(this, DotCallLaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    fun startCall(): Boolean {
        arm("start")
        return runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/dots"))
                .setPackage(DotCallController.CHATGPT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrElse { cancelRequest(); false }
    }

    fun endCall() { arm("end"); inspect() }
    fun cancelRequest() { request = null; main.removeCallbacks(poll) }
    private fun arm(action: String) {
        cancelRequest()
        request = action
        deadline = SystemClock.elapsedRealtime() + 20_000
        main.post(poll)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() == DotCallController.CHATGPT && controller.active) inspect()
    }

    private fun inspect() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != DotCallController.CHATGPT) return
        val nodes = ArrayList<AccessibilityNodeInfo>()
        fun collect(n: AccessibilityNodeInfo) {
            if (!n.isVisibleToUser) return
            nodes += n
            for (i in 0 until n.childCount) n.getChild(i)?.let(::collect)
        }
        collect(root)
        fun labelled(label: String) = nodes.filter { it.contentDescription?.toString() == label }
        val ends = labelled("End")
        val isCall = ends.size == 1 && labelled("Mute").size + labelled("Unmute").size == 1
        if (isCall) controller.callVisible()
        when (request) {
            "start" -> {
                if (isCall) { cancelRequest(); return }
                // A specific dot composer and exactly one Call control. Ordinary ChatGPT voice is never opened.
                val calls = labelled("Call")
                if (calls.size == 1 && labelled("Message Django").size == 1) {
                    if (click(calls.single())) cancelRequest()
                }
            }
            "end" -> if (isCall && click(ends.single())) {
                cancelRequest()
                controller.endedInUi()
            }
        }
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        return target?.isEnabled == true && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    override fun onInterrupt() { cancelRequest() }
    override fun onDestroy() {
        cancelRequest()
        if (current === this) current = null
        super.onDestroy()
    }

    companion object { @Volatile var current: DotCallAccessibilityService? = null; private set }
}
