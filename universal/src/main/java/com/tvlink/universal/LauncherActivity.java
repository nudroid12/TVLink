package com.tvlink.universal;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;

public final class LauncherActivity extends Activity {
    static boolean isTelevision(Context context) {
        UiModeManager mode = context.getSystemService(UiModeManager.class);
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || (mode != null && mode.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean television = isTelevision(this);
        Intent source = getIntent();
        boolean share = !television && Intent.ACTION_SEND.equals(source.getAction());
        Intent launch = new Intent(this, television ? com.tvlink.tv.MainActivity.class
                : com.tvlink.mobile.MainActivity.class);
        if (share) {
            launch.setAction(Intent.ACTION_SEND);
            launch.setType(source.getType());
            launch.setClipData(source.getClipData());
            try {
                Object stream = source.getParcelableExtra(Intent.EXTRA_STREAM);
                if (stream instanceof Uri) launch.putExtra(Intent.EXTRA_STREAM, (Uri) stream);
            } catch (RuntimeException error) {
                android.util.Log.w("TVLink", "Could not read shared URI", error);
            }
            launch.addFlags(source.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(launch);
        finish();
    }
}
