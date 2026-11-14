package com.tvlink.mobile;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

final class TransferHistory {
    private static final String KEY = "transfer_history";

    static JSONArray read(SharedPreferences prefs) {
        try {
            return new JSONArray(prefs.getString(KEY, "[]"));
        } catch (org.json.JSONException error) {
            return new JSONArray();
        }
    }

    static synchronized void add(SharedPreferences prefs, String outcome) {
        try {
            JSONArray old = read(prefs);
            JSONArray updated = new JSONArray();
            updated.put(new JSONObject().put("outcome", outcome).put("time", System.currentTimeMillis()));
            for (int i = 0; i < Math.min(old.length(), 49); i++) updated.put(old.get(i));
            prefs.edit().putString(KEY, updated.toString()).apply();
        } catch (org.json.JSONException error) {
            android.util.Log.w("TVLink", "Could not save transfer history", error);
        }
    }

    private TransferHistory() {}
}
