package com.macedonga.visor.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macedonga.visor.R
import com.macedonga.visor.notify.ReadAmount
import com.macedonga.visor.core.design.Visor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A phone app that can reach the glasses, with its name and icon. */
private class PhoneApp(val pkg: String, val name: String, val icon: ImageBitmap?)

/** Which phone apps reach the glasses: recent senders first, the rest behind "Show every app". */
@Composable
fun NotifyAppsScreen(model: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val on by model.notifyChoice.enabled.collectAsStateWithLifecycle()
    val options by model.spokenSettings.options.collectAsStateWithLifecycle()
    val status by model.spokenReader.status.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<PhoneApp>?>(null) }
    var every by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { launchable(context, model.apps.seen.value.keys) } }
    val amounts = ReadAmount.entries.map { it to it.label }
    SettingsPage("Spoken notifications", light = 0.35f) {
        item(key = "enable") {
            Rows({ ToggleRow("Read notifications with Finn", on, model.notifyChoice::set,
                sub = "Only the sources you choose below. Announcements wait until your assistant conversation ends and respect Do Not Disturb.") })
        }
        if (on) {
            item(key = "sources") {
                SectionLabel("Choose what to hear")
                Rows(
                    { PillsRow("Django / Dots", amounts, options.dots, { value -> model.spokenSettings.change { it.copy(dots = value) } },
                        sub = "Agent messages from ChatGPT. Promotions and ordinary ChatGPT alerts are excluded.") },
                    { PillsRow("WhatsApp", listOf(ReadAmount.OFF to "Off", ReadAmount.SENDER to "Sender only"), options.whatsapp,
                        { value -> model.spokenSettings.change { it.copy(whatsapp = value) } }, sub = "Says who sent a message. Message text is never read aloud.") },
                    { PillsRow("Texts", amounts, options.sms, { value -> model.spokenSettings.change { it.copy(sms = value) } },
                        sub = "SMS and RCS from your default messaging app.") },
                    { ToggleRow("Incoming call announcements", options.calls, { value -> model.spokenSettings.change { it.copy(calls = value) } },
                        sub = "Announces the caller when the app supplies incoming-call details. Your normal ringing still applies.") },
                    { ToggleRow("Notification sound", options.chime, { value -> model.spokenSettings.change { it.copy(chime = value) } },
                        sub = "A short tone before Finn speaks.") },
                    { ValueRow("Test Finn notification", status, onClick = { model.spokenReader.test() },
                        sub = "Wear the connected glasses and finish any assistant conversation first.") },
                )
            }
            item(key = "other-label") {
                SectionLabel("Other apps")
                Rows({ ValueRow("Choose additional apps", "${options.other.size} selected", onClick = { every = !every },
                    sub = "All other apps start off. Enabling one reads its sender and full notification text.") })
            }
            if (every) {
                val sms = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
                val rest = apps.orEmpty().filter { it.pkg !in setOf("com.openai.chatgpt", "com.whatsapp", "com.whatsapp.w4b", sms) }
                item(key = "other-apps") {
                    Rows(*rest.map { app -> @Composable { AppRow(app, app.pkg in options.other) { value ->
                        model.spokenSettings.change { it.copy(other = if (value) it.other + app.pkg else it.other - app.pkg) }
                    } } }.toTypedArray())
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: PhoneApp, on: Boolean, onChange: (Boolean) -> Unit) {
    ToggleRow(app.name, on, onChange, leading = { AppIcon(app) })
}

@Composable
private fun AppIcon(app: PhoneApp) {
    if (app.icon != null) Image(app.icon, null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
    else Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Visor.colors.control))
}

/** Every app with a launcher entry and every other app that has [sent] something, by name, but Visor itself. */
private fun launchable(context: Context, sent: Set<String>): List<PhoneApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val listed = pm.queryIntentActivities(intent, 0).map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
    val others = (sent - listed.map { it.packageName }.toSet()).map { pkg ->
        runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()?.let { phoneApp(pm, it) } ?: PhoneApp(pkg, pkg, null)
    }
    return (listed.map { phoneApp(pm, it) } + others)
        .filter { it.pkg != context.packageName }
        .sortedBy { it.name.lowercase() }
}

private fun phoneApp(pm: PackageManager, info: ApplicationInfo): PhoneApp {
    val icon = runCatching { pm.getApplicationIcon(info).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull()
    return PhoneApp(info.packageName, pm.getApplicationLabel(info).toString(), icon)
}

private const val ICON_PX = 96
