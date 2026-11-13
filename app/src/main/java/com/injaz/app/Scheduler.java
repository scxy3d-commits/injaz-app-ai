package com.injaz.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

final class Scheduler {
    private Scheduler() {}

    private static PendingIntent pi(Context c, String action, int req) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void set(AlarmManager am, long at, PendingIntent p) {
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        }
    }

    /** (Re)schedules the daily reminder for the next occurrence of the chosen time. */
    static void scheduleDaily(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent p = pi(c, ReminderReceiver.DAILY, 1);
        am.cancel(p);
        SharedPreferences sp = Store.sp(c);
        if (!sp.getBoolean("remOn", true)) return;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, sp.getInt("remH", 8));
        cal.set(Calendar.MINUTE, sp.getInt("remM", 0));
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 5000) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        set(am, cal.getTimeInMillis(), p);
    }

    static void scheduleFocus(Context c, long endAt, int minutes) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        Store.sp(c).edit().putLong("focusEnd", endAt).putInt("focusMin", minutes).apply();
        set(am, endAt, pi(c, ReminderReceiver.FOCUS_END, 2));
    }

    static void cancelFocus(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(pi(c, ReminderReceiver.FOCUS_END, 2));
        Store.sp(c).edit().remove("focusEnd").remove("focusMin").apply();
    }

    /** Restores alarms after reboot, app update or a clock change. */
    static void restore(Context c) {
        scheduleDaily(c);
        SharedPreferences sp = Store.sp(c);
        long end = sp.getLong("focusEnd", 0);
        if (end > System.currentTimeMillis()) {
            scheduleFocus(c, end, sp.getInt("focusMin", 25));
        } else if (end > 0) {
            sp.edit().remove("focusEnd").remove("focusMin").apply();
        }
    }
}
