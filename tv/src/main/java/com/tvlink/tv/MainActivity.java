package com.tvlink.tv;

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
    private boolean showingHome;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        home();
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private LinearLayout screen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(24), dp(24), dp(24));
        page.setBackgroundColor(0xff101624);
        setContentView(page);
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
        button.setTextSize(18);
        button.setGravity(Gravity.CENTER);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_focused}, tile(primary ? 0xff9ac2ff : 0xff263a5a, true));
        background.addState(new int[] {}, tile(primary ? 0xff9ac2ff : 0xff1b2740, false));
        button.setBackground(background);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private TransferServer receiver;
    private TextView pairing;
    private final android.os.Handler refresh = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable updatePairing = new Runnable() {
        @Override public void run() {
            if (pairing == null) return;
            if (isLinked()) {
                showReceive();
            } else {
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

    private LinearLayout feature(String title) {
        showingHome = false;
        stopPairingDisplay();
        LinearLayout page = screen();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(38)));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.tvlink_wordmark);
        logo.setContentDescription("TVLink");
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(logo, new LinearLayout.LayoutParams(dp(116), -1));
        header.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 1, 1));
        TextView device = new TextView(this);
        device.setText(TvSettings.device(this));
        device.setTextColor(0xffb2c2dd);
        header.addView(device);
        divider(page);

        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(24);
        heading.setTextColor(Color.WHITE);
        heading.setPadding(0, dp(8), 0, dp(4));
        body.addView(heading);

        if ("Pair".equals(title)) {
            page.setFocusableInTouchMode(true);
            page.requestFocus();
            return body;
        }

        divider(page);
        LinearLayout tabs = new LinearLayout(this);
        page.addView(tabs);
        boolean receiveSelected = "Receive Files".equals(title);
        Button receive = styleButton(new Button(this), "Receive Files", this::showReceive, receiveSelected);
        Button files = styleButton(new Button(this), "My Files", this::showFiles, !receiveSelected);
        tabs.addView(receive, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(44), 1);
        right.setMarginStart(dp(10));
        tabs.addView(files, right);
        (receiveSelected ? receive : files).requestFocus();
        return body;
    }

    private void home() {
        showingHome = true;
        stopPairingDisplay();
        LinearLayout page = screen();
        page.setPadding(0, 0, 0, 0);
        android.widget.FrameLayout layout = new android.widget.FrameLayout(this);
        page.addView(layout, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.FrameLayout.LayoutParams headerSize =
                new android.widget.FrameLayout.LayoutParams(-1, dp(44), Gravity.TOP);
        headerSize.setMargins(dp(24), dp(24), dp(24), 0);
        layout.addView(header, headerSize);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.tvlink_wordmark);
        logo.setContentDescription("TVLink");
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(logo, new LinearLayout.LayoutParams(dp(116), dp(40)));
        header.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 1, 1));
        Button pair = button(isLinked() ? "Paired" : "Pair", this::showLinkStatus);
        pair.setSingleLine(true);
        pair.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(pair, new LinearLayout.LayoutParams(-2, dp(44)));
        Button gear = button("", this::showSettings);
        gear.setContentDescription("Settings");
        gear.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_settings, 0, 0);
        gear.setPadding(0, dp(10), 0, 0);
        LinearLayout.LayoutParams gearSize = new LinearLayout.LayoutParams(dp(44), dp(44));
        gearSize.setMarginStart(dp(8));
        header.addView(gear, gearSize);
        LinearLayout row = new LinearLayout(this);
        layout.addView(row, new android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int size = Math.min(dp(170), Math.min((metrics.widthPixels - dp(96)) / 3,
                metrics.heightPixels - dp(152)));
        String[] titles = {"Receive", "Remote", "Files"};
        int[] icons = {R.drawable.ic_send, R.drawable.ic_remote, R.drawable.ic_files};
        Runnable[] actions = {this::showReceive, this::showRemoteHelp, this::showFiles};
        // Keep equal outer margins while bringing the cards closer together.
        row.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 1, 2));
        for (int index = 0; index < titles.length; index++) {
            Button card = styleButton(new Button(this), titles[index], actions[index], index == 0);
            card.setCompoundDrawablesWithIntrinsicBounds(0, icons[index], 0, 0);
            card.setPadding(0, size / 4, 0, size / 7);
            if (index == 0) card.getCompoundDrawables()[1].setTint(0xff0c1c35);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            row.addView(card, params);
            row.addView(new android.view.View(this),
                    new LinearLayout.LayoutParams(0, 1, index == titles.length - 1 ? 2 : 1));
            if (index == 0) card.requestFocus();
        }
    }

    private void showRemoteHelp() {
        LinearLayout body = feature("Remote");
        info(body, "Use Remote in TVLink on your phone to control this TV.");
        if (!isLinked()) body.addView(button("Pair", this::showLinkStatus));
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
        stopPairingDisplay();
        LinearLayout page = screen();
        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextColor(Color.WHITE);
        title.setTextSize(23);
        title.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(title, new LinearLayout.LayoutParams(-1, dp(44)));
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        setting(rows, "Check for updates", "App version " + appVersion(), () -> ReleaseCheck.check(this));
        setting(rows, "Device Name", TvSettings.device(this), () -> editSetting("Device Name", "device_name", TvSettings.device(this)));
        setting(rows, "Transfer Directory", received().getAbsolutePath(), this::chooseDirectory);
        setting(rows, "Allow access all files", TvSettings.hasStorageAccess(this) ? "Allowed" : "Manage storage access", this::requestStorageAccess);
        toggle(rows, "Receive Transfers", "Accept incoming transfers from other devices", "receive", true);
        toggle(rows, "Launch at startup", "Open TVLink at boot where supported by the TV", "startup", false);
        toggle(rows, "Overwrite", "Replace an existing file with the same name", "overwrite", false);
        toggle(rows, "Modify date", "Update the modified date when replacing files", "modify_date", true);
        rows.getChildAt(0).requestFocus();
    }

    private void setting(LinearLayout rows, String title, String detail, Runnable action) {
        Button item = button(title + "\n" + detail, action);
        item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        item.setPadding(dp(14), dp(8), dp(14), dp(8));
        rows.addView(item, new LinearLayout.LayoutParams(-1, dp(62)));
        divider(rows);
    }

    private void toggle(LinearLayout rows, String title, String detail, String key, boolean fallback) {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        android.widget.CheckBox box = new android.widget.CheckBox(this);
        box.setText(title + "\n" + detail);
        box.setBackground(tile(0xff1b2740, false));
        box.setPadding(dp(12), 0, dp(12), 0);
        box.setChecked(prefs.getBoolean(key, fallback));
        box.setOnFocusChangeListener((view, focused) -> box.setBackground(tile(focused ? 0xff263a5a : 0xff1b2740, focused)));
        box.setOnCheckedChangeListener((view, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
        });
        rows.addView(box, new LinearLayout.LayoutParams(-1, dp(62)));
        divider(rows);
    }

    private void editSetting(String title, String key, String initial) {
        EditText value = new EditText(this);
        value.setSingleLine(true);
        value.setText(initial);
        new AlertDialog.Builder(this).setTitle(title).setView(value).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String input = value.getText().toString().trim();
                    if (input.isEmpty() || input.length() > 40) {
                        notifyUser("Enter a device name of 1–40 characters.");
                        return;
                    }
                    getPreferences(MODE_PRIVATE).edit().putString(key, input).apply();
                    showSettings();
                }).show();
    }

    private void chooseDirectory() {
        new AlertDialog.Builder(this).setTitle("Transfer Directory")
                .setItems(new String[] {"TVLink app folder", "Downloads"}, (dialog, index) -> {
                    if (index == 1 && !TvSettings.hasStorageAccess(this)) {
                        notifyUser("Allow storage access first, then choose Downloads.");
                        return;
                    }
                    closeReceiver();
                    getPreferences(MODE_PRIVATE).edit().putBoolean("downloads", index == 1).apply();
                    if (isLinked()) startReceiver();
                    showSettings();
                }).setNegativeButton("Cancel", null).show();
    }

    private void requestStorageAccess() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (android.content.ActivityNotFoundException error) {
                notifyUser("This TV does not expose the all-files access screen.");
            }
        } else {
            requestPermissions(new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 81);
        }
    }

    private void divider(LinearLayout page) {
        android.view.View line = new android.view.View(this);
        line.setBackgroundColor(0xff303c56);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.setMargins(0, dp(8), 0, dp(8));
        page.addView(line, params);
    }

    private void info(LinearLayout body, String value) {
        TextView text = new TextView(this);
        text.setPadding(0, dp(8), 0, dp(8));
        text.setText(value);
        body.addView(text);
    }

    private void closeReceiver() {
        if (receiver != null) receiver.close();
        receiver = null;
        stopPairingDisplay();
    }

    private boolean startReceiver() {
        if (receiver != null) return true;
        try {
            receiver = new TransferServer(received(), getPreferences(MODE_PRIVATE));
            return true;
        } catch (IOException error) {
            notifyUser("Cannot start receiver: " + error.getMessage());
            return false;
        }
    }

    private void showLinkStatus() {
        LinearLayout body = feature("Pair");
        if (isLinked()) {
            info(body, "Phone linked for Remote and file transfers.");
            body.addView(button("Forget link", () -> new AlertDialog.Builder(this).setTitle("Forget link?")
                    .setMessage("To revoke system remote access as well, remove TVLink in Android TV's remote settings.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Forget", (dialog, which) -> {
                        getPreferences(MODE_PRIVATE).edit().remove("link_token").remove("peer_host").apply();
                        showLinkStatus();
                    }).show()));
            return;
        }
        startPairing(body);
    }

    private void startPairing(LinearLayout body) {
        if (!startReceiver()) return;
        info(body, "On your phone, tap Pair → Find TV. Keep this TV screen open.");
        pairing = new TextView(this);
        pairing.setText("Enter the code on your phone when Android TV shows it.");
        body.addView(pairing);
        receiver.enableLinking(true);
        refresh.postDelayed(updatePairing, 400);
        refresh.postDelayed(expireLink, 180000);
    }

    private final Runnable expireLink = () -> {
        if (receiver != null) receiver.enableLinking(false);
        if (pairing != null) pairing.setText("Pairing timed out. Reopen Pair to try again.");
    };

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (pairing != null && receiver != null && hasWindowFocus()) {
            int digit = linkDigit(event.getKeyCode());
            if (digit != -2) {
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    receiver.linkKey(digit);
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static int linkDigit(int key) {
        switch (key) {
            case android.view.KeyEvent.KEYCODE_DPAD_CENTER: return -1;
            case android.view.KeyEvent.KEYCODE_DPAD_UP: return 0;
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN: return 1;
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT: return 2;
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT: return 3;
            default: return -2;
        }
    }

    private void showReceive() {
        LinearLayout body = feature("Receive Files");
        if (!getPreferences(MODE_PRIVATE).getBoolean("receive", true)) {
            info(body, "Receive Transfers is disabled in Settings.");
        } else if (!isLinked()) {
            startPairing(body);
        } else {
            info(body, startReceiver() ? "Ready to receive from your linked phone." : "Could not start receiver.");
        }
    }

    private void sendFile(File file) {
        if (!isLinked()) {
            showLinkStatus();
            return;
        }
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String host = prefs.getString("peer_host", "");
        String token = prefs.getString("link_token", "");
        queueSend(() -> {
            try (FileInputStream stream = new FileInputStream(file)) {
                sendToPhone(host, token, file.getName(), stream.getChannel().size(), stream);
            }
        });
    }

    private interface SendWork { void run() throws IOException; }

    private void queueSend(SendWork work) {
        sender.execute(() -> {
            try {
                work.run();
            } catch (IOException | SecurityException error) {
                runOnUiThread(() -> notifyUser(error.getMessage()));
            }
        });
        notifyUser("Sending file…");
    }

    private void sendToPhone(String host, String token, String name, long size, InputStream stream)
            throws IOException {
        if (stream == null) throw new IOException("Cannot read file");
        try (TransferClient client = new TransferClient(LinkNetwork.resolve(host, token), "SEND", token)) {
            client.send(name, size, stream, percent -> {});
        }
        runOnUiThread(() -> notifyUser("Sent: " + name));
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
        queueSend(() -> {
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
                sendToPhone(host, token, name, size, stream);
            }
        });
    }

    private void showFiles() {
        LinearLayout body = feature("My Files");
        body.addView(button("Choose file to send", this::chooseFileToSend));
        body.addView(button("Refresh", this::showFiles));
        File[] files = received().listFiles(f -> f.isFile() && !f.getName().startsWith("."));
        if (files == null || files.length == 0) {
            info(body, "No files received yet");
            return;
        }
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : files) body.addView(button(file.getName(), () -> fileActions(file)));
    }

    private void fileActions(File file) {
        boolean apk = file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".apk");
        new AlertDialog.Builder(this).setTitle(file.getName()).setItems(new String[] {
                apk ? "Install APK" : "Open",
                "Rename", "Delete", "Send to phone"
        }, (d, choice) -> {
            switch (choice) {
                case 0: openFile(file, apk); break;
                case 1: renameFile(file); break;
                case 2:
                    new AlertDialog.Builder(this).setTitle("Delete file?")
                            .setMessage(file.getName()).setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", (dialog, which) -> {
                                if (!file.delete()) notifyUser("Could not delete file");
                                showFiles();
                            }).show();
                    break;
                case 3: sendFile(file); break;
            }
        }).show();
    }

    private void renameFile(File file) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(file.getName());
        new AlertDialog.Builder(this).setTitle("Rename").setView(input)
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
                    showFiles();
                }).show();
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
                    .authority("com.tvlink.tv.files").appendPath(file.getName()).build();
            String type = apk ? "application/vnd.android.package-archive" : getContentResolver().getType(uri);
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (android.content.ActivityNotFoundException | SecurityException error) {
            notifyUser("No compatible app or installer available");
        }
    }

    private void notifyUser(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void stopPairingDisplay() {
        if (pairing != null && receiver != null) receiver.enableLinking(false);
        pairing = null;
        refresh.removeCallbacks(updatePairing);
        refresh.removeCallbacks(expireLink);
    }

    @Override protected void onStop() {
        // Android TV's pairing dialog can briefly cover this activity.
        if (pairing == null) closeReceiver();
        super.onStop();
    }

    @Override protected void onStart() {
        super.onStart();
        if (isLinked()) startReceiver();
    }

    @Override protected void onDestroy() {
        closeReceiver();
        sender.shutdownNow();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (showingHome) super.onBackPressed();
        else home();
    }
}
