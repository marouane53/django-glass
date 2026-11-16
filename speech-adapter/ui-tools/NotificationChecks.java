package org.marouane.glassesspeech.ui;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Telephony;
import android.service.notification.StatusBarNotification;
import java.lang.reflect.Method;

/** Device checks with synthetic notifications. Does not post, send or read any real messages. */
public final class NotificationChecks {
    private final Context target;
    private final Context tools;
    private final ClassLoader loader;
    private final Method parse;
    private final Method line;
    private final Object options;
    private int passed;

    private NotificationChecks(Context context) throws Exception {
        tools = context;
        target = context.createPackageContext("org.marouane.djangoglasses", Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        loader = target.getClassLoader();
        Class<?> notice = loader.loadClass("com.macedonga.visor.notify.SpokenNotice");
        Class<?> prefs = loader.loadClass("com.macedonga.visor.notify.SpokenOptions");
        parse = loader.loadClass("com.macedonga.visor.notify.SpokenParserKt").getMethod("spokenNotice", Context.class, StatusBarNotification.class);
        line = loader.loadClass("com.macedonga.visor.notify.SpokenPolicyKt").getMethod("spokenLine", notice, prefs);
        options = prefs.getConstructor().newInstance();
    }

    public static int run(Context context) throws Exception { return new NotificationChecks(context).run(); }

    private Object parse(String pkg, Notification notification) throws Exception {
        StatusBarNotification sbn = new StatusBarNotification(pkg, pkg, 42, null, 10000, 0, 0, notification, android.os.Process.myUserHandle(), System.currentTimeMillis());
        return parse.invoke(null, target, sbn);
    }
    private String say(Object n) throws Exception { return n == null ? null : (String) line.invoke(null, n, options); }
    private void check(boolean good) { if (!good) throw new AssertionError("Notification extraction check " + (passed + 1)); passed++; }
    private Notification.Builder builder(String channel) { return new Notification.Builder(target, channel).setSmallIcon(android.R.drawable.ic_dialog_info); }
    private int run() throws Exception {
        Person self = new Person.Builder().setName("Me").setKey("self").build();
        Person sender = new Person.Builder().setName("Amine").setKey("sender").build();
        Notification n = builder("messages").setStyle(new Notification.MessagingStyle(self)
            .addMessage("Hidden WhatsApp content 123456", 10, sender)).build();
        check("WhatsApp message from Amine.".equals(say(parse("com.whatsapp", n))));
        n = builder("messages").setStyle(new Notification.MessagingStyle(self)
            .addMessage("Incoming", 10, sender).addMessage("My outgoing reply", 11, (Person) null)).build();
        check(say(parse("com.whatsapp", n)) == null);
        n = builder("messages").setStyle(new Notification.MessagingStyle(self).addMessage("My own message", 11, self)).build();
        check(say(parse("com.whatsapp", n)) == null);
        n = builder("messages").setContentTitle("Amine").setContentText("Hidden content").setGroup("messages").setGroupSummary(true).build();
        check(say(parse("com.whatsapp", n)) == null);
        n = builder("codex").setContentTitle("Django").setStyle(new Notification.BigTextStyle().bigText("Your task is finished. Full details." )).build();
        check(say(parse("com.openai.chatgpt", n)).contains("Your task is finished. Full details."));
        n = builder("marketing").setContentTitle("Django").setContentText("A promotion").build();
        check(parse("com.openai.chatgpt", n) == null);
        PendingIntent action = PendingIntent.getActivity(tools, 909, new Intent(Intent.ACTION_MAIN).setPackage(tools.getPackageName()), PendingIntent.FLAG_IMMUTABLE);
        n = builder("calls").setStyle(Notification.CallStyle.forIncomingCall(sender, action, action)).setOngoing(true).build();
        String callerLine = say(parse("com.samsung.android.incallui", n));
        if (!"Incoming call from Amine.".equals(callerLine)) throw new IllegalStateException("Incoming extraction: category="+n.category+" type="+n.extras.getInt(Notification.EXTRA_CALL_TYPE,-1)+" person="+n.extras.containsKey(Notification.EXTRA_CALL_PERSON)+" parsed="+(callerLine!=null));
        passed++;
        n = builder("calls").setStyle(Notification.CallStyle.forOngoingCall(sender, action)).setOngoing(true).build();
        check(parse("com.samsung.android.incallui", n) == null);
        String sms = Telephony.Sms.getDefaultSmsPackage(target);
        if (sms == null) throw new AssertionError("Default messaging app unavailable");
        n = builder("messages").setStyle(new Notification.MessagingStyle(self).addMessage("Full SMS text", 10, sender)).build();
        check("Text from Amine. Full SMS text".equals(say(parse(sms, n))));
        n = builder("messages").setContentTitle("Other app").setContentText("Unselected text").build();
        check(say(parse("unselected.example", n)) == null);
        return passed;
    }
}
