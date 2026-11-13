package com.injaz.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONObject;

public class MainActivity extends Activity {
    static final int REQ_NOTIF = 77;
    private static final String HOME = "file:///android_asset/www/index.html";

    WebView web;
    private FrameLayout root;
    private boolean pageReady = false;
    private String pendingRoute = null;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Notifier.channels(this);
        Scheduler.restore(this);

        root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        applyChrome(Store.sp(this).getString("chromeColor", isNight() ? "#0B1220" : "#F6F7F9"),
                Store.sp(this).getBoolean("chromeDarkIcons", !isNight()));

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return insets;
            });
        }

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setTextZoom(100);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.addJavascriptInterface(new Bridge(this), "Native");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme();
                if (scheme.equals("file")) return false;
                if (scheme.equals("http") || scheme.equals("https") || scheme.equals("mailto")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, u));
                    } catch (Exception ignored) {
                        // No app can open the link.
                    }
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (pendingRoute != null) {
                    String r = pendingRoute;
                    pendingRoute = null;
                    go(r);
                }
            }
        });

        handleIntent(getIntent());
        if (state != null) {
            web.restoreState(state);
        } else {
            web.loadUrl(HOME);
        }
    }

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Colours the area behind the system bars and picks light or dark bar icons. */
    void applyChrome(String hex, boolean darkIcons) {
        int color;
        try {
            color = Color.parseColor(hex);
        } catch (Exception e) {
            color = isNight() ? 0xFF0B1220 : 0xFFF6F7F9;
        }
        root.setBackgroundColor(color);
        web.setBackgroundColor(color);
        Window w = getWindow();
        if (Build.VERSION.SDK_INT < 35) {
            w.setStatusBarColor(color);
            w.setNavigationBarColor(color);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController ic = w.getInsetsController();
            if (ic != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                ic.setSystemBarsAppearance(darkIcons ? mask : 0, mask);
            }
        } else {
            View d = w.getDecorView();
            int f = d.getSystemUiVisibility();
            int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            d.setSystemUiVisibility(darkIcons ? (f | lightFlags) : (f & ~lightFlags));
        }
    }

    private void handleIntent(Intent i) {
        if (i == null) return;
        String ci = i.getStringExtra("ci");
        if ("notify".equals(ci)) Notifier.daily(this);
        if ("focus".equals(ci)) Notifier.focusDone(this, 50);
        String route = i.getStringExtra("route");
        if (route != null && !route.isEmpty()) {
            i.removeExtra("route");
            if (pageReady) go(route);
            else pendingRoute = route;
        }
    }

    private void go(String route) {
        web.evaluateJavascript("window.appRoute && window.appRoute(" + JSONObject.quote(route) + ")", null);
    }

    void js(String code) {
        runOnUiThread(() -> {
            if (web != null && pageReady) web.evaluateJavascript(code, null);
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (web == null || !pageReady) {
            super.onBackPressed();
            return;
        }
        web.evaluateJavascript("(window.appBack ? window.appBack() : false)", v -> {
            if (!"true".equals(v)) finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
            js("window.appResume && window.appResume()");
        }
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        js("window.appSystemTheme && window.appSystemTheme(" + (isNight() ? "true" : "false") + ")");
    }

    boolean systemDark() {
        return isNight();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_NOTIF) {
            boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            Store.sp(this).edit().putBoolean("askedNotif", true).apply();
            if (ok) Scheduler.scheduleDaily(this);
            js("window.appNotifResult && window.appNotifResult(" + (ok ? "true" : "false") + ")");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            root.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
