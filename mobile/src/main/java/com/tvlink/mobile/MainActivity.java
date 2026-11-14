package com.tvlink.mobile;

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

    private boolean showingHome;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) exportName = state.getString("export_name");
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
        toolbar.addView(title, new LinearLayout.LayoutParams(-1, dp(44)));
        divider(page);
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        setting(rows, "Check for updates", "App version " + appVersion(), () -> ReleaseCheck.check(this));
        setting(rows, "Device Name", LocalSettings.device(this), () -> editSetting("Device Name", "device_name", LocalSettings.device(this)));
        setting(rows, "Transfer Directory", received().getAbsolutePath(), this::chooseDirectory);
        setting(rows, "Direct Downloads access", LocalSettings.hasStorageAccess(this) ? "Allowed" : "Optional for direct Downloads transfers", this::requestStorageAccess);
        toggle(rows, "Receive Transfers", "Accept incoming transfers from other devices", "receive", true);
        toggle(rows, "Launch at startup", "Open TVLink at boot where supported by the device", "startup", false);
        toggle(rows, "Overwrite", "Replace an existing file with the same name", "overwrite", false);
        toggle(rows, "Modify date", "Update the modified date when replacing files", "modify_date", true);
        setting(rows, "Receive Files", "Open this phone’s Wi-Fi receiver", this::receiveFiles);
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

    private void divider(LinearLayout page) {
        android.view.View line = new android.view.View(this);
        line.setBackgroundColor(0xff303c56);
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

    private void startReceiver() {
        if (receiver != null || !hasFilePairing()) return;
        try {
            receiver = new TransferServer(received(), getNoBackupFilesDir(), getPreferences(MODE_PRIVATE));
        } catch (IOException error) {
            notifyUser("Cannot start receiver: " + error.getMessage());
        }
    }

    private void receiveFiles() {
        LinearLayout body = feature("Receive Files");
        if (!hasFilePairing()) {
            body.addView(button("Pair TV", () -> pairSystemRemote(this::receiveFiles)));
        } else if (!getPreferences(MODE_PRIVATE).getBoolean("receive", true)) {
            info(body, "Receive Transfers is disabled in Settings.");
        } else {
            startReceiver();
            info(body, receiver == null ? "Could not start receiver." : "Ready to receive from your linked TV.");
        }
        body.addView(button("Received files", this::showLocalFiles));
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
        Button pair = button(hasRemotePairing() ? "Paired" : "Pair", this::pairMenu);
        header.addView(pair, new LinearLayout.LayoutParams(dp(84), dp(48)));
        Button settings = button("", this::showSettings);
        settings.setContentDescription("Settings");
        settings.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_settings, 0, 0, 0);
        settings.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams settingsSize = new LinearLayout.LayoutParams(dp(48), dp(48));
        settingsSize.setMarginStart(dp(8));
        header.addView(settings, settingsSize);

        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(0xff303c56);
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(1));
        line.setMargins(0, dp(14), 0, dp(18));
        page.addView(divider, line);

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setGravity(Gravity.CENTER_VERTICAL);
        ScrollView cards = new ScrollView(this);
        cards.setFillViewport(true);
        page.addView(cards, new LinearLayout.LayoutParams(-1, 0, 1));
        cards.addView(menu);
        String[] titles = {"Send", "Remote", "Files"};
        Runnable[] actions = {this::openSend, this::openRemote, this::showFiles};
        int[] icons = {R.drawable.ic_send, R.drawable.ic_remote, R.drawable.ic_files};
        for (int index = 0; index < titles.length; index++) {
            String title = titles[index];
            boolean primary = index == 0;
            Button item = styleButton(new Button(this), title, actions[index], primary);
            item.setCompoundDrawablesWithIntrinsicBounds(0, icons[index], 0, 0);
            item.setCompoundDrawablePadding(dp(8));
            item.setPadding(dp(2), dp(10), dp(2), dp(10));
            item.setSingleLine(true);
            item.setTextSize(16);
            if (primary) item.getCompoundDrawables()[1].setTint(0xff0c1c35);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(100));
            if (index > 0) params.topMargin = dp(20);
            menu.addView(item, params);
        }
    }

    private final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean busy;

    private LinearLayout feature(String title) {
        showingHome = false;
        LinearLayout page = screen(title);
        page.addView(button("Back", this::home));
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

    private void pairMenu() {
        if (!hasRemotePairing()) {
            pairSystemRemote(this::home, false);
            return;
        }
        new AlertDialog.Builder(this, R.style.TVLinkPairingDialog).setTitle("Linked TV")
                .setMessage(hasFilePairing() ? "Remote and file transfers connected."
                        : "Remote paired · File transfer not connected")
                .setNegativeButton("Close", null)
                .setPositiveButton(hasFilePairing() ? "Change TV" : "Connect file transfer",
                        (dialog, choice) -> pairSystemRemote(this::home, !hasFilePairing()))
                .show();
    }

    private void openSend() {
        if (hasFilePairing()) showSend();
        else pairSystemRemote(this::showSend);
    }

    private void openRemote() {
        if (hasRemotePairing()) showRemote();
        else pairSystemRemote(this::showRemote, false);
    }

    private void showSend() {
        LinearLayout body = feature("Send");
        body.addView(button("Choose file to send", this::chooseFileToSend));
        body.addView(button("Received files", this::showLocalFiles));
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
        android.net.Uri uri = data.getData();
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

    private void finishSend(SendSession session, String outcome) {
        runOnUiThread(() -> {
            if (activeSend == session) activeSend = null;
            session.dialog.dismiss();
            if (!isFinishing() && !isDestroyed()) notifyUser(outcome);
        });
    }

    private void sendToPeer(SendSession session, String host, String token, String name, long size, InputStream stream)
            throws IOException {
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
        LinearLayout body = feature("Files");
        body.addView(button("Receive Files", this::receiveFiles));
        body.addView(button("Received files", this::showLocalFiles));
        body.addView(button("Files on TV", () -> {
            if (hasFilePairing()) showTvFiles();
            else pairSystemRemote(this::showTvFiles);
        }));
    }

    private void showLocalFiles() {
        LinearLayout body = feature("Received Files");
        if (hasFilePairing()) body.addView(button("Choose file to send", this::chooseFileToSend));
        body.addView(button("Refresh", this::showLocalFiles));
        File[] files = received().listFiles(file -> file.isFile() && !file.getName().startsWith("."));
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
                    showLocalFiles();
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
        if (busy) return;
        LinearLayout body = feature("Files on TV");
        body.addView(button("Refresh", this::showTvFiles));
        work(() -> {
            try (TransferClient client = client("LIST")) {
                java.util.List<String> files = client.list();
                java.util.Collections.sort(files, String.CASE_INSENSITIVE_ORDER);
                runOnUiThread(() -> {
                    if (files.isEmpty()) info(body, "No files received yet");
                    for (String name : files) body.addView(button(name, () -> fileActions(name)));
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
                        new AlertDialog.Builder(this).setTitle("Rename").setView(input)
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Save", (d, w) ->
                                        changeFile("RENAME", name, input.getText().toString().trim())).show();
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
            if (!showingHome) showTvFiles();
        });
    }

    private final java.util.concurrent.ThreadPoolExecutor remoteIo =
            new java.util.concurrent.ThreadPoolExecutor(1, 1, 0, java.util.concurrent.TimeUnit.SECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(16),
                    new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
    private AndroidTvRemote remoteConnection;
    private boolean remotePairing;

    private void showRemote() {
        LinearLayout body = feature("Remote");
        TextView status = info(body, "Connecting to Android TV Remote Service…");
        remoteStatus = status;
        body.addView(button("Reconnect", () -> connectRemoteScreen(status)));
        connectRemoteScreen(status);
        remoteRow(body, new String[] {"↑"}, new int[] {19});
        remoteRow(body, new String[] {"←", "OK", "→"}, new int[] {21, 23, 22});
        remoteRow(body, new String[] {"↓"}, new int[] {20});
        remoteRow(body, new String[] {"Back", "Home"}, new int[] {4, 3});
    }

    private void pairSystemRemote() {
        pairSystemRemote(() -> {}, false);
    }

    private void pairSystemRemote(Runnable paired) {
        pairSystemRemote(paired, true);
    }

    private void pairSystemRemote(Runnable paired, boolean linkFiles) {
        if (remotePairing || busy) return;
        new AlertDialog.Builder(this, R.style.TVLinkPairingDialog).setTitle("Pair TV")
                .setMessage(linkFiles ? "Open Pair in TVLink on your TV. Keep both devices on the same Wi-Fi."
                        : "Keep your Android TV on and connected to the same network.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Find TV", (dialog, choice) -> findPairingTv(paired, linkFiles))
                .show();
    }

    private void findPairingTv(Runnable paired, boolean linkFiles) {
        if (remotePairing || isFinishing() || isDestroyed()) return;
        remotePairing = true;
        PairingLog searching = pairingProgress("Searching for TV…");
        remoteIo.execute(() -> {
            try {
                android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
                String pendingHost = prefs.getString("pending_file_host", "");
                String pendingToken = prefs.getString("pending_file_token", "");
                if (linkFiles && !pendingToken.isEmpty()) searching.step("Checking saved file link…");
                if (linkFiles && !pendingToken.isEmpty() && pendingHost.equals(prefs.getString("remote_host", ""))
                        && confirmLink(pendingHost, pendingToken)) {
                    finishLink(pendingHost, searching);
                    runOnUiThread(searching::dismiss);
                    showLinkComplete(paired);
                    return;
                }
                java.util.Map<String, String> devices = linkFiles ? LinkNetwork.findPairingTvs()
                        : RemoteDiscovery.find(this);
                if (!linkFiles && devices.isEmpty()) {
                    searching.step("No Remote service found. Searching for TVLink on Wi-Fi…");
                    devices = LinkNetwork.findPairingTvs();
                }
                java.util.Map<String, String> discovered = devices;
                runOnUiThread(() -> {
                    searching.dismiss();
                    remotePairing = false;
                    if (isFinishing() || isDestroyed()) return;
                    if (discovered.isEmpty()) {
                        new AlertDialog.Builder(this, R.style.TVLinkPairingDialog).setTitle("No TV found")
                                .setMessage(linkFiles ? "Open Pair on your TV and check that both devices are on the same Wi-Fi."
                                        : "Keep Android TV on and on the same network. You can also open Pair in TVLink on the TV.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Try again", (dialog, choice) -> findPairingTv(paired, linkFiles))
                                .show();
                    } else if (discovered.size() == 1) {
                        connectForPairing(discovered.keySet().iterator().next(), paired, linkFiles);
                    } else {
                        String[] hosts = discovered.keySet().toArray(new String[0]);
                        String[] names = discovered.values().toArray(new String[0]);
                        new AlertDialog.Builder(this, R.style.TVLinkPairingDialog).setTitle("Choose your TV")
                                .setItems(names, (dialog, choice) -> connectForPairing(hosts[choice], paired, linkFiles))
                                .setNegativeButton("Cancel", null).show();
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    searching.dismiss();
                    remotePairing = false;
                    if (!isFinishing() && !isDestroyed()) searching.failed("Could not find or link TV",
                            error, () -> findPairingTv(paired, linkFiles));
                });
            }
        });
    }

    private void connectForPairing(String host, Runnable paired, boolean linkFiles) {
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
                        if (linkFiles) {
                            saveRemotePairing(host, previousPin, legacy);
                            connecting.step("Using saved Remote pairing. Connecting file transfer…");
                            finishLink(host, connecting);
                        } else {
                            try (AndroidTvRemote remote = new AndroidTvRemote(
                                    this, getNoBackupFilesDir(), host, false, previousPin, legacy, connecting::step)) {
                                remote.start();
                            } catch (Exception error) {
                                throw new RemoteUnavailable(error);
                            }
                            saveRemotePairing(host, previousPin, legacy);
                        }
                        reportPairingStatus(host, "PAIRED");
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
                    enterRemoteCode(pending, host, paired, linkFiles);
                });
            } catch (Exception error) {
                if (pairing != null) pairing.close();
                reportPairingStatus(host, "FAILED");
                android.util.Log.w("TVLink", "TV pairing failed before entering code", error);
                runOnUiThread(() -> {
                    connecting.dismiss();
                    remotePairing = false;
                    if (isFinishing() || isDestroyed()) return;
                    connecting.failed(linkFiles ? "Could not connect file transfer" : "Could not start Android TV pairing",
                            error, () -> findPairingTv(paired, linkFiles));
                });
            }
        });
    }

    private void enterRemoteCode(AndroidTvRemote pairing, String host, Runnable paired, boolean linkFiles) {
        if (isFinishing() || isDestroyed()) {
            pairing.close();
            return;
        }
        EditText code = new EditText(this);
        code.setSingleLine(true);
        code.setHint("······");
        code.setTextSize(27);
        code.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        code.setLetterSpacing(0.24f);
        code.setGravity(android.view.Gravity.CENTER);
        code.setContentDescription("Six-character Android TV pairing code");
        code.setTextColor(0xfff4f6ff);
        code.setHintTextColor(0xffc0cce4);
        android.graphics.drawable.GradientDrawable field = new android.graphics.drawable.GradientDrawable();
        field.setColor(0xff0e1728);
        field.setCornerRadius(dp(12));
        field.setStroke(dp(1), 0xff60769f);
        code.setBackground(field);
        LinearLayout codeContainer = new LinearLayout(this);
        codeContainer.setPadding(dp(24), dp(12), dp(24), dp(8));
        codeContainer.addView(code, new LinearLayout.LayoutParams(-1, dp(62)));
        code.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.AllCaps(),
                new android.text.InputFilter.LengthFilter(6)});
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.TVLinkPairingDialog).setTitle("Pair with Android TV")
                .setMessage("Enter the 6-character code on your TV.")
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
                new AlertDialog.Builder(this, R.style.TVLinkPairingDialog).setTitle("Pairing code timed out")
                        .setNegativeButton("Close", null)
                        .setPositiveButton("Try again", (d, w) -> findPairingTv(paired, linkFiles)).show();
            }
        };
        dialog.setOnDismissListener(ignored -> {
            timer.removeCallbacks(expire);
            pairing.close();
            remotePairing = false;
            if (!isDestroyed()) remoteIo.execute(() -> reportPairingStatus(host, "CANCELLED"));
        });
        dialog.show();
        timer.postDelayed(expire, 90000);
        code.requestFocus();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        Button pairButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        pairButton.setEnabled(false);
        code.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                pairButton.setEnabled(text.toString().matches("[0-9A-Fa-f]{6}"));
            }
            public void afterTextChanged(android.text.Editable text) {}
        });
        pairButton.setOnClickListener(view -> {
            String value = code.getText().toString().trim();
            if (!value.matches("[0-9a-fA-F]{6}")) {
                dialog.setMessage("The code needs 6 characters. You entered " + value.length()
                        + ". Include any letters A–F shown on the TV.");
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
                    saveRemotePairing(host, pin, false);
                    remoteSaved = true;
                    reportPairingStatus(host, "PAIRED");
                    if (linkFiles) {
                        progress.step("Remote paired. Connecting file transfer…");
                        finishLink(host, progress);
                    }
                    runOnUiThread(progress::dismiss);
                    showLinkComplete(paired);
                } catch (Exception error) {
                    boolean pairedSuccessfully = remoteSaved;
                    if (!pairedSuccessfully) reportPairingStatus(host, "FAILED");
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        progress.dismiss();
                        progress.failed(pairedSuccessfully ? "Remote paired · File transfer not connected"
                                : "Could not finish pairing", error, () -> findPairingTv(paired, linkFiles));
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

    private PairingLog pairingProgress(String status) {
        PairingLog dialog = new PairingLog();
        dialog.step(status);
        dialog.show();
        return dialog;
    }

    private final class PairingLog extends AlertDialog {
        private final long start = android.os.SystemClock.elapsedRealtime();
        private final StringBuilder entries = new StringBuilder();
        private final TextView output = new TextView(MainActivity.this);
        private final ScrollView scroll = new ScrollView(MainActivity.this);

        private boolean closedByUser;

        PairingLog() {
            super(MainActivity.this, R.style.TVLinkPairingDialog);
            setTitle("Connecting");
            setCancelable(false);
            setButton(BUTTON_NEGATIVE, "Close", (dialog, which) -> closedByUser = true);
            setButton(BUTTON_NEUTRAL, "Copy log", (dialog, which) -> {});
            output.setTypeface(android.graphics.Typeface.MONOSPACE);
            output.setTextColor(0xffe4ecff);
            output.setPadding(dp(24), dp(8), dp(24), dp(8));
            scroll.addView(output);
            LinearLayout container = new LinearLayout(MainActivity.this);
            container.addView(scroll, new LinearLayout.LayoutParams(-1, dp(160)));
            setView(container);
        }

        @Override public void show() {
            if (closedByUser) return;
            super.show();
            getButton(BUTTON_NEUTRAL).setOnClickListener(view -> copyLog());
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
                output.setText(entries);
                scroll.post(() -> scroll.fullScroll(android.view.View.FOCUS_DOWN));
            });
        }

        void failed(String title, Exception error, Runnable retry) {
            String detail = error.getMessage();
            step("Failed: " + (detail == null ? error.getClass().getSimpleName() : detail));
            dismiss();
            new AlertDialog.Builder(MainActivity.this, R.style.TVLinkPairingDialog)
                    .setTitle(title).setMessage(entries.toString())
                    .setNegativeButton("Close", null)
                    .setNeutralButton("Copy log", null)
                    .setPositiveButton("Try again", (d, w) -> retry.run()).show()
                    .getButton(BUTTON_NEUTRAL).setOnClickListener(view -> copyLog());
        }
    }

    private void reportPairingStatus(String host, String status) {
        // Status is advisory and must never block system pairing.
        LinkNetwork.sendPairingStatus(host, status);
    }

    private void saveRemotePairing(String host, String pin, boolean legacyIdentity) throws IOException {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        android.content.SharedPreferences.Editor edit = prefs.edit()
                .putString("remote_host", host).putString("remote_pin", pin)
                .putBoolean("remote_legacy_identity", legacyIdentity);
        if (!host.equals(prefs.getString("remote_host", ""))) edit.remove("link_token");
        if (!edit.commit()) throw new IOException("Could not save Remote pairing on this phone");
        closeSystemRemote();
    }

    private static final class RemoteUnavailable extends IOException {
        RemoteUnavailable(Exception cause) { super(cause.getMessage(), cause); }
    }

    private void finishLink(String host, PairingLog progress) throws Exception {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String token = host.equals(prefs.getString("pending_file_host", ""))
                ? prefs.getString("pending_file_token", "") : "";
        if (token.isEmpty() || !confirmLink(host, token)) {
            java.security.KeyPair key = FilePairing.keyPair();
            String clientKey = FilePairing.encode(key.getPublic().getEncoded());
            progress.step("Opening encrypted connection to " + host + ":47832…");
            try (TransferClient client = new TransferClient(host, "PAIR_FILES", FilePairing.commitment(clientKey))) {
                progress.step("TLS connected. Waiting for TV pairing key…");
                FilePairing.Result link = client.exchangeFileKeys(key, clientKey);
                progress.step("Keys exchanged. Confirm the matching number on both screens.");
                if (!prefs.edit().putString("pending_file_host", host)
                        .putString("pending_file_token", link.token).commit()) {
                    throw new IOException("Could not save pending file pairing");
                }
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
                progress.step("Phone approved. Waiting for Allow on TV…");
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
            notifyUser(hasFilePairing() ? "Remote and file transfers connected"
                    : "Remote paired · File transfer not connected");
            paired.run();
        });
    }

    private void remoteRow(LinearLayout body, String[] names, int[] keys) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        body.addView(row);
        for (int index = 0; index < names.length; index++) {
            int key = keys[index];
            Button control = button(names[index], () -> sendSystemKey(key));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(76), dp(52));
            params.setMargins(dp(3), dp(3), dp(3), dp(3));
            row.addView(control, params);
        }
    }

    private TextView remoteStatus;
    private boolean remoteConnecting;

    private void connectRemoteScreen(TextView status) {
        if (remoteConnecting || remotePairing) return;
        remoteConnecting = true;
        PairingLog progress = pairingProgress("Opening Remote session…");
        remoteIo.execute(() -> {
            Exception failure = null;
            try {
                ensureRemote(progress::step);
            } catch (Exception error) {
                closeSystemRemote();
                failure = error;
            }
            Exception result = failure;
            runOnUiThread(() -> {
                remoteConnecting = false;
                progress.dismiss();
                if (isFinishing() || isDestroyed()) return;
                if (result == null) {
                    status.setText("Remote connected");
                } else {
                    status.setText("Remote disconnected. Tap Reconnect.");
                    progress.failed("Remote disconnected", result, () -> connectRemoteScreen(status));
                }
            });
        });
    }

    private void sendSystemKey(int key) {
        if (remotePairing || remoteConnecting) return;
        TextView status = remoteStatus;
        remoteIo.execute(() -> {
            try {
                ensureRemote(message -> runOnUiThread(() -> status.setText(message)));
                remoteConnection.key(key);
                runOnUiThread(() -> status.setText("Remote connected · Key sent"));
            } catch (Exception error) {
                closeSystemRemote();
                String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                runOnUiThread(() -> status.setText("Remote disconnected: " + detail));
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
        remoteConnection = new AndroidTvRemote(this, getNoBackupFilesDir(), host, false, pin, legacyIdentity, log);
        log.accept("TLS connected. Negotiating Remote session…");
        remoteConnection.start(log);
    }

    private void closeSystemRemote() {
        if (remoteConnection != null) remoteConnection.close();
        remoteConnection = null;
    }

    @Override protected void onStop() {
        started = false;
        closeReceiver();
        super.onStop();
    }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        startReceiver();
    }

    @Override protected void onDestroy() {
        if (activeSend != null) {
            activeSend.cancel();
            activeSend.dialog.dismiss();
        }
        remoteIo.getQueue().clear();
        remoteIo.execute(this::closeSystemRemote);
        remoteIo.shutdown();
        io.shutdownNow();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (showingHome) super.onBackPressed();
        else home();
    }
}
