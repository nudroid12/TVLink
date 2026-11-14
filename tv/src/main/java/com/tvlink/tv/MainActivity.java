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
    private static final int FOCUS_BLUE = 0xff7fb1fe;

    private boolean showingHome;

    @Override public void onCreate(Bundle state) {
        setTheme(R.style.TVLinkTheme);
        super.onCreate(state);
        getPreferences(MODE_PRIVATE).registerOnSharedPreferenceChangeListener(deviceListener);
        home();
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private LinearLayout screen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(24), dp(24), dp(24));
        page.setBackgroundColor(0xff060f25);
        deviceCardName = null;
        deviceCard = null;
        setContentView(page);
        return page;
    }

    private GradientDrawable tile(int fill, boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(12));
        shape.setStroke(dp(focused ? 3 : 1), focused ? FOCUS_BLUE : 0xff82aaff);
        return shape;
    }

    private StateListDrawable highlightOnFocus(int fill) {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_focused}, tile(FOCUS_BLUE, true));
        background.addState(new int[] {}, tile(fill, false));
        return background;
    }

    private StateListDrawable flatRowBackground() {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_focused}, tile(0xff263a5a, true));
        background.addState(new int[] {}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        return background;
    }

    private void styleRowText(TextView text) {
        text.setTextColor(Color.WHITE);
        text.setTextSize(18);
        text.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL);
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
        background.addState(new int[] {android.R.attr.state_focused}, tile(primary ? FOCUS_BLUE : 0xff263a5a, true));
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
                pairing.setText(receiver.pairingStatus());
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

    private void addHeader(LinearLayout page) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.tvlink_wordmark);
        logo.setContentDescription("TVLink");
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(logo, new LinearLayout.LayoutParams(dp(116), -1));
        header.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 1, 1));
        Button pair = button(isLinked() ? peerName() : "Pair", this::showLinkStatus);
        pairedHeader = pair;
        pair.setMinWidth(0);
        pair.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        pair.setCompoundDrawablePadding(dp(6));
        pair.setPadding(dp(12), 0, dp(12), 0);
        updateDeviceLabels();
        pair.setMaxWidth(dp(280));
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
        divider(page);
    }

    private LinearLayout feature(String title) {
        showingHome = false;
        stopPairingDisplay();
        LinearLayout page = screen();
        addHeader(page);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(48)));
        boolean showBack = !"Pair".equals(title) && !"Remote".equals(title)
                && !"My Files".equals(title) && !"Settings".equals(title)
                && !"Send / Receive".equals(title);
        if (showBack) {
            titleRow.addView(button("Back", this::home), new LinearLayout.LayoutParams(dp(82), dp(42)));
        }
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(24);
        heading.setTextColor(Color.WHITE);
        heading.setPadding(showBack ? dp(16) : 0, 0, 0, 0);
        titleRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        if ("My Files".equals(title)) {
            Button sort = button("Sort: " + (newestFirst ? "Newest" : "Name"), () -> {
                newestFirst = !newestFirst;
                showFiles();
            });
            sort.setBackground(flatRowBackground());
            styleRowText(sort);
            titleRow.addView(sort, new LinearLayout.LayoutParams(dp(150), dp(42)));
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
        addHeader(page);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        page.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = Math.round((metrics.widthPixels - dp(48)) * .23f);
        int height = Math.min(Math.round(metrics.heightPixels * .44f), metrics.heightPixels - dp(126));
        String[] titles = {"Send / Receive", "Remote", "Files"};
        int[] icons = {R.drawable.ic_send, R.drawable.ic_remote, R.drawable.ic_files};
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
        info(body, "Use Remote in TVLink on your phone to control this TV.");
        showDevice(body);
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
        setting(rows, "Check for updates", "App version " + appVersion(), () -> ReleaseCheck.check(this));
        setting(rows, "Device Name", TvSettings.device(this), () -> editSetting("Device Name", "device_name", TvSettings.device(this)));
        setting(rows, "Transfer Directory", received().getAbsolutePath(), this::chooseDirectory);
        setting(rows, "Direct Downloads access", TvSettings.hasStorageAccess(this) ? "Allowed" : "Optional for direct Downloads transfers", this::requestStorageAccess);
        toggle(rows, "Receive Transfers", "Accept incoming transfers from other devices", "receive", true);
        toggle(rows, "Launch at startup", "Open TVLink at boot where supported by the TV", "startup", false);
        toggle(rows, "Overwrite", "Replace an existing file with the same name", "overwrite", false);
        toggle(rows, "Modify date", "Update the modified date when replacing files", "modify_date", true);
        rows.getChildAt(0).requestFocus();
    }

    private void setting(LinearLayout rows, String title, String detail, Runnable action) {
        Button item = button(title + "\n" + detail, action);
        item.setBackground(flatRowBackground());
        styleRowText(item);
        item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        item.setPadding(dp(14), dp(8), dp(14), dp(8));
        rows.addView(item, new LinearLayout.LayoutParams(-1, dp(62)));
        divider(rows);
    }

    private void toggle(LinearLayout rows, String title, String detail, String key, boolean fallback) {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        android.widget.CheckBox box = new android.widget.CheckBox(this);
        box.setText(title + "\n" + detail);
        box.setBackground(flatRowBackground());
        styleRowText(box);
        box.setPadding(dp(12), 0, dp(12), 0);
        box.setChecked(prefs.getBoolean(key, fallback));
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
        line.setBackgroundColor(0xff82aaff);
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
            receiver = new TransferServer(received(), getNoBackupFilesDir(), getPreferences(MODE_PRIVATE));
            return true;
        } catch (IOException error) {
            notifyUser("Cannot start receiver: " + error.getMessage());
            return false;
        }
    }

    private void showLinkStatus() {
        LinearLayout body = feature("Pair");
        if (isLinked()) showDevice(body);
        else {
            info(body, "Device:");
            info(body, "No phone linked.");
            startPairing(body, true);
        }
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
        card.addView(button("Unpair", () -> new AlertDialog.Builder(this).setTitle("Unpair " + peerName() + "?")
                .setMessage("This removes the file link. Android TV Remote access can be removed in system settings.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Unpair", (d, w) -> unpairPhone()).show()), new LinearLayout.LayoutParams(-1, dp(44)));
    }

    private void addUnlinkedDevice(LinearLayout body) {
        info(body, "No phone linked.");
        body.addView(button("Pair", this::showLinkStatus));
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
        if (!startReceiver()) return;
        info(body, "On your phone, tap Pair → Find TV. Keep this TV screen open.");
        pairing = new TextView(this);
        pairing.setText("Ready to pair");
        body.addView(pairing);
        receiver.enableLinking(true);
        refresh.postDelayed(updatePairing, 400);
        refresh.postDelayed(expireLink, 180000);
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
        boolean remotePaired = receiver.pairingStatus().startsWith("Phone reports Remote pairing saved");
        receiver.enableLinking(false);
        if (!remotePaired) pairing.setText("Pairing timed out. Press Back and reopen Pair to try again.");
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
        sendFile.setBackground(highlightOnFocus(0xff1b2740));
        sendFile.setTextColor(new android.content.res.ColorStateList(
                new int[][] {{android.R.attr.state_focused}, {}}, new int[] {0xff0c1c35, Color.WHITE}));
        send.addView(sendFile, new LinearLayout.LayoutParams(dp(160), dp(48)));

        android.view.View rule = new android.view.View(this);
        rule.setBackgroundColor(0xff82aaff);
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

    private void addTransferDetails(LinearLayout body, LinearLayout receive) {
        TextView status = info(receive, "");
        status.setTextColor(Color.WHITE);
        android.widget.ProgressBar progress = new android.widget.ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(android.view.View.GONE);
        receive.addView(progress, new LinearLayout.LayoutParams(-1, dp(12)));
        divider(body);
        addTransferHeading(body, "Transfer history");
        LinearLayout history = new LinearLayout(this);
        history.setOrientation(LinearLayout.VERTICAL);
        body.addView(history);
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        java.text.DateFormat dates = java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.SHORT, java.text.DateFormat.SHORT);
        java.util.regex.Pattern percent = java.util.regex.Pattern.compile(" · (\\d{1,3})%$");
        Runnable refresh = new Runnable() {
            private String lastHistory;

            @Override public void run() {
                if (!body.isAttachedToWindow()) return;
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
                        TextView row = info(history, item.optString("outcome") + "\n"
                                + dates.format(new java.util.Date(item.optLong("time"))));
                        row.setTextColor(Color.WHITE);
                        if (index + 1 < entries.length()) divider(history);
                    }
                }
                body.postDelayed(this, 500);
            }
        };
        body.post(refresh);
    }

    private void finishSend(SendSession session, String outcome) {
        TransferHistory.add(getPreferences(MODE_PRIVATE), outcome);
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

    private void showFiles() {
        LinearLayout body = feature("My Files");
        File[] files = received().listFiles(f -> f.isFile() && !f.getName().startsWith("."));
        if (files == null || files.length == 0) {
            info(body, "No files received yet");
            return;
        }
        java.util.Comparator<File> byName = java.util.Comparator.comparing(File::getName,
                String.CASE_INSENSITIVE_ORDER);
        java.util.Arrays.sort(files, newestFirst
                ? java.util.Comparator.comparingLong(File::lastModified).reversed().thenComparing(byName)
                : byName);
        for (File file : files) addFileRow(body, file);
        body.getChildAt(0).requestFocus();
    }

    private void addFileRow(LinearLayout body, File file) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setFocusable(true);
        row.setOnClickListener(view -> fileActions(file, view));
        row.setBackground(flatRowBackground());
        body.addView(row, new LinearLayout.LayoutParams(-1, dp(62)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_document);
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
                        runOnUiThread(() -> { if (icon.isAttachedToWindow()) icon.setImageDrawable(appIcon); });
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
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView name = new TextView(this);
        name.setText(filename);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        styleRowText(name);
        labels.addView(name);
        TextView size = new TextView(this);
        size.setText(android.text.format.Formatter.formatShortFileSize(this, file.length()));
        styleRowText(size);
        labels.addView(size);
        TextView date = new TextView(this);
        date.setText(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
                java.text.DateFormat.SHORT).format(new java.util.Date(file.lastModified())));
        styleRowText(date);
        row.addView(date);
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
        icon.setImageResource(R.drawable.ic_document);
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
        addFileAction(panel, dialog, "Delete", R.drawable.ic_file_delete, 0xffff8585,
                () -> new AlertDialog.Builder(this).setTitle("Delete file?")
                        .setMessage(filename).setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (confirm, which) -> {
                            if (!file.delete()) notifyUser("Could not delete file");
                            showFiles();
                        }).show());

        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.55f);
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            window.setLayout(Math.min(dp(560), metrics.widthPixels - dp(48)),
                    Math.min(dp(334), metrics.heightPixels - dp(40)));
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
        row.setTextColor(color);
        row.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        row.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(color));
        row.setCompoundDrawablePadding(dp(14));
        panel.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
        return row;
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
        dismissFileApproval();
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
        if (isLinked()) {
            startReceiver();
            recoverDeviceName();
        }
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

    @Override public void onBackPressed() {
        if (showingHome) super.onBackPressed();
        else home();
    }
}
