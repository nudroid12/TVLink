package com.tvlink.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

final class ReleaseCheck {
    private static boolean checking;
    private static boolean downloading;
    static final String UPDATE_FILE = "tvlink-update.apk";
    private static final long MAX_APK = 256L * 1024 * 1024;
    private static final String REPO = "nudroid12/TVLink";

    static void check(Activity activity) {
        if (checking || downloading) return;
        checking = true;
        android.widget.Toast.makeText(activity, "Checking for updates…", android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String result;
            String link = null;
            try {
                android.content.pm.PackageInfo current = activity.getPackageManager()
                        .getPackageInfo(activity.getPackageName(), 0);
                long installed = android.os.Build.VERSION.SDK_INT >= 28
                        ? current.getLongVersionCode() : current.versionCode;
                JSONObject release = fetch();
                long next = release.getLong("versionCode");
                if (next <= installed) {
                    result = "You already have the latest update (" + current.versionName + ").";
                } else {
                    String version = release.getString("versionName");
                    String candidate = release.getString("downloadUrl");
                    String expected = "https://github.com/" + REPO + "/releases/download/tvlink-dev/TVLink-MOBILE-" + next + ".apk";
                    if (!expected.equals(candidate)) throw new java.io.IOException("Invalid update URL");
                    link = candidate;
                    result = "Version " + version + " is available. Installed: " + current.versionName + ".";
                }
            } catch (java.io.FileNotFoundException error) {
                result = error.getMessage();
            } catch (Exception error) {
                result = "Unable to check for updates. Check your connection and try again.";
            }
            String message = result;
            String releaseLink = link;
            activity.runOnUiThread(() -> {
                checking = false;
                if (activity.isFinishing() || activity.isDestroyed()) return;
                AlertDialog.Builder dialog = new AlertDialog.Builder(activity).setTitle("Check for updates")
                        .setMessage(message)
                        .setNegativeButton("Close", null);
                if (releaseLink != null) dialog.setPositiveButton("Download & Install", (d, w) ->
                        startDownload(activity, releaseLink));
                dialog.show();
            });
        }, "TVLink release check").start();
    }

    private static void startDownload(Activity activity, String link) {
        if (downloading) return;
        if (android.os.Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity).setTitle("Allow app installs")
                    .setMessage("Allow TVLink to install apps, then return and select Download & Install again.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Open settings", (d, w) -> {
                        try {
                            activity.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName())));
                        } catch (android.content.ActivityNotFoundException error) {
                            showResult(activity, "This phone does not expose the install permission settings.");
                        }
                    }).show();
            return;
        }

        downloading = true;
        android.widget.Toast.makeText(activity, "Downloading update…", android.widget.Toast.LENGTH_LONG).show();
        new Thread(() -> {
            java.io.File partial = null;
            String failure = null;
            try {
                partial = java.io.File.createTempFile("tvlink-update-", ".apk", activity.getCacheDir());
                download(link, partial);
                validate(activity, partial);
                java.io.File apk = new java.io.File(activity.getCacheDir(), UPDATE_FILE);
                if (!partial.renameTo(apk)) throw new java.io.IOException("Unable to save the downloaded APK.");
            } catch (Exception error) {
                android.util.Log.w("TVLink", "Update download or validation failed", error);
                failure = error.getMessage() == null ? "Unable to download the update." : error.getMessage();
            } finally {
                if (partial != null) partial.delete();
            }
            String message = failure;
            activity.runOnUiThread(() -> {
                downloading = false;
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (message != null) {
                    showResult(activity, message);
                    return;
                }
                Uri uri = new Uri.Builder().scheme("content").authority("com.tvlink.mobile.files")
                        .appendPath(UPDATE_FILE).build();
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                } catch (android.content.ActivityNotFoundException | SecurityException error) {
                    showResult(activity, "The Android package installer is unavailable.");
                }
            });
        }, "TVLink update download").start();
    }

    private static void showResult(Activity activity, String message) {
        new AlertDialog.Builder(activity).setTitle("TVLink update").setMessage(message)
                .setPositiveButton("Close", null).show();
    }

    private static void download(String link, java.io.File target) throws java.io.IOException {
        URL url = new URL(link);
        if (!"https".equals(url.getProtocol()) || !"github.com".equalsIgnoreCase(url.getHost())
                || !url.getPath().startsWith("/" + REPO + "/releases/download/")) {
            throw new java.io.IOException("Invalid TVLink release asset URL.");
        }
        for (int redirects = 0; redirects < 5; redirects++) {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new java.io.IOException("Update download redirect is missing.");
                    url = new URL(url, location);
                    if (!"https".equals(url.getProtocol())) throw new java.io.IOException("Insecure download redirect.");
                    continue;
                }
                if (status != 200) throw new java.io.IOException("Update download failed (HTTP " + status + ").");
                long expected = connection.getContentLength();
                if (expected > MAX_APK) throw new java.io.IOException("Update APK is too large.");
                try (InputStream input = connection.getInputStream();
                     java.io.FileOutputStream output = new java.io.FileOutputStream(target)) {
                    byte[] buffer = new byte[65536];
                    long total = 0;
                    for (int count; (count = input.read(buffer)) != -1;) {
                        total += count;
                        if (total > MAX_APK) throw new java.io.IOException("Update APK is too large.");
                        output.write(buffer, 0, count);
                    }
                    if (total == 0 || (expected >= 0 && total != expected)) {
                        throw new java.io.IOException("Incomplete APK download.");
                    }
                }
                return;
            } finally {
                connection.disconnect();
            }
        }
        throw new java.io.IOException("Too many update download redirects.");
    }

    @SuppressWarnings("deprecation")
    private static void validate(Activity activity, java.io.File apk) throws Exception {
        android.content.pm.PackageManager pm = activity.getPackageManager();
        boolean modern = android.os.Build.VERSION.SDK_INT >= 28;
        int flags = modern ? android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                : android.content.pm.PackageManager.GET_SIGNATURES;
        android.content.pm.PackageInfo current = pm.getPackageInfo(activity.getPackageName(), flags);
        android.content.pm.PackageInfo update = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (update == null || !current.packageName.equals(update.packageName)) {
            throw new java.io.IOException("The download is not a TVLink Mobile APK.");
        }
        long installed = modern ? current.getLongVersionCode() : current.versionCode;
        long incoming = modern ? update.getLongVersionCode() : update.versionCode;
        if (incoming <= installed) throw new java.io.IOException("The downloaded APK is not newer.");
        android.content.pm.Signature[] before = modern && current.signingInfo != null
                ? current.signingInfo.getApkContentsSigners() : current.signatures;
        android.content.pm.Signature[] after = modern && update.signingInfo != null
                ? update.signingInfo.getApkContentsSigners() : update.signatures;
        if (before == null || after == null || before.length == 0 || after.length != before.length
                || !new java.util.HashSet<>(java.util.Arrays.asList(before))
                        .equals(new java.util.HashSet<>(java.util.Arrays.asList(after)))) {
            throw new java.io.IOException("This update uses a different signing key.");
        }
    }

    private static JSONObject fetch() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://github.com/" + REPO + "/releases/download/tvlink-dev/update.json").openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setUseCaches(false);
        try {
            if (conn.getResponseCode() == 404) throw new java.io.FileNotFoundException("No update has been published for this app yet.");
            try (InputStream input = conn.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[8192];
                for (int count; (count = input.read(bytes)) != -1;) {
                    if (output.size() + count > 1048576) throw new java.io.IOException("Release response too large");
                    output.write(bytes, 0, count);
                }
                return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            conn.disconnect();
        }
    }
}
