package com.tvlink.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.Arrays;

final class VoiceMeter {
    private final View anchor;
    private PopupWindow popup;
    private TextView status;
    private Waveform waveform;

    VoiceMeter(View anchor) {
        this.anchor = anchor;
    }

    void show(String message) {
        hide();
        if (!anchor.isAttachedToWindow()) return;
        Context context = anchor.getContext();
        FrameLayout overlay = new FrameLayout(context);
        overlay.setBackgroundColor(0x99000000);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(18));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff1b2740);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), 0xff34425a);
        panel.setBackground(background);
        int width = Math.min(dp(420), anchor.getResources().getDisplayMetrics().widthPixels - dp(24));
        overlay.addView(panel, new FrameLayout.LayoutParams(width, -2, Gravity.CENTER));

        TextView title = text(context, "Voice input", 22);
        panel.addView(title);
        status = text(context, message, 16);
        status.setPadding(0, dp(8), 0, dp(6));
        panel.addView(status);
        waveform = new Waveform(context);
        panel.addView(waveform, new LinearLayout.LayoutParams(-1, dp(36)));

        popup = new PopupWindow(overlay, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // The original mic button keeps receiving the held pointer and its release.
        popup.setTouchable(false);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.showAtLocation(anchor, Gravity.CENTER, 0, 0);
    }

    private TextView text(Context context, String text, int size) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        return view;
    }

    void label(String message) {
        if (status != null) status.setText(message);
    }

    void level(int value) {
        if (waveform != null) waveform.push(value);
    }

    void hide() {
        if (popup != null) popup.dismiss();
        popup = null;
        status = null;
        waveform = null;
    }

    private int dp(int value) {
        return Math.round(value * anchor.getResources().getDisplayMetrics().density);
    }

    private static final class Waveform extends View {
        private final int[] levels = new int[26];
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Waveform(Context context) {
            super(context);
            paint.setColor(0xffaec0ff);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        void push(int level) {
            if (level < 0) {
                Arrays.fill(levels, 0);
            } else {
                System.arraycopy(levels, 1, levels, 0, levels.length - 1);
                levels[levels.length - 1] = Math.max(0, Math.min(100, level));
            }
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float gap = getWidth() / (float) levels.length;
            float center = getHeight() * .5f;
            paint.setStrokeWidth(Math.max(1, gap * .32f));
            for (int i = 0; i < levels.length; i++) {
                float x = gap * (i + .5f);
                float extent = Math.max(1, center * .82f * levels[i] / 100);
                canvas.drawLine(x, center - extent, x, center + extent, paint);
            }
        }
    }
}
