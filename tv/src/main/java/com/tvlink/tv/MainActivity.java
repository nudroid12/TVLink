package com.tvlink.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final boolean TV = true;

    private boolean malay;
    private boolean showingHome;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        malay = getPreferences(MODE_PRIVATE).getBoolean("malay", false);
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
        if (title == null) return page;
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
        return styleButton(new Button(this), title, action, false);
    }

    private Button styleButton(Button button, String title, Runnable action, boolean primary) {
        button.setText(title);
        button.setAllCaps(false);
        button.setTextColor(primary ? 0xff0c1c35 : Color.WHITE);
        button.setTextSize(TV ? 20 : 16);
        button.setGravity(Gravity.CENTER);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_focused}, tile(primary ? 0xff9ac2ff : 0xff263a5a, true));
        background.addState(new int[] {}, tile(primary ? 0xff9ac2ff : 0xff1b2740, false));
        button.setBackground(background);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private String label(String english, String bahasaMelayu) {
        return malay ? bahasaMelayu : english;
    }

    private void chooseLanguage() {
        new AlertDialog.Builder(this)
                .setTitle(label("Language", "Bahasa"))
                .setSingleChoiceItems(new String[] {"English", "Bahasa Melayu"}, malay ? 1 : 0,
                        (dialog, choice) -> {
                            malay = choice == 1;
                            getPreferences(MODE_PRIVATE).edit().putBoolean("malay", malay).apply();
                            dialog.dismiss();
                            home();
                        })
                .setNegativeButton(label("Cancel", "Batal"), null)
                .show();
    }

    private void home() {
        showingHome = true;
        LinearLayout page = screen(null);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));
        ImageView wordmark = new ImageView(this);
        wordmark.setImageResource(R.drawable.tvlink_wordmark);
        wordmark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        wordmark.setContentDescription("TVLink");
        header.addView(wordmark, new LinearLayout.LayoutParams(dp(TV ? 154 : 116), -1));
        header.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 1, 1));
        Button language = button(label("EN", "BM"), this::chooseLanguage);
        language.setContentDescription(label("Language: English", "Bahasa: Bahasa Melayu"));
        header.addView(language, new LinearLayout.LayoutParams(dp(56), dp(48)));

        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(0xff303c56);
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(1));
        line.setMargins(0, dp(14), 0, dp(18));
        page.addView(divider, line);

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(menu, new LinearLayout.LayoutParams(-1, -2));
        String[] titles = {label("Send", "Hantar"), "Remote", label("Files", "Fail")};
        int[] icons = {R.drawable.ic_send, R.drawable.ic_remote, R.drawable.ic_files};
        Button first = null;
        for (int index = 0; index < titles.length; index++) {
            String title = titles[index];
            boolean primary = index == 0;
            Button item = styleButton(new MenuTile(), title, () -> preview(title), primary);
            item.setCompoundDrawablesWithIntrinsicBounds(0, icons[index], 0, 0);
            item.setCompoundDrawablePadding(dp(8));
            item.setPadding(dp(2), dp(10), dp(2), dp(10));
            item.setSingleLine(true);
            item.setTextSize(TV ? 20 : 14);
            if (primary) item.getCompoundDrawables()[1].setTint(0xff0c1c35);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, TV ? dp(110) : -2, 1);
            if (index > 0) params.setMarginStart(dp(8));
            menu.addView(item, params);
            if (primary) first = item;
        }
        if (TV && first != null) first.requestFocus();
    }

    private final class MenuTile extends Button {
        MenuTile() {
            super(MainActivity.this);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            if (!TV && android.view.View.MeasureSpec.getMode(widthSpec) == android.view.View.MeasureSpec.EXACTLY) {
                int width = android.view.View.MeasureSpec.getSize(widthSpec);
                heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(
                        Math.max(dp(72), width), android.view.View.MeasureSpec.EXACTLY);
            }
            super.onMeasure(widthSpec, heightSpec);
        }
    }

    private void preview(String title) {
        showingHome = false;
        LinearLayout page = screen(title);
        TextView status = new TextView(this);
        status.setText(label("Feature in development", "Fungsi dalam pembangunan"));
        status.setTextColor(0xffb2c2dd);
        status.setPadding(0, 0, 0, dp(16));
        page.addView(status);
        Button back = button(label("Home", "Muka depan"), this::home);
        page.addView(back, new LinearLayout.LayoutParams(-2, dp(48)));
        if (TV) back.requestFocus();
    }

    @Override public void onBackPressed() {
        if (showingHome) super.onBackPressed();
        else home();
    }
}
