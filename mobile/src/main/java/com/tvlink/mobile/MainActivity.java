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
        setting(rows, "Allow access all files", LocalSettings.hasStorageAccess(this) ? "Allowed" : "Manage storage access", this::requestStorageAccess);
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
            receiver = new TransferServer(received(), getPreferences(MODE_PRIVATE));
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
        Button pair = button(hasFilePairing() && hasRemotePairing() ? "Paired" : "Pair", this::pairMenu);
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
        if (!hasFilePairing()) {
            pairSystemRemote(this::home);
            return;
        }
        new AlertDialog.Builder(this).setTitle("Linked TV")
                .setMessage("This link is used for Remote and file transfers.")
                .setNegativeButton("Close", null)
                .setPositiveButton("Change TV", (dialog, choice) -> pairSystemRemote(this::home))
                .show();
    }

    private void openSend() {
        if (hasFilePairing()) showSend();
        else pairSystemRemote(this::showSend);
    }

    private void openRemote() {
        if (hasFilePairing() && hasRemotePairing()) showRemote();
        else pairSystemRemote(this::showRemote);
    }

    private void showSend() {
        LinearLayout body = feature("Send");
        body.addView(button("Choose file", () -> {
            if (busy) return;
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*");
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(picker, 10);
        }));
    }

    private TransferClient client(String operation) throws IOException {
        android.content.SharedPreferences settings = getPreferences(MODE_PRIVATE);
        String host = settings.getString("remote_host", "");
        String token = settings.getString("link_token", "");
        if (token.isEmpty()) throw new IOException("Pair with the TV first");
        return new TransferClient(LinkNetwork.resolve(host, token), operation, token);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 10 || result != RESULT_OK || data == null || data.getData() == null) return;
        android.net.Uri uri = data.getData();
        LinearLayout body = feature("Send");
        TextView progress = info(body, "Preparing file…");
        work(() -> {
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
            try (InputStream stream = getContentResolver().openInputStream(uri);
                 TransferClient client = client("SEND")) {
                if (stream == null) throw new IOException("Cannot read file");
                client.send(name, size, stream, percent -> runOnUiThread(() ->
                        progress.setText(name + " · " + percent + "%")));
            }
            runOnUiThread(() -> progress.setText("Sent: " + name));
        });
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
        File[] files = received().listFiles(file -> file.isFile() && !file.getName().startsWith("."));
        if (files == null || files.length == 0) {
            info(body, "No files received yet");
            return;
        }
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : files) body.addView(button(file.getName(), () -> {
            try {
                android.net.Uri uri = new android.net.Uri.Builder().scheme("content")
                        .authority(getPackageName() + ".received").appendPath(file.getName()).build();
                String type = getContentResolver().getType(uri);
                startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type == null ? "*/*" : type)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            } catch (RuntimeException error) {
                notifyUser("No app can open this file");
            }
        }));
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
        info(body, "Connect to Android TV Remote Service over Wi-Fi.");
        remoteRow(body, new String[] {"↑"}, new int[] {19});
        remoteRow(body, new String[] {"←", "OK", "→"}, new int[] {21, 23, 22});
        remoteRow(body, new String[] {"↓"}, new int[] {20});
        remoteRow(body, new String[] {"Back", "Home"}, new int[] {4, 3});
    }

    private void pairSystemRemote() {
        pairSystemRemote(() -> {});
    }

    private void pairSystemRemote(Runnable paired) {
        if (remotePairing || busy) return;
        new AlertDialog.Builder(this).setTitle("Pair TV")
                .setMessage("Open Pair in TVLink on your TV. Keep both devices on the same Wi-Fi.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Find TV", (dialog, choice) -> findPairingTv(paired))
                .show();
    }

    private void findPairingTv(Runnable paired) {
        if (remotePairing || isFinishing() || isDestroyed()) return;
        remotePairing = true;
        notifyUser("Looking for your TV…");
        remoteIo.execute(() -> {
            try {
                java.util.Map<String, String> devices = LinkNetwork.findPairingTvs();
                runOnUiThread(() -> {
                    remotePairing = false;
                    if (isFinishing() || isDestroyed()) return;
                    if (devices.isEmpty()) {
                        new AlertDialog.Builder(this).setTitle("No TV found")
                                .setMessage("Open Pair on your TV and check that both devices are on the same Wi-Fi.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Try again", (dialog, choice) -> findPairingTv(paired))
                                .show();
                    } else if (devices.size() == 1) {
                        connectForPairing(devices.keySet().iterator().next(), paired);
                    } else {
                        String[] hosts = devices.keySet().toArray(new String[0]);
                        String[] names = devices.values().toArray(new String[0]);
                        new AlertDialog.Builder(this).setTitle("Choose your TV")
                                .setItems(names, (dialog, choice) -> connectForPairing(hosts[choice], paired))
                                .setNegativeButton("Cancel", null).show();
                    }
                });
            } catch (IOException error) {
                runOnUiThread(() -> {
                    remotePairing = false;
                    notifyUser("Could not search your Wi-Fi for a TV.");
                });
            }
        });
    }

    private void connectForPairing(String host, Runnable paired) {
        if (remotePairing || isFinishing() || isDestroyed()) return;
        remotePairing = true;
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        remoteIo.execute(() -> {
            AndroidTvRemote pairing = null;
            try {
                String previousHost = prefs.getString("pending_host", prefs.getString("remote_host", ""));
                String previousPin = prefs.getString("pending_pin", prefs.getString("remote_pin", ""));
                if (!previousPin.isEmpty() && previousHost.equals(host)) {
                    try {
                        boolean pending = !prefs.getString("pending_pin", "").isEmpty();
                        boolean legacy = prefs.getBoolean(pending ? "pending_legacy_identity" : "remote_legacy_identity", true);
                        finishLink(host, previousPin, legacy);
                        showLinkComplete(paired);
                        return;
                    } catch (RemoteUnavailable error) {
                        // Try the system pairing flow if a saved remote connection no longer works.
                    }
                }
                pairing = new AndroidTvRemote(host, true, "", false);
                pairing.beginPairing();
                AndroidTvRemote pending = pairing;
                runOnUiThread(() -> enterRemoteCode(pending, host, paired));
            } catch (Exception error) {
                if (pairing != null) pairing.close();
                android.util.Log.w("TVLink", "TV pairing failed before entering code", error);
                runOnUiThread(() -> {
                    remotePairing = false;
                    if (isFinishing() || isDestroyed()) return;
                    String detail = error.getMessage();
                    new AlertDialog.Builder(this).setTitle("Could not start Android TV pairing")
                            .setMessage(detail == null ? error.getClass().getSimpleName() : detail)
                            .setPositiveButton("OK", null).show();
                });
            }
        });
    }

    private void enterRemoteCode(AndroidTvRemote pairing, String host, Runnable paired) {
        if (isFinishing() || isDestroyed()) {
            pairing.close();
            return;
        }
        EditText code = new EditText(this);
        code.setSingleLine(true);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Enter the code shown on TV").setView(code)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Pair", null).create();
        dialog.setOnDismissListener(ignored -> {
            pairing.close();
            remotePairing = false;
        });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = code.getText().toString().trim();
            if (!value.matches("[0-9a-fA-F]{6}")) {
                code.setError("Enter the six-character code");
                return;
            }
            dialog.setOnDismissListener(null);
            dialog.dismiss();
            remoteIo.execute(() -> {
                try (AndroidTvRemote connection = pairing) {
                    String pin = connection.finishPairing(value);
                    finishLink(host, pin, false);
                    showLinkComplete(paired);
                } catch (Exception error) {
                    runOnUiThread(() -> notifyUser("Pairing failed: " + error.getMessage()));
                } finally {
                    runOnUiThread(() -> remotePairing = false);
                }
            });
        });
    }

    private static final class RemoteUnavailable extends IOException {
        RemoteUnavailable(Exception cause) { super(cause.getMessage(), cause); }
    }

    private void finishLink(String host, String pin, boolean legacyIdentity) throws Exception {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String token = prefs.getString("pending_token", "");
        if (!host.equals(prefs.getString("pending_host", "")) || token.isEmpty()) token = LinkKeys.newToken();
        if (!prefs.edit().putString("pending_host", host).putString("pending_pin", pin)
                .putString("pending_token", token).putBoolean("pending_legacy_identity", legacyIdentity).commit()) {
            throw new IOException("Could not save pending pairing");
        }

        // A lost response can be retried without re-entering the Android TV code.
        if (!confirmLink(host, token)) {
            AndroidTvRemote remote;
            try {
                remote = new AndroidTvRemote(host, false, pin, legacyIdentity);
                try {
                    remote.start();
                } catch (Exception error) {
                    remote.close();
                    throw error;
                }
            } catch (Exception error) {
                throw new RemoteUnavailable(error);
            }
            try (AndroidTvRemote connection = remote) {
                LinkKeys.send(connection, token);
                if (!confirmLink(host, token)) {
                    throw new IOException("Open Pair on the TV, then tap Pair on your phone again.");
                }
            }
        }
        if (!prefs.edit().putString("remote_host", host).putString("remote_pin", pin)
                .putString("link_token", token).putBoolean("remote_legacy_identity", legacyIdentity)
                .remove("pending_host").remove("pending_pin").remove("pending_legacy_identity")
                .remove("pending_token").commit()) {
            throw new IOException("Could not save the link on this phone");
        }
        closeSystemRemote();
    }

    private boolean confirmLink(String host, String token) throws IOException {
        try (TransferClient client = new TransferClient(host, "LINK", LinkKeys.proof(token))) {
            try {
                client.confirm();
                return true;
            } catch (IOException error) {
                return false;
            }
        }
    }

    private void showLinkComplete(Runnable paired) {
        runOnUiThread(() -> {
            remotePairing = false;
            if (isDestroyed() || isFinishing()) return;
            if (started) startReceiver();
            notifyUser("TV linked");
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

    private void sendSystemKey(int key) {
        if (remotePairing) return;
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String host = prefs.getString("remote_host", "");
        String pin = prefs.getString("remote_pin", "");
        String token = prefs.getString("link_token", "");
        boolean legacyIdentity = prefs.getBoolean("remote_legacy_identity", true);
        if (host.isEmpty() || pin.isEmpty()) {
            pairSystemRemote();
            return;
        }
        remoteIo.execute(() -> {
            try {
                if (remoteConnection == null || !remoteConnection.isOpen()) {
                    closeSystemRemote();
                    try {
                        startRemote(host, pin, legacyIdentity);
                    } catch (Exception previousAddressUnavailable) {
                        closeSystemRemote();
                        String resolved = LinkNetwork.resolve(host, token);
                        if (resolved.equals(host)) throw previousAddressUnavailable;
                        startRemote(resolved, pin, legacyIdentity);
                        prefs.edit().putString("remote_host", resolved).apply();
                    }
                }
                remoteConnection.key(key);
            } catch (Exception error) {
                closeSystemRemote();
                remoteIo.getQueue().clear();
                runOnUiThread(() -> notifyUser("TV remote unavailable: " + error.getMessage()));
            }
        });
    }

    private void startRemote(String host, String pin, boolean legacyIdentity) throws Exception {
        remoteConnection = new AndroidTvRemote(host, false, pin, legacyIdentity);
        remoteConnection.start();
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
