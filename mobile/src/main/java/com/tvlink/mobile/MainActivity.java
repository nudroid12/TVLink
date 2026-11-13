package com.tvlink.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final boolean TV = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        home();
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private LinearLayout screen(String title) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(24), dp(24), dp(24));
        page.setBackgroundColor(0xff101624);
        setContentView(page);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(28);
        heading.setPadding(0, 0, 0, dp(20));
        page.addView(heading);
        return page;
    }

    private GradientDrawable tile(int fill, boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(12));
        shape.setStroke(dp(focused ? 3 : 1), focused ? 0xff9ac2ff : 0xff3a4b6b);
        return shape;
    }

    private Button button(String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TV ? 20 : 16);
        button.setGravity(Gravity.CENTER);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_focused}, tile(0xff263a5a, true));
        background.addState(new int[] {}, tile(0xff1b2740, false));
        button.setBackground(background);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void home() {
        LinearLayout page = screen("TVLink");
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(TV ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        page.addView(menu);
        Button first = null;
        for (String title : new String[] { "Hantar", "Remote", "Fail" }) {
            Button item = button(title, () -> preview(title));
            LinearLayout.LayoutParams params = TV
                    ? new LinearLayout.LayoutParams(0, dp(110), 1)
                    : new LinearLayout.LayoutParams(-1, dp(60));
            params.setMargins(0, 0, TV ? dp(8) : 0, TV ? 0 : dp(8));
            menu.addView(item, params);
            if (first == null) first = item;
        }
        if (TV && first != null) first.requestFocus();
    }

    private void preview(String title) {
        LinearLayout page = screen(title);
        TextView status = new TextView(this);
        status.setText("Fungsi dalam pembangunan");
        status.setTextColor(0xffb2c2dd);
        status.setPadding(0, 0, 0, dp(16));
        page.addView(status);
        Button back = button("Muka depan", this::home);
        page.addView(back, new LinearLayout.LayoutParams(-2, dp(48)));
        if (TV) back.requestFocus();
    }

    @Override public void onBackPressed() {
        home();
    }
}
