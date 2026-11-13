package com.injaz.app;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

/** Renders the home-screen widget inside an activity so its layout can be checked. */
public class WidgetPreviewActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String lang = getIntent() == null ? null : getIntent().getStringExtra("lang");
        if (lang != null) Store.sp(this).edit().putString("lang", lang).apply();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF64748B);
        root.setFitsSystemWindows(true);
        View w = InjazWidget.build(this).apply(getApplicationContext(), root);
        int wpx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 330, getResources().getDisplayMetrics());
        int hpx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 170, getResources().getDisplayMetrics());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(wpx, hpx);
        lp.gravity = Gravity.CENTER;
        root.addView(w, lp);
        setContentView(root);
    }
}
