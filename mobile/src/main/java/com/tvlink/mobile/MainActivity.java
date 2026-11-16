package com.tvlink.mobile;

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
    private static final boolean TV = false;

    private static final int FOCUS_BLUE = 0xff7fb1fe;

    private boolean showingHome;
    private boolean showingAbout;
    private Button homePair;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener deviceListener =
            (prefs, key) -> {
                if (("link_token".equals(key) || "remote_host".equals(key) || "receive".equals(key))
                        && this.started) {
                    startReceiver();
                }
                if ("peer_name".equals(key) || "remote_name".equals(key)) {
                    if (showingHome && homePair != null && hasRemotePairing()) {
                        homePair.setText(linkedTvName());
                    }
                }
            };

    @Override public void onCreate(Bundle state) {
        setTheme(R.style.TVLinkTheme);
        super.onCreate(state);
        if (state != null) exportName = state.getString("export_name");
        getPreferences(MODE_PRIVATE).registerOnSharedPreferenceChangeListener(deviceListener);
        home();
        if (state == null) handleSharedFile(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleSharedFile(intent);
    }

    private void handleSharedFile(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        android.net.Uri uri;
        try {
            Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream instanceof android.net.Uri) uri = (android.net.Uri) stream;
            else {
                android.content.ClipData clip = intent.getClipData();
                uri = clip != null && clip.getItemCount() == 1 ? clip.getItemAt(0).getUri() : null;
            }
        } catch (RuntimeException error) {
            notifyUser("Could not read the shared file.");
            return;
        }
        if (uri == null || !"content".equals(uri.getScheme())
                || checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notifyUser("Share a file from your file manager.");
            return;
        }
        if (activeSend != null || remotePairing) {
            notifyUser("Finish the current transfer or pairing first.");
            return;
        }
        Runnable confirm = () -> {
            if (isFinishing() || isDestroyed()) return;
            new AlertDialog.Builder(this).setTitle("Send shared file?")
                    .setMessage("Send the selected file to " + linkedTvName() + ".")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Send", (dialog, which) -> sendFileUri(uri)).show();
        };
        if (hasFilePairing()) confirm.run();
        else pairSystemRemote(confirm);
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private LinearLayout screen(String title) {
        showingAbout = false;
        exitDeadline = 0;
        if (exitMessage != null) {
            exitMessage.cancel();
            exitMessage = null;
        }
        if (transferRefresh != null) {
            receiverHandler.removeCallbacks(transferRefresh);
            transferRefresh = null;
        }
        if (remoteStatus != null) {
            suspendRemoteSession();
            remoteStatus = null;
        }
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(24), dp(24), dp(24));
        page.setBackgroundColor(0xff101624);
        showingTransfers = false;
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
        shape.setStroke(dp(focused ? 3 : 1), focused ? FOCUS_BLUE : CONTENT_DIVIDER);
        return shape;
    }

    private StateListDrawable flatRowBackground() {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_pressed}, tile(0xff263a5a, true));
        background.addState(new int[] {android.R.attr.state_focused}, tile(0xff263a5a, true));
        background.addState(new int[] {}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        return background;
    }

    private Button button(String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TV ? 20 : 16);
        button.setGravity(Gravity.CENTER);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_pressed}, tile(0xff263a5a, true));
        background.addState(new int[] {android.R.attr.state_focused}, tile(0xff263a5a, true));
        background.addState(new int[] {}, tile(0xff1b2740, false));
        button.setBackground(background);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            return "";
        }
    }

    private void showSettings() {
        showingHome = false;
        LinearLayout page = screen(null);
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(toolbar);
        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextSize(23);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setTextColor(Color.WHITE);
        title.setMinHeight(dp(44));
        toolbar.addView(title, new LinearLayout.LayoutParams(-1, -2));
        divider(page);
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        setting(rows, "About", "Version " + appVersion(), this::showAbout);
        setting(rows, "Device Name", LocalSettings.device(this), () -> editSetting("Device Name", "device_name", LocalSettings.device(this)));
        setting(rows, "Save received files to", transferFolderLabel(), this::chooseDirectory);
        setting(rows, "Direct Downloads access", LocalSettings.hasStorageAccess(this) ? "Allowed" : "Optional", this::requestStorageAccess);
        toggle(rows, "Receive Transfers", "Accept incoming files", "receive", true);
        toggle(rows, "Launch at startup", "Open TVLink at boot when supported", "startup", false);
        toggle(rows, "Overwrite", "Replace files with the same name", "overwrite", false);
        toggle(rows, "Modify date", "Update the modified date when replacing files", "modify_date", true);
    }

    private String transferFolderLabel() {
        return LocalSettings.hasStorageAccess(this) && getPreferences(MODE_PRIVATE).getBoolean("downloads", false)
                ? "Downloads / TVLink" : "TVLink app folder";
    }

    private void showAbout() {
        showingHome = false;
        LinearLayout page = screen(null);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView heading = new TextView(this);
        heading.setText("About");
        heading.setTextSize(23);
        heading.setTextColor(Color.WHITE);
        header.addView(heading);
        divider(page);
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        showingAbout = true;

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(0, dp(16), 0, dp(16));
        int width = Math.min(dp(430), getResources().getDisplayMetrics().widthPixels - dp(48));
        LinearLayout.LayoutParams contentSize = new LinearLayout.LayoutParams(width, -2);
        contentSize.gravity = Gravity.CENTER_HORIZONTAL;
        body.addView(content, contentSize);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.tvlink_wordmark);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("TVLink");
        content.addView(logo, new LinearLayout.LayoutParams(dp(160), dp(52)));

        TextView identity = new TextView(this);
        identity.setGravity(Gravity.CENTER);
        identity.setText("Version " + appVersion() + "\nRemote control, phone keyboard input and file transfer for Android TV.");
        identity.setTextColor(0xffc0cce4);
        identity.setTextSize(14);
        identity.setLineSpacing(dp(4), 1);
        identity.setPadding(0, dp(4), 0, dp(16));
        content.addView(identity, new LinearLayout.LayoutParams(-1, -2));

        addAboutAction(content, "Check for updates", () -> ReleaseCheck.check(this));
        addAboutAction(content, "Support", this::showAboutSupport);
        divider(content);

        TextView credit = new TextView(this);
        credit.setText("Developed by NudroidLabs\nPublished by KebunApp");
        credit.setTextColor(0xffc0cce4);
        credit.setTextSize(13);
        credit.setLineSpacing(dp(4), 1);
        credit.setPadding(dp(12), dp(12), dp(12), 0);
        content.addView(credit, new LinearLayout.LayoutParams(-1, -2));
    }

    private Button addAboutAction(LinearLayout content, String label, Runnable action) {
        divider(content);
        Button row = button(label, action);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setBackground(flatRowBackground());
        content.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
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
        android.widget.CompoundButton control = new android.widget.Switch(this);
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
        row.setPaddingRelative(0, dp(8), dp(12), dp(8));
        row.setMinimumHeight(dp(56));
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
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(16);
        heading.setTextColor(Color.WHITE);
        labels.addView(heading);
        TextView description = new TextView(this);
        description.setText(detail);
        description.setTextSize(13);
        description.setTextColor(0xffb8c3d6);
        LinearLayout.LayoutParams descriptionSize = new LinearLayout.LayoutParams(-1, -2);
        descriptionSize.topMargin = dp(2);
        labels.addView(description, descriptionSize);
        if (control != null) {
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, -2);
            size.setMarginStart(dp(12));
            row.addView(control, size);
        }

        android.view.View line = new android.view.View(this);
        line.setBackgroundColor(CONTENT_DIVIDER);
        rows.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void showTextEditor(AlertDialog dialog, EditText input) {
        android.view.Window window = dialog.getWindow();
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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
        input.post(keyboard);
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
        new AlertDialog.Builder(this).setTitle("Save received files to")
                .setSingleChoiceItems(new String[] {
                        "TVLink app folder\n" + new File(getFilesDir(), "received").getAbsolutePath(),
                        "Downloads / TVLink\n" + new File(android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS), "TVLink").getAbsolutePath()
                }, transferFolderLabel().equals("TVLink app folder") ? 0 : 1, (dialog, index) -> {
                    dialog.dismiss();
                    if (index == 1 && !LocalSettings.hasStorageAccess(this)) {
                        notifyUser("Allow storage access first, then choose Downloads.");
                        return;
                    }
                    closeReceiver();
                    getPreferences(MODE_PRIVATE).edit().putBoolean("downloads", index == 1).apply();
                    startReceiver();
                    showSettings();
                }).setNegativeButton("Cancel", null).show();
    }

    private void requestStorageAccess() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (android.content.ActivityNotFoundException error) {
                notifyUser("This phone does not expose the all-files access screen.");
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

    private TransferServer receiver;
    private boolean started;

    private java.io.File received() {
        return LocalSettings.directory(this);
    }

    private void closeReceiver() {
        if (receiver != null) receiver.close();
        receiver = null;
    }

    private final android.os.Handler receiverHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable retryReceiver = this::startReceiver;
    private final TransferHistory transferHistory = new TransferHistory();
    private boolean showingTransfers;
    private TextView homeReceiveStatus;
    private String receiverError;

    private void startReceiver() {
        receiverHandler.removeCallbacks(retryReceiver);
        if (!started) return;
        if (!hasFilePairing() || !getPreferences(MODE_PRIVATE).getBoolean("receive", true)) {
            closeReceiver();
            receiverError = null;
        } else if (receiver == null) {
            try {
                receiver = new TransferServer(received(), getNoBackupFilesDir(), getPreferences(MODE_PRIVATE),
                        transferHistory, filename -> runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            if (started && !showingTransfers) showTransfers();
                            showReceivedNotice(filename);
                        }));
                receiverError = null;
            } catch (IOException | SecurityException error) {
                receiverError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                android.util.Log.w("TVLink", "Cannot start file receiver", error);
                receiverHandler.postDelayed(retryReceiver, 3000);
            }
        }
        if (showingHome) updateHomeReceiverError();
    }

    private void updateHomeReceiverError() {
        if (homeReceiveStatus == null) return;
        homeReceiveStatus.setVisibility(receiverError == null ? android.view.View.GONE : android.view.View.VISIBLE);
        homeReceiveStatus.setText(receiverError == null ? "" : receiveStatus());
    }

    private String receiveStatus() {
        if (!getPreferences(MODE_PRIVATE).getBoolean("receive", true)) return "Receive Transfers is off";
        if (!hasFilePairing()) return "Pair TV to receive files";
        if (receiver != null) return "Ready to receive";
        return receiverError == null ? "Starting receiver…" : "Could not start receiver: " + receiverError;
    }

    private void showTransfers() {
        LinearLayout body = feature("Send / Receive");
        showingTransfers = true;
        addTransferHeading(body, "Send");
        Button send = hasFilePairing() ? button("Send file", this::chooseFileToSend)
                : button("Pair TV", () -> pairSystemRemote(this::showTransfers));
        if (hasFilePairing()) {
            StateListDrawable background = new StateListDrawable();
            background.addState(new int[] {android.R.attr.state_pressed}, tile(FOCUS_BLUE, true));
            background.addState(new int[] {android.R.attr.state_focused}, tile(FOCUS_BLUE, true));
            background.addState(new int[] {}, tile(FOCUS_BLUE, false));
            send.setBackground(background);
            send.setTextColor(0xff121725);
        }
        send.setPadding(dp(16), 0, dp(16), 0);
        body.addView(send, new LinearLayout.LayoutParams(-2, dp(48)));
        divider(body);
        addTransferHeading(body, "Receive");
        if (getPreferences(MODE_PRIVATE).getBoolean("receive", true) && hasFilePairing()) {
            startReceiver();
        }
        addTransferDetails(body, body);
    }

    private void home() {
        showingHome = true;
        LinearLayout page = screen(null);
        addHeader(page);
        homeReceiveStatus = new TextView(this);
        updateHomeReceiverError();
        homeReceiveStatus.setTextColor(0xffb8c3d6);
        homeReceiveStatus.setTextSize(13);
        homeReceiveStatus.setGravity(Gravity.CENTER);
        page.addView(homeReceiveStatus);

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setGravity(Gravity.CENTER);
        ScrollView cards = new ScrollView(this);
        cards.setFillViewport(true);
        page.addView(cards, new LinearLayout.LayoutParams(-1, 0, 1));
        cards.addView(menu);
        addHomeCard(menu, "Send / Receive", R.drawable.ic_send, this::showTransfers);
        addHomeCard(menu, "Remote", R.drawable.ic_remote, this::openRemote);
        addHomeCard(menu, "Files", R.drawable.ic_files, this::showFiles);
    }

    private void addHeader(LinearLayout page) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(52));
        page.addView(header, new LinearLayout.LayoutParams(-1, -2));
        ImageView wordmark = new ImageView(this);
        wordmark.setImageResource(R.drawable.tvlink_wordmark);
        wordmark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        wordmark.setContentDescription("TVLink");
        boolean linked = hasRemotePairing();
        int screenWidth = getResources().getConfiguration().screenWidthDp;
        int logoWidth = Math.max(64, Math.min(116, screenWidth / 3));
        header.addView(wordmark, new LinearLayout.LayoutParams(dp(logoWidth), dp(52)));
        header.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 1, 1));

        Button pair = button(linked ? linkedTvName() : "Pair", this::showDevice);
        homePair = pair;
        pair.setGravity(Gravity.CENTER);
        pair.setMinHeight(dp(48));
        pair.setMinWidth(0);
        pair.setMaxWidth(dp(Math.max(48, screenWidth - logoWidth - 112)));
        pair.setPadding(dp(10), dp(6), dp(10), dp(6));
        if (linked) {
            pair.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            pair.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_tv, 0, 0, 0);
            pair.setCompoundDrawablePadding(dp(6));
        }
        LinearLayout.LayoutParams pairSize = new LinearLayout.LayoutParams(-2, -2);
        pairSize.setMarginStart(dp(8));
        header.addView(pair, pairSize);
        Button settings = button("", this::showSettings);
        settings.setContentDescription("Settings");
        settings.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_settings, 0, 0, 0);
        settings.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams settingsSize = new LinearLayout.LayoutParams(dp(48), dp(48));
        settingsSize.setMarginStart(dp(8));
        header.addView(settings, settingsSize);

        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(CONTENT_DIVIDER);
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(1));
        line.setMargins(0, dp(14), 0, dp(18));
        page.addView(divider, line);

    }

    private void addHomeCard(LinearLayout menu, String title, int iconResource, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setMinimumHeight(dp(144));
        card.setFocusable(true);
        card.setOnClickListener(view -> action.run());
        card.setContentDescription(title);
        card.setAccessibilityDelegate(new android.view.View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(android.view.View host,
                    android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
            }
        });
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_pressed}, tile(FOCUS_BLUE, true));
        background.addState(new int[] {android.R.attr.state_focused}, tile(FOCUS_BLUE, true));
        background.addState(new int[] {}, tile(FOCUS_BLUE, false));
        card.setBackground(background);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setColorFilter(REMOTE_NAVY);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(REMOTE_NAVY);
        label.setTextSize(16);
        label.setGravity(Gravity.CENTER);
        label.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams labelSize = new LinearLayout.LayoutParams(-1, -2);
        labelSize.topMargin = dp(10);
        card.addView(label, labelSize);

        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.setMarginStart(dp(20));
        size.setMarginEnd(dp(20));
        if (menu.getChildCount() > 0) size.topMargin = dp(24);
        menu.addView(card, size);
    }

    private final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean busy;

    private LinearLayout feature(String title) {
        showingHome = false;
        LinearLayout page = screen("Send / Receive".equals(title) ? null : title);
        if ("Send / Receive".equals(title)) {
            addHeader(page);
            TextView heading = addTransferHeading(page, title);
            heading.setTextSize(24);
        }
        if (!"Send / Receive".equals(title) && !"My Files".equals(title) && !"Device".equals(title)) {
            page.addView(button("Back", this::home));
        }
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        return body;
    }

    private TextView info(LinearLayout body, String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setPadding(0, dp(12), 0, dp(12));
        body.addView(view);
        return view;
    }

    private interface Work { void run() throws IOException; }

    private void work(Work action) {
        work(action, null);
    }

    private void work(Work action, Runnable success) {
        if (busy || isDestroyed()) return;
        busy = true;
        io.execute(() -> {
            boolean succeeded = false;
            try {
                action.run();
                succeeded = true;
                if (success != null) {
                    runOnUiThread(() -> {
                        busy = false;
                        if (!isDestroyed()) success.run();
                    });
                    return;
                }
            }
            catch (Exception error) {
                runOnUiThread(() -> notifyUser(error.getMessage() == null ? "Request failed" : error.getMessage()));
            } finally {
                if (!succeeded || success == null) runOnUiThread(() -> busy = false);
            }
        });
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this).setTitle("Clear transfer history?")
                .setMessage("Your files will be kept.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> transferHistory.clear()).show();
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
        if (!isFinishing() && !isDestroyed()) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private boolean hasFilePairing() {
        android.content.SharedPreferences settings = getPreferences(MODE_PRIVATE);
        return !settings.getString("remote_host", "").isEmpty() && !settings.getString("link_token", "").isEmpty();
    }

    private boolean hasRemotePairing() {
        android.content.SharedPreferences settings = getPreferences(MODE_PRIVATE);
        return !settings.getString("remote_host", "").isEmpty() && !settings.getString("remote_pin", "").isEmpty();
    }

    private void showDevice() {
        LinearLayout body = feature("Device");
        if (!hasRemotePairing()) {
            addDevicePairingIntro(body);
            return;
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(tile(0xff1b2740, false));
        body.addView(card, new LinearLayout.LayoutParams(-1, -2));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_tv);
        card.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView name = info(card, linkedTvName());
        name.setTextColor(Color.WHITE);
        name.setTextSize(20);
        name.setGravity(Gravity.CENTER);

        boolean linkedFiles = hasFilePairing();
        TextView status = info(card, linkedFiles ? "Remote and file transfers paired."
                : "Remote paired · File transfer not connected");
        status.setGravity(Gravity.CENTER);
        Button unpair = button("Unpair", this::confirmUnpair);
        unpair.setTextColor(0xffff9baa);
        StateListDrawable unpairBackground = new StateListDrawable();
        unpairBackground.addState(new int[] {android.R.attr.state_pressed}, tile(0xff263a5a, true));
        unpairBackground.addState(new int[] {android.R.attr.state_focused}, tile(0xff263a5a, true));
        GradientDrawable unpairOutline = tile(0xff1b2740, false);
        unpairBackground.addState(new int[] {}, unpairOutline);
        unpair.setBackground(unpairBackground);
        card.addView(unpair, new LinearLayout.LayoutParams(-1, -2));

        Button change = button(linkedFiles ? "Change TV" : "Connect file transfer",
                () -> pairSystemRemote(this::showDevice));
        LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(-1, -2);
        action.topMargin = dp(12);
        body.addView(change, action);
    }

    private String linkedTvName() {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        return prefs.getString("peer_name", prefs.getString("remote_name", "Linked TV"));
    }

    private void addDevicePairingIntro(LinearLayout body) {
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(0, dp(48), 0, dp(24));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_pair_devices);
        body.addView(icon, new LinearLayout.LayoutParams(dp(136), dp(68)));

        TextView heading = new TextView(this);
        heading.setText("Pair with your TV");
        heading.setTextSize(24);
        heading.setTextColor(Color.WHITE);
        heading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleSize = new LinearLayout.LayoutParams(-1, -2);
        titleSize.topMargin = dp(16);
        body.addView(heading, titleSize);

        TextView detail = info(body, "Open TVLink on your TV.\nConnect both devices to the same network.");
        detail.setTextSize(16);
        detail.setGravity(Gravity.CENTER);
        detail.setTextColor(0xffb8c3d6);

        LinearLayout actions = new LinearLayout(this);
        LinearLayout.LayoutParams actionsSize = new LinearLayout.LayoutParams(-1, -2);
        actionsSize.topMargin = dp(16);
        body.addView(actions, actionsSize);
        actions.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 1, 1));
        Button find = button("Find TV", () -> {
            if (!busy) findPairingTv(this::showDevice);
        });
        actions.addView(find, new LinearLayout.LayoutParams(0, dp(48), 5));
        actions.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 1, 1));
    }

    private void confirmUnpair() {
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.TVLinkPairingDialog).setTitle("Unpair " + linkedTvName() + "?")
                .setMessage("This removes the saved Remote and file link from this phone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Unpair", (d, w) -> unpairTv()).show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xffff7186);
    }

    private void unpairTv() {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String host = prefs.getString("remote_host", "");
        String token = prefs.getString("link_token", "");
        if (activeSend != null) activeSend.cancel();
        closeReceiver();
        prefs.edit().remove("remote_host").remove("remote_pin").remove("remote_legacy_identity")
                .remove("remote_name").remove("peer_name").remove("link_token").remove("pending_host")
                .remove("pending_pin").remove("pending_legacy_identity").remove("pending_token")
                .remove("pending_file_host").remove("pending_file_token").apply();
        home();
        remoteIo.execute(this::closeSystemRemote);
        if (!token.isEmpty()) io.execute(() -> {
            try (TransferClient client = new TransferClient(host, "UNPAIR", token)) {
                client.confirm();
            } catch (IOException error) {
                runOnUiThread(() -> notifyUser("Unpaired here. TV was unavailable."));
            }
        });
    }

    private void openRemote() {
        if (hasRemotePairing()) showRemote();
        else pairSystemRemote(this::showRemote);
    }

    private void chooseFileToSend() {
        if (busy || activeSend != null) return;
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*");
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(picker, 10);
        } catch (android.content.ActivityNotFoundException error) {
            notifyUser("No file picker is available. Choose a file from Received Files.");
        }
    }

    private TransferClient client(String operation) throws IOException {
        android.content.SharedPreferences settings = getPreferences(MODE_PRIVATE);
        String host = settings.getString("remote_host", "");
        String token = settings.getString("link_token", "");
        if (token.isEmpty()) throw new IOException("Pair with the TV first");
        return new TransferClient(LinkNetwork.resolve(host, token), operation, token);
    }

    private static final int EXPORT_FILE = 12;
    private String exportName;

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("export_name", exportName);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == EXPORT_FILE) {
            String name = exportName;
            exportName = null;
            if (result == RESULT_OK && name != null && data != null && data.getData() != null) {
                exportCopy(name, data.getData());
            }
            return;
        }
        if (request != 10 || result != RESULT_OK || data == null || data.getData() == null) return;
        sendFileUri(data.getData());
    }

    private void sendFileUri(android.net.Uri uri) {
        SendSession session = beginSend();
        if (session == null) return;
        io.execute(() -> {
            try (java.net.Socket socket = session.socket) {
                sendSelectedFile(session, uri);
            } catch (IOException | SecurityException error) {
                finishSend(session, session.cancelled ? "Transfer cancelled" : "Could not send: " + error.getMessage());
            }
        });
    }

    private void sendSelectedFile(SendSession session, android.net.Uri uri) throws IOException {
        String name;
        long size;
        try (android.database.Cursor cursor = getContentResolver().query(uri,
                new String[] {android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE},
                null, null, null)) {
            if (cursor == null || !cursor.moveToFirst() || cursor.isNull(0) || cursor.isNull(1)) {
                throw new IOException("File name or size unavailable. Choose a locally stored file.");
            }
            name = cursor.getString(0);
            size = cursor.getLong(1);
        }
        android.content.SharedPreferences settings = getPreferences(MODE_PRIVATE);
        String token = settings.getString("link_token", "");
        if (token.isEmpty()) throw new IOException("Connect file transfer first");
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            sendToPeer(session, settings.getString("remote_host", ""), token, name, size, stream);
        }
        finishSend(session, "Sent: " + name);
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
                boolean paired = hasFilePairing();
                String incoming = receiver == null ? "" : receiver.receiveProgress;
                String message;
                if (!incoming.isEmpty()) message = incoming;
                else if (!enabled) message = "Receive Transfers is disabled in Settings.";
                else if (!paired) message = "Pair to receive files.";
                else if (receiver == null) message = receiveStatus();
                else message = "●  Ready to receive from " + linkedTvName() + ".";
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

                org.json.JSONArray entries = transferHistory.read();
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
                receiverHandler.postDelayed(this, 500);
            }
        };
        if (started) receiverHandler.post(transferRefresh);
    }

    private void finishSend(SendSession session, String outcome) {
        transferHistory.add(outcome, session.peer, session.size);
        runOnUiThread(() -> {
            if (activeSend == session) activeSend = null;
            session.dialog.dismiss();
            if (!isFinishing() && !isDestroyed()) notifyUser(outcome);
        });
    }

    private void sendToPeer(SendSession session, String host, String token, String name, long size, InputStream stream)
            throws IOException {
        session.peer = linkedTvName();
        session.size = size;
        if (stream == null) throw new IOException("Cannot read file");
        if (session.cancelled) throw new IOException("Transfer cancelled");
        runOnUiThread(() -> {
            if (!session.cancelled) session.dialog.setMessage("Finding linked device…");
        });
        String address = LinkNetwork.resolve(host, token);
        if (session.cancelled) throw new IOException("Transfer cancelled");
        try (TransferClient client = new TransferClient(address, "SEND", token, session.socket)) {
            client.send(name, size, stream, percent -> runOnUiThread(() -> {
                if (session.cancelled) return;
                session.dialog.setMessage(percent < 100 ? name + " · " + percent + "%" : "Finishing transfer…");
            }));
        }
    }

    private void showFiles() {
        showLocalFiles();
    }

    private boolean newestFilesFirst = true;
    private LinearLayout currentTvFiles;

    private void showLocalFiles() {
        LinearLayout body = filesPage(false);
        File[] files = received().listFiles(file -> file.isFile() && !file.getName().startsWith("."));
        if (files == null || files.length == 0) {
            showFilesEmptyState(body, "No files received yet");
            return;
        }
        java.util.Comparator<File> byName = java.util.Comparator.comparing(
                File::getName, String.CASE_INSENSITIVE_ORDER);
        java.util.Arrays.sort(files, newestFilesFirst
                ? java.util.Comparator.comparingLong(File::lastModified).reversed().thenComparing(byName)
                : byName);
        for (File file : files) addLocalFileRow(body, file);
    }

    private LinearLayout filesPage(boolean onTv) {
        showingHome = false;
        LinearLayout page = screen(null);
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        TextView title = new TextView(this);
        title.setText("My Files");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (!onTv) {
            addFileToolbarIcon(toolbar, R.drawable.ic_files_sort,
                    newestFilesFirst ? "Sort: Newest" : "Sort: Name", () -> {
                        newestFilesFirst = !newestFilesFirst;
                        showLocalFiles();
                    });
        }
        addFileToolbarIcon(toolbar, R.drawable.ic_files_refresh, "Refresh",
                onTv ? this::showTvFiles : this::showLocalFiles);

        LinearLayout tabs = new LinearLayout(this);
        page.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
        addFileTab(tabs, "Received", !onTv, this::showLocalFiles);
        addFileTab(tabs, "On TV", onTv, this::showTvFiles);
        divider(page);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        currentTvFiles = onTv ? body : null;
        return body;
    }

    private void addFileToolbarIcon(LinearLayout toolbar, int icon, String label, Runnable action) {
        android.widget.ImageButton button = new android.widget.ImageButton(this);
        button.setImageResource(icon);
        button.setContentDescription(label);
        button.setBackground(flatRowBackground());
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setOnClickListener(view -> action.run());
        toolbar.addView(button, new LinearLayout.LayoutParams(dp(48), dp(48)));
    }

    private void addFileTab(LinearLayout tabs, String title, boolean selected, Runnable action) {
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tabs.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
        Button button = button(title, action);
        button.setBackground(flatRowBackground());
        button.setSelected(selected);
        tab.addView(button, new LinearLayout.LayoutParams(-1, dp(48)));
        android.view.View indicator = new android.view.View(this);
        indicator.setBackgroundColor(selected ? FOCUS_BLUE : Color.TRANSPARENT);
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(2));
        line.setMarginStart(dp(16));
        line.setMarginEnd(dp(16));
        tab.addView(indicator, line);
    }

    private void showFilesEmptyState(LinearLayout body, String message) {
        body.setGravity(Gravity.CENTER);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_files);
        icon.setColorFilter(FOCUS_BLUE);
        body.addView(icon, new LinearLayout.LayoutParams(dp(68), dp(68)));
        TextView text = info(body, message);
        text.setGravity(Gravity.CENTER);
    }

    private void addLocalFileRow(LinearLayout body, File file) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setMinimumHeight(dp(64));
        row.setFocusable(true);
        row.setOnClickListener(view -> fileActions(file));
        row.setBackground(flatRowBackground());
        body.addView(row, new LinearLayout.LayoutParams(-1, -2));
        divider(body);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_document);
        row.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, 0, 0);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView name = new TextView(this);
        name.setText(file.getName());
        name.setTextSize(16);
        name.setTextColor(Color.WHITE);
        
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(name);

        TextView details = new TextView(this);
        String size = android.text.format.Formatter.formatShortFileSize(this, file.length());
        String date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
                java.text.DateFormat.SHORT).format(new java.util.Date(file.lastModified()));
        details.setText(size + " · " + date);
        details.setTextColor(Color.WHITE);
        labels.addView(details);

        if (file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
            io.execute(() -> {
                try {
                    String path = file.getAbsolutePath();
                    android.content.pm.PackageInfo archive = getPackageManager().getPackageArchiveInfo(path, 0);
                    if (archive != null && archive.applicationInfo != null) {
                        archive.applicationInfo.sourceDir = path;
                        archive.applicationInfo.publicSourceDir = path;
                        android.graphics.drawable.Drawable appIcon = archive.applicationInfo.loadIcon(getPackageManager());
                        runOnUiThread(() -> { if (icon.isAttachedToWindow()) icon.setImageDrawable(appIcon); });
                    }
                } catch (RuntimeException error) {
                    android.util.Log.d("TVLink", "Could not load APK icon", error);
                }
            });
        }
    }

    private void fileActions(File file) {
        boolean apk = file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".apk");
        new AlertDialog.Builder(this).setTitle(file.getName()).setItems(new String[] {
                apk ? "Install APK" : "Open",
                "Rename", "Delete", "Send to TV", "Save a copy"
        }, (d, choice) -> {
            switch (choice) {
                case 0: openFile(file, apk); break;
                case 1: renameFile(file); break;
                case 2:
                    new AlertDialog.Builder(this).setTitle("Delete file?")
                            .setMessage(file.getName()).setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", (dialog, which) -> {
                                if (!file.delete()) notifyUser("Could not delete file");
                                showLocalFiles();
                            }).show();
                    break;
                case 3: sendFile(file); break;
                case 4: chooseExportLocation(file); break;
            }
        }).show();
    }

    private void chooseExportLocation(File file) {
        Intent picker = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream");
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.putExtra(Intent.EXTRA_TITLE, file.getName());
        try {
            exportName = file.getName();
            startActivityForResult(picker, EXPORT_FILE);
        } catch (android.content.ActivityNotFoundException error) {
            exportName = null;
            notifyUser("No document picker is available on this device.");
        }
    }

    private void exportCopy(String name, android.net.Uri target) {
        File folder = received();
        io.execute(() -> {
            try {
                File source = TransferServer.file(folder, name);
                try (InputStream in = new FileInputStream(source);
                     OutputStream out = getContentResolver().openOutputStream(target, "wt")) {
                    if (out == null) throw new IOException("Cannot write the selected document");
                    byte[] buffer = new byte[65536];
                    for (int count; (count = in.read(buffer)) != -1;) out.write(buffer, 0, count);
                }
                runOnUiThread(() -> notifyUser("Copy saved: " + name));
            } catch (IOException | SecurityException error) {
                runOnUiThread(() -> notifyUser("Could not save copy: " + error.getMessage()));
            }
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
                        File target = TransferServer.file(received(), input.getText().toString().trim());
                        if (!target.equals(file)) {
                            synchronized (received().getAbsolutePath().intern()) {
                                if (target.exists() || !file.renameTo(target)) throw new IOException("Cannot rename file");
                            }
                        }
                    } catch (IOException error) { notifyUser(error.getMessage()); }
                    showLocalFiles();
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
            android.net.Uri uri = new android.net.Uri.Builder().scheme("content")
                    .authority(getPackageName() + ".received").appendPath(file.getName()).build();
            String type = apk ? "application/vnd.android.package-archive" : getContentResolver().getType(uri);
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (android.content.ActivityNotFoundException | SecurityException error) {
            notifyUser("No compatible app or installer available");
        }
    }

    private void sendFile(File file) {
        if (!hasFilePairing()) {
            pairSystemRemote(() -> sendFile(file));
            return;
        }
        SendSession session = beginSend();
        if (session == null) return;
        io.execute(() -> {
            android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            try (java.net.Socket socket = session.socket; FileInputStream stream = new FileInputStream(file)) {
                sendToPeer(session, prefs.getString("remote_host", ""), prefs.getString("link_token", ""),
                        file.getName(), stream.getChannel().size(), stream);
                finishSend(session, "Sent: " + file.getName());
            } catch (IOException | SecurityException error) {
                finishSend(session, session.cancelled ? "Transfer cancelled" : "Could not send: " + error.getMessage());
            }
        });
    }

    private void showTvFiles() {
        if (isDestroyed()) return;
        LinearLayout body = filesPage(true);
        if (!hasFilePairing()) {
            body.addView(button("Pair TV", () -> pairSystemRemote(this::showTvFiles)),
                    new LinearLayout.LayoutParams(-2, dp(48)));
            return;
        }
        TextView loading = info(body, "Loading files…");
        io.execute(() -> {
            try (TransferClient client = client("LIST")) {
                java.util.List<String> files = client.list();
                java.util.Collections.sort(files, String.CASE_INSENSITIVE_ORDER);
                runOnUiThread(() -> {
                    if (!body.isAttachedToWindow() || currentTvFiles != body) return;
                    body.removeView(loading);
                    if (files.isEmpty()) showFilesEmptyState(body, "No files on TV yet");
                    for (String name : files) {
                        Button row = button(name, () -> fileActions(name));
                        row.setBackground(flatRowBackground());
                        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                        row.setMinHeight(dp(56));
                        row.setPadding(dp(12), dp(8), dp(12), dp(8));
                        row.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_document, 0, 0, 0);
                        row.setCompoundDrawablePadding(dp(12));
                        body.addView(row, new LinearLayout.LayoutParams(-1, -2));
                        divider(body);
                    }
                });
            } catch (IOException error) {
                runOnUiThread(() -> {
                    if (body.isAttachedToWindow() && currentTvFiles == body) {
                        loading.setText(error.getMessage() == null ? "Could not load files from TV" : error.getMessage());
                    }
                });
            }
        });
    }

    private void fileActions(String name) {
        new AlertDialog.Builder(this).setTitle(name)
                .setItems(new String[] {"Rename", "Delete"}, (dialog, choice) -> {
                    if (choice == 0) {
                        EditText input = new EditText(this);
                        input.setSingleLine(true);
                        input.setText(name);
                        AlertDialog editor = new AlertDialog.Builder(this).setTitle("Rename").setView(input)
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Save", (d, w) ->
                                        changeFile("RENAME", name, input.getText().toString().trim())).create();
                        showTextEditor(editor, input);
                    } else {
                        new AlertDialog.Builder(this).setTitle("Delete file?").setMessage(name)
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete", (d, w) -> changeFile("DELETE", name)).show();
                    }
                }).show();
    }

    private void changeFile(String operation, String... names) {
        work(() -> {
            try (TransferClient client = client(operation)) { client.change(names); }
        }, () -> {
            if (currentTvFiles != null && currentTvFiles.isAttachedToWindow()) showTvFiles();
        });
    }

    private final java.util.concurrent.ThreadPoolExecutor remoteIo =
            new java.util.concurrent.ThreadPoolExecutor(1, 1, 0, java.util.concurrent.TimeUnit.SECONDS,
                    new java.util.concurrent.LinkedBlockingQueue<>());
    private volatile AndroidTvRemote remoteConnection;
    private boolean remotePairing;

    private static final int REMOTE_NAVY = 0xff0b1329;
    static final int REMOTE_BLUE = 0xffaec0ff;

    private void showRemote() {
        showingHome = false;
        LinearLayout page = screen(null);
        page.setPadding(dp(20), dp(12), dp(20), dp(16));
        page.setBackgroundColor(REMOTE_NAVY);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView title = new TextView(this);
        title.setText("Remote");
        title.setTextSize(26);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(REMOTE_BLUE);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(-1, -1));

        TextView status = info(page, "Connecting…");
        status.setGravity(Gravity.CENTER);
        status.setTextColor(REMOTE_BLUE);
        status.setMaxLines(2);
        remoteStatus = status;
        status.setOnClickListener(view -> showDevice());
        status.setContentDescription("Connection status. Tap for paired device options.");

        remoteKeyboardButton = button("Open keyboard", this::openRemoteKeyboard);
        remoteKeyboardButton.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams keyboardSize = new LinearLayout.LayoutParams(-2, dp(48));
        keyboardSize.gravity = Gravity.CENTER_HORIZONTAL;
        page.addView(remoteKeyboardButton, keyboardSize);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(12), 0, dp(88));
        scroll.addView(controls, new ScrollView.LayoutParams(-1, -2));
        controls.addView(remoteKey("power", "Power", 26, false, 24),
                new LinearLayout.LayoutParams(dp(48), dp(48)));

        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        int diameter = Math.max(dp(170), Math.min(dp(300), Math.min(width - dp(64), height / 3)));
        android.widget.FrameLayout pad = new android.widget.FrameLayout(this);
        pad.setBackground(remoteBackground(REMOTE_BLUE, 0, 200));
        LinearLayout.LayoutParams padSize = new LinearLayout.LayoutParams(diameter, diameter);
        padSize.setMargins(0, dp(24), 0, dp(30));
        controls.addView(pad, padSize);
        int target = Math.round(diameter * .30f);
        addDirection(pad, "up", "Up", 19, Gravity.TOP | Gravity.CENTER_HORIZONTAL, target);
        addDirection(pad, "down", "Down", 20, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, target);
        addDirection(pad, "left", "Left", 21, Gravity.LEFT | Gravity.CENTER_VERTICAL, target);
        addDirection(pad, "right", "Right", 22, Gravity.RIGHT | Gravity.CENTER_VERTICAL, target);
        Button ok = button("OK", () -> sendSystemKey(23));
        ok.setPadding(0, 0, 0, 0);
        ok.setTextColor(REMOTE_BLUE);
        ok.setTextSize(22);
        ok.setBackground(remoteBackground(REMOTE_NAVY, 0, 120));
        int center = Math.round(diameter * .42f);
        pad.addView(ok, new android.widget.FrameLayout.LayoutParams(center, center, Gravity.CENTER));

        addRemoteControls(controls, "back", "Back", 4, "home", "Home", 3, "volume_up", "Volume up", 24, true);
        addRemoteControls(controls, "mic", "Voice assistant", 231, "mute", "Mute", 164,
                "volume_down", "Volume down", 25, false);
        connectRemoteScreen(status);
    }

    private StateListDrawable remoteBackground(int fill, int border, int radius) {
        StateListDrawable background = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(fill == REMOTE_BLUE ? FOCUS_BLUE : 0xff31446d);
        pressed.setStroke(dp(2), FOCUS_BLUE);
        pressed.setCornerRadius(dp(radius));
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(fill);
        normal.setCornerRadius(dp(radius));
        if (border != 0) normal.setStroke(dp(1), border);
        background.addState(new int[] {android.R.attr.state_pressed}, pressed);
        background.addState(new int[] {android.R.attr.state_focused}, pressed);
        background.addState(new int[] {}, normal);
        return background;
    }

    private RemoteIconView remoteIcon(String icon, String label, Runnable action, boolean light,
                                      int radius, boolean onPad) {
        RemoteIconView button = new RemoteIconView(this, icon, label,
                light || onPad ? REMOTE_NAVY : REMOTE_BLUE);
        button.setBackground(remoteBackground(onPad ? Color.TRANSPARENT : light ? REMOTE_BLUE : REMOTE_NAVY,
                light || onPad ? 0 : CONTENT_DIVIDER, radius));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private RemoteIconView remoteKey(String icon, String label, int key, boolean light, int radius) {
        RemoteIconView button = remoteIcon(icon, label, () -> sendSystemKey(key), light, radius, false);
        if ("mic".equals(icon)) {
            button.setOnClickListener(view -> showVoicePopup(button, "Hold the mic to talk",
                    this::startRemoteVoice, this::finishAssistantVoice, this::cancelAssistantVoice));
        }
        return button;
    }

    private void addDirection(android.widget.FrameLayout pad, String icon, String label, int key,
                              int gravity, int size) {
        pad.addView(remoteIcon(icon, label, () -> sendSystemKey(key), false, 36, true),
                new android.widget.FrameLayout.LayoutParams(size, size, gravity));
    }

    private void addRemoteControls(LinearLayout controls, String left, String leftLabel, int leftKey,
                                   String middle, String middleLabel, int middleKey, String right,
                                   String rightLabel, int rightKey, boolean home) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowSize = new LinearLayout.LayoutParams(-1, dp(56));
        rowSize.bottomMargin = home ? dp(16) : 0;
        controls.addView(row, rowSize);
        row.addView(remoteKey(left, leftLabel, leftKey, false, 28),
                new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout.LayoutParams centerSize = new LinearLayout.LayoutParams(dp(104), dp(home ? 56 : 44));
        int availableWidth = getResources().getConfiguration().screenWidthDp - 40;
        int gap = Math.max(0, Math.min(24, (availableWidth - 216) / 2));
        centerSize.setMargins(dp(gap), 0, dp(gap), 0);
        row.addView(remoteKey(middle, middleLabel, middleKey, home, home ? 28 : 12), centerSize);
        row.addView(remoteKey(right, rightLabel, rightKey, false, 28),
                new LinearLayout.LayoutParams(dp(56), dp(56)));
    }

    private void pairSystemRemote(Runnable paired) {
        if (remotePairing || busy) return;
        new AlertDialog.Builder(this, R.style.TVLinkPairingDialog).setTitle("Pair TV")
                .setMessage("Open Pair in TVLink on your TV. Keep both devices on the same Wi-Fi.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Find TV", (dialog, choice) -> findPairingTv(paired))
                .show();
    }

    private void findPairingTv(Runnable paired) {
        if (remotePairing || isFinishing() || isDestroyed()) return;
        remotePairing = true;
        PairingLog searching = pairingProgress("Searching for TV…");
        remoteIo.execute(() -> {
            try {
                android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
                String pendingHost = prefs.getString("pending_file_host", "");
                String pendingToken = prefs.getString("pending_file_token", "");
                if (!pendingToken.isEmpty()) searching.step("Checking saved file link…");
                if (!pendingToken.isEmpty() && pendingHost.equals(prefs.getString("remote_host", ""))
                        && confirmLink(pendingHost, pendingToken)) {
                    finishLink(pendingHost, searching);
                    runOnUiThread(searching::dismiss);
                    showLinkComplete(paired);
                    return;
                }
                java.util.Map<String, String> discovered = LinkNetwork.findPairingTvs();
                runOnUiThread(() -> {
                    searching.dismiss();
                    remotePairing = false;
                    if (isFinishing() || isDestroyed()) return;
                    if (discovered.isEmpty()) {
                        String hint = "Open Pair on your TV and check that both devices are on the same Wi-Fi.";
                        searching.failed("No TV found", new IOException(hint), () -> findPairingTv(paired));
                    } else if (discovered.size() == 1) {
                        java.util.Map.Entry<String, String> device = discovered.entrySet().iterator().next();
                        connectForPairing(device.getKey(), device.getValue(), paired);
                    } else {
                        String[] hosts = discovered.keySet().toArray(new String[0]);
                        String[] names = discovered.values().toArray(new String[0]);
                        new AlertDialog.Builder(this, R.style.TVLinkPairingDialog).setTitle("Choose your TV")
                                .setItems(names, (dialog, choice) -> connectForPairing(hosts[choice], names[choice], paired))
                                .setNegativeButton("Cancel", null).show();
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    searching.dismiss();
                    remotePairing = false;
                    if (!isFinishing() && !isDestroyed()) searching.failed("Could not find or link TV",
                            error, () -> findPairingTv(paired));
                });
            }
        });
    }

    private void connectForPairing(String host, String name, Runnable paired) {
        if (remotePairing || isFinishing() || isDestroyed()) return;
        remotePairing = true;
        PairingLog connecting = pairingProgress("Connecting to TV…");
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        remoteIo.execute(() -> {
            AndroidTvRemote pairing = null;
            try {
                reportPairingStatus(host, "CONNECTING");
                String previousHost = prefs.getString("pending_host", prefs.getString("remote_host", ""));
                String previousPin = prefs.getString("pending_pin", prefs.getString("remote_pin", ""));
                if (!previousPin.isEmpty() && previousHost.equals(host)) {
                    try {
                        boolean pending = !prefs.getString("pending_pin", "").isEmpty();
                        boolean legacy = prefs.getBoolean(pending ? "pending_legacy_identity" : "remote_legacy_identity", true);
                        try (AndroidTvRemote remote = new AndroidTvRemote(
                                this, getNoBackupFilesDir(), host, false, previousPin, legacy, connecting::step)) {
                            remote.start();
                        } catch (Exception error) {
                            throw new RemoteUnavailable(error);
                        }
                        saveRemotePairing(host, previousPin, legacy, name);
                        reportPairingStatus(host, "PAIRED");
                        connecting.loading("Connecting file transfer…");
                        finishLink(host, connecting);
                        runOnUiThread(connecting::dismiss);
                        showLinkComplete(paired);
                        return;
                    } catch (RemoteUnavailable error) {
                        // Try the system pairing flow if a saved remote connection no longer works.
                    }
                }
                pairing = new AndroidTvRemote(this, getNoBackupFilesDir(), host, true, "", false, connecting::step);
                connecting.step("TLS connected. Requesting Android TV code…");
                pairing.beginPairing();
                AndroidTvRemote pending = pairing;
                reportPairingStatus(host, "CODE");
                runOnUiThread(() -> {
                    connecting.dismiss();
                    enterRemoteCode(pending, host, name, paired);
                });
            } catch (Exception error) {
                if (pairing != null) pairing.close();
                reportPairingStatus(host, "FAILED");
                android.util.Log.w("TVLink", "TV pairing failed before entering code", error);
                runOnUiThread(() -> {
                    connecting.dismiss();
                    remotePairing = false;
                    if (isFinishing() || isDestroyed()) return;
                    connecting.failed("Could not connect to TV",
                            error, () -> findPairingTv(paired));
                });
            }
        });
    }

    private void enterRemoteCode(AndroidTvRemote pairing, String host, String name, Runnable paired) {
        if (isFinishing() || isDestroyed()) {
            pairing.close();
            return;
        }
        TextView[] digits = new TextView[6];
        LinearLayout boxes = new LinearLayout(this);
        boxes.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        for (int index = 0; index < digits.length; index++) {
            TextView digit = new TextView(this);
            digit.setGravity(Gravity.CENTER);
            digit.setTextSize(24);
            digit.setTypeface(android.graphics.Typeface.MONOSPACE);
            digit.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(48), 1);
            if (index > 0) size.setMarginStart(dp(6));
            boxes.addView(digit, size);
            digits[index] = digit;
        }

        EditText code = new EditText(this) {
            @Override protected void onSelectionChanged(int start, int end) {
                super.onSelectionChanged(start, end);
                updatePairingBoxes(this, digits);
            }
        };
        code.setSingleLine(true);
        code.setGravity(Gravity.CENTER);
        code.setContentDescription("Six-character Android TV pairing code");
        code.setTextColor(Color.TRANSPARENT);
        code.setCursorVisible(false);
        code.setBackgroundColor(Color.TRANSPARENT);
        code.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        code.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.AllCaps(),
                new android.text.InputFilter.LengthFilter(digits.length)});

        android.widget.FrameLayout input = new android.widget.FrameLayout(this);
        input.addView(boxes, new android.widget.FrameLayout.LayoutParams(-1, -1));
        input.addView(code, new android.widget.FrameLayout.LayoutParams(-1, -1));
        LinearLayout codeContainer = new LinearLayout(this);
        codeContainer.setPadding(dp(24), dp(16), dp(24), dp(8));
        codeContainer.addView(input, new LinearLayout.LayoutParams(-1, dp(48)));
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.TVLinkPairingDialog)
                .setTitle("Enter the code shown on your TV")
                .setView(codeContainer).setNegativeButton("Cancel", null)
                .setPositiveButton("Pair", null).create();
        android.os.Handler timer = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable expire = () -> {
            if (!dialog.isShowing()) return;
            dialog.setOnDismissListener(null);
            dialog.dismiss();
            pairing.close();
            remotePairing = false;
            if (!isDestroyed()) {
                remoteIo.execute(() -> reportPairingStatus(host, "TIMED_OUT"));
                PairingLog log = new PairingLog();
                log.failed("Pairing code timed out", new IOException("The TV code was not submitted within 90 seconds."),
                        () -> findPairingTv(paired));
            }
        };
        dialog.setOnDismissListener(ignored -> {
            timer.removeCallbacks(expire);
            pairing.close();
            remotePairing = false;
            if (!isDestroyed()) remoteIo.execute(() -> reportPairingStatus(host, "CANCELLED"));
        });
        code.setOnFocusChangeListener((view, focused) -> updatePairingBoxes(code, digits));
        showTextEditor(dialog, code);
        timer.postDelayed(expire, 90000);
        updatePairingBoxes(code, digits);
        Button pairButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        pairButton.setEnabled(false);
        code.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                pairButton.setEnabled(text.toString().matches("[0-9A-Fa-f]{6}"));
                updatePairingBoxes(code, digits);
            }
            public void afterTextChanged(android.text.Editable text) {}
        });
        code.setOnEditorActionListener((view, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) return false;
            if (pairButton.isEnabled()) pairButton.performClick();
            return true;
        });
        pairButton.setOnClickListener(view -> {
            String value = code.getText().toString().trim();
            if (!value.matches("[0-9a-fA-F]{6}")) {
                code.setError("Enter six characters using 0–9 and A–F.");
                code.requestFocus();
                return;
            }
            timer.removeCallbacks(expire);
            dialog.setOnDismissListener(null);
            dialog.dismiss();
            PairingLog progress = pairingProgress("Verifying code with Android TV…");
            remoteIo.execute(() -> {
                boolean remoteSaved = false;
                try (AndroidTvRemote connection = pairing) {
                    reportPairingStatus(host, "VERIFYING");
                    String pin = connection.finishPairing(value);
                    saveRemotePairing(host, pin, false, name);
                    remoteSaved = true;
                    reportPairingStatus(host, "PAIRED");
                    progress.loading("Connecting file transfer…");
                    finishLink(host, progress, true);
                    runOnUiThread(progress::dismiss);
                    showLinkComplete(paired);
                } catch (Exception error) {
                    boolean pairedSuccessfully = remoteSaved;
                    reportPairingStatus(host, "FAILED");
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        progress.dismiss();
                        progress.failed(pairedSuccessfully ? "Remote paired · File transfer not connected"
                                : "Could not finish pairing", error, () -> findPairingTv(paired));
                    });
                } finally {
                    runOnUiThread(() -> {
                        remotePairing = false;
                        progress.dismiss();
                    });
                }
            });
        });
    }

    private void updatePairingBoxes(EditText code, TextView[] digits) {
        CharSequence text = code.getText();
        if (text == null) return;
        int active = Math.min(Math.max(code.getSelectionEnd(), 0), digits.length - 1);
        for (int index = 0; index < digits.length; index++) {
            TextView digit = digits[index];
            digit.setText(index < text.length() ? text.subSequence(index, index + 1) : "");
            boolean focused = code.hasFocus() && active == index;
            GradientDrawable border = new GradientDrawable();
            border.setColor(0xff0e1728);
            border.setCornerRadius(dp(8));
            border.setStroke(dp(focused ? 2 : 1), focused ? FOCUS_BLUE : CONTENT_DIVIDER);
            digit.setBackground(border);
        }
    }

    private PairingLog pairingProgress(String status) {
        PairingLog dialog = new PairingLog();
        dialog.loading(status);
        dialog.show();
        return dialog;
    }

    private final class PairingLog extends AlertDialog {
        private final long start = android.os.SystemClock.elapsedRealtime();
        private final StringBuilder entries = new StringBuilder();
        private final TextView status = new TextView(MainActivity.this);

        PairingLog() {
            super(MainActivity.this, R.style.TVLinkPairingDialog);
            setCancelable(false);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(24), dp(18), dp(24), dp(18));
            android.widget.ProgressBar spinner = new android.widget.ProgressBar(MainActivity.this);
            row.addView(spinner, new LinearLayout.LayoutParams(dp(24), dp(24)));
            status.setTextColor(Color.WHITE);
            status.setTextSize(16);
            status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
            LinearLayout.LayoutParams labelSize = new LinearLayout.LayoutParams(0, -2, 1);
            labelSize.setMarginStart(dp(16));
            row.addView(status, labelSize);
            setView(row);
        }

        void loading(String message) {
            step(message);
            runOnUiThread(() -> status.setText(message));
        }

        private void copyLog() {
            android.content.ClipboardManager clipboard = getSystemService(android.content.ClipboardManager.class);
            if (clipboard == null) {
                notifyUser("Clipboard unavailable");
                return;
            }
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("TVLink connection log", entries.toString()));
            notifyUser("Log copied");
        }

        void step(String message) {
            runOnUiThread(() -> {
                double seconds = (android.os.SystemClock.elapsedRealtime() - start) / 1000.0;
                if (entries.length() > 0) entries.append('\n');
                entries.append(String.format(java.util.Locale.ROOT, "[%.1fs] %s", seconds, message));
            });
        }

        void failed(String title, Exception error, Runnable retry) {
            failed(title, error, "Try again", retry);
        }

        void failed(String title, Exception error, String retryLabel, Runnable retry) {
            String detail = error.getMessage();
            step("Failed: " + (detail == null ? error.getClass().getSimpleName() : detail));
            dismiss();
            new AlertDialog.Builder(MainActivity.this, R.style.TVLinkPairingDialog)
                    .setTitle(title).setMessage(entries.toString())
                    .setNegativeButton("Close", null)
                    .setNeutralButton("Copy log", null)
                    .setPositiveButton(retryLabel, (d, w) -> retry.run()).show()
                    .getButton(BUTTON_NEUTRAL).setOnClickListener(view -> copyLog());
        }
    }

    private void reportPairingStatus(String host, String status) {
        // Status is advisory and must never block system pairing.
        LinkNetwork.sendPairingStatus(host, status);
    }

    private void saveRemotePairing(String host, String pin, boolean legacyIdentity, String name) throws IOException {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        android.content.SharedPreferences.Editor edit = prefs.edit()
                .putString("remote_host", host).putString("remote_pin", pin).putString("remote_name", name)
                .putBoolean("remote_legacy_identity", legacyIdentity);
        if (!host.equals(prefs.getString("remote_host", ""))) edit.remove("link_token").remove("peer_name");
        if (!edit.commit()) throw new IOException("Could not save Remote pairing on this phone");
        closeSystemRemote();
    }

    private static final class RemoteUnavailable extends IOException {
        RemoteUnavailable(Exception cause) { super(cause.getMessage(), cause); }
    }

    private void finishLink(String host, PairingLog progress) throws Exception {
        finishLink(host, progress, false);
    }

    private boolean tryRemoteFilePairing(String host, PairingLog progress) throws InterruptedException {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        try (AndroidTvRemote remote = new AndroidTvRemote(this, getNoBackupFilesDir(), host, false,
                prefs.getString("remote_pin", ""), false, progress::step)) {
            remote.start();
            java.security.KeyPair key = FilePairing.keyPair();
            String clientKey = FilePairing.encode(key.getPublic().getEncoded());
            try (TransferClient client = new TransferClient(host, "PAIR_FILES_REMOTE", FilePairing.commitment(clientKey))) {
                FilePairing.Result link = client.exchangeFileKeys(key, clientKey);
                String secret = LinkKeys.newToken();
                LinkKeys.send(remote, secret);
                client.verifyRemoteLink(secret, link.token);
                savePendingFileLink(host, link.token);
                client.approveFileLink();
                client.confirm();
                return true;
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw error;
        } catch (Exception error) {
            progress.step("Remote verification unavailable. Using standard file approval.");
            return false;
        }
    }

    private void savePendingFileLink(String host, String token) throws IOException {
        if (!getPreferences(MODE_PRIVATE).edit().putString("pending_file_host", host)
                .putString("pending_file_token", token).commit()) {
            throw new IOException("Could not save pending file pairing");
        }
    }

    private void finishLink(String host, PairingLog progress, boolean tryRemote) throws Exception {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String savedToken = prefs.getString("link_token", "");
        if (host.equals(prefs.getString("remote_host", "")) && !savedToken.isEmpty()
                && confirmLink(host, savedToken)) {
            syncDeviceName(host, savedToken);
            return;
        }
        boolean remoteConfirmed = tryRemote && prefs.getString("pending_file_token", "").isEmpty()
                && tryRemoteFilePairing(host, progress);
        String token = host.equals(prefs.getString("pending_file_host", ""))
                ? prefs.getString("pending_file_token", "") : "";
        if (!remoteConfirmed && (token.isEmpty() || !confirmLink(host, token))) {
            java.security.KeyPair key = FilePairing.keyPair();
            String clientKey = FilePairing.encode(key.getPublic().getEncoded());
            progress.step("Opening encrypted connection to " + host + ":47832…");
            try (TransferClient client = new TransferClient(host, "PAIR_FILES", FilePairing.commitment(clientKey))) {
                progress.step("TLS connected. Waiting for TV pairing key…");
                FilePairing.Result link = client.exchangeFileKeys(key, clientKey);
                progress.step("Keys exchanged. Confirm the matching number on both screens.");
                savePendingFileLink(host, link.token);
                confirmFileNumber(progress, link.code);
                client.approveFileLink();
                client.confirm();
                token = link.token;
            }
        }
        if (!prefs.edit().putString("link_token", token)
                .remove("pending_file_host").remove("pending_file_token").remove("pending_host")
                .remove("pending_pin").remove("pending_legacy_identity").remove("pending_token").commit()) {
            throw new IOException("Could not save the link on this phone");
        }
        syncDeviceName(host, token);
    }

    private void syncDeviceName(String host, String token) {
        try (TransferClient client = new TransferClient(host, "DEVICE", token)) {
            String name = client.exchangeDeviceName(LocalSettings.device(this));
            android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            if (!name.isEmpty() && token.equals(prefs.getString("link_token", ""))) {
                prefs.edit().putString("peer_name", name).putString("remote_name", name).apply();
            }
        } catch (IOException error) {
            android.util.Log.d("TVLink", "Could not synchronize paired device name", error);
        }
    }

    private void confirmFileNumber(PairingLog progress, String code) throws Exception {
        java.util.concurrent.CompletableFuture<Boolean> answer = new java.util.concurrent.CompletableFuture<>();
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                answer.complete(false);
                return;
            }
            progress.dismiss();
            AlertDialog dialog = new AlertDialog.Builder(this, R.style.TVLinkPairingDialog).setTitle("Connect file transfer")
                    .setMessage("If " + code + " matches on the TV, select Allow on both screens.")
                    .setNegativeButton("Cancel", (d, w) -> answer.complete(false))
                    .setPositiveButton("Allow", (d, w) -> answer.complete(true))
                    .setOnDismissListener(d -> answer.complete(false)).show();
            android.os.Handler timer = new android.os.Handler(android.os.Looper.getMainLooper());
            Runnable expire = () -> {
                answer.completeExceptionally(new IOException("File pairing timed out"));
                dialog.dismiss();
            };
            timer.postDelayed(expire, 50000);
            dialog.setOnDismissListener(d -> {
                timer.removeCallbacks(expire);
                answer.complete(false);
            });
        });
        if (!answer.get(55, java.util.concurrent.TimeUnit.SECONDS)) throw new IOException("File pairing cancelled");
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                progress.loading("Waiting for approval on your TV…");
                progress.show();
            }
        });
    }

    private boolean confirmLink(String host, String token) {
        try (TransferClient client = new TransferClient(host, "PING", token)) {
            client.confirm();
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    private void showLinkComplete(Runnable paired) {
        runOnUiThread(() -> {
            remotePairing = false;
            if (isDestroyed() || isFinishing()) return;
            if (started) startReceiver();
            notifyUser("Remote and file transfers connected");
            paired.run();
        });
    }

    private Button remoteKeyboardButton;
    private AndroidTvRemote.ImeState remoteIme;
    private android.app.Dialog remoteKeyboardDialog;
    private EditText remoteEditor;
    private Runnable pendingRemoteText;
    private boolean updatingRemoteEditor;

    private void updateRemoteIme(AndroidTvRemote.ImeState state) {
        boolean changedField = state != null && (remoteIme == null || state.field != remoteIme.field);
        remoteIme = state;
        if (remoteKeyboardButton != null) remoteKeyboardButton.setVisibility(
                state == null ? android.view.View.GONE : android.view.View.VISIBLE);
        if (remoteKeyboardDialog != null && (state == null || changedField)) {
            cancelPendingRemoteText();
            remoteKeyboardDialog.dismiss();
            return;
        }
        if (changedField && !remoteConnecting) openRemoteKeyboard();
        if (state != null && remoteEditor != null && !state.text.contentEquals(remoteEditor.getText())) {
            cancelPendingRemoteText();
            updatingRemoteEditor = true;
            remoteEditor.setText(state.text);
            remoteEditor.setSelection(remoteEditor.length());
            updatingRemoteEditor = false;
        }
    }

    private void cancelPendingRemoteText() {
        if (pendingRemoteText == null) return;
        receiverHandler.removeCallbacks(pendingRemoteText);
        pendingRemoteText = null;
    }

    private void openRemoteKeyboard() {
        AndroidTvRemote.ImeState state = remoteIme;
        AndroidTvRemote connection = remoteConnection;
        if (!started || isFinishing() || isDestroyed() || state == null || connection == null
                || !connection.isOpen() || remoteKeyboardDialog != null) return;

        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar);
        LinearLayout page = new LinearLayout(dialog.getContext());
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(16), dp(24), dp(12));
        page.setBackgroundColor(REMOTE_NAVY);

        TextView device = new TextView(dialog.getContext());
        device.setText(linkedTvName());
        device.setTextColor(REMOTE_BLUE);
        device.setTextSize(22);
        device.setGravity(Gravity.CENTER);
        page.addView(device, new LinearLayout.LayoutParams(-1, dp(48)));

        EditText editor = new EditText(dialog.getContext());
        editor.setInputType(state.inputType);
        editor.setHorizontallyScrolling(false);
        editor.setMaxLines(8);
        editor.setTextColor(Color.WHITE);
        editor.setHintTextColor(0xffb8c3d6);
        editor.setTextSize(30);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setBackgroundColor(Color.TRANSPARENT);
        editor.setPadding(0, dp(20), 0, dp(12));
        editor.setHint("Start typing on your phone to see it on TV");
        editor.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(8000)});
        editor.setText(state.text);
        editor.setSelection(editor.length());
        page.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));

        Button navigation = button("Switch to navigation", () -> {
            Runnable latest = pendingRemoteText;
            cancelPendingRemoteText();
            if (latest != null) latest.run();
            dialog.dismiss();
        });
        LinearLayout.LayoutParams navigationSize = new LinearLayout.LayoutParams(-2, dp(48));
        navigationSize.gravity = Gravity.CENTER_HORIZONTAL;
        if (!isPasswordField(state.inputType)) {
            RemoteIconView mic = remoteIcon("mic", "Voice dictation", () -> {}, false, 24, false);
            mic.setOnClickListener(view -> showVoicePopup(mic, "Hold the mic to dictate",
                    meter -> startDictation(dialog, editor, meter), this::finishDictation,
                    this::cancelDictation));
            LinearLayout.LayoutParams micSize = new LinearLayout.LayoutParams(dp(48), dp(48));
            micSize.gravity = Gravity.CENTER_HORIZONTAL;
            micSize.bottomMargin = dp(20);
            page.addView(mic, micSize);
        }
        page.addView(navigation, navigationSize);
        dialog.setContentView(page);
        remoteKeyboardDialog = dialog;
        remoteEditor = editor;
        int generation = remoteSessionGeneration;
        editor.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void afterTextChanged(android.text.Editable text) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (updatingRemoteEditor) return;
                cancelPendingRemoteText();
                String value = text.toString();
                pendingRemoteText = () -> {
                    pendingRemoteText = null;
                    remoteIo.execute(() -> {
                        if (generation != remoteSessionGeneration || connection != remoteConnection) return;
                        try {
                            if (connection.replaceImeText(state.field, value)) return;
                        } catch (IOException error) {
                            closeSystemRemote();
                        }
                        runOnUiThread(() -> {
                            if (remoteKeyboardDialog == dialog) dialog.dismiss();
                        });
                    });
                };
                receiverHandler.postDelayed(pendingRemoteText, 80);
            }
        });
        dialog.setOnDismissListener(ignored -> {
            dismissVoicePopup();
            cancelDictation();
            cancelPendingRemoteText();
            remoteEditor = null;
            remoteKeyboardDialog = null;
            if (remoteIme != null && remoteIme.field != state.field && !remoteConnecting) {
                openRemoteKeyboard();
            }
        });

        android.view.Window window = dialog.getWindow();
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        Runnable showKeyboard = () -> {
            if (!dialog.isShowing() || !editor.requestFocus()) return;
            if (android.os.Build.VERSION.SDK_INT >= 30 && window.getInsetsController() != null) {
                window.getInsetsController().show(android.view.WindowInsets.Type.ime());
                return;
            }
            android.view.inputmethod.InputMethodManager ime =
                    getSystemService(android.view.inputmethod.InputMethodManager.class);
            if (ime != null) ime.showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        };
        page.getViewTreeObserver().addOnWindowFocusChangeListener(
                new android.view.ViewTreeObserver.OnWindowFocusChangeListener() {
                    @Override public void onWindowFocusChanged(boolean focused) {
                        if (!focused) return;
                        page.getViewTreeObserver().removeOnWindowFocusChangeListener(this);
                        editor.post(showKeyboard);
                    }
                });
        editor.setOnClickListener(view -> editor.post(showKeyboard));
        editor.requestFocus();
        dialog.show();
        window.setLayout(-1, -1);
    }

    private TextView remoteStatus;
    private boolean remoteConnecting;
    private volatile int remoteSessionGeneration;

    private static final int VOICE_PERMISSION_REQUEST = 241;
    private AssistantVoiceHold assistantVoice;
    private VoiceDictation dictation;
    private VoiceMeter voicePopup;

    private void showVoicePopup(android.view.View anchor, String micLabel,
                                java.util.function.Consumer<VoiceMeter> start,
                                Runnable finish, Runnable cancel) {
        if (!started || !anchor.isAttachedToWindow() || voicePopup != null
                || assistantVoice != null || dictation != null) return;
        RemoteIconView holdButton = remoteIcon("mic", micLabel, () -> {}, false, 24, false);
        VoiceMeter meter = new VoiceMeter(anchor, holdButton);
        bindVoiceHold(holdButton, () -> start.accept(meter), finish, cancel);
        voicePopup = meter;
        meter.show(() -> {
            voicePopup = null;
            // A released button is disabled while its voice input is being processed.
            if (holdButton.isEnabled()) cancel.run();
        });
    }

    private void dismissVoicePopup() {
        if (voicePopup != null) voicePopup.hide();
    }

    private static void bindVoiceHold(android.view.View button, Runnable start, Runnable finish, Runnable cancel) {
        button.setOnTouchListener(new android.view.View.OnTouchListener() {
            private int pointer = -1;

            @Override public boolean onTouch(android.view.View view, android.view.MotionEvent event) {
                int action = event.getActionMasked();
                if (action == android.view.MotionEvent.ACTION_DOWN) {
                    pointer = event.getPointerId(0);
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    view.setPressed(true);
                    start.run();
                } else if (action == android.view.MotionEvent.ACTION_CANCEL) {
                    boolean holding = pointer != -1;
                    reset(view);
                    if (holding) cancel.run();
                } else if ((action == android.view.MotionEvent.ACTION_UP
                        || action == android.view.MotionEvent.ACTION_POINTER_UP)
                        && event.getPointerId(event.getActionIndex()) == pointer) {
                    reset(view);
                    finish.run();
                }
                return true;
            }

            private void reset(android.view.View view) {
                pointer = -1;
                view.setPressed(false);
                if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(false);
            }
        });
    }

    private boolean hasVoicePermission() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) return true;
        requestPermissions(new String[] {android.Manifest.permission.RECORD_AUDIO}, VOICE_PERMISSION_REQUEST);
        return false;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != VOICE_PERMISSION_REQUEST) return;
        if (results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notifyUser("Hold the microphone button again to speak.");
        } else {
            notifyUser("Allow microphone access to use voice input.");
        }
    }

    private static boolean isPasswordField(int inputType) {
        int kind = inputType & android.text.InputType.TYPE_MASK_CLASS;
        int variation = inputType & android.text.InputType.TYPE_MASK_VARIATION;
        if (kind == android.text.InputType.TYPE_CLASS_NUMBER) {
            return variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        }
        return kind == android.text.InputType.TYPE_CLASS_TEXT
                && (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
    }

    private void startDictation(android.app.Dialog keyboard, EditText editor, VoiceMeter meter) {
        if (dictation != null || assistantVoice != null || !started || remoteEditor != editor
                || remoteKeyboardDialog != keyboard || !hasVoicePermission()) return;
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            meter.ready("Speech recognition is unavailable on this phone.");
            return;
        }
        AndroidTvRemote.ImeState target = remoteIme;
        if (target == null || isPasswordField(target.inputType)) return;
        meter.label("Starting microphone…");
        dictation = new VoiceDictation(this, meter, (words, error) -> {
            dictation = null;
            meter.ready(error);
            if (remoteKeyboardDialog != keyboard || remoteEditor != editor || !started
                    || remoteIme == null || remoteIme.field != target.field) return;
            if (words != null) {
                int selectionStart = editor.getSelectionStart();
                int selectionEnd = editor.getSelectionEnd();
                if (selectionStart < 0 || selectionEnd < 0) selectionStart = selectionEnd = editor.length();
                editor.getText().replace(Math.min(selectionStart, selectionEnd),
                        Math.max(selectionStart, selectionEnd), words);
                Runnable latest = pendingRemoteText;
                cancelPendingRemoteText();
                if (latest != null) latest.run();
            }
        });
        dictation.start();
    }

    private void finishDictation() {
        if (dictation != null) dictation.finish();
    }

    private void cancelDictation() {
        if (dictation != null) dictation.cancel();
    }

    private static final class AssistantVoiceHold {
        final java.util.concurrent.atomic.AtomicBoolean stopped = new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicBoolean finish = new java.util.concurrent.atomic.AtomicBoolean();
        final VoiceMeter meter;
        final long startedAt = android.os.SystemClock.elapsedRealtime();

        AssistantVoiceHold(VoiceMeter meter) {
            this.meter = meter;
        }
    }

    private void startRemoteVoice(VoiceMeter meter) {
        AndroidTvRemote connection = remoteConnection;
        if (assistantVoice != null || dictation != null || !started || remoteStatus == null || remoteConnecting) return;
        if (connection == null || !connection.isOpen()) {
            meter.ready("Connect to your TV before using the microphone.");
            return;
        }
        if (remoteIme != null) {
            meter.ready("Use the microphone on the keyboard to enter text.");
            return;
        }
        if (!connection.supportsVoice()) {
            meter.ready("This TV does not offer Remote voice input.");
            return;
        }
        if (!hasVoicePermission()) return;
        AssistantVoiceHold hold = new AssistantVoiceHold(meter);
        assistantVoice = hold;
        meter.beginDiagnostics();
        assistantDiagnostic(hold, "Mic pressed");
        meter.label("Waiting for your TV…");
        new Thread(() -> {
            String failure = null;
            try {
                RemoteVoice.capture(connection, hold.stopped, hold.finish, level -> runOnUiThread(() -> {
                    if (assistantVoice == hold && !hold.stopped.get()) {
                        meter.label("Listening · release to finish");
                        meter.level(level);
                    }
                }), event -> assistantDiagnostic(hold, event));
            } catch (IOException | RuntimeException error) {
                if (!hold.stopped.get() || hold.finish.get()) {
                    failure = error.getMessage() == null ? "Could not use the microphone." : error.getMessage();
                }
            } finally {
                String message = failure;
                assistantDiagnostic(hold, message == null ? "Session finished" : "Session failed");
                runOnUiThread(() -> {
                    if (assistantVoice != hold) return;
                    assistantVoice = null;
                    meter.ready(message);
                });
            }
        }, "TVLink voice capture").start();
    }

    private void assistantDiagnostic(AssistantVoiceHold hold, String event) {
        double seconds = (android.os.SystemClock.elapsedRealtime() - hold.startedAt) / 1000.0;
        String line = String.format(java.util.Locale.US, "+%.2fs  %s", seconds, event);
        runOnUiThread(() -> {
            if (assistantVoice == hold) hold.meter.addDiagnostic(line);
        });
    }

    private void finishAssistantVoice() {
        if (assistantVoice == null) return;
        assistantDiagnostic(assistantVoice, "Mic released");
        assistantVoice.finish.set(true);
        assistantVoice.stopped.set(true);
        assistantVoice.meter.processing("Waiting for the TV to process…");
    }

    private void cancelAssistantVoice() {
        if (assistantVoice == null) return;
        assistantVoice.finish.set(false);
        assistantVoice.stopped.set(true);
        assistantVoice.meter.processing("Stopping microphone…");
    }

    private void suspendRemoteSession() {
        dismissVoicePopup();
        cancelDictation();
        cancelAssistantVoice();
        remoteSessionGeneration++;
        updateRemoteIme(null);
        remoteConnecting = false;
        remoteIo.execute(this::closeSystemRemote);
    }

    private void connectRemoteScreen(TextView status) {
        if (!started || remoteStatus != status || remoteConnecting || remotePairing) return;
        int generation = remoteSessionGeneration;
        remoteConnecting = true;
        status.setText(linkedTvName() + " · Connecting…");
        PairingLog progress = new PairingLog();
        progress.step("Opening Remote session…");
        remoteIo.execute(() -> {
            if (generation != remoteSessionGeneration) return;
            Exception failure = null;
            try {
                ensureRemote(progress::step);
            } catch (Exception error) {
                closeSystemRemote();
                failure = error;
            }
            Exception result = failure;
            runOnUiThread(() -> {
                if (generation != remoteSessionGeneration || isFinishing()
                        || isDestroyed() || remoteStatus != status) return;
                remoteConnecting = false;
                if (result == null) {
                    status.setText(linkedTvName() + " · Connected");
                    if (remoteIme != null) openRemoteKeyboard();
                } else {
                    status.setText(linkedTvName() + " · Disconnected");
                    progress.failed("Remote disconnected", result, "Reconnect",
                            () -> connectRemoteScreen(status));
                }
            });
        });
    }

    private void sendSystemKey(int key) {
        if (!started || remotePairing || remoteConnecting || remoteStatus == null
                || remoteIo.getQueue().size() >= 16) return;
        TextView status = remoteStatus;
        int generation = remoteSessionGeneration;
        remoteIo.execute(() -> {
            if (generation != remoteSessionGeneration) return;
            java.util.List<String> messages = new java.util.ArrayList<>();
            try {
                ensureRemote(messages::add);
                if (generation == remoteSessionGeneration) remoteConnection.key(key);
            } catch (Exception error) {
                closeSystemRemote();
                runOnUiThread(() -> {
                    if (generation != remoteSessionGeneration || isFinishing()
                            || isDestroyed() || remoteStatus != status || remoteConnecting) return;
                    if ("Remote disconnected".contentEquals(status.getText())) return;
                    status.setText("Remote disconnected");
                    PairingLog log = new PairingLog();
                    for (String message : messages) log.step(message);
                    log.failed("Remote disconnected", error, "Reconnect", () -> connectRemoteScreen(status));
                });
            }
        });
    }

    private void ensureRemote(java.util.function.Consumer<String> log) throws Exception {
        if (remoteConnection != null && remoteConnection.isOpen()) return;
        closeSystemRemote();
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String host = prefs.getString("remote_host", "");
        String pin = prefs.getString("remote_pin", "");
        if (host.isEmpty() || pin.isEmpty()) throw new IOException("Pair Remote first");
        boolean legacy = prefs.getBoolean("remote_legacy_identity", true);
        try {
            startRemote(host, pin, legacy, log);
        } catch (java.net.SocketException original) {
            closeSystemRemote();
            log.accept("Saved address failed. Looking for the paired TV…");
            for (String address : RemoteDiscovery.find(this).keySet()) {
                if (host.equals(address)) continue;
                try {
                    startRemote(address, pin, legacy, log);
                    prefs.edit().putString("remote_host", address).apply();
                    return;
                } catch (Exception error) {
                    closeSystemRemote();
                    original.addSuppressed(error);
                }
            }
            throw original;
        }
    }

    private void startRemote(String host, String pin, boolean legacyIdentity,
                             java.util.function.Consumer<String> log) throws Exception {
        AndroidTvRemote connection = new AndroidTvRemote(this, getNoBackupFilesDir(), host, false, pin, legacyIdentity, log);
        remoteConnection = connection;
        log.accept("TLS connected. Negotiating Remote session…");
        int generation = remoteSessionGeneration;
        connection.start(log, state -> runOnUiThread(() -> {
            if (generation != remoteSessionGeneration || !started || remoteStatus == null) return;
            if (remoteConnection == connection || (remoteConnection == null && state == null)) {
                updateRemoteIme(state);
            }
        }));
    }

    private void closeSystemRemote() {
        if (remoteConnection != null) remoteConnection.close();
        remoteConnection = null;
    }

    @Override protected void onStop() {
        exitDeadline = 0;
        if (exitMessage != null) {
            exitMessage.cancel();
            exitMessage = null;
        }
        started = false;
        if (transferRefresh != null) receiverHandler.removeCallbacks(transferRefresh);
        if (remoteStatus != null) suspendRemoteSession();
        receiverHandler.removeCallbacks(retryReceiver);
        closeReceiver();
        if (receivedNotice != null) {
            ((android.widget.FrameLayout) findViewById(android.R.id.content)).removeView(receivedNotice);
            receivedNotice = null;
        }
        super.onStop();
    }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        if (remoteStatus != null) connectRemoteScreen(remoteStatus);
        startReceiver();
        if (transferRefresh != null) receiverHandler.post(transferRefresh);
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String token = prefs.getString("link_token", "");
        String host = prefs.getString("remote_host", "");
        if (!host.isEmpty() && !token.isEmpty()) {
            io.execute(() -> syncDeviceName(host, token));
        }
    }

    @Override protected void onDestroy() {
        getPreferences(MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(deviceListener);
        if (activeSend != null) {
            activeSend.cancel();
            activeSend.dialog.dismiss();
        }
        remoteIo.getQueue().clear();
        remoteIo.execute(this::closeSystemRemote);
        remoteIo.shutdown();
        io.shutdownNow();
        transferHistory.clear();
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
        else home();
    }
}
