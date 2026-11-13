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
        Runnable[] actions = {this::showSend, this::showRemote, this::showFiles};
        int[] icons = {R.drawable.ic_send, R.drawable.ic_remote, R.drawable.ic_files};
        Button first = null;
        for (int index = 0; index < titles.length; index++) {
            String title = titles[index];
            boolean primary = index == 0;
            Button item = styleButton(new MenuTile(), title, actions[index], primary);
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

    private final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean busy;

    private LinearLayout feature(String title) {
        showingHome = false;
        LinearLayout page = screen(title);
        page.addView(button(label("Back", "Kembali"), this::home));
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

    private void showSend() {
        LinearLayout body = feature(label("Send", "Hantar"));
        info(body, label("Open Receive on the TV. Both devices must be on the same trusted network.",
                "Buka Terima pada TV. Kedua-dua peranti mesti berada pada rangkaian dipercayai yang sama."));
        body.addView(button(label("Pair TV", "Pasangkan TV"), this::pairTv));
        body.addView(button(label("Choose file", "Pilih fail"), () -> {
            if (getPreferences(MODE_PRIVATE).getString("token", "").isEmpty()) {
                pairTv();
                return;
            }
            if (busy) return;
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*");
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(picker, 10);
        }));
    }

    private void pairTv() {
        LinearLayout inputs = new LinearLayout(this);
        inputs.setOrientation(LinearLayout.VERTICAL);
        EditText host = new EditText(this);
        host.setSingleLine(true);
        host.setHint(label("TV IPv4 address", "Alamat IPv4 TV"));
        host.setText(getPreferences(MODE_PRIVATE).getString("host", ""));
        EditText code = new EditText(this);
        code.setHint(label("Pairing code", "Kod pasangan"));
        code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputs.addView(host);
        inputs.addView(code);
        new AlertDialog.Builder(this).setTitle(label("Pair TV", "Pasangkan TV")).setView(inputs)
                .setNegativeButton(label("Cancel", "Batal"), null)
                .setPositiveButton(label("Pair", "Pasangkan"), (dialog, which) -> {
                    String ip = host.getText().toString().trim();
                    String pin = code.getText().toString().trim();
                    if (!android.util.Patterns.IP_ADDRESS.matcher(ip).matches() || !pin.matches("[0-9]{8}")) {
                        notifyUser(label("Enter the TV address and eight-digit code.", "Masukkan alamat TV dan kod lapan digit."));
                        return;
                    }
                    android.content.SharedPreferences settings = getPreferences(MODE_PRIVATE);
                    work(() -> {
                        try (TransferClient client = new TransferClient(ip, "PAIR", pin)) {
                            settings.edit().putString("host", ip).putString("token", client.pair()).apply();
                        }
                        runOnUiThread(() -> notifyUser(label("TV paired", "TV dipasangkan")));
                    });
                }).show();
    }

    private TransferClient client(String operation) throws IOException {
        android.content.SharedPreferences settings = getPreferences(MODE_PRIVATE);
        String host = settings.getString("host", "");
        String token = settings.getString("token", "");
        if (token.isEmpty()) throw new IOException("Pair with the TV first");
        return new TransferClient(host, operation, token);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 10 || result != RESULT_OK || data == null || data.getData() == null) return;
        android.net.Uri uri = data.getData();
        LinearLayout body = feature(label("Send", "Hantar"));
        TextView progress = info(body, label("Preparing file…", "Menyediakan fail…"));
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
            runOnUiThread(() -> progress.setText(label("Sent: ", "Dihantar: ") + name));
        });
    }

    private void showFiles() {
        if (busy) return;
        LinearLayout body = feature(label("Files on TV", "Fail di TV"));
        body.addView(button(label("Refresh", "Muat semula"), this::showFiles));
        body.addView(button(label("Pair TV", "Pasangkan TV"), this::pairTv));
        work(() -> {
            try (TransferClient client = client("LIST")) {
                java.util.List<String> files = client.list();
                java.util.Collections.sort(files, String.CASE_INSENSITIVE_ORDER);
                runOnUiThread(() -> {
                    if (files.isEmpty()) info(body, label("No files received yet", "Belum ada fail diterima"));
                    for (String name : files) body.addView(button(name, () -> fileActions(name)));
                });
            }
        });
    }

    private void fileActions(String name) {
        new AlertDialog.Builder(this).setTitle(name)
                .setItems(new String[] {label("Rename", "Tukar nama"), label("Delete", "Padam")}, (dialog, choice) -> {
                    if (choice == 0) {
                        EditText input = new EditText(this);
                        input.setSingleLine(true);
                        input.setText(name);
                        new AlertDialog.Builder(this).setTitle(label("Rename", "Tukar nama")).setView(input)
                                .setNegativeButton(label("Cancel", "Batal"), null)
                                .setPositiveButton(label("Save", "Simpan"), (d, w) ->
                                        changeFile("RENAME", name, input.getText().toString().trim())).show();
                    } else {
                        new AlertDialog.Builder(this).setTitle(label("Delete file?", "Padam fail?")).setMessage(name)
                                .setNegativeButton(label("Cancel", "Batal"), null)
                                .setPositiveButton(label("Delete", "Padam"), (d, w) -> changeFile("DELETE", name)).show();
                    }
                }).show();
    }

    private void changeFile(String operation, String... names) {
        work(() -> {
            try (TransferClient client = client(operation)) { client.change(names); }
        }, () -> {
            if (!showingHome) showFiles();
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
        info(body, label("Connect to Android TV Remote Service over Wi-Fi.",
                "Sambung ke Android TV Remote Service melalui Wi-Fi."));
        body.addView(button(label("Pair system remote", "Pasangkan remote sistem"), this::pairSystemRemote));
        remoteRow(body, new String[] {"↑"}, new int[] {19});
        remoteRow(body, new String[] {"←", "OK", "→"}, new int[] {21, 23, 22});
        remoteRow(body, new String[] {"↓"}, new int[] {20});
        remoteRow(body, new String[] {label("Back", "Kembali"), label("Home", "Utama")}, new int[] {4, 3});
    }

    private void pairSystemRemote() {
        if (remotePairing) return;
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        EditText ip = new EditText(this);
        ip.setSingleLine(true);
        ip.setHint("TV IP address");
        ip.setText(prefs.getString("remote_host", prefs.getString("host", "")));
        new AlertDialog.Builder(this).setTitle("Pair system remote").setView(ip)
                .setNegativeButton(label("Cancel", "Batal"), null)
                .setPositiveButton("Connect", (dialog, which) -> {
                    String host = ip.getText().toString().trim();
                    if (!android.util.Patterns.IP_ADDRESS.matcher(host).matches()) {
                        notifyUser("Enter a valid TV IP address.");
                        return;
                    }
                    remotePairing = true;
                    remoteIo.execute(() -> {
                        AndroidTvRemote pairing = null;
                        try {
                            pairing = new AndroidTvRemote(host, true, "");
                            pairing.beginPairing();
                            AndroidTvRemote pending = pairing;
                            runOnUiThread(() -> enterRemoteCode(pending, host));
                        } catch (Exception error) {
                            if (pairing != null) pairing.close();
                            runOnUiThread(() -> {
                                remotePairing = false;
                                notifyUser("Could not reach Android TV Remote Service: " + error.getMessage());
                            });
                        }
                    });
                }).show();
    }

    private void enterRemoteCode(AndroidTvRemote pairing, String host) {
        if (isFinishing() || isDestroyed()) {
            pairing.close();
            return;
        }
        EditText code = new EditText(this);
        code.setSingleLine(true);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Enter the code shown on TV").setView(code)
                .setNegativeButton(label("Cancel", "Batal"), null)
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
                    getPreferences(MODE_PRIVATE).edit().putString("remote_host", host)
                            .putString("remote_pin", pin).apply();
                    closeSystemRemote();
                    runOnUiThread(() -> notifyUser("System remote paired"));
                } catch (Exception error) {
                    runOnUiThread(() -> notifyUser("Pairing failed: " + error.getMessage()));
                } finally {
                    runOnUiThread(() -> remotePairing = false);
                }
            });
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
        if (host.isEmpty() || pin.isEmpty()) {
            pairSystemRemote();
            return;
        }
        remoteIo.execute(() -> {
            try {
                if (remoteConnection == null || !remoteConnection.isOpen()) {
                    closeSystemRemote();
                    remoteConnection = new AndroidTvRemote(host, false, pin);
                    remoteConnection.start();
                }
                remoteConnection.key(key);
            } catch (Exception error) {
                closeSystemRemote();
                remoteIo.getQueue().clear();
                runOnUiThread(() -> notifyUser("TV remote unavailable: " + error.getMessage()));
            }
        });
    }

    private void closeSystemRemote() {
        if (remoteConnection != null) remoteConnection.close();
        remoteConnection = null;
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
