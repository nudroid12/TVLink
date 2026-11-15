package com.tvlink.mobile;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class TransferHistory {
    private JSONArray entries = new JSONArray();

    synchronized JSONArray read() {
        return entries;
    }

    void add(String outcome) {
        add(outcome, null, -1);
    }

    synchronized void add(String outcome, String peer, long size) {
        try {
            JSONArray updated = new JSONArray();
            JSONObject item = new JSONObject().put("outcome", outcome).put("time", System.currentTimeMillis());
            if (peer != null && !peer.isEmpty()) item.put("peer", peer);
            if (size >= 0) item.put("size", size);
            updated.put(item);
            for (int i = 0; i < Math.min(entries.length(), 49); i++) updated.put(entries.get(i));
            entries = updated;
        } catch (JSONException error) {
            android.util.Log.w("TVLink", "Could not add transfer to history", error);
        }
    }

    synchronized void clear() {
        entries = new JSONArray();
    }

    static void addCard(Context context, LinearLayout history,
                        JSONObject item, DateFormat dates) {
        String outcome = item.optString("outcome");
        boolean sent = outcome.startsWith("Sent: ");
        boolean received = outcome.startsWith("Received: ");
        boolean succeeded = sent || received;
        String title = succeeded ? outcome.substring(sent ? "Sent: ".length() : "Received: ".length()) : outcome;
        String peer = item.optString("peer");
        if (succeeded && !peer.isEmpty()) title = (sent ? "To " : "From ") + peer + ": " + title;
        int accent = 0xff7fb1fe;

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 12);
        card.setPadding(padding, padding, padding, padding);
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(0xff111e35);
        panel.setCornerRadius(dp(context, 8));
        panel.setStroke(dp(context, 1), 0xff34425a);
        card.setBackground(panel);
        LinearLayout.LayoutParams cardSize = new LinearLayout.LayoutParams(-1, -2);
        cardSize.bottomMargin = dp(context, 8);
        history.addView(card, cardSize);

        LinearLayout header = new LinearLayout(context);
        card.addView(header, new LinearLayout.LayoutParams(-1, -2));
        TextView icon = label(context, succeeded ? (sent ? "↑" : "↓") : "!", accent, 24);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(icon, new LinearLayout.LayoutParams(dp(context, 30), -2));
        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        header.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        TextView name = label(context, title, 0xffffffff, 16);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.addView(name);
        boolean cancelled = "Transfer cancelled".equals(outcome);
        text.addView(label(context, succeeded ? "Succeeded" : cancelled ? "Cancelled" : "Failed",
                succeeded ? 0xff4cde99 : cancelled ? 0xffb8c3d6 : 0xffff9a9a, 14));

        long bytes = item.optLong("size", -1);
        if (succeeded && bytes >= 0) {
            ProgressBar progress = new ProgressBar(context, null,
                    android.R.attr.progressBarStyleHorizontal);
            progress.setProgressTintList(ColorStateList.valueOf(accent));
            progress.setMax(100);
            progress.setProgress(100);
            LinearLayout.LayoutParams progressSize = new LinearLayout.LayoutParams(-1, dp(context, 8));
            progressSize.topMargin = dp(context, 10);
            card.addView(progress, progressSize);
            String size = android.text.format.Formatter.formatShortFileSize(context, bytes);
            card.addView(label(context, size + " / " + size, 0xffb8c3d6, 13));
        } else {
            long time = item.optLong("time", -1);
            if (time >= 0) card.addView(label(context, dates.format(new Date(time)), 0xffb8c3d6, 13));
        }
    }

    private static TextView label(Context context, String value, int color, int sp) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        return view;
    }

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }
}
