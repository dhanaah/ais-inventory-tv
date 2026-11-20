package com.aisglass.inventorytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * AIS Inventory TV - full-screen kiosk for the AIS Inventory Status dashboard.
 *
 *  - Loads http://<server>:8080/tv.html full screen, keeps the screen on.
 *  - Registered as a HOME app: when the TV "goes Home" it shows the dashboard.
 *  - Reconnects by itself when the server / LAN is down (retries every 10-60 s).
 *  - Health check every 2 min + full refresh every 6 h (long-running TVs).
 *  - Recovers if the WebView renderer crashes.
 *  - BACK  = back inside the dashboard (Settings -> TV); never exits.
 *  - Long-press BACK, or MENU / SETTINGS key = PIN-protected app Setup.
 *
 * Developed by RYZ Systems.
 */
public class MainActivity extends Activity {

    /** true while the dashboard is in front (read by KeepFrontService). */
    static volatile boolean sResumed = false;
    private static boolean sAutoSetupShown = false;

    private static final long HEALTH_MS = 2 * 60 * 1000L;        // page health check
    private static final long FULL_REFRESH_MS = 6 * 60 * 60 * 1000L;
    private static final long LONG_PRESS_MS = 900L;

    private final Handler h = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private WebView web;
    private TextView overlay;
    private String url = "";
    private boolean errorFlag = false;
    private int retrySec = 10;
    private long backDownAt = 0;
    private boolean longFired = false;
    private boolean pinDialogOpen = false;

    private final Runnable retry = new Runnable() {
        @Override public void run() { loadDashboard(); }
    };
    private final Runnable health = new Runnable() {
        @Override public void run() { checkHealth(); h.postDelayed(this, HEALTH_MS); }
    };
    private final Runnable fullRefresh = new Runnable() {
        @Override public void run() {
            if (!errorFlag) loadDashboard();
            h.postDelayed(this, FULL_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B1437);
        createWebView();

        overlay = new TextView(this);
        overlay.setTextColor(Color.WHITE);
        overlay.setTextSize(22);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(60, 60, 60, 60);
        overlay.setBackgroundColor(0xE60B1437);
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        hideSystemUi();

        if (Prefs.keepFront(this)) KeepFrontService.start(this);
        h.postDelayed(fullRefresh, FULL_REFRESH_MS);
    }

    private void createWebView() {
        web = new WebView(this);
        web.setBackgroundColor(0xFF0B1437);
        web.setKeepScreenOn(true);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);     // always fresh (settings changes)
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " AISInventoryTV/" + Prefs.VERSION);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new Client());
        root.addView(web, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onResume() {
        super.onResume();
        sResumed = true;
        hideSystemUi();
        String saved = Prefs.url(this);
        if (saved.isEmpty()) {
            if (!sAutoSetupShown) {          // first run: go straight to Setup
                sAutoSetupShown = true;
                startActivity(new Intent(this, SetupActivity.class));
            } else {
                showOverlay("AIS Inventory TV is not set up yet.\n\n"
                        + "Press OK or MENU to open Setup.\n\n" + Prefs.SIGNATURE);
            }
            return;
        }
        if (!saved.equals(url) || web.getUrl() == null || "about:blank".equals(web.getUrl())) {
            url = saved;
            loadDashboard();
        }
        web.onResume();
        web.resumeTimers();
        h.removeCallbacks(health);
        h.postDelayed(health, HEALTH_MS);
        if (Prefs.keepFront(this)) KeepFrontService.start(this);
    }

    @Override
    protected void onPause() {
        sResumed = false;
        h.removeCallbacks(health);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (web != null) {
            root.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    // ------------------------------------------------------------ loading ---

    private void loadDashboard() {
        h.removeCallbacks(retry);
        if (url.isEmpty() || web == null) return;
        errorFlag = false;
        showOverlay("Connecting to the dashboard...\n\n" + url);
        web.loadUrl(url);
    }

    private void showOffline(String why) {
        errorFlag = true;
        showOverlay("Cannot reach the dashboard server\n\n" + url + "\n(" + why + ")\n\n"
                + "Retrying every " + retrySec + " s - it will come back by itself.\n\n"
                + "Check: server PC is ON and \"Start TV Server\" is running.\n"
                + "Long-press BACK or press MENU for Setup.");
        try { web.stopLoading(); web.loadUrl("about:blank"); } catch (Throwable ignored) { }
        h.removeCallbacks(retry);
        h.postDelayed(retry, retrySec * 1000L);
        retrySec = Math.min(60, retrySec + 10);
    }

    private void showOverlay(String text) {
        overlay.setText(text);
        overlay.setVisibility(View.VISIBLE);
    }

    private void hideOverlay() { overlay.setVisibility(View.GONE); }

    /** If the page went blank (rare WebView glitch on long-running TVs), reload it. */
    private void checkHealth() {
        if (errorFlag || web == null || url.isEmpty()) return;
        web.evaluateJavascript(
                "(function(){try{return (document.body&&document.body.innerText||'').length}catch(e){return 0}})()",
                new ValueCallback<String>() {
                    @Override public void onReceiveValue(String v) {
                        if (v == null || "0".equals(v) || "null".equals(v)) loadDashboard();
                    }
                });
    }

    private class Client extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String u) {
            if (!errorFlag && u != null && !u.startsWith("about:")) {
                hideOverlay();
                retrySec = 10;
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView view, int code, String desc, String failingUrl) {
            // Called for the main page only (all Android versions).
            showOffline(desc == null ? "network error" : desc);
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest req, WebResourceResponse resp) {
            if (Build.VERSION.SDK_INT >= 21 && req != null && req.isForMainFrame()
                    && resp != null && resp.getStatusCode() >= 500) {
                showOffline("server error " + resp.getStatusCode());
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            // The page engine crashed / was killed for memory: rebuild it.
            try {
                root.removeView(view);
                view.destroy();
            } catch (Throwable ignored) { }
            web = null;
            createWebView();
            loadDashboard();
            return true;
        }
    }

    // --------------------------------------------------------------- keys ---

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        int action = e.getAction();

        if (code == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) {
                    backDownAt = e.getEventTime();
                    longFired = false;
                } else if (!longFired && e.getEventTime() - backDownAt >= LONG_PRESS_MS) {
                    longFired = true;
                    openSetupWithPin();
                }
            } else if (action == KeyEvent.ACTION_UP) {
                if (!longFired) {
                    if (e.getEventTime() - backDownAt >= LONG_PRESS_MS) {
                        openSetupWithPin();          // remotes that send no key-repeat
                    } else if (web != null && web.canGoBack()) {
                        web.goBack();                // e.g. Settings page -> TV
                    }                                // else: stay (kiosk - never exit)
                }
                longFired = false;
            }
            return true;
        }

        if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SETTINGS) {
            if (action == KeyEvent.ACTION_UP) openSetupWithPin();
            return true;
        }

        if (url.isEmpty() && action == KeyEvent.ACTION_UP
                && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) {
            startActivity(new Intent(this, SetupActivity.class));
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /**
     * Setup is protected with the SAME PIN as the dashboard Settings page
     * (checked against the server). If the server cannot be reached, Setup
     * opens anyway so the address can be fixed.
     */
    private void openSetupWithPin() {
        if (pinDialogOpen) return;
        final String saved = Prefs.url(this);
        if (saved.isEmpty()) {
            startActivity(new Intent(this, SetupActivity.class));
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(50, 30, 50, 10);
        TextView t = new TextView(this);
        t.setText("Enter the dashboard Settings PIN to open the app Setup.");
        t.setTextSize(18);
        final EditText pin = new EditText(this);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setTextSize(24);
        pin.setHint("PIN");
        box.addView(t);
        box.addView(pin);

        pinDialogOpen = true;
        final AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("AIS Inventory TV - Setup")
                .setView(box)
                .setPositiveButton("Unlock", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.setOnDismissListener(di -> { pinDialogOpen = false; hideSystemUi(); });
        d.setOnShowListener(di -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String p = pin.getText().toString().trim();
            if (p.isEmpty()) { pin.setError("Enter the PIN"); return; }
            final String base = Prefs.base(saved);
            new Thread(() -> {
                Boolean ok;
                try {
                    String body = Net.postJson(base + "/api/verify",
                            new JSONObject().put("pin", p).toString(), 5000);
                    ok = new JSONObject(body).optBoolean("ok", false);
                } catch (Exception ex) {
                    ok = null;                       // server unreachable
                }
                final Boolean res = ok;
                runOnUiThread(() -> {
                    if (res == null) {
                        Toast.makeText(this, "Server not reachable - Setup unlocked so you can fix the address.",
                                Toast.LENGTH_LONG).show();
                        d.dismiss();
                        startActivity(new Intent(this, SetupActivity.class));
                    } else if (res) {
                        d.dismiss();
                        startActivity(new Intent(this, SetupActivity.class));
                    } else {
                        pin.setText("");
                        pin.setError("Wrong PIN");
                    }
                });
            }, "pin-check").start();
        }));
        d.show();
        pin.requestFocus();
    }
}
