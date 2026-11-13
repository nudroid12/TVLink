package com.tvlink.tv;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class WifiRemoteService extends AccessibilityService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private TransferServer listener;

    @Override protected void onServiceConnected() {
        stopListener();
        try {
            listener = new TransferServer(new File(getFilesDir(), "received"),
                    getSharedPreferences("MainActivity", MODE_PRIVATE), this::send);
        } catch (IOException error) {
            android.util.Log.e("TVLink", "Cannot start Wi-Fi remote listener", error);
        }
    }

    private boolean send(String key) throws IOException {
        FutureTask<Boolean> command = new FutureTask<>(() -> handleKey(key));
        main.post(command);
        try {
            return command.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Remote command interrupted", error);
        } catch (ExecutionException | TimeoutException error) {
            throw new IOException("Remote command failed", error);
        } finally {
            command.cancel(false);
        }
    }

    private boolean handleKey(String key) {
        switch (key) {
            case "HOME": return performGlobalAction(GLOBAL_ACTION_HOME);
            case "BACK": return performGlobalAction(GLOBAL_ACTION_BACK);
            case "UP": return direction(GLOBAL_ACTION_DPAD_UP, View.FOCUS_UP);
            case "DOWN": return direction(GLOBAL_ACTION_DPAD_DOWN, View.FOCUS_DOWN);
            case "LEFT": return direction(GLOBAL_ACTION_DPAD_LEFT, View.FOCUS_LEFT);
            case "RIGHT": return direction(GLOBAL_ACTION_DPAD_RIGHT, View.FOCUS_RIGHT);
            case "OK":
                if (Build.VERSION.SDK_INT >= 33 && performGlobalAction(GLOBAL_ACTION_DPAD_CENTER)) return true;
                AccessibilityNodeInfo focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                try {
                    return focused != null && focused.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                } finally {
                    if (focused != null) focused.recycle();
                }
            default: return false;
        }
    }

    private boolean direction(int action, int direction) {
        if (Build.VERSION.SDK_INT >= 33 && performGlobalAction(action)) return true;
        AccessibilityNodeInfo focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) return false;
        AccessibilityNodeInfo next = null;
        try {
            next = focused.focusSearch(direction);
            return next != null && next.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        } finally {
            if (next != null) next.recycle();
            focused.recycle();
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    private void stopListener() {
        if (listener != null) listener.close();
        listener = null;
    }

    @Override public boolean onUnbind(android.content.Intent intent) {
        stopListener();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        stopListener();
        super.onDestroy();
    }
}
