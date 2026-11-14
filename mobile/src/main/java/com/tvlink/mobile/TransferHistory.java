package com.tvlink.mobile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class TransferHistory {
    private JSONArray entries = new JSONArray();

    synchronized JSONArray read() {
        return entries;
    }

    synchronized void add(String outcome) {
        try {
            JSONArray updated = new JSONArray();
            updated.put(new JSONObject().put("outcome", outcome).put("time", System.currentTimeMillis()));
            for (int i = 0; i < Math.min(entries.length(), 49); i++) updated.put(entries.get(i));
            entries = updated;
        } catch (JSONException error) {
            android.util.Log.w("TVLink", "Could not add transfer to history", error);
        }
    }

    synchronized void clear() {
        entries = new JSONArray();
    }
}
