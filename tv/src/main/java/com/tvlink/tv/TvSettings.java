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

    static File directory(Context context) {
        if (prefs(context).getBoolean("downloads", false) && hasStorageAccess(context)) {
            return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TVLink");
        }
        return new File(context.getFilesDir(), "received");
    }
}
