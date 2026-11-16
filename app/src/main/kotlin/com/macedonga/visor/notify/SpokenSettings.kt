package com.macedonga.visor.notify

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An allowlist: installing another app never enables its announcements. */
@Singleton
class SpokenSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("spoken_notifications", Context.MODE_PRIVATE)
    private fun read(key: String, fallback: ReadAmount) = runCatching { ReadAmount.valueOf(prefs.getString(key, fallback.name)!!) }.getOrDefault(fallback)
    private val _options = MutableStateFlow(SpokenOptions(
        dots = read("dots", ReadAmount.FULL), whatsapp = read("whatsapp", ReadAmount.SENDER), sms = read("sms", ReadAmount.FULL),
        calls = prefs.getBoolean("calls", true), chime = prefs.getBoolean("chime", true),
        other = prefs.getStringSet("other", emptySet()).orEmpty().toSet(),
    ))
    val options = _options.asStateFlow()

    @Synchronized fun change(edit: (SpokenOptions) -> SpokenOptions) {
        val next = edit(_options.value)
        _options.value = next
        prefs.edit().putString("dots", next.dots.name).putString("whatsapp", next.whatsapp.name).putString("sms", next.sms.name)
            .putBoolean("calls", next.calls).putBoolean("chime", next.chime).putStringSet("other", next.other).apply()
    }
}
