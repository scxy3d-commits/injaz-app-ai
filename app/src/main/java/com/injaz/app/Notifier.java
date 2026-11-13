package com.injaz.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

final class Notifier {
    static final String CH_DAILY = "daily";
    static final String CH_FOCUS = "focus";
    static final int ID_DAILY = 1001;
    static final int ID_FOCUS = 1002;

    private Notifier() {}

    static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        boolean ar = Store.isAr(c);
        NotificationChannel d = new NotificationChannel(CH_DAILY,
                ar ? "التذكير اليومي" : "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT);
        d.setDescription(ar ? "خطوة اليوم من دروسك" : "Today's step from your lessons");
        NotificationChannel f = new NotificationChannel(CH_FOCUS,
                ar ? "مؤقّت التركيز" : "Focus timer", NotificationManager.IMPORTANCE_HIGH);
        f.setDescription(ar ? "تنبيه عند انتهاء جلسة التركيز" : "Alert when a focus session ends");
        f.enableVibration(true);
        nm.createNotificationChannel(d);
        nm.createNotificationChannel(f);
    }

    static boolean allowed(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.areNotificationsEnabled();
    }

    private static PendingIntent open(Context c, int req, String route) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (route != null) i.putExtra("route", route);
        return PendingIntent.getActivity(c, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void show(Context c, String channel, int id, String title, String body, String route) {
        channels(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || !nm.areNotificationsEnabled()) return;
        Notification.Builder b = new Notification.Builder(c, channel)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFF0F766E)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(open(c, id, route))
                .setAutoCancel(true)
                .setShowWhen(true);
        try {
            nm.notify(id, b.build());
        } catch (SecurityException ignored) {
            // Permission was revoked between the check and the post.
        }
    }

    /** Posts today's step: the next unfinished lesson and its action. */
    static void daily(Context c) {
        String lang = Store.lang(c);
        int nextN = Store.sp(c).getInt("nextN", 1);
        JSONObject l = Store.lessonAt(c, Store.indexOf(c, nextN));
        String title;
        String body;
        String route = null;
        if (l != null) {
            title = ("ar".equals(lang) ? "خطوة اليوم: " : "Today's step: ") + Store.text(l, "t", lang);
            body = Store.text(l, "a", lang);
            route = "lesson/" + l.optInt("n");
        } else {
            title = "ar".equals(lang) ? "خطوة اليوم" : "Today's step";
            body = "ar".equals(lang) ? "افتح التطبيق وأكمل درسك التالي." : "Open the app and continue your next lesson.";
        }
        show(c, CH_DAILY, ID_DAILY, title, body, route);
    }

    static void focusDone(Context c, int minutes) {
        boolean ar = Store.isAr(c);
        String title = ar ? "انتهت جلسة التركيز" : "Focus session complete";
        String body = ar
                ? ("أنهيت " + minutes + " دقيقة من التركيز. قم وتحرّك بعيداً عن الشاشة قبل الجلسة التالية.")
                : ("You finished " + minutes + " minutes of focus. Stand up and step away from the screen before the next one.");
        show(c, CH_FOCUS, ID_FOCUS, title, body, "focus");
    }

    static void test(Context c) {
        boolean ar = Store.isAr(c);
        show(c, CH_DAILY, ID_DAILY + 5,
                ar ? "الإشعارات تعمل" : "Notifications are working",
                ar ? "هكذا سيصلك تذكير خطوة اليوم." : "This is how your daily step reminder will arrive.",
                null);
    }
}
