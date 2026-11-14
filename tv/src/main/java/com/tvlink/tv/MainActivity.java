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
    private boolean receivingScreen;
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
            if (pairing == null || receiver == null) return;
            String code = receiver.currentCode();
            pairing.setText(code.isEmpty()
                    ? "Phone paired. Ready to receive."
                    : "Pairing code: " + code);
            refresh.postDelayed(this, 500);
        }
    };

    private File received() {
        return TvSettings.directory(this);
    }

    private LinearLayout feature(String title) {
        showingHome = false;
        receivingScreen = false;
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
        receivingScreen = false;
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
        boolean paired = !getPreferences(MODE_PRIVATE).getString("transfer_token", "").isEmpty();
        Button pair = button(paired ? "Paired" : "Pair", this::showReceive);
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
        // The same flexible space goes at both edges and between cards.
        row.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 1, 1));
        for (int index = 0; index < titles.length; index++) {
            Button card = styleButton(new Button(this), titles[index], actions[index], index == 0);
            card.setCompoundDrawablesWithIntrinsicBounds(0, icons[index], 0, 0);
            card.setPadding(0, size / 4, 0, size / 7);
            if (index == 0) card.getCompoundDrawables()[1].setTint(0xff0c1c35);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            row.addView(card, params);
            row.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 1, 1));
            if (index == 0) card.requestFocus();
        }
    }

    private void showRemoteHelp() {
        LinearLayout body = feature("Remote");
        info(body, "On your phone, open TVLink → Remote. Pair with Android TV using the code shown on this TV.");
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
        receivingScreen = false;
        stopPairingDisplay();
        LinearLayout page = screen();
        Button back = button("←  Settings", this::home);
        back.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        page.addView(back, new LinearLayout.LayoutParams(-1, dp(44)));
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
            if ("receive".equals(key) && !checked) closeReceiver();
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

    private void showReceive() {
        LinearLayout body = feature("Receive Files");
        receivingScreen = true;
        if (!getPreferences(MODE_PRIVATE).getBoolean("receive", true)) {
            info(body, "Receive Transfers is disabled in Settings.");
            return;
        }
        try {
            if (receiver == null) receiver = new TransferServer(received(), getPreferences(MODE_PRIVATE));
        } catch (IOException error) {
            info(body, "Cannot start receiver: " + error.getMessage());
            return;
        }
        info(body, "TV IPv4: " + localAddresses());
        info(body, "Keep TVLink open while transferring over your trusted network.");
        pairing = new TextView(this);
        pairing.setTextSize(24);
        body.addView(pairing);
        updatePairing.run();
        body.addView(button("New pairing code", () -> {
            receiver.renewCode();
            refresh.removeCallbacks(updatePairing);
            updatePairing.run();
        }));
        body.addView(button("Forget paired phone", () -> receiver.forgetPhone()));
    }

    private String localAddresses() {
        java.util.List<String> addresses = new java.util.ArrayList<>();
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                java.net.NetworkInterface nic = interfaces.nextElement();
                if (!nic.isUp() || nic.isLoopback()) continue;
                java.util.Enumeration<java.net.InetAddress> list = nic.getInetAddresses();
                while (list.hasMoreElements()) {
                    java.net.InetAddress address = list.nextElement();
                    if (address instanceof java.net.Inet4Address) addresses.add(address.getHostAddress());
                }
            }
        } catch (IOException ignored) {}
        return addresses.isEmpty() ? "Check network settings"
                : android.text.TextUtils.join(" / ", addresses);
    }

    private void showFiles() {
        LinearLayout body = feature("My Files");
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
                "Rename", "Delete"
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
        pairing = null;
        refresh.removeCallbacks(updatePairing);
    }

    @Override protected void onStop() {
        closeReceiver();
        super.onStop();
    }

    @Override protected void onStart() {
        super.onStart();
        if (receivingScreen && receiver == null) {
            showReceive();
        } else if (receiver == null && getPreferences(MODE_PRIVATE).getBoolean("receive", true)
                && !getPreferences(MODE_PRIVATE).getString("transfer_token", "").isEmpty()) {
            try {
                receiver = new TransferServer(received(), getPreferences(MODE_PRIVATE));
            } catch (IOException error) {
                android.util.Log.w("TVLink", "Cannot resume receiver", error);
            }
        }
    }

    @Override public void onBackPressed() {
        if (showingHome) super.onBackPressed();
        else home();
    }
}
