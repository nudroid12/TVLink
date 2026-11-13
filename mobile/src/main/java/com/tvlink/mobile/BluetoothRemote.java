package com.tvlink.mobile;

import android.app.Activity;
import android.bluetooth.*;
import android.content.Context;
import java.util.function.Consumer;

final class BluetoothRemote implements AutoCloseable {
    private static final byte[] KEYBOARD = {
        0x05,0x01,0x09,0x06,(byte)0xa1,0x01,(byte)0x85,0x01,
        0x05,0x07,0x19,0x00,0x29,0x65,0x15,0x00,0x25,0x65,0x75,0x08,
        (byte)0x95,0x01,(byte)0x81,0x00,(byte)0xc0,
        0x05,0x0c,0x09,0x01,(byte)0xa1,0x01,(byte)0x85,0x02,
        0x15,0x00,0x25,0x01,0x0a,0x23,0x02,0x0a,0x24,0x02,
        0x75,0x01,(byte)0x95,0x02,(byte)0x81,0x02,
        0x75,0x06,(byte)0x95,0x01,(byte)0x81,0x03,(byte)0xc0
    };

    private final BluetoothAdapter adapter;
    private BluetoothHidDevice hid;
    private BluetoothDevice host;
    private boolean ready;
    private boolean closed;

    BluetoothRemote(Activity activity, Consumer<String> status) {
        BluetoothManager manager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            status.accept("Turn on Bluetooth to use the remote.");
            return;
        }
        boolean requested = adapter.getProfileProxy(activity, new BluetoothProfile.ServiceListener() {
            @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (closed) {
                    adapter.closeProfileProxy(profile, proxy);
                    return;
                }
                hid = (BluetoothHidDevice) proxy;
                BluetoothHidDeviceAppSdpSettings info = new BluetoothHidDeviceAppSdpSettings(
                        "TVLink Remote", "TV remote", "TVLink", BluetoothHidDevice.SUBCLASS1_KEYBOARD, KEYBOARD);
                if (!hid.registerApp(info, null, null, activity::runOnUiThread, new BluetoothHidDevice.Callback() {
                    @Override public void onAppStatusChanged(BluetoothDevice device, boolean registered) {
                        if (closed) return;
                        ready = registered;
                        if (!registered) host = null;
                        status.accept(registered ? "Ready to pair or connect a TV." : "Bluetooth remote unavailable.");
                    }
                    @Override public void onConnectionStateChanged(BluetoothDevice device, int state) {
                        if (closed) return;
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            host = device;
                            status.accept("Connected to " + device.getName());
                        } else if (state == BluetoothProfile.STATE_DISCONNECTED && device.equals(host)) {
                            host = null;
                            status.accept("TV disconnected.");
                        }
                    }
                })) status.accept("Could not register Bluetooth remote.");
            }
            @Override public void onServiceDisconnected(int profile) {
                hid = null;
                host = null;
                ready = false;
                if (!closed) status.accept("Bluetooth remote disconnected.");
            }
        }, BluetoothProfile.HID_DEVICE);
        if (!requested) status.accept("This phone does not expose Bluetooth HID Device.");
    }

    boolean ready() { return ready; }
    BluetoothDevice[] paired() { return adapter.getBondedDevices().toArray(new BluetoothDevice[0]); }
    boolean connect(BluetoothDevice device) { return ready && hid != null && hid.connect(device); }

    boolean key(int report, int key) {
        if (hid == null || host == null) return false;
        boolean pressed = hid.sendReport(host, report, new byte[] {(byte) key});
        boolean released = hid.sendReport(host, report, new byte[] {0});
        return pressed && released;
    }

    @Override public void close() {
        closed = true;
        if (hid != null) {
            try { hid.unregisterApp(); } catch (SecurityException ignored) {}
            adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid);
        }
        hid = null;
        host = null;
        ready = false;
    }
}
