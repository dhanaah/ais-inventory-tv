package com.aisglass.inventorytv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONObject;

/** Small HTTP + LAN helpers (no external libraries). Developed by RYZ Systems. */
final class Net {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private Net() {}

    /** GET -> body (throws on network error or non-200). */
    static String get(String url, int connectMs, int readMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(connectMs);
            c.setReadTimeout(readMs);
            c.setUseCaches(false);
            c.setRequestProperty("Cache-Control", "no-cache");
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            return read(c.getInputStream(), 64 * 1024);
        } finally {
            c.disconnect();
        }
    }

    /** POST JSON -> body (also returns the body of 4xx/5xx replies). Throws only on network error. */
    static String postJson(String url, String json, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setUseCaches(false);
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            byte[] b = json.getBytes(UTF8);
            c.setFixedLengthStreamingMode(b.length);
            OutputStream os = c.getOutputStream();
            os.write(b);
            os.close();
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            return in == null ? "" : read(in, 64 * 1024);
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in, int max) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        try {
            while ((n = in.read(buf)) > 0 && bo.size() < max) bo.write(buf, 0, n);
        } finally {
            in.close();
        }
        return new String(bo.toByteArray(), UTF8);
    }

    /**
     * Asks the server who it is. Returns its version (e.g. "2.99.21") if it is an
     * AIS Inventory TV server, otherwise null.
     */
    static String ping(String base, int connectMs, int readMs) {
        try {
            String body = get(base + "/api/ping", connectMs, readMs);
            JSONObject o = new JSONObject(body);
            String v = o.optString("v", "");
            return v.isEmpty() ? null : v;
        } catch (Exception e) {
            return null;
        }
    }

    /** This TV's IPv4 address on the LAN ("" if not connected). */
    static String localIPv4() {
        try {
            Enumeration<NetworkInterface> it = NetworkInterface.getNetworkInterfaces();
            if (it == null) return "";
            for (NetworkInterface ni : Collections.list(it)) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress() && a.isSiteLocalAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) { }
        return "";
    }

    interface ScanListener {
        void onFound(String ip, String version);
        void onDone(List<String> ips, String subnet);
    }

    /**
     * Finds AIS Inventory TV servers on this TV's subnet (x.x.x.1-254) by asking
     * each address for /api/ping on the given port. Runs in the background; the
     * listener is called from background threads.
     */
    static void scan(final int port, final ScanListener l) {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<String> found = Collections.synchronizedList(new ArrayList<String>());
                String me = localIPv4();
                if (me.isEmpty()) {
                    l.onDone(found, "");
                    return;
                }
                final String prefix = me.substring(0, me.lastIndexOf('.') + 1);
                ExecutorService pool = Executors.newFixedThreadPool(48);
                final AtomicInteger left = new AtomicInteger(254);
                for (int i = 1; i <= 254; i++) {
                    final String ip = prefix + i;
                    pool.execute(new Runnable() {
                        @Override public void run() {
                            String v = ping("http://" + ip + ":" + port, 700, 1200);
                            if (v != null) {
                                found.add(ip);
                                l.onFound(ip, v);
                            }
                            left.decrementAndGet();
                        }
                    });
                }
                pool.shutdown();
                try { pool.awaitTermination(40, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                l.onDone(new ArrayList<String>(found), prefix + "1-254");
            }
        }, "lan-scan").start();
    }
}
