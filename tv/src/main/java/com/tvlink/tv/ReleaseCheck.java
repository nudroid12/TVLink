package com.tvlink.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

final class ReleaseCheck {
    private static boolean checking;
    private static final String REPO = "nudroid12/TVLink";

    static void check(Activity activity) {
        if (checking) return;
        checking = true;
        android.widget.Toast.makeText(activity, "Checking for updates…", android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String result;
            String link = null;
            try {
                String installed = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
                JSONObject release = fetch();
                String tag = release.getString("tag_name");
                if (compare(tag, installed) <= 0) {
                    result = "You already have the latest release (" + installed + ").";
                } else {
                    JSONArray assets = release.optJSONArray("assets");
                    for (int i = 0; assets != null && i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.getString("name").toLowerCase(java.util.Locale.ROOT);
                        if ((name.contains("-tv") || name.startsWith("tv-")) && name.endsWith(".apk")) {
                            link = "https://github.com/" + REPO + "/releases/tag/" + Uri.encode(tag);
                            break;
                        }
                    }
                    result = link == null ? tag + " is available, but it has no TV APK attached."
                            : "TV version " + tag + " is available. Installed: " + installed + ".";
                }
            } catch (Exception error) {
                result = "Unable to check for updates. Check your connection and try again.";
            }
            String message = result;
            String releaseLink = link;
            activity.runOnUiThread(() -> {
                checking = false;
                if (activity.isFinishing() || activity.isDestroyed()) return;
                AlertDialog.Builder dialog = new AlertDialog.Builder(activity).setTitle("Check for updates")
                        .setMessage(releaseLink == null ? message : message + "\n\n" + releaseLink)
                        .setNegativeButton("Close", null);
                if (releaseLink != null) dialog.setPositiveButton("Open release", (d, w) -> {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(releaseLink)));
                    } catch (android.content.ActivityNotFoundException error) {
                        new AlertDialog.Builder(activity).setMessage(releaseLink).setPositiveButton("Close", null).show();
                    }
                });
                dialog.show();
            });
        }, "TVLink release check").start();
    }

    private static JSONObject fetch() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        try (InputStream input = conn.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192];
            for (int count; (count = input.read(bytes)) != -1;) {
                if (output.size() + count > 1048576) throw new java.io.IOException("Release response too large");
                output.write(bytes, 0, count);
            }
            return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            conn.disconnect();
        }
    }

    private static int compare(String latest, String installed) {
        String[] left = latest.replaceFirst("^[vV]", "").split("\\.");
        String[] right = installed.split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int a = i < left.length ? Integer.parseInt(left[i]) : 0;
            int b = i < right.length ? Integer.parseInt(right[i]) : 0;
            if (a != b) return Integer.compare(a, b);
        }
        return 0;
    }
}
