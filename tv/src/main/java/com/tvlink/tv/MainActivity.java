package com.tvlink.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
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
    private static final boolean TV = true;

    private boolean malay;
    private boolean showingHome;
    private boolean receivingScreen;

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
        receivingScreen = false;
        stopPairingDisplay();
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
        String[] titles = {label("Receive", "Terima"), "Remote", label("Files", "Fail")};
        Runnable[] actions = {this::showReceive, this::showRemoteHelp, this::showFiles};
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

    private TransferServer receiver;
    private TextView pairing;
    private final android.os.Handler refresh = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable updatePairing = new Runnable() {
        @Override public void run() {
            if (pairing == null || receiver == null) return;
            String code = receiver.currentCode();
            pairing.setText(code.isEmpty()
                    ? label("Phone paired. Ready to receive.", "Telefon dipasangkan. Sedia menerima.")
                    : label("Pairing code: ", "Kod pasangan: ") + code);
            refresh.postDelayed(this, 500);
        }
    };

    private File received() {
        return new File(getFilesDir(), "received");
    }

    private LinearLayout feature(String title) {
        receivingScreen = false;
        stopPairingDisplay();
        showingHome = false;
        LinearLayout page = screen(title);
        Button back = button(label("Back", "Kembali"), this::home);
        page.addView(back);
        back.requestFocus();
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        return body;
    }

    private void info(LinearLayout body, String value) {
        TextView text = new TextView(this);
        text.setPadding(0, dp(8), 0, dp(8));
        text.setText(value);
        body.addView(text);
    }

    private void showReceive() {
        LinearLayout body = feature(label("Receive", "Terima"));
        receivingScreen = true;
        try {
            if (receiver == null) receiver = new TransferServer(received(), getPreferences(MODE_PRIVATE), this::remoteKey);
        } catch (IOException error) {
            info(body, label("Cannot start receiver: ", "Penerima gagal dimulakan: ") + error.getMessage());
            return;
        }
        info(body, label("TV IPv4: ", "IPv4 TV: ") + localAddresses());
        info(body, label("Keep TVLink open while transferring over your trusted network.",
                "Biarkan TVLink terbuka semasa pindahan melalui rangkaian dipercayai."));
        pairing = new TextView(this);
        pairing.setTextSize(24);
        body.addView(pairing);
        updatePairing.run();
        body.addView(button(label("New pairing code", "Kod pasangan baharu"), () -> {
            receiver.renewCode();
            refresh.removeCallbacks(updatePairing);
            updatePairing.run();
        }));
        body.addView(button(label("Forget paired phone", "Lupakan telefon dipasangkan"), () -> receiver.forgetPhone()));
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
        return addresses.isEmpty() ? label("Check network settings", "Semak tetapan rangkaian")
                : android.text.TextUtils.join(" / ", addresses);
    }

    private void showRemoteHelp() {
        LinearLayout body = feature("Remote");
        info(body, label("Keep TVLink open. Pair your phone using Receive and use the remote to navigate TVLink.",
                "Biarkan TVLink terbuka. Pasangkan telefon melalui Terima dan guna remote untuk menavigasi TVLink."));
        body.addView(button(label("Receive", "Terima"), this::showReceive));
    }

    private boolean remoteKey(String key) throws IOException {
        java.util.concurrent.FutureTask<Boolean> command = new java.util.concurrent.FutureTask<>(() -> {
            if (receiver == null || !hasWindowFocus()) return false;
            int keyCode;
            switch (key) {
                case "UP": keyCode = android.view.KeyEvent.KEYCODE_DPAD_UP; break;
                case "DOWN": keyCode = android.view.KeyEvent.KEYCODE_DPAD_DOWN; break;
                case "LEFT": keyCode = android.view.KeyEvent.KEYCODE_DPAD_LEFT; break;
                case "RIGHT": keyCode = android.view.KeyEvent.KEYCODE_DPAD_RIGHT; break;
                case "OK": keyCode = android.view.KeyEvent.KEYCODE_DPAD_CENTER; break;
                case "BACK":
                case "HOME":
                    home();
                    return true;
                default: return false;
            }
            boolean handled = dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode));
            return dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)) || handled;
        });
        refresh.post(command);
        try {
            return command.get(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Remote command interrupted", error);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException error) {
            throw new IOException("Remote command failed", error);
        } finally {
            command.cancel(false);
        }
    }

    private void showFiles() {
        LinearLayout body = feature(label("Files", "Fail"));
        body.addView(button(label("Refresh", "Muat semula"), this::showFiles));
        File[] files = received().listFiles(f -> f.isFile() && !f.getName().startsWith("."));
        if (files == null || files.length == 0) {
            info(body, label("No files received yet", "Belum ada fail diterima"));
            return;
        }
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : files) body.addView(button(file.getName(), () -> fileActions(file)));
    }

    private void fileActions(File file) {
        boolean apk = file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".apk");
        new AlertDialog.Builder(this).setTitle(file.getName()).setItems(new String[] {
                apk ? label("Install APK", "Pasang APK") : label("Open", "Buka"),
                label("Rename", "Tukar nama"), label("Delete", "Padam")
        }, (d, choice) -> {
            switch (choice) {
                case 0: openFile(file, apk); break;
                case 1: renameFile(file); break;
                case 2:
                    new AlertDialog.Builder(this).setTitle(label("Delete file?", "Padam fail?"))
                            .setMessage(file.getName()).setNegativeButton(label("Cancel", "Batal"), null)
                            .setPositiveButton(label("Delete", "Padam"), (dialog, which) -> {
                                if (!file.delete()) notifyUser(label("Could not delete file", "Fail gagal dipadam"));
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
        new AlertDialog.Builder(this).setTitle(label("Rename", "Tukar nama")).setView(input)
                .setNegativeButton(label("Cancel", "Batal"), null)
                .setPositiveButton(label("Save", "Simpan"), (d, choice) -> {
                    try {
                        File target = TransferServer.file(received(), input.getText().toString().trim());
                        if (!target.equals(file)) {
                            synchronized (getFilesDir().getAbsolutePath().intern()) {
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
                notifyUser(label("Allow TVLink to install unknown apps, then select the APK again.",
                        "Benarkan TVLink memasang aplikasi tidak diketahui, kemudian pilih APK semula."));
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
            notifyUser(label("No compatible app or installer available", "Tiada aplikasi atau pemasang yang serasi"));
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
        if (receiver != null) receiver.close();
        receiver = null;
        stopPairingDisplay();
        super.onStop();
    }

    @Override protected void onStart() {
        super.onStart();
        if (receivingScreen && receiver == null) {
            showReceive();
        } else if (receiver == null && !getPreferences(MODE_PRIVATE).getString("transfer_token", "").isEmpty()) {
            try {
                receiver = new TransferServer(received(), getPreferences(MODE_PRIVATE), this::remoteKey);
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
