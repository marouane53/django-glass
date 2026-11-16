package com.macedonga.visor.dots

import android.app.KeyguardManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.macedonga.visor.core.assistant.DotCallAccess
import com.macedonga.visor.core.assistant.dotCallAccess
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Wakes the display and dismisses an already-authenticated keyguard through Android's public API. */
@AndroidEntryPoint
class DotCallLaunchActivity : ComponentActivity() {
    @Inject lateinit var controller: DotCallController
    private var attempted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContentView(TextView(this).apply {
            text = "Opening Django…"
            gravity = android.view.Gravity.CENTER
        })
    }

    override fun onResume() {
        super.onResume()
        if (attempted) return
        attempted = true
        if (!controller.active) { finish(); return }
        val keyguard = getSystemService(KeyguardManager::class.java)
        when (dotCallAccess(keyguard.isDeviceLocked, keyguard.isKeyguardLocked)) {
            DotCallAccess.AUTHENTICATION_REQUIRED -> fail("Your phone requires authentication. Unlock it once, then try again.")
            DotCallAccess.OPEN -> openCall()
            DotCallAccess.DISMISS_TRUSTED_KEYGUARD -> keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { openCall() }
                override fun onDismissCancelled() { fail("The lock screen was not dismissed. Try again when Android trusts the glasses.") }
                override fun onDismissError() { fail("Android could not dismiss the lock screen. Unlock the phone once, then try again.") }
            })
        }
    }

    private fun openCall() {
        if (!isFinishing && !isDestroyed && controller.active) controller.openNativeCall()
        finish()
    }

    private fun fail(message: String) { controller.failed(message); finish() }
}
