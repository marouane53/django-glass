package com.macedonga.visor.notify

import android.app.Notification
import android.app.Person
import android.content.Context
import android.os.Bundle
import android.provider.Telephony
import android.service.notification.StatusBarNotification

/** Android extraction is separate from the tested selection and speech policy. */
@Suppress("DEPRECATION")
fun spokenNotice(context: Context, sbn: StatusBarNotification): SpokenNotice? {
    val n = sbn.notification
    val e = n.extras
    val pkg = sbn.packageName
    if (pkg == context.packageName || pkg in setOf("android", "com.android.systemui")) return null
    val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val last = e.getParcelableArray(Notification.EXTRA_MESSAGES)?.lastOrNull() as? Bundle
    val person = last?.getParcelable("sender_person") as? Person
    val call = n.category == Notification.CATEGORY_CALL || e.getString(Notification.EXTRA_TEMPLATE) == Notification.CallStyle::class.java.name
    val incoming = call && e.getInt(Notification.EXTRA_CALL_TYPE, -1) == 1
    // Only CallStyle's explicit incoming type is safe to announce; never mistake an ongoing/outgoing call for one.
    if (call && !incoming) return null
    val source = when {
        incoming -> NoticeSource.CALL
        pkg == "com.openai.chatgpt" -> if (dotNotice(n.channelId.orEmpty(), "$title ${person?.name?.toString().orEmpty()}")) NoticeSource.DOTS else return null
        pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b" -> NoticeSource.WHATSAPP
        pkg == Telephony.Sms.getDefaultSmsPackage(context) -> NoticeSource.SMS
        else -> NoticeSource.OTHER
    }
    val app = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    val user = e.getParcelable(Notification.EXTRA_MESSAGING_PERSON) as? Person
    val outgoing = last != null && (person == null && last.getCharSequence("sender").isNullOrBlank() ||
        person != null && user != null && person.key == user.key && person.name == user.name && person.uri == user.uri)
    val caller = e.getParcelable(Notification.EXTRA_CALL_PERSON) as? Person
    val sender = (if (incoming) caller?.name?.toString() else person?.name?.toString() ?: last?.getCharSequence("sender")?.toString())
        ?: title.substringBefore(':').takeIf { it.isNotBlank() && it != app }.orEmpty()
    val body = last?.getCharSequence("text")?.toString()
        ?: (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
    return SpokenNotice(sbn.key, pkg, source, app, sender, body,
        last?.getLong("time")?.takeIf { it > 0 } ?: n.`when`.takeIf { it > 0 } ?: sbn.postTime,
        sbn.postTime, n.channelId.orEmpty(), outgoing,
        n.flags and Notification.FLAG_GROUP_SUMMARY != 0, n.flags and Notification.FLAG_ONGOING_EVENT != 0, incoming)
}
