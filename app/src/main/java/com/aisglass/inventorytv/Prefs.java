package com.aisglass.inventorytv;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URL;

/**
 * Saved settings of the AIS Inventory TV app (kept on the TV).
 * Developed by RYZ Systems.
 */
final class Prefs {
    /** App version - keep in step with versionName in app/build.gradle. */
    static final String VERSION = "1.0.0";
    static final String SIGNATURE = "Developed by RYZ Systems";
    static final int DEFAULT_PORT = 8080;

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("ais_inventory_tv", Context.MODE_PRIVATE);
    }

    /** Full dashboard address, e.g. http://192.168.1.50:8080/tv.html ("" = not set up). */
    static String url(Context c) { return sp(c).getString("url", ""); }
    static void setUrl(Context c, String u) { sp(c).edit().putString("url", u).apply(); }

    /** Bring the dashboard back to the front automatically when something else covers it. */
    static boolean keepFront(Context c) { return sp(c).getBoolean("keepFront", true); }
    static void setKeepFront(Context c, boolean v) { sp(c).edit().putBoolean("keepFront", v).apply(); }

    /** Auto-return can be paused (e.g. while a technician works in TV settings). */
    static long pauseUntil(Context c) { return sp(c).getLong("pauseUntil", 0L); }
    static void pauseFor(Context c, long ms) {
        sp(c).edit().putLong("pauseUntil", ms <= 0 ? 0L : System.currentTimeMillis() + ms).apply();
    }
    static boolean paused(Context c) { return System.currentTimeMillis() < pauseUntil(c); }

    /**
     * Turns whatever was typed into a full dashboard URL:
     *   "192.168.1.50"            -> http://192.168.1.50:8080/tv.html
     *   "192.168.1.50:9090"       -> http://192.168.1.50:9090/tv.html
     *   "http://host:8080/"       -> http://host:8080/tv.html
     * Returns "" if it cannot be understood.
     */
    static String normalize(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.isEmpty()) return "";
        if (!s.toLowerCase().startsWith("http://") && !s.toLowerCase().startsWith("https://")) {
            s = "http://" + s;
        }
        try {
            URL u = new URL(s);
            String host = u.getHost();
            if (host == null || host.isEmpty()) return "";
            int port = u.getPort() > 0 ? u.getPort() : DEFAULT_PORT;
            String path = u.getPath();
            if (path == null || path.isEmpty() || "/".equals(path)) path = "/tv.html";
            String q = u.getQuery() != null ? "?" + u.getQuery() : "";
            return u.getProtocol() + "://" + host + ":" + port + path + q;
        } catch (Exception e) {
            return "";
        }
    }

    /** "http://host:port" part of a dashboard URL ("" if invalid). */
    static String base(String url) {
        try {
            URL u = new URL(url);
            return u.getProtocol() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
        } catch (Exception e) {
            return "";
        }
    }

    /** Port of a dashboard URL, or the default 8080. */
    static int port(String url) {
        try {
            int p = new URL(url).getPort();
            return p > 0 ? p : DEFAULT_PORT;
        } catch (Exception e) {
            return DEFAULT_PORT;
        }
    }
}
