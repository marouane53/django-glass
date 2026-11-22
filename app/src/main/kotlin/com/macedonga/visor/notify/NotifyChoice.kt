package com.macedonga.visor.notify

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Delivery settings shared by Finn readouts and the native notification mirror. */
@Singleton
class NotifyChoice @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("notify", Context.MODE_PRIVATE)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _onlyLocked = MutableStateFlow(prefs.getBoolean(KEY_LOCKED, false))
    val onlyLocked: StateFlow<Boolean> = _onlyLocked.asStateFlow()

    fun set(on: Boolean) {
        _enabled.value = on
        prefs.edit().putBoolean(KEY, on).apply()
    }

    fun setOnlyLocked(on: Boolean) {
        _onlyLocked.value = on
        prefs.edit().putBoolean(KEY_LOCKED, on).apply()
    }

    internal fun allowsDelivery(matchesInterruptionFilter: Boolean?, preview: Boolean = false): Boolean =
        enabled.value && passesDeliveryPolicy(
            onlyLocked.value, preview, matchesInterruptionFilter,
            // A notification can wake the screen without dismissing the lock screen.
            phoneLockedOrOff = { keyguard?.isKeyguardLocked == true || power?.isInteractive == false },
            dndOff = { notifications?.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL },
        )

    private companion object {
        const val KEY = "mirror"
        const val KEY_LOCKED = "only_locked"
    }
}
