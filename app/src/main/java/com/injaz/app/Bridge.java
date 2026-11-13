package com.injaz.app;

import android.Manifest;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

/** Methods the web UI can call. All run on a WebView background thread. */
final class Bridge {
    private final MainActivity a;

    Bridge(MainActivity activity) {
        this.a = activity;
    }

    @JavascriptInterface
    public String getState() {
        return Store.sp(a).getString("state", "");
    }

    @JavascriptInterface
    public void saveState(String json) {
        Store.sp(a).edit().putString("state", json == null ? "" : json).apply();
    }

    /** Mirrors the few values the widget and notifications need. */
    @JavascriptInterface
    public void sync(String lang, int done, int total, int nextN) {
        SharedPreferences sp = Store.sp(a);
        boolean langChanged = !Store.lang(a).equals("en".equals(lang) ? "en" : "ar");
        sp.edit().putString("lang", "en".equals(lang) ? "en" : "ar")
                .putInt("done", done).putInt("total", total).putInt("nextN", nextN)
                .putInt("wOffset", 0).apply();
        if (langChanged) Notifier.channels(a);
        InjazWidget.refreshAll(a);
    }

    @JavascriptInterface
    public void setChrome(String hex, boolean darkIcons) {
        Store.sp(a).edit().putString("chromeColor", hex).putBoolean("chromeDarkIcons", darkIcons).apply();
        a.runOnUiThread(() -> a.applyChrome(hex, darkIcons));
    }

    @JavascriptInterface
    public boolean systemDark() {
        return a.systemDark();
    }

    @JavascriptInterface
    public String info() {
        try {
            JSONObject o = new JSONObject();
            o.put("sdk", Build.VERSION.SDK_INT);
            o.put("version", a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName);
            o.put("device", Build.MANUFACTURER + " " + Build.MODEL);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    // ---- notifications ----

    @JavascriptInterface
    public boolean notificationsAllowed() {
        return Notifier.allowed(a);
    }

    @JavascriptInterface
    public void requestNotifications() {
        a.runOnUiThread(() -> {
            if (Notifier.allowed(a)) {
                a.js("window.appNotifResult && window.appNotifResult(true)");
                return;
            }
            boolean asked = Store.sp(a).getBoolean("askedNotif", false);
            if (Build.VERSION.SDK_INT >= 33
                    && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    && (!asked || a.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, MainActivity.REQ_NOTIF);
            } else {
                openNotificationSettings();
            }
        });
    }

    @JavascriptInterface
    public void openNotificationSettings() {
        a.runOnUiThread(() -> {
            try {
                Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName());
                a.startActivity(i);
            } catch (Exception e) {
                try {
                    a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + a.getPackageName())));
                } catch (Exception ignored) {
                    // Settings screen unavailable.
                }
            }
        });
    }

    @JavascriptInterface
    public void setReminder(boolean on, int hour, int minute) {
        int h = Math.max(0, Math.min(23, hour));
        int m = Math.max(0, Math.min(59, minute));
        Store.sp(a).edit().putBoolean("remOn", on).putInt("remH", h).putInt("remM", m).apply();
        Scheduler.scheduleDaily(a);
    }

    @JavascriptInterface
    public String getReminder() {
        SharedPreferences sp = Store.sp(a);
        try {
            JSONObject o = new JSONObject();
            o.put("on", sp.getBoolean("remOn", true));
            o.put("h", sp.getInt("remH", 8));
            o.put("m", sp.getInt("remM", 0));
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public void testNotification() {
        Notifier.test(a);
    }

    // ---- focus timer ----

    @JavascriptInterface
    public double startFocus(int minutes) {
        int m = Math.max(1, Math.min(240, minutes));
        long end = System.currentTimeMillis() + m * 60000L;
        Scheduler.scheduleFocus(a, end, m);
        return (double) end;
    }

    @JavascriptInterface
    public void cancelFocus() {
        Scheduler.cancelFocus(a);
    }

    @JavascriptInterface
    public String getFocus() {
        SharedPreferences sp = Store.sp(a);
        try {
            JSONObject o = new JSONObject();
            o.put("end", (double) sp.getLong("focusEnd", 0));
            o.put("min", sp.getInt("focusMin", 0));
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    // ---- widget ----

    @JavascriptInterface
    public boolean canPinWidget() {
        try {
            return AppWidgetManager.getInstance(a).isRequestPinAppWidgetSupported();
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public boolean pinWidget() {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(a);
            if (!m.isRequestPinAppWidgetSupported()) return false;
            return m.requestPinAppWidget(new ComponentName(a, InjazWidget.class), null, null);
        } catch (Exception e) {
            return false;
        }
    }

    // ---- misc ----

    @JavascriptInterface
    public void share(String text) {
        a.runOnUiThread(() -> {
            try {
                Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
                a.startActivity(Intent.createChooser(i, null));
            } catch (Exception ignored) {
                // No share target.
            }
        });
    }

    @JavascriptInterface
    public void haptic() {
        try {
            Vibrator v = a.getSystemService(Vibrator.class);
            if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {
            // Vibration unavailable.
        }
    }

    @JavascriptInterface
    public void exit() {
        a.runOnUiThread(a::finish);
    }
}
