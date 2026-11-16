package com.tvlink.mobile;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

final class VoiceMeter extends LinearLayout {
    private final TextView status;
    private final ProgressBar bar;

    VoiceMeter(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        status = new TextView(context);
        status.setGravity(Gravity.CENTER);
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        addView(status, new LayoutParams(-1, -2));
        bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(0xffaec0ff));
        int height = Math.round(6 * getResources().getDisplayMetrics().density);
        addView(bar, new LayoutParams(-1, height));
        hide();
    }

    void show(String message) {
        label(message);
        level(0);
        setVisibility(VISIBLE);
    }

    void label(String message) { status.setText(message); }

    void level(int value) { bar.setProgress(Math.max(0, Math.min(100, value))); }

    void hide() { setVisibility(INVISIBLE); }
}
