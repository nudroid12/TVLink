package com.tvlink.universal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class StartupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        BroadcastReceiver receiver = LauncherActivity.isTelevision(context)
                ? new com.tvlink.tv.StartupReceiver()
                : new com.tvlink.mobile.StartupReceiver();
        receiver.onReceive(context, intent);
    }
}
