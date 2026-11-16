package com.tvlink.tv;

import com.tvlink.ui.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Toast;
import java.io.*;
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
    private static final int FOCUS_FILL = Color.WHITE;
    private static final int FOCUS_BLUE = 0xff7fb1fe;

    private boolean showingHome;
    private boolean showingAbout;

    @Override public void onCreate(Bundle state) {
        setTheme(R.style.TVLinkTelevisionTheme);
        super.onCreate(state);
        getPreferences(MODE_PRIVATE).registerOnSharedPreferenceChangeListener(deviceListener);
        home();
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private LinearLayout screen() {
        showingAbout = false;
        exitDeadline = 0;
        if (exitMessage != null) {
            exitMessage.cancel();
            exitMessage = null;
        }
        if (transferRefresh != null) {
            refresh.removeCallbacks(transferRefresh);
            transferRefresh = null;
        }
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(24), dp(24), dp(24));
        page.setBackgroundColor(0xff101624);
        deviceCardName = null;
        deviceCard = null;
        storageRoot = null;
        storageFolder = null;
        setContentView(page);
        return page;
    }

    private GradientDrawable tile(int fill, boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(12));
        shape.setStroke(dp(focused ? 3 : 1), focused ? FOCUS_FILL : CONTENT_DIVIDER);
        return shape;
    }

    private StateListDrawable highlightOnFocus(int fill) {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_focused}, tile(FOCUS_FILL, true));
        background.addState(new int[] {}, tile(fill, false));
        return background;
    }

    private StateListDrawable flatRowBackground() {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_focused}, tile(FOCUS_FILL, true));
        background.addState(new int[] {}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        return background;
    }

    private android.content.res.ColorStateList focusText(int normal) {
        return new android.content.res.ColorStateList(
                new int[][] {{android.R.attr.state_focused}, {}}, new int[] {0xff0c1c35, normal});
    }

    private void styleRowText(TextView text) {
        text.setTextColor(focusText(Color.WHITE));
        text.setTextSize(18);
        text.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL);
    }

    private Button button(String title, Runnable action) {
        return styleButton(new Button(this), title, action, false);
    }

    private Button styleButton(Button button, String title, Runnable action, boolean primary) {
        button.setText(title);
        button.setAllCaps(false);
        button.setTextColor(focusText(primary ? 0xff0c1c35 : Color.WHITE));
        button.setCompoundDrawableTintList(focusText(Color.WHITE));
        button.setTextSize(18);
        button.setGravity(Gravity.CENTER);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_focused}, tile(FOCUS_FILL, true));
        background.addState(new int[] {}, tile(primary ? 0xff9ac2ff : 0xff1b2740, false));
        button.setBackground(background);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private TransferServer receiver;
    private TextView pairing;
    private boolean pairingFromDevicePage;
    private FileApprovalDialog fileApprovalDialog;
    private final android.os.Handler refresh = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable updatePairing = new Runnable() {
        @Override public void run() {
            if (pairing == null) return;
            if (isLinked()) {
                if (pairingFromDevicePage) showLinkStatus();
                else showReceive();
            } else if (receiver != null) {
                String status = receiver.pairingStatus();
                pairing.setText(pairingFromDevicePage && "Ready to pair".equals(status)
                        ? "Waiting for your phone…" : status);
                showFileApproval();
                refresh.postDelayed(this, 400);
            }
        }
    };
    private final java.util.concurrent.ExecutorService sender = java.util.concurrent.Executors.newSingleThreadExecutor();

    private boolean isLinked() {
        return !getPreferences(MODE_PRIVATE).getString("link_token", "").isEmpty();
    }

    private File received() {
        return TvSettings.directory(this);
    }

    private Button pairedHeader;
    private TextView deviceCardName;
    private LinearLayout deviceCard;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener deviceListener =
            (prefs, key) -> {
                if ("peer_name".equals(key) || "link_token".equals(key)) updateDeviceLabels();
            };

    private void updateDeviceLabels() {
        boolean linked = isLinked();
        if (pairedHeader != null) {
            pairedHeader.setText(linked ? peerName() : "Pair");
            pairedHeader.setGravity(linked ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER);
            pairedHeader.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    linked ? R.drawable.ic_phone : 0, 0, 0, 0);
        }
        if (deviceCardName != null && linked) deviceCardName.setText(peerName());
        if (deviceCard != null && !linked) {
            LinearLayout body = (LinearLayout) deviceCard.getParent();
            body.removeView(deviceCard);
            deviceCardName = null;
            deviceCard = null;
            addUnlinkedDevice(body);
        }
    }

    private String peerName() {
        return getPreferences(MODE_PRIVATE).getString("peer_name", "Name unavailable");
    }

    private void addHeader(LinearLayout page, boolean pairingIntro) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.tv_tvlink_wordmark);
        logo.setContentDescription("TVLink");
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(logo, new LinearLayout.LayoutParams(dp(116), -1));
        header.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 1, 1));
        Button pair = button(isLinked() ? peerName() : "Pair", this::showLinkStatus);
        pairedHeader = pair;
        pair.setMinWidth(0);
        pair.setCompoundDrawablePadding(dp(6));
        pair.setPadding(dp(12), 0, dp(12), 0);
        updateDeviceLabels();
        pair.setMaxWidth(dp(280));
        pair.setSingleLine(true);
        pair.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(pair, new LinearLayout.LayoutParams(-2, dp(44)));
        pair.setVisibility(pairingIntro ? android.view.View.GONE : android.view.View.VISIBLE);
        Button gear = button("", this::showSettings);
        gear.setContentDescription("Settings");
        gear.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.tv_ic_settings, 0, 0);
        gear.setPadding(0, dp(10), 0, 0);
        LinearLayout.LayoutParams gearSize = new LinearLayout.LayoutParams(dp(44), dp(44));
        gearSize.setMarginStart(dp(8));
        header.addView(gear, gearSize);
        divider(page);
    }

    private LinearLayout feature(String title) {
        return feature(title, null);
    }

    private LinearLayout feature(String title, LinearLayout actions) {
        showingHome = false;
        stopPairingDisplay();
        LinearLayout page = screen();
        boolean pairingIntro = !isLinked() && ("Remote".equals(title) || "Pair".equals(title));
        addHeader(page, pairingIntro);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(48)));
        boolean showBack = !"Pair".equals(title) && !"Remote".equals(title)
                && !"My Files".equals(title) && !"Settings".equals(title)
                && !"Send / Receive".equals(title) && !"About".equals(title);
        if (showBack) {
            titleRow.addView(button("Back", this::home), new LinearLayout.LayoutParams(dp(82), dp(42)));
        }
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(24);
        heading.setTextColor(Color.WHITE);
        heading.setPadding(showBack ? dp(16) : 0, 0, 0, 0);
        if (actions == null) {
            titleRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        } else {
            titleRow.addView(heading, new LinearLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams actionsSize = new LinearLayout.LayoutParams(0, -1, 1);
            actionsSize.setMarginStart(dp(16));
            titleRow.addView(actions, actionsSize);
        }
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        return body;
    }

    private void home() {
        showingHome = true;
        stopPairingDisplay();
        LinearLayout page = screen();
        addHeader(page, false);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        page.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = Math.round((metrics.widthPixels - dp(48)) * .23f);
        int height = Math.min(Math.round(metrics.heightPixels * .44f), metrics.heightPixels - dp(126));
        String[] titles = {"Send / Receive", "Remote", "Files"};
        int[] icons = {R.drawable.tv_ic_send, R.drawable.tv_ic_remote, R.drawable.tv_ic_files};
        Runnable[] actions = {this::showReceive, this::showRemoteHelp, this::showFiles};
        // Keep equal outer margins while bringing the cards closer together.
        row.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 1, 2));
        for (int index = 0; index < titles.length; index++) {
            Button card = button(titles[index], actions[index]);
            card.setBackground(highlightOnFocus(0xff1b2740));
            card.setTextColor(new android.content.res.ColorStateList(
                    new int[][] {new int[] {android.R.attr.state_focused}, new int[] {}},
                    new int[] {0xff0c1c35, Color.WHITE}));
            android.graphics.drawable.Drawable icon = getDrawable(icons[index]).mutate();
            int iconSize = Math.min(dp(56), width / 2);
            icon.setBounds(0, 0, iconSize, iconSize);
            card.setCompoundDrawables(null, icon, null, null);
            card.setPadding(dp(4), height / 6, dp(4), height / 9);
            card.setTextSize(22);
            card.getCompoundDrawables()[1].mutate().setTintList(new android.content.res.ColorStateList(
                    new int[][] {new int[] {android.R.attr.state_focused}, new int[] {}},
                    new int[] {0xff0c1c35, Color.WHITE}));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
            row.addView(card, params);
            row.addView(new android.view.View(this),
                    new LinearLayout.LayoutParams(0, 1, index == titles.length - 1 ? 2 : 1));
            if (index == 0) card.requestFocus();
        }
    }

    private void showRemoteHelp() {
        LinearLayout body = feature("Remote");
        if (isLinked()) {
            info(body, "Use Remote in TVLink on your phone to control this TV.");
            showDevice(body);
        } else {
            addUnlinkedDevice(body);
        }
    }

    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            return "";
        }
    }

    private void showSettings() {
        LinearLayout rows = feature("Settings");
        setting(rows, "About", "Version " + appVersion(), this::showAbout);
        setting(rows, "Device Name", TvSettings.device(this), () -> editSetting("Device Name", "device_name", TvSettings.device(this)));
        setting(rows, "Save received files to", TvSettings.directoryLabel(this), this::chooseDirectory);
        setting(rows, "Direct Downloads access", TvSettings.hasStorageAccess(this) ? "Allowed" : "Optional", this::requestStorageAccessFromSettings);
        toggle(rows, "Receive Transfers", "Accept incoming files", "receive", true);
        toggle(rows, "Launch at startup", "Open TVLink at boot when supported", "startup", false);
        toggle(rows, "Overwrite", "Replace files with the same name", "overwrite", false);
        toggle(rows, "Modify date", "Update the modified date when replacing files", "modify_date", true);
        rows.getChildAt(0).requestFocus();
    }

    private void showAbout() {
        LinearLayout body = feature("About");
        showingAbout = true;

        LinearLayout columns = new LinearLayout(this);
        columns.setBaselineAligned(false);
        columns.setPadding(0, dp(24), 0, dp(16));
        body.addView(columns, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams identitySize = new LinearLayout.LayoutParams(0, -2, 1.05f);
        identitySize.setMarginEnd(dp(24));
        columns.addView(identity, identitySize);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.tv_tvlink_wordmark);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        logo.setContentDescription("TVLink");
        int logoWidth = Math.min(dp(260), (getResources().getDisplayMetrics().widthPixels - dp(72)) / 2);
        int logoHeight = dp(84);
        identity.addView(logo, new LinearLayout.LayoutParams(logoWidth, logoHeight));
        // The first visible pixel of the T is 34 px from the left edge of the 820 × 239 PNG.
        float logoScale = Math.min(logoWidth / 820f, logoHeight / 239f);
        int textInset = Math.round(34 * logoScale);
        addAboutText(identity, "Version " + appVersion(), Color.WHITE, 20, 4, textInset);
        addAboutText(identity, "Remote control, phone keyboard input and file transfer for Android TV.",
                Color.WHITE, 18, 12, textInset);
        addAboutText(identity, "Developed by NudroidLabs\nPublished by KebunApp", 0xffc0cce4, 16, 24, textInset);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        columns.addView(actions, new LinearLayout.LayoutParams(0, -2, 1));
        Button updates = addAboutAction(actions, "Check for updates", R.drawable.ic_about_update,
                () -> ReleaseCheck.check(this));
        addAboutAction(actions, "Support", R.drawable.ic_about_support, this::showAboutSupport);
        updates.requestFocus();
    }

    private void addAboutText(LinearLayout parent, String value, int color, int size, int topMargin, int startMargin) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(size);
        text.setLineSpacing(dp(4), 1);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(topMargin);
        params.setMarginStart(startMargin);
        parent.addView(text, params);
    }

    private Button addAboutAction(LinearLayout content, String label, int icon, Runnable action) {
        Button row = button(label, action);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setCompoundDrawablePadding(dp(12));
        row.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, R.drawable.ic_about_chevron, 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        row.setMinHeight(dp(58));
        content.addView(row, params);
        return row;
    }

    private void showAboutSupport() {
        String issues = "https://github.com/nudroid12/TVLink/issues";
        new AlertDialog.Builder(this)
                .setTitle("Support")
                .setMessage("Report an issue with your device model and TVLink version.\n\n" + issues)
                .setNegativeButton("Close", null)
                .setPositiveButton("Open GitHub", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(issues)));
                    } catch (android.content.ActivityNotFoundException error) {
                        Toast.makeText(this, "Open the GitHub link on your phone.", Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void setting(LinearLayout rows, String title, String detail, Runnable action) {
        addSettingsRow(rows, title, detail, null, action);
    }

    private void toggle(LinearLayout rows, String title, String detail, String key, boolean fallback) {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        android.widget.CompoundButton control = new android.widget.CheckBox(this);
        control.setFocusable(false);
        control.setClickable(false);
        control.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        control.setChecked(prefs.getBoolean(key, fallback));
        control.setOnCheckedChangeListener((view, checked) -> prefs.edit().putBoolean(key, checked).apply());
        addSettingsRow(rows, title, detail, control, control::toggle);
    }

    private void addSettingsRow(LinearLayout rows, String title, String detail,
                                android.widget.CompoundButton control, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setMinimumHeight(dp(64));
        row.setBackground(flatRowBackground());
        row.setFocusable(true);
        row.setOnClickListener(view -> action.run());
        row.setContentDescription(title + ", " + detail);
        row.setAccessibilityDelegate(new android.view.View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(android.view.View host,
                    android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(control == null ? Button.class.getName() : control.getClass().getName());
                if (control != null) {
                    info.setCheckable(true);
                    info.setChecked(control.isChecked());
                }
            }
        });
        rows.addView(row, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        labels.setDuplicateParentStateEnabled(true);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(18);
        heading.setDuplicateParentStateEnabled(true);
        heading.setTextColor(focusText(Color.WHITE));
        labels.addView(heading);
        TextView description = new TextView(this);
        description.setText(detail);
        description.setTextSize(14);
        description.setDuplicateParentStateEnabled(true);
        description.setTextColor(focusText(0xffb8c3d6));
        LinearLayout.LayoutParams descriptionSize = new LinearLayout.LayoutParams(-1, -2);
        descriptionSize.topMargin = dp(2);
        labels.addView(description, descriptionSize);
        if (control != null) {
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, -2);
            size.setMarginStart(dp(12));
            row.addView(control, size);
            android.content.res.ColorStateList normalTint = control.getButtonTintList();
            android.content.res.ColorStateList focusedTint = new android.content.res.ColorStateList(
                    new int[][] {{android.R.attr.state_checked}, {}}, new int[] {0xff0c1c35, 0xff53627a});
            row.setOnFocusChangeListener((view, focused) ->
                    control.setButtonTintList(focused ? focusedTint : normalTint));
        }
        android.view.View line = new android.view.View(this);
        line.setBackgroundColor(CONTENT_DIVIDER);
        rows.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void showTextEditor(AlertDialog dialog, EditText input) {
        android.view.Window window = dialog.getWindow();
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show();
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        input.requestFocus();
        Runnable keyboard = () -> {
            if (!dialog.isShowing() || !input.hasFocus()) return;
            android.view.inputmethod.InputMethodManager ime =
                    getSystemService(android.view.inputmethod.InputMethodManager.class);
            if (ime != null) ime.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        };
        input.setOnClickListener(view -> input.post(keyboard));
        input.setOnKeyListener((view, key, event) -> {
            if (key != android.view.KeyEvent.KEYCODE_DPAD_CENTER && key != android.view.KeyEvent.KEYCODE_ENTER) return false;
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) input.post(keyboard);
            return true;
        });
    }

    private void editSetting(String title, String key, String initial) {
        EditText value = new EditText(this);
        value.setSingleLine(true);
        value.setText(initial);
        AlertDialog editor = new AlertDialog.Builder(this).setTitle(title).setView(value).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String input = value.getText().toString().trim();
                    if (input.isEmpty() || input.length() > 40) {
                        notifyUser("Enter a device name of 1–40 characters.");
                        return;
                    }
                    getPreferences(MODE_PRIVATE).edit().putString(key, input).apply();
                    showSettings();
                }).create();
        showTextEditor(editor, value);
    }

    private void chooseDirectory() {
        int destination = TvSettings.destination(this);
        int checked = destination == TvSettings.DOWNLOADS_TVLINK ? -1 : destination;
        new AlertDialog.Builder(this).setTitle("Save received files to")
                .setSingleChoiceItems(new String[] {
                        "TVLink app folder\n" + new File(getFilesDir(), "received").getAbsolutePath(),
                        "Downloads\n" + TvSettings.downloadsDirectory().getAbsolutePath()
                }, checked, (dialog, index) -> {
                    dialog.dismiss();
                    closeReceiver();
                    TvSettings.setDestination(this, index);
                    if (isLinked()) startReceiver();
                    showSettings();
                    if (index != TvSettings.APP_FOLDER && !TvSettings.hasStorageAccess(this)) {
                        requestStorageAccessFromSettings();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private boolean checkingStorageAccess;
    private boolean returnToStorageSettings;

    private void requestStorageAccessFromSettings() {
        returnToStorageSettings = true;
        requestStorageAccess();
    }

    private void requestStorageAccess() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                checkingStorageAccess = true;
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (android.content.ActivityNotFoundException error) {
                checkingStorageAccess = false;
                returnToStorageSettings = false;
                notifyUser("This TV does not expose the all-files access screen.");
            }
        } else {
            requestPermissions(new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 81);
        }
    }

    private static final int CONTENT_DIVIDER = 0xff34425a;

    private void divider(LinearLayout page) {

        android.view.View line = new android.view.View(this);
        line.setBackgroundColor(CONTENT_DIVIDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.setMargins(0, dp(8), 0, dp(8));
        page.addView(line, params);
    }

    private TextView info(LinearLayout body, String value) {
        TextView text = new TextView(this);
        text.setPadding(0, dp(8), 0, dp(8));
        text.setText(value);
        body.addView(text);
        return text;
    }

    private void closeReceiver() {
        if (receiver != null) receiver.close();
        receiver = null;
        stopPairingDisplay();
    }

    private boolean startReceiver() {
        if (receiver != null) return true;
        try {
            receiver = new TransferServer(received(), getNoBackupFilesDir(), getPreferences(MODE_PRIVATE),
                    filename -> runOnUiThread(() -> showReceivedNotice(filename)));
            return true;
        } catch (IOException error) {
            notifyUser("Cannot start receiver: " + error.getMessage());
            return false;
        }
    }

    private void showLinkStatus() {
        LinearLayout body = feature("Pair");
        if (isLinked()) showDevice(body);
        else startPairing(body, true);
    }

    private void showDevice(LinearLayout body) {
        info(body, "Device:");
        if (!isLinked()) {
            addUnlinkedDevice(body);
            return;
        }
        LinearLayout card = new LinearLayout(this);
        deviceCard = card;
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(tile(0xff1b2740, false));
        body.addView(card, new LinearLayout.LayoutParams(dp(244), -2));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_phone);
        card.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        deviceCardName = info(card, peerName());
        deviceCardName.setGravity(Gravity.CENTER);
        deviceCardName.setMaxLines(2);
        deviceCardName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Button unpair = button("Unpair", () -> {
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Unpair " + peerName() + "?")
                    .setMessage("This removes the file link. Android TV Remote access can be removed in system settings.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Unpair", (d, w) -> unpairPhone()).show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xffff7186);
        });
        unpair.setTextColor(focusText(0xffff9baa));
        StateListDrawable unpairBackground = new StateListDrawable();
        unpairBackground.addState(new int[] {android.R.attr.state_focused}, tile(FOCUS_FILL, true));
        GradientDrawable unpairOutline = tile(0xff1b2740, false);
        unpairBackground.addState(new int[] {}, unpairOutline);
        unpair.setBackground(unpairBackground);
        card.addView(unpair, new LinearLayout.LayoutParams(-1, dp(44)));
    }

    private void addUnlinkedDevice(LinearLayout body) {
        addPairingIntro(body, "Use TVLink on your phone to control this TV.");
        Button action = button("Pair phone", this::showLinkStatus);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_focused}, tile(FOCUS_FILL, true));
        background.addState(new int[] {}, tile(0xff121d33, false));
        action.setBackground(background);
        action.setTextColor(focusText(Color.WHITE));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(320), dp(48));
        size.topMargin = dp(18);
        body.addView(action, size);
        action.requestFocus();
    }

    private void addPairingIntro(LinearLayout body, String instruction) {
        ((ScrollView) body.getParent()).setFillViewport(true);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, dp(12), 0, dp(28));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.tv_ic_pair_devices);
        body.addView(icon, new LinearLayout.LayoutParams(dp(132), dp(66)));

        TextView heading = new TextView(this);
        heading.setText("Pair your phone");
        heading.setTextSize(28);
        heading.setTextColor(Color.WHITE);
        heading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleSize = new LinearLayout.LayoutParams(-1, -2);
        titleSize.topMargin = dp(14);
        body.addView(heading, titleSize);

        TextView detail = info(body, instruction);
        detail.setGravity(Gravity.CENTER);
        detail.setTextSize(18);
        detail.setTextColor(0xffb8c3d6);
    }

    private void unpairPhone() {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String host = prefs.getString("peer_host", "");
        String token = prefs.getString("link_token", "");
        if (activeSend != null) activeSend.cancel();
        closeReceiver();
        prefs.edit().remove("link_token").remove("peer_host").remove("peer_name").apply();
        showLinkStatus();
        sender.execute(() -> {
            try (TransferClient client = new TransferClient(host, "UNPAIR", token)) {
                client.confirm();
            } catch (IOException error) {
                runOnUiThread(() -> notifyUser("Unpaired here. Phone was unavailable."));
            }
        });
    }

    private void startPairing(LinearLayout body, boolean fromDevicePage) {
        pairingFromDevicePage = fromDevicePage;
        if (fromDevicePage) {
            addPairingIntro(body, "On your phone, open TVLink\nSelect Pair → Find TV");
        }
        if (!startReceiver()) {
            if (fromDevicePage) {
                TextView error = info(body, "Could not start pairing. Press Back and reopen Pair.");
                error.setGravity(Gravity.CENTER);
            }
            return;
        }
        if (!fromDevicePage) {
            info(body, "On your phone, tap Pair → Find TV. Keep this TV screen open.");
        }
        pairing = new TextView(this);
        pairing.setText(fromDevicePage ? "Waiting for your phone…" : "Ready to pair");
        if (fromDevicePage) {
            pairing.setGravity(Gravity.CENTER);
            pairing.setTextSize(16);
            pairing.setTextColor(0xffa9c8ff);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(FOCUS_BLUE);
            dot.setBounds(0, 0, dp(8), dp(8));
            pairing.setCompoundDrawablesRelative(dot, null, null, null);
            pairing.setCompoundDrawablePadding(dp(10));
            LinearLayout.LayoutParams statusSize = new LinearLayout.LayoutParams(-2, -2);
            statusSize.topMargin = dp(8);
            body.addView(pairing, statusSize);
        } else {
            body.addView(pairing);
        }
        receiver.enableLinking(true);
        refresh.postDelayed(updatePairing, 400);
        refresh.postDelayed(expireLink, 180000);
    }

    private int capturedPairingKey = android.view.KeyEvent.KEYCODE_UNKNOWN;

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int code = event.getKeyCode();
        if (code == capturedPairingKey) {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) {
                capturedPairingKey = android.view.KeyEvent.KEYCODE_UNKNOWN;
            }
            return true;
        }
        if (started && pairing != null && receiver != null && hasWindowFocus()
                && event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            int digit;
            switch (code) {
                case android.view.KeyEvent.KEYCODE_DPAD_CENTER: digit = -1; break;
                case android.view.KeyEvent.KEYCODE_DPAD_UP: digit = 0; break;
                case android.view.KeyEvent.KEYCODE_DPAD_DOWN: digit = 1; break;
                case android.view.KeyEvent.KEYCODE_DPAD_LEFT: digit = 2; break;
                case android.view.KeyEvent.KEYCODE_DPAD_RIGHT: digit = 3; break;
                default: return super.dispatchKeyEvent(event);
            }
            if (receiver.receiveRemotePairingKey(digit)) {
                capturedPairingKey = code;
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void showFileApproval() {
        TransferServer.FileApproval request = receiver.fileApproval();
        if (fileApprovalDialog != null && fileApprovalDialog.request == request) return;
        dismissFileApproval();
        if (request == null) return;
        TransferServer server = receiver;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Connect file transfer")
                .setMessage("Does this number match the phone? " + request.code)
                .setNegativeButton("Decline", (d, w) -> server.answerFileApproval(request, false))
                .setPositiveButton("Allow", (d, w) -> server.answerFileApproval(request, true))
                .setOnCancelListener(d -> server.answerFileApproval(request, false)).show();
        fileApprovalDialog = new FileApprovalDialog(request, dialog);
    }

    private void dismissFileApproval() {
        if (fileApprovalDialog == null) return;
        fileApprovalDialog.dialog.dismiss();
        fileApprovalDialog = null;
    }

    private static final class FileApprovalDialog {
        final TransferServer.FileApproval request;
        final AlertDialog dialog;
        FileApprovalDialog(TransferServer.FileApproval request, AlertDialog dialog) {
            this.request = request;
            this.dialog = dialog;
        }
    }

    private final Runnable expireLink = () -> {
        refresh.removeCallbacks(updatePairing);
        dismissFileApproval();
        if (receiver == null || pairing == null) return;
        receiver.enableLinking(false);
        pairing.setText("Pairing timed out. Press Back and reopen Pair to try again.");
    };

    private void showReceive() {
        LinearLayout body = feature("Send / Receive");
        LinearLayout columns = new LinearLayout(this);
        columns.setPadding(0, dp(12), 0, dp(12));
        body.addView(columns, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout send = new LinearLayout(this);
        send.setOrientation(LinearLayout.VERTICAL);
        columns.addView(send, new LinearLayout.LayoutParams(0, -2, 2));
        addTransferHeading(send, "Send");
        Button sendFile = button("Send file", this::chooseFileToSend);
        send.addView(sendFile, new LinearLayout.LayoutParams(dp(160), dp(48)));

        android.view.View rule = new android.view.View(this);
        rule.setBackgroundColor(CONTENT_DIVIDER);
        LinearLayout.LayoutParams ruleSize = new LinearLayout.LayoutParams(dp(1), -1);
        ruleSize.setMargins(dp(20), 0, dp(20), 0);
        columns.addView(rule, ruleSize);
        LinearLayout receive = new LinearLayout(this);
        receive.setOrientation(LinearLayout.VERTICAL);
        columns.addView(receive, new LinearLayout.LayoutParams(0, -2, 3));
        addTransferHeading(receive, "Receive");
        if (getPreferences(MODE_PRIVATE).getBoolean("receive", true)) {
            if (isLinked()) startReceiver();
            else startPairing(receive, false);
        }
        addTransferDetails(body, receive);
        sendFile.requestFocus();
    }

    private void sendFile(File file) {
        if (!isLinked()) {
            showLinkStatus();
            return;
        }
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String host = prefs.getString("peer_host", "");
        String token = prefs.getString("link_token", "");
        queueSend(session -> {
            try (FileInputStream stream = new FileInputStream(file)) {
                sendToPeer(session, host, token, file.getName(), stream.getChannel().size(), stream);
            }
            return file.getName();
        });
    }

    private interface SendWork { String run(SendSession session) throws IOException; }

    private void queueSend(SendWork work) {
        SendSession session = beginSend();
        if (session == null) return;
        sender.execute(() -> {
            try (java.net.Socket socket = session.socket) {
                String name = work.run(session);
                finishSend(session, "Sent: " + name);
            } catch (IOException | SecurityException error) {
                finishSend(session, session.cancelled ? "Transfer cancelled" : "Could not send: " + error.getMessage());
            }
        });
    }

    private static final class SendSession {
        final java.net.Socket socket = new java.net.Socket();
        final AlertDialog dialog;
        volatile boolean cancelled;
        String peer;
        long size = -1;

        SendSession(AlertDialog dialog) { this.dialog = dialog; }

        void cancel() {
            cancelled = true;
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private SendSession activeSend;

    private SendSession beginSend() {
        if (activeSend != null) {
            notifyUser("A transfer is already running.");
            return null;
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Send file")
                .setMessage("Preparing file…").setCancelable(false).setNegativeButton("Cancel", null).show();
        SendSession session = new SendSession(dialog);
        activeSend = session;
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
            session.dialog.setMessage("Cancelling transfer…");
            session.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            session.cancel();
        });
        return session;
    }

    private TextView addTransferHeading(LinearLayout parent, String label) {
        TextView heading = new TextView(this);
        heading.setText(label);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(20);
        heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        heading.setPadding(0, dp(8), 0, dp(8));
        parent.addView(heading);
        return heading;
    }

    private boolean started;
    private Runnable transferRefresh;

    private void addTransferDetails(LinearLayout body, LinearLayout receive) {
        TextView status = info(receive, "");
        status.setTextColor(Color.WHITE);
        android.widget.ProgressBar progress = new android.widget.ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(android.view.View.GONE);
        receive.addView(progress, new LinearLayout.LayoutParams(-1, dp(12)));
        divider(body);
        LinearLayout historyHeading = new LinearLayout(this);
        historyHeading.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(historyHeading, new LinearLayout.LayoutParams(-1, -2));
        TextView heading = addTransferHeading(historyHeading, "Transfer history");
        heading.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        historyHeading.addView(button("Clear history", this::confirmClearHistory),
                new LinearLayout.LayoutParams(-2, dp(48)));
        LinearLayout history = new LinearLayout(this);
        history.setOrientation(LinearLayout.VERTICAL);
        body.addView(history);
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        java.text.DateFormat dates = java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.SHORT, java.text.DateFormat.SHORT);
        java.util.regex.Pattern percent = java.util.regex.Pattern.compile(" · (\\d{1,3})%$");
        transferRefresh = new Runnable() {
            private String lastHistory;

            @Override public void run() {
                if (!started || transferRefresh != this) return;
                boolean enabled = prefs.getBoolean("receive", true);
                boolean paired = isLinked();
                String incoming = receiver == null ? "" : receiver.receiveProgress;
                String message;
                if (!incoming.isEmpty()) message = incoming;
                else if (!enabled) message = "Receive Transfers is disabled in Settings.";
                else if (!paired) message = "Pair to receive files.";
                else if (receiver == null) message = "Could not start receiver.";
                else message = "●  Ready to receive from " + peerName() + ".";
                if (!message.contentEquals(status.getText())) {
                    android.text.SpannableString label = new android.text.SpannableString(message);
                    if (message.startsWith("●  Ready")) {
                        label.setSpan(new android.text.style.ForegroundColorSpan(FOCUS_BLUE), 0, 1,
                                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    status.setText(label);
                }

                java.util.regex.Matcher match = percent.matcher(incoming);
                boolean active = match.find();
                progress.setVisibility(active ? android.view.View.VISIBLE : android.view.View.GONE);
                if (active) progress.setProgress(Integer.parseInt(match.group(1)));

                org.json.JSONArray entries = TransferHistory.read(prefs);
                String snapshot = entries.toString();
                if (!snapshot.equals(lastHistory)) {
                    lastHistory = snapshot;
                    history.removeAllViews();
                    if (entries.length() == 0) info(history, "No transfers yet");
                    for (int index = 0; index < entries.length(); index++) {
                        org.json.JSONObject item = entries.optJSONObject(index);
                        if (item == null) continue;
                        TransferHistory.addCard(MainActivity.this, history, item, dates);
                    }
                }
                refresh.postDelayed(this, 500);
            }
        };
        if (started) refresh.post(transferRefresh);
    }

    private void finishSend(SendSession session, String outcome) {
        TransferHistory.add(getPreferences(MODE_PRIVATE), outcome, session.peer, session.size);
        runOnUiThread(() -> {
            if (activeSend == session) activeSend = null;
            session.dialog.dismiss();
            if (isFinishing() || isDestroyed()) return;
            if (outcome.startsWith("Could not send: ")) {
                new AlertDialog.Builder(this).setTitle("Send to phone failed")
                        .setMessage(outcome.substring("Could not send: ".length()))
                        .setPositiveButton("OK", null).show();
            } else {
                notifyUser(outcome);
            }
        });
    }

    private void sendToPeer(SendSession session, String host, String token, String name, long size, InputStream stream)
            throws IOException {
        session.peer = peerName();
        session.size = size;
        if (stream == null) throw new IOException("Cannot read file");
        if (session.cancelled) throw new IOException("Transfer cancelled");
        runOnUiThread(() -> {
            if (!session.cancelled) session.dialog.setMessage("Finding linked device…");
        });
        String address;
        try {
            address = LinkNetwork.resolve(host, token);
        } catch (IOException error) {
            throw new IOException("Keep TVLink open on your phone and check its receive status on Home.\n\n"
                    + error.getMessage(), error);
        }
        if (session.cancelled) throw new IOException("Transfer cancelled");
        try (TransferClient client = new TransferClient(address, "SEND", token, session.socket)) {
            client.send(name, size, stream, percent -> runOnUiThread(() -> {
                if (session.cancelled) return;
                session.dialog.setMessage(percent < 100 ? name + " · " + percent + "%" : "Finishing transfer…");
            }));
        }
    }

    private void chooseFileToSend() {
        if (!isLinked()) {
            showLinkStatus();
            return;
        }
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*");
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(picker, 11);
        } catch (android.content.ActivityNotFoundException error) {
            notifyUser("This TV has no file picker. Choose a file from My Files.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 11 || result != RESULT_OK || data == null || data.getData() == null || !isLinked()) return;
        android.net.Uri uri = data.getData();
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String host = prefs.getString("peer_host", "");
        String token = prefs.getString("link_token", "");
        queueSend(session -> {
            String name;
            long size;
            try (android.database.Cursor cursor = getContentResolver().query(uri,
                    new String[] {android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE},
                    null, null, null)) {
                if (cursor == null || !cursor.moveToFirst() || cursor.isNull(0) || cursor.isNull(1)) {
                    throw new IOException("File name or size unavailable");
                }
                name = cursor.getString(0);
                size = cursor.getLong(1);
            }
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                sendToPeer(session, host, token, name, size, stream);
            }
            return name;
        });
    }

    private boolean newestFirst = true;
    private File storageRoot;
    private File storageFolder;
    private enum FilesView {
        BOTH("Both"), FILES("Files only"), FOLDERS("Folders only");

        final String label;
        FilesView(String label) { this.label = label; }

        boolean includes(File file) {
            return file.isDirectory() ? this != FILES : file.isFile() && this != FOLDERS;
        }
    }
    private FilesView filesView = FilesView.BOTH;

    private void browseStorage() {
        if (!TvSettings.hasStorageAccess(this)) {
            new AlertDialog.Builder(this).setTitle("Storage access").setMessage("Allow storage access to browse files on the TV.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Allow", (dialog, which) -> requestStorageAccess()).show();
            return;
        }
        java.util.List<File> roots = TvSettings.storageRoots(this);
        if (roots.isEmpty()) {
            notifyUser("No accessible storage found.");
            return;
        }
        String[] labels = new String[roots.size()];
        for (int i = 0; i < roots.size(); i++) {
            labels[i] = i == 0 ? "Internal storage" : "External storage · " + roots.get(i).getName();
        }
        new AlertDialog.Builder(this).setTitle("Browse storage").setItems(labels, (dialog, index) -> {
            storageRoot = roots.get(index);
            storageFolder = storageRoot;
            renderFiles();
        }).setNegativeButton("Cancel", null).show();
    }


    private void showFiles() {
        storageRoot = null;
        storageFolder = null;
        renderFiles();
    }

    private LinearLayout filesToolbar;

    private void renderFiles() {
        File folderToRender = storageFolder;
        File browseRoot = storageRoot;
        boolean browsing = browseRoot != null;
        File receiveRoot = received();
        if (!browsing && folderToRender != null && !TvSettings.isReceivedEntry(receiveRoot, folderToRender)) {
            folderToRender = null;
        }
        File folder = folderToRender != null ? folderToRender : receiveRoot;
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        addFilesToolbarButton(toolbar, "Received", this::showFiles);
        Button browse = addFilesToolbarButton(toolbar, "Browse storage", this::browseStorage);
        LinearLayout controls = toolbar;
        if (getResources().getConfiguration().screenWidthDp < 840) {
            controls = new LinearLayout(this);
            controls.setGravity(Gravity.CENTER_VERTICAL);
        }
        controls.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 1, 1));
        if (!browsing) addFilesToolbarButton(controls, "New folder", () -> createReceivedFolder(receiveRoot, folder));
        addFilesToolbarButton(controls, "Show: " + filesView.label, this::chooseFilesView);
        addFilesToolbarButton(controls, "Sort: " + (newestFirst ? "Newest" : "Name"), () -> {
            newestFirst = !newestFirst;
            renderFiles();
        });
        LinearLayout body = feature("My Files", toolbar);
        filesToolbar = toolbar;
        storageRoot = browseRoot;
        storageFolder = folderToRender;
        if (controls != toolbar) body.addView(controls, new LinearLayout.LayoutParams(-1, dp(44)));
        divider(body);
        if (browsing) {
            info(body, folder.getAbsolutePath());
        } else if (!folder.equals(receiveRoot)) {
            info(body, folder.getAbsolutePath().substring(receiveRoot.getAbsolutePath().length() + 1)
                    .replace(File.separator, " / "));
        }
        File[] files = folder.listFiles(file -> !file.getName().startsWith(".") && filesView.includes(file)
                && (browsing ? TvSettings.canBrowse(this, file) : TvSettings.isReceivedEntry(receiveRoot, file)));
        if (files == null || files.length == 0) {
            info(body, files == null && folder.exists() ? "Cannot read this folder."
                    : filesView == FilesView.BOTH ? "This folder is empty."
                    : filesView == FilesView.FILES ? "No files in this folder." : "No folders here.");
            browse.requestFocus();
            return;
        }
        java.util.Comparator<File> byName = java.util.Comparator.comparing(File::getName,
                String.CASE_INSENSITIVE_ORDER);
        java.util.Arrays.sort(files, newestFirst
                ? java.util.Comparator.comparingLong(File::lastModified).reversed().thenComparing(byName)
                : byName);
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::isFile));
        int firstFile = body.getChildCount();
        for (File file : files) addFileRow(body, file);
        body.getChildAt(firstFile).requestFocus();
    }

    private void chooseFilesView() {
        FilesView[] choices = FilesView.values();
        String[] labels = new String[choices.length];
        for (int index = 0; index < choices.length; index++) labels[index] = choices[index].label;
        new AlertDialog.Builder(this).setTitle("Show")
                .setSingleChoiceItems(labels, filesView.ordinal(), (dialog, which) -> {
                    filesView = choices[which];
                    dialog.dismiss();
                    renderFiles();
                }).setNegativeButton("Cancel", null).show();
    }

    private void createReceivedFolder(File root, File folder) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Folder name");
        AlertDialog editor = new AlertDialog.Builder(this).setTitle("New folder").setView(input)
                .setNegativeButton("Cancel", null).setPositiveButton("Create", null).create();
        showTextEditor(editor, input);
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                if (!root.equals(received()) || (!folder.equals(root) && !TvSettings.isReceivedEntry(root, folder))) {
                    throw new IOException("Folder is unavailable");
                }
                File target = TransferServer.file(folder, input.getText().toString().trim());
                if (!root.isDirectory() && !root.mkdirs() && !root.isDirectory()) {
                    throw new IOException("Cannot open the received folder");
                }
                if (target.exists()) throw new IOException("That name is already in use");
                if (!target.mkdir()) throw new IOException("Could not create folder");
                if (filesView == FilesView.FILES) filesView = FilesView.BOTH;
                editor.dismiss();
                renderFiles();
            } catch (IOException error) {
                input.setError(error.getMessage());
            }
        });
    }

    private Button addFilesToolbarButton(LinearLayout toolbar, String title, Runnable action) {
        Button item = button(title, action);
        item.setBackground(flatRowBackground());
        styleRowText(item);
        item.setPadding(dp(14), 0, dp(14), 0);
        toolbar.addView(item, new LinearLayout.LayoutParams(-2, dp(44)));
        return item;
    }

    private void addFileRow(LinearLayout body, File file) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setFocusable(true);
        row.setOnClickListener(view -> {
            if (file.isDirectory()) {
                storageFolder = file;
                renderFiles();
            } else {
                fileActions(file, view);
            }
        });
        row.setBackground(flatRowBackground());
        body.addView(row, new LinearLayout.LayoutParams(-1, dp(62)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(file.isDirectory() ? R.drawable.tv_ic_files : R.drawable.ic_tv_document);
        icon.setDuplicateParentStateEnabled(true);
        icon.setImageTintList(focusText(Color.WHITE));
        String filename = file.getName();
        if (filename.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
            String path = file.getAbsolutePath();
            sender.execute(() -> {
                try {
                    android.content.pm.PackageInfo archive = getPackageManager().getPackageArchiveInfo(path, 0);
                    if (archive != null && archive.applicationInfo != null) {
                        archive.applicationInfo.sourceDir = path;
                        archive.applicationInfo.publicSourceDir = path;
                        android.graphics.drawable.Drawable appIcon = archive.applicationInfo.loadIcon(getPackageManager());
                        runOnUiThread(() -> {
                            if (icon.isAttachedToWindow()) {
                                icon.setImageTintList(null);
                                icon.setImageDrawable(appIcon);
                            }
                        });
                    }
                } catch (RuntimeException error) {
                    android.util.Log.d("TVLink", "Could not load APK icon", error);
                }
            });
        }
        row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(16), 0, dp(12), 0);
        labels.setDuplicateParentStateEnabled(true);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView name = new TextView(this);
        name.setText(filename);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setDuplicateParentStateEnabled(true);
        styleRowText(name);
        labels.addView(name);
        TextView size = new TextView(this);
        size.setText(file.isDirectory() ? "Folder" : android.text.format.Formatter.formatShortFileSize(this, file.length()));
        size.setDuplicateParentStateEnabled(true);
        styleRowText(size);
        size.setTextSize(14);
        labels.addView(size);
        TextView date = new TextView(this);
        date.setText(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
                java.text.DateFormat.SHORT).format(new java.util.Date(file.lastModified())));
        date.setDuplicateParentStateEnabled(true);
        styleRowText(date);
        date.setTextSize(14);
        row.addView(date);
        size.setTextColor(focusText(0xffb8c3d6));
        date.setTextColor(focusText(0xffb8c3d6));
        divider(body);
    }

    private void fileActions(File file, android.view.View source) {
        String filename = file.getName();
        int dot = filename.lastIndexOf('.');
        String type = dot > 0 && dot + 1 < filename.length()
                ? filename.substring(dot + 1).toUpperCase(java.util.Locale.ROOT) : "File";
        boolean apk = "APK".equals(type);
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(12), dp(16), dp(12));
        panel.setBackground(tile(0xff1b2740, false));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.setOnDismissListener(closed -> {
            if (source.isAttachedToWindow()) source.requestFocus();
        });

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(heading);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_tv_document);
        icon.setColorFilter(Color.WHITE);
        heading.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, 0, 0);
        heading.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = addTransferHeading(labels, filename);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info(labels, type + " · " + android.text.format.Formatter.formatShortFileSize(this, file.length()));
        divider(panel);

        Button open = addFileAction(panel, dialog, apk ? "Install APK" : "Open", R.drawable.ic_file_open,
                Color.WHITE, () -> openFile(file, apk));
        addFileAction(panel, dialog, "Rename", R.drawable.ic_file_rename, Color.WHITE, () -> renameFile(file));
        addFileAction(panel, dialog, "Send to phone", R.drawable.ic_phone, Color.WHITE, () -> sendFile(file));
        addFileAction(panel, dialog, "Move to", R.drawable.tv_ic_files, Color.WHITE, () -> chooseMoveStorage(file));
        addFileAction(panel, dialog, "Delete", R.drawable.ic_file_delete, 0xffff8585,
                () -> new AlertDialog.Builder(this).setTitle("Delete file?")
                        .setMessage(filename).setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (confirm, which) -> {
                            synchronized (file.getParentFile().getAbsolutePath().intern()) {
                                if (file.delete()) TvSettings.forgetReceivedFile(getPreferences(MODE_PRIVATE), file);
                                else notifyUser("Could not delete file");
                            }
                            renderFiles();
                        }).show());

        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.55f);
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            window.setLayout(Math.min(dp(560), metrics.widthPixels - dp(48)),
                    Math.min(dp(382), metrics.heightPixels - dp(40)));
        }
        open.requestFocus();
    }

    private Button addFileAction(LinearLayout panel, android.app.Dialog dialog, String title,
                                 int icon, int color, Runnable action) {
        Button row = button(title, () -> {
            dialog.dismiss();
            action.run();
        });
        row.setBackground(flatRowBackground());
        row.setTextColor(focusText(color));
        row.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        row.setCompoundDrawableTintList(focusText(color));
        row.setCompoundDrawablePadding(dp(14));
        panel.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
        return row;
    }

    private void chooseMoveStorage(File file) {
        java.util.List<File> roots = new java.util.ArrayList<>();
        if (TvSettings.hasStorageAccess(this)) roots.addAll(TvSettings.storageRoots(this));
        File appFolder = new File(getFilesDir(), "received");
        File receiveRoot = received();
        boolean separateReceived = !receiveRoot.equals(appFolder);
        String[] labels = new String[roots.size() + (separateReceived ? 2 : 1)];
        for (int i = 0; i < roots.size(); i++) {
            labels[i] = i == 0 ? "Internal storage" : "External storage · " + roots.get(i).getName();
        }
        int appFolderIndex = roots.size();
        labels[appFolderIndex] = separateReceived ? "TVLink app folder" : "Received / TVLink app folder";
        int receivedIndex = appFolderIndex + 1;
        if (separateReceived) labels[receivedIndex] = "Received / " + TvSettings.directoryLabel(this);
        new AlertDialog.Builder(this).setTitle("Move to").setItems(labels, (dialog, index) -> {
            File root = index == receivedIndex ? receiveRoot : index == appFolderIndex ? appFolder : roots.get(index);
            chooseMoveFolder(file, root, root, root.equals(appFolder));
        }).setNegativeButton("Cancel", null).show();
    }

    private void chooseMoveFolder(File file, File root, File folder, boolean appFolder) {
        File[] children = folder.listFiles(entry -> entry.isDirectory() && !entry.getName().startsWith(".")
                && (appFolder ? TvSettings.isReceivedEntry(root, entry) : TvSettings.canBrowse(this, entry)));
        if (children == null) children = new File[0];
        java.util.Arrays.sort(children, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        File[] folders = children;
        boolean atRoot = folder.equals(root);
        int firstFolder = atRoot ? 0 : 1;
        String[] labels = new String[folders.length + firstFolder];
        if (!atRoot) labels[0] = "↑ Parent folder";
        for (int i = 0; i < folders.length; i++) labels[i + firstFolder] = folders[i].getName();
        AlertDialog.Builder picker = new AlertDialog.Builder(this)
                .setTitle(appFolder && folder.equals(root) ? "TVLink app folder" : folder.getName())
                .setNegativeButton("Back", (dialog, which) -> {
                    if (atRoot) chooseMoveStorage(file);
                    else chooseMoveFolder(file, root, folder.getParentFile(), appFolder);
                });
        if (labels.length > 0) picker.setItems(labels, (dialog, index) -> {
            File next = index < firstFolder ? folder.getParentFile() : folders[index - firstFolder];
            chooseMoveFolder(file, root, next, appFolder);
        });
        AlertDialog dialog = picker.setPositiveButton("Move here", (closed, which) ->
                moveFile(file, root, folder, appFolder)).create();
        dialog.setOnCancelListener(closed -> {
            if (atRoot) chooseMoveStorage(file);
            else chooseMoveFolder(file, root, folder.getParentFile(), appFolder);
        });
        dialog.show();
        if (folder.equals(file.getParentFile()) || (!appFolder && !folder.canWrite())) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        }
    }

    private void moveFile(File source, File root, File folder, boolean appFolder) {
        LinearLayout view = filesToolbar;
        File receiveDirectory = received();
        sender.execute(() -> {
            String message;
            try {
                if (appFolder) {
                    if (!folder.equals(root) && !TvSettings.isReceivedEntry(root, folder)) {
                        throw new IOException("Folder is unavailable");
                    }
                    if (!folder.isDirectory() && !folder.mkdir() && !folder.isDirectory()) {
                        throw new IOException("Cannot create TVLink app folder");
                    }
                } else if (!TvSettings.hasStorageAccess(this)
                        || !(folder.equals(root) && TvSettings.storageRoots(this).contains(root)
                                || TvSettings.canBrowse(this, folder))) {
                    throw new IOException("Storage access is unavailable");
                }
                File target = TransferServer.file(folder, source.getName());
                if (source.getCanonicalFile().equals(target.getCanonicalFile())) {
                    throw new IOException("File is already in this folder");
                }
                android.content.SharedPreferences settings = getPreferences(MODE_PRIVATE);
                String sourceLock = source.getParentFile().getAbsolutePath().intern();
                String targetLock = folder.getAbsolutePath().intern();
                boolean sourceFirst = sourceLock.compareTo(targetLock) <= 0;
                synchronized (sourceFirst ? sourceLock : targetLock) {
                    synchronized (sourceFirst ? targetLock : sourceLock) {
                        boolean receivedFile = (TvSettings.isDownloadsFile(source)
                                || source.getParentFile().getCanonicalFile().equals(receiveDirectory.getCanonicalFile()))
                                && TvSettings.isReceivedFile(settings, source);
                        FileMove.move(source, target);
                        TvSettings.forgetReceivedFile(settings, source);
                        if (receivedFile) TvSettings.rememberReceivedFile(settings, target);
                    }
                }
                message = "Moved to " + (appFolder && folder.equals(root) ? "TVLink app folder" : folder.getName());
            } catch (IOException | SecurityException error) {
                message = error.getMessage() == null ? "Could not move file" : error.getMessage();
            }
            String result = message;
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                if (view.isAttachedToWindow()) renderFiles();
                notifyUser(result);
            });
        });
    }

    private void renameFile(File file) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(file.getName());
        AlertDialog editor = new AlertDialog.Builder(this).setTitle("Rename").setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, choice) -> {
                    try {
                        File folder = file.getParentFile();
                        File target = TransferServer.file(folder, input.getText().toString().trim());
                        if (!target.equals(file)) {
                            synchronized (folder.getAbsolutePath().intern()) {
                                android.content.SharedPreferences settings = getPreferences(MODE_PRIVATE);
                                boolean receivedDownload = TvSettings.isDownloadsFile(file)
                                        && TvSettings.isReceivedFile(settings, file);
                                if (target.exists() || !file.renameTo(target)) throw new IOException("Cannot rename file");
                                if (receivedDownload) {
                                    TvSettings.forgetReceivedFile(settings, file);
                                    TvSettings.rememberReceivedFile(settings, target);
                                }
                            }
                        }
                    } catch (IOException error) { notifyUser(error.getMessage()); }
                    renderFiles();
                }).create();
        showTextEditor(editor, input);
    }

    private void openFile(File file, boolean apk) {
        try {
            if (apk && android.os.Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                notifyUser("Allow TVLink to install unknown apps, then select the APK again.");
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:" + getPackageName())));
                return;
            }
            android.net.Uri.Builder builder = new android.net.Uri.Builder().scheme("content")
                    .authority(getPackageName() + ".files");
            android.net.Uri uri;
            if (storageRoot != null) {
                uri = builder.appendPath("storage").appendPath(file.getAbsolutePath()).build();
            } else if (storageFolder != null) {
                uri = builder.appendPath("received").appendPath(file.getAbsolutePath()).build();
            } else {
                uri = builder.appendPath(file.getName()).build();
            }
            String type = apk ? "application/vnd.android.package-archive" : getContentResolver().getType(uri);
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (android.content.ActivityNotFoundException | SecurityException error) {
            notifyUser("No compatible app or installer available");
        }
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this).setTitle("Clear transfer history?")
                .setMessage("Your files will be kept.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> TransferHistory.clear(getPreferences(MODE_PRIVATE))).show();
    }

    private TextView receivedNotice;

    private void showReceivedNotice(String filename) {
        if (!started || isFinishing() || isDestroyed()) return;
        android.widget.FrameLayout content = findViewById(android.R.id.content);
        if (receivedNotice != null) content.removeView(receivedNotice);
        TextView banner = new TextView(this);
        banner.setText("File received\n" + filename);
        banner.setTextColor(Color.WHITE);
        banner.setTextSize(16);
        banner.setMaxLines(3);
        banner.setEllipsize(android.text.TextUtils.TruncateAt.END);
        banner.setPadding(dp(16), dp(10), dp(16), dp(10));
        banner.setBackground(tile(0xff1b2740, false));
        banner.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        android.widget.FrameLayout.LayoutParams size = new android.widget.FrameLayout.LayoutParams(
                -2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        size.setMargins(dp(24), 0, dp(24), dp(24));
        content.addView(banner, size);
        receivedNotice = banner;
        banner.postDelayed(() -> {
            if (receivedNotice != banner) return;
            content.removeView(banner);
            receivedNotice = null;
        }, 4500);
    }

    private void notifyUser(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void stopPairingDisplay() {
        dismissFileApproval();
        if (pairing != null && receiver != null) receiver.enableLinking(false);
        pairing = null;
        refresh.removeCallbacks(updatePairing);
        refresh.removeCallbacks(expireLink);
    }

    @Override protected void onStop() {
        exitDeadline = 0;
        if (exitMessage != null) {
            exitMessage.cancel();
            exitMessage = null;
        }
        started = false;
        if (transferRefresh != null) refresh.removeCallbacks(transferRefresh);
        // Android TV's pairing dialog can briefly cover this activity.
        if (pairing == null) closeReceiver();
        if (receivedNotice != null) {
            ((android.widget.FrameLayout) findViewById(android.R.id.content)).removeView(receivedNotice);
            receivedNotice = null;
        }
        super.onStop();
    }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        if (isLinked()) {
            startReceiver();
            recoverDeviceName();
        }
        if (transferRefresh != null) refresh.post(transferRefresh);
    }

    @Override protected void onResume() {
        super.onResume();
        if (checkingStorageAccess) {
            checkingStorageAccess = false;
            refreshStorageAccess();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 81) refreshStorageAccess();
    }

    private void refreshStorageAccess() {
        if (isLinked()) {
            closeReceiver();
            startReceiver();
        }
        boolean showSettingsAgain = returnToStorageSettings;
        returnToStorageSettings = false;
        if (showSettingsAgain) showSettings();
    }

    private void recoverDeviceName() {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String host = prefs.getString("peer_host", "");
        String token = prefs.getString("link_token", "");
        if (host.isEmpty() || token.isEmpty() || prefs.contains("peer_name")) return;
        String localName = TvSettings.device(this);
        sender.execute(() -> {
            try (TransferClient client = new TransferClient(host, "DEVICE", token)) {
                String name = client.exchangeDeviceName(localName);
                runOnUiThread(() -> {
                    if (!name.isEmpty() && token.equals(prefs.getString("link_token", ""))) {
                        prefs.edit().putString("peer_name", name).apply();
                    }
                });
            } catch (IOException error) {
                android.util.Log.d("TVLink", "Could not recover paired phone name", error);
            }
        });
    }

    @Override protected void onDestroy() {
        getPreferences(MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(deviceListener);
        if (activeSend != null) {
            activeSend.cancel();
            activeSend.dialog.dismiss();
        }
        closeReceiver();
        sender.shutdownNow();
        super.onDestroy();
    }

    private long exitDeadline;
    private Toast exitMessage;

    private void confirmBackExit() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (exitDeadline > now) {
            finish();
            return;
        }
        exitDeadline = now + 2000;
        if (exitMessage != null) exitMessage.cancel();
        exitMessage = Toast.makeText(this, "Press again to exit", Toast.LENGTH_SHORT);
        exitMessage.show();
    }

    @Override public void onBackPressed() {
        if (showingAbout) showSettings();
        else if (showingHome) confirmBackExit();
        else if (storageFolder != null) {
            File parent = storageFolder.getParentFile();
            if (storageFolder.equals(storageRoot) || (storageRoot == null && received().equals(parent))) showFiles();
            else {
                storageFolder = storageFolder.getParentFile();
                renderFiles();
            }
        } else home();
    }
}
