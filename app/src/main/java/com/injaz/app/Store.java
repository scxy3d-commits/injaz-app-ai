package com.injaz.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Small persistent store shared by the web UI, notifications and the widget. */
final class Store {
    private static final String PREFS = "injaz";
    private static JSONArray lessonsCache;

    private Store() {}

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String lang(Context c) {
        String l = sp(c).getString("lang", "ar");
        return "en".equals(l) ? "en" : "ar";
    }

    static boolean isAr(Context c) {
        return "ar".equals(lang(c));
    }

    /** Flat list of all lessons, each with its section title attached as "s". */
    static synchronized JSONArray lessons(Context c) {
        if (lessonsCache != null) return lessonsCache;
        JSONArray flat = new JSONArray();
        try (InputStream in = c.getApplicationContext().getAssets().open("content.json")) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            JSONObject root = new JSONObject(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            JSONArray secs = root.getJSONArray("sections");
            for (int i = 0; i < secs.length(); i++) {
                JSONObject s = secs.getJSONObject(i);
                JSONArray ls = s.getJSONArray("lessons");
                for (int j = 0; j < ls.length(); j++) {
                    JSONObject l = ls.getJSONObject(j);
                    l.put("s", s.optJSONObject("t"));
                    flat.put(l);
                }
            }
        } catch (Exception e) {
            // Leave the list empty; callers handle that.
        }
        lessonsCache = flat;
        return flat;
    }

    static JSONObject lessonAt(Context c, int index) {
        JSONArray a = lessons(c);
        if (a.length() == 0) return null;
        int i = ((index % a.length()) + a.length()) % a.length();
        return a.optJSONObject(i);
    }

    /** Index (in the flat list) of the lesson with course number n, or 0. */
    static int indexOf(Context c, int n) {
        JSONArray a = lessons(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject l = a.optJSONObject(i);
            if (l != null && l.optInt("n") == n) return i;
        }
        return 0;
    }

    static String text(JSONObject o, String key, String lang) {
        if (o == null) return "";
        JSONObject t = o.optJSONObject(key);
        if (t == null) return "";
        String v = t.optString(lang, "");
        return v.isEmpty() ? t.optString("en", "") : v;
    }
}
