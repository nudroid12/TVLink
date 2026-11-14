package com.tvlink.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import java.io.File;

final class LocalSettings {
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

    static File directory(Context context) {
        if (prefs(context).getBoolean("downloads", false) && hasStorageAccess(context)) {
            return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TVLink");
        }
        return new File(context.getFilesDir(), "received");
    }
}
