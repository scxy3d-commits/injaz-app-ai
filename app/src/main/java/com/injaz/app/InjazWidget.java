package com.injaz.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import org.json.JSONObject;

public class InjazWidget extends AppWidgetProvider {
    static final String NEXT = "com.injaz.app.WIDGET_NEXT";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(c));
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent != null && NEXT.equals(intent.getAction())) {
            SharedPreferences sp = Store.sp(c);
            sp.edit().putInt("wOffset", sp.getInt("wOffset", 0) + 1).apply();
            refreshAll(c);
            return;
        }
        super.onReceive(c, intent);
    }

    static void refreshAll(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, InjazWidget.class));
            if (ids == null || ids.length == 0) return;
            RemoteViews v = build(c);
            for (int id : ids) m.updateAppWidget(id, v);
        } catch (Exception ignored) {
            // The widget host may be unavailable; nothing to do.
        }
    }

    static RemoteViews build(Context c) {
        SharedPreferences sp = Store.sp(c);
        String lang = Store.lang(c);
        boolean ar = "ar".equals(lang);
        RemoteViews v = new RemoteViews(c.getPackageName(), ar ? R.layout.widget_rtl : R.layout.widget_ltr);

        int total = Math.max(1, Store.lessons(c).length());
        int done = Math.max(0, Math.min(total, sp.getInt("done", 0)));
        int base = Store.indexOf(c, sp.getInt("nextN", 1));
        JSONObject l = Store.lessonAt(c, base + sp.getInt("wOffset", 0));

        v.setTextViewText(R.id.w_app, ar ? "إنجاز" : "Injaz");
        v.setTextViewText(R.id.w_progress, done + " / " + total);
        v.setProgressBar(R.id.w_bar, total, done, false);
        String route = null;
        if (l != null) {
            v.setTextViewText(R.id.w_title, Store.text(l, "t", lang));
            v.setTextViewText(R.id.w_body, Store.text(l, "a", lang));
            route = "lesson/" + l.optInt("n");
        } else {
            v.setTextViewText(R.id.w_title, ar ? "ابدأ رحلتك" : "Start your journey");
            v.setTextViewText(R.id.w_body, ar ? "افتح التطبيق لترى خطوة اليوم." : "Open the app to see today's step.");
        }
        v.setTextViewText(R.id.w_open, ar ? "افتح الدرس" : "Open lesson");
        v.setTextViewText(R.id.w_next, ar ? "نصيحة أخرى" : "Another tip");

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (route != null) open.putExtra("route", route);
        PendingIntent po = PendingIntent.getActivity(c, 20, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_root, po);
        v.setOnClickPendingIntent(R.id.w_open, po);

        Intent next = new Intent(c, InjazWidget.class).setAction(NEXT);
        PendingIntent pn = PendingIntent.getBroadcast(c, 21, next,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_next, pn);
        return v;
    }
}
