package com.tvlink.mobile;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

final class RemoteDiscovery {
    private static final String SERVICE = "_androidtvremote2._tcp.";

    static Map<String, String> find(Context context) throws InterruptedException {
        Map<String, String> devices = new LinkedHashMap<>();
        NsdManager manager = context.getSystemService(NsdManager.class);
        if (manager == null) return devices;
        BlockingQueue<NsdServiceInfo> found = new LinkedBlockingQueue<>();
        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            public void onDiscoveryStarted(String type) {}
            public void onDiscoveryStopped(String type) {}
            public void onStartDiscoveryFailed(String type, int error) {}
            public void onStopDiscoveryFailed(String type, int error) {}
            public void onServiceLost(NsdServiceInfo service) {}
            public void onServiceFound(NsdServiceInfo service) { found.offer(service); }
        };
        try {
            manager.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, listener);
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            long deadline = android.os.SystemClock.elapsedRealtime() + 2200;
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                long remaining = Math.max(1, deadline - android.os.SystemClock.elapsedRealtime());
                NsdServiceInfo service = found.poll(remaining, TimeUnit.MILLISECONDS);
                if (service == null) break;
                if (!seen.add(service.getServiceName())) continue;
                java.util.concurrent.CompletableFuture<NsdServiceInfo> result = new java.util.concurrent.CompletableFuture<>();
                manager.resolveService(service, new NsdManager.ResolveListener() {
                    public void onResolveFailed(NsdServiceInfo info, int error) { result.complete(null); }
                    public void onServiceResolved(NsdServiceInfo info) { result.complete(info); }
                });
                try {
                    NsdServiceInfo resolved = result.get(Math.max(1, deadline - android.os.SystemClock.elapsedRealtime()),
                            TimeUnit.MILLISECONDS);
                    if (resolved != null && resolved.getHost() != null) {
                        devices.put(resolved.getHost().getHostAddress(), resolved.getServiceName());
                    }
                } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException ignored) {
                    break;
                }
            }
        } catch (RuntimeException error) {
            android.util.Log.w("TVLink", "Remote discovery unavailable", error);
        } finally {
            try { manager.stopServiceDiscovery(listener); } catch (RuntimeException ignored) {}
        }
        return devices;
    }

    static Socket connect(Context context, String address, int port, java.util.function.Consumer<String> log) throws IOException {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        ArrayList<Network> routes = new ArrayList<>();
        if (manager != null) {
            try {
                for (Network network : manager.getAllNetworks()) {
                    NetworkCapabilities caps = manager.getNetworkCapabilities(network);
                    if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                            && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) routes.add(network);
                }
            } catch (SecurityException ignored) {}
        }
        routes.add(null);
        InetSocketAddress endpoint = new InetSocketAddress(InetAddress.getByName(address), port);
        IOException failure = null;
        for (Network network : routes) {
            Socket socket = new Socket();
            try {
                log.accept("Trying " + address + ":" + port + " via "
                        + (network == null ? "default network" : "local Wi-Fi/Ethernet") + "…");
                if (network != null) network.bindSocket(socket);
                socket.connect(endpoint, routes.size() == 1 ? 5000 : 2500);
                return socket;
            } catch (IOException error) {
                failure = error;
                log.accept("TCP attempt failed: " + error.getClass().getSimpleName());
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
        throw new IOException("Could not reach the TV Remote Service at " + address + ":" + port
                + ". " + (failure == null ? "" : failure.getMessage()), failure);
    }

    private RemoteDiscovery() {}
}
