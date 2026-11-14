package com.tvlink.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class StartupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && TvSettings.prefs(context).getBoolean("startup", false)) {
            context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }
}
