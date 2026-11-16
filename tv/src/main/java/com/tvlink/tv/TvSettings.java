package com.tvlink.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import java.io.File;

final class TvSettings {
    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE);
    }

    static String device(Context context) {
        return prefs(context).getString("device_name", Build.MODEL);
    }

    static boolean hasStorageAccess(Context context) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    static java.util.List<File> storageRoots(Context context) {
        java.util.List<File> roots = new java.util.ArrayList<>();
        addRoot(roots, Environment.getExternalStorageDirectory());
        if (Build.VERSION.SDK_INT >= 30) {
            android.os.storage.StorageManager storage = context.getSystemService(android.os.storage.StorageManager.class);
            if (storage != null) {
                for (android.os.storage.StorageVolume volume : storage.getStorageVolumes()) {
                    File root = volume.getDirectory();
                    if (!volume.isPrimary() && root != null && root.isDirectory()) addRoot(roots, root);
                }
            }
        }
        return roots;
    }

    private static void addRoot(java.util.List<File> roots, File root) {
        try { roots.add(root.getCanonicalFile()); }
        catch (java.io.IOException ignored) {}
    }

    static boolean canBrowse(Context context, File entry) {
        if (!hasStorageAccess(context) || !entry.canRead()) return false;
        try {
            String canonical = entry.getCanonicalPath();
            if (!canonical.equals(entry.getAbsolutePath())) return false;
            for (File root : storageRoots(context)) {
                String prefix = root.getCanonicalPath() + File.separator;
                if (!canonical.startsWith(prefix)) continue;
                String relative = canonical.substring(prefix.length());
                if (!relative.equals("Android") && !relative.startsWith("Android/")) return true;
            }
        } catch (java.io.IOException ignored) {}
        return false;
    }

    static final int APP_FOLDER = 0;
    static final int DOWNLOADS = 1;
    static final int DOWNLOADS_TVLINK = 2;
    private static final String DESTINATION = "receive_destination";

    static File downloadsDirectory() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    static int destination(Context context) {
        SharedPreferences settings = prefs(context);
        int previous = settings.contains("downloads")
                ? (settings.getBoolean("downloads", false) ? DOWNLOADS_TVLINK : APP_FOLDER)
                : DOWNLOADS;
        return settings.getInt(DESTINATION, previous);
    }

    static void setDestination(Context context, int destination) {
        prefs(context).edit().putInt(DESTINATION, destination).apply();
    }

    static String directoryLabel(Context context) {
        if (hasStorageAccess(context)) {
            if (destination(context) == DOWNLOADS) return "Downloads";
            if (destination(context) == DOWNLOADS_TVLINK) return "Downloads / TVLink";
        }
        return "TVLink app folder";
    }

    static File directory(Context context) {
        if (hasStorageAccess(context)) {
            if (destination(context) == DOWNLOADS) return downloadsDirectory();
            if (destination(context) == DOWNLOADS_TVLINK) return new File(downloadsDirectory(), "TVLink");
        }
        return new File(context.getFilesDir(), "received");
    }

    static boolean isReceivedEntry(File root, File entry) {
        try {
            String rootPath = root.getAbsolutePath();
            String entryPath = entry.getAbsolutePath();
            if (!entryPath.startsWith(rootPath + File.separator) || !entry.canRead()) return false;
            String relative = entryPath.substring(rootPath.length() + 1);
            if (java.util.Arrays.stream(relative.split(File.separator)).anyMatch(part -> part.startsWith("."))) {
                return false;
            }
            return entry.getCanonicalFile().equals(new File(root.getCanonicalFile(), relative))
                    && (entry.isDirectory() || entry.isFile());
        } catch (java.io.IOException error) {
            return false;
        }
    }

    static boolean isDownloadsFile(File file) {
        return downloadsDirectory().equals(file.getParentFile());
    }

    static boolean isReceivedFile(SharedPreferences settings, File file) {
        if (!file.isFile() || file.getName().startsWith(".")) return false;
        if (!isDownloadsFile(file)) return true;
        try {
            File canonical = new File(downloadsDirectory().getCanonicalFile(), file.getName());
            return canonical.equals(file.getCanonicalFile()) && fingerprint(file).equals(
                    settings.getString(receivedKey(file), null));
        } catch (java.io.IOException error) {
            return false;
        }
    }

    static void rememberReceivedFile(SharedPreferences settings, File file) {
        if (isDownloadsFile(file)) {
            settings.edit().putString(receivedKey(file), fingerprint(file)).apply();
        }
    }

    static void forgetReceivedFile(SharedPreferences settings, File file) {
        if (isDownloadsFile(file)) settings.edit().remove(receivedKey(file)).apply();
    }

    private static String receivedKey(File file) {
        return "received_download:" + file.getName();
    }

    private static String fingerprint(File file) {
        return file.lastModified() + ":" + file.length();
    }
}
