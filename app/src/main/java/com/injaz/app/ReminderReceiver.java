package com.injaz.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class ReminderReceiver extends BroadcastReceiver {
    static final String DAILY = "com.injaz.app.DAILY";
    static final String FOCUS_END = "com.injaz.app.FOCUS_END";

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent == null ? null : intent.getAction();
        if (DAILY.equals(a)) {
            Notifier.daily(c);
            Scheduler.scheduleDaily(c);
            InjazWidget.refreshAll(c);
        } else if (FOCUS_END.equals(a)) {
            SharedPreferences sp = Store.sp(c);
            int minutes = sp.getInt("focusMin", 25);
            sp.edit().remove("focusEnd").remove("focusMin").apply();
            Notifier.focusDone(c, minutes);
        }
    }
}
