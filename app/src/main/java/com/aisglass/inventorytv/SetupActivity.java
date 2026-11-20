package com.aisglass.inventorytv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Setup screen of AIS Inventory TV - built for the TV remote
 * (arrow keys move between the big buttons, OK presses).
 * Developed by RYZ Systems.
 */
public class SetupActivity extends Activity {

    /** true while Setup is open (KeepFrontService must not pull the dashboard over it). */
    static volatile boolean sOpen = false;

    private static final int GREEN = 0xFF3ECF8E;
    private static final int NAVY = 0xFF22355F;
    private static final int BG = 0xFF0B1437;
    private static final long PAUSE_MS = 30 * 60 * 1000L;

    private EditText field;
    private TextView status;
    private TextView info;
    private CheckBox keep;
    private Button findBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(48), dp(28), dp(48), dp(28));
        sv.addView(col);

        TextView title = text("AIS Inventory TV  -  Setup", 30, Color.WHITE, true);
        col.addView(title);
        col.addView(text("v" + Prefs.VERSION + "  ·  " + Prefs.SIGNATURE, 14, 0xFF9FB6E0, false));

        info = text("", 15, 0xFFB9C9EE, false);
        info.setPadding(0, dp(10), 0, dp(4));
        col.addView(info);

        col.addView(text("Dashboard server address", 17, Color.WHITE, true), lp(0, dp(10)));
        field = new EditText(this);
        field.setSingleLine(true);
        field.setTextSize(22);
        field.setTextColor(0xFF0B1324);
        field.setHintTextColor(0xFF6B7A99);
        field.setHint("e.g. 192.168.1.50   (or press Find server automatically)");
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        GradientDrawable fbg = new GradientDrawable();
        fbg.setColor(0xFFEAF2FF);
        fbg.setCornerRadius(dp(10));
        fbg.setStroke(dp(2), 0x553ECF8E);
        field.setBackground(fbg);
        field.setPadding(dp(16), dp(12), dp(16), dp(12));
        String saved = Prefs.url(this);
        field.setText(saved);
        col.addView(field, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)));
        col.addView(text("Type only the server PC's IP if you like - port 8080 and /tv.html are added for you.",
                13, 0xFF9FB6E0, false), lp(0, dp(4)));

        status = text("", 17, 0xFFFFD27A, true);
        status.setPadding(0, dp(12), 0, dp(6));
        col.addView(status);

        // Row 1: find / test / save
        LinearLayout r1 = row();
        findBtn = btn("Find server automatically", v -> findServer());
        r1.addView(findBtn, weight());
        r1.addView(btn("Test connection", v -> testConnection()), weight());
        r1.addView(btn("Save & start dashboard", v -> saveAndStart()), weight());
        col.addView(r1);

        keep = new CheckBox(this);
        keep.setText("  Bring the dashboard back automatically if the TV leaves it (recommended)");
        keep.setTextColor(Color.WHITE);
        keep.setTextSize(17);
        keep.setChecked(Prefs.keepFront(this));
        keep.setButtonTintList(ColorStateList.valueOf(GREEN));
        keep.setPadding(dp(8), dp(8), 0, dp(8));
        keep.setOnCheckedChangeListener((b, on) -> Prefs.setKeepFront(this, on));
        col.addView(keep, lp(0, dp(12)));

        // Row 2: kiosk controls
        LinearLayout r2 = row();
        r2.addView(btn("Set as TV Home app", v -> openHomeSettings()), weight());
        r2.addView(btn("Allow auto-return", v -> openOverlayPermission()), weight());
        r2.addView(btn("Pause auto-return 30 min", v -> pause()), weight());
        col.addView(r2);

        // Row 3: exits
        LinearLayout r3 = row();
        r3.addView(btn("Android TV Settings", v -> openAndroidSettings()), weight());
        r3.addView(btn("Close setup", v -> closeSetup()), weight());
        col.addView(r3);

        col.addView(text("Tips:  Long-press BACK (or press MENU) on the dashboard to come back here  ·  "
                        + "the dashboard's own gear opens the inventory Settings (same PIN).",
                13, 0xFF9FB6E0, false), lp(0, dp(16)));

        setContentView(sv);
        (saved.isEmpty() ? findBtn : field).requestFocus();
        if (saved.isEmpty()) {
            status.setText("First time? Press  \"Find server automatically\"  - or type the server PC's IP.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sOpen = true;
        refreshInfo();
    }

    @Override
    protected void onPause() {
        sOpen = false;
        super.onPause();
    }

    private void refreshInfo() {
        String ip = Net.localIPv4();
        String ov;
        if (Build.VERSION.SDK_INT < 23) ov = "not needed";
        else ov = Settings.canDrawOverlays(this) ? "allowed" : "NOT allowed";
        String p = Prefs.paused(this) ? "  ·  auto-return PAUSED" : "";
        info.setText("This TV: " + (ip.isEmpty() ? "no network!" : ip)
                + "   ·   Android " + Build.VERSION.RELEASE
                + "   ·   auto-return permission: " + ov + p);
    }

    // ------------------------------------------------------------- actions ---

    private void findServer() {
        final String typed = Prefs.normalize(field.getText().toString());
        final int port = typed.isEmpty() ? Prefs.DEFAULT_PORT : Prefs.port(typed);
        if (Net.localIPv4().isEmpty()) {
            status.setText("This TV has no network connection. Connect Wi-Fi / LAN first.");
            return;
        }
        findBtn.setEnabled(false);
        status.setText("Searching this network for the AIS Inventory server (port " + port + ")... ~10 s");
        Net.scan(port, new Net.ScanListener() {
            private boolean first = true;

            @Override public void onFound(final String ip, final String version) {
                runOnUiThread(() -> {
                    if (first) {
                        first = false;
                        field.setText("http://" + ip + ":" + port + "/tv.html");
                    }
                    status.setText("Found server " + ip + "  (v" + version + ")  ...still searching");
                });
            }

            @Override public void onDone(final List<String> ips, final String subnet) {
                runOnUiThread(() -> {
                    findBtn.setEnabled(true);
                    if (ips.isEmpty()) {
                        status.setText("No server found on " + subnet + ".  Check the server PC is ON, "
                                + "\"Start TV Server\" is running and the TV is on the same network - "
                                + "or type the PC's IP above.");
                    } else if (ips.size() == 1) {
                        status.setText("✓ Server found: " + ips.get(0) + "   →  press  \"Save & start dashboard\".");
                    } else {
                        status.setText("✓ " + ips.size() + " servers found: " + join(ips)
                                + ".  First one selected - change the address if needed, then Save.");
                    }
                });
            }
        });
    }

    private void testConnection() {
        final String u = Prefs.normalize(field.getText().toString());
        if (u.isEmpty()) {
            status.setText("Enter the server address first (e.g. 192.168.1.50).");
            return;
        }
        field.setText(u);
        status.setText("Testing " + u + " ...");
        final String base = Prefs.base(u);
        new Thread(() -> {
            final String v = Net.ping(base, 3000, 4000);
            runOnUiThread(() -> status.setText(v != null
                    ? "✓ Connected - AIS Inventory server v" + v + ".  Press  \"Save & start dashboard\"."
                    : "✗ Cannot reach " + base + ".  Is the server PC on and \"Start TV Server\" running?"));
        }, "test").start();
    }

    private void saveAndStart() {
        String u = Prefs.normalize(field.getText().toString());
        if (u.isEmpty()) {
            status.setText("Enter a valid server address first (e.g. 192.168.1.50).");
            field.requestFocus();
            return;
        }
        Prefs.setUrl(this, u);
        Prefs.setKeepFront(this, keep.isChecked());
        Prefs.pauseFor(this, 0);              // saving resumes auto-return
        if (keep.isChecked()) KeepFrontService.start(this);
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    private void openHomeSettings() {
        Prefs.pauseFor(this, PAUSE_MS);       // don't yank the user back while choosing
        status.setText("Choose  \"AIS Inventory TV\"  as the Home app (tick \"Always\" if asked).  "
                + "Some Google TV models hide this - then keep auto-return ON and allow it.");
        if (!launch(new Intent(Settings.ACTION_HOME_SETTINGS))) {
            // Fallback: fire the Home chooser itself.
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            if (!launch(Intent.createChooser(home, "Choose Home app"))) launch(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void openOverlayPermission() {
        if (Build.VERSION.SDK_INT < 23) {
            status.setText("✓ Not needed on this TV - auto-return already works.");
            return;
        }
        if (Settings.canDrawOverlays(this)) {
            status.setText("✓ Auto-return is already allowed on this TV.");
            return;
        }
        Prefs.pauseFor(this, PAUSE_MS);
        status.setText("Turn ON  \"Display over other apps\"  for AIS Inventory TV, then press BACK.");
        if (!launch(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())))) {
            status.setText("This TV hides that setting. IT can enable it once with:\n"
                    + "adb shell appops set " + getPackageName() + " SYSTEM_ALERT_WINDOW allow\n"
                    + "(or simply set the app as the TV Home app).");
        }
    }

    private void pause() {
        Prefs.pauseFor(this, PAUSE_MS);
        status.setText("Auto-return paused for 30 minutes (Save & start resumes it).");
        refreshInfo();
    }

    private void openAndroidSettings() {
        Prefs.pauseFor(this, PAUSE_MS);
        launch(new Intent(Settings.ACTION_SETTINGS));
    }

    private void closeSetup() {
        if (!Prefs.url(this).isEmpty()) {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
        finish();
    }

    @Override
    public void onBackPressed() { closeSetup(); }

    // ----------------------------------------------------------------- UI ---

    private boolean launch(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private Button btn(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setMinHeight(dp(64));
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        b.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {android.R.attr.state_pressed},
                        {-android.R.attr.state_enabled}, {}},
                new int[]{0xFF052311, 0xFF052311, 0xFF7F8CA8, 0xFFEAF2FF}));
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, round(GREEN));
        sl.addState(new int[]{android.R.attr.state_pressed}, round(GREEN));
        sl.addState(new int[]{}, round(NAVY));
        b.setBackground(sl);
        b.setFocusable(true);
        b.setOnClickListener(l);
        return b;
    }

    private GradientDrawable round(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(12));
        return g;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(8), 0, 0);
        return r;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(6), 0, dp(6), 0);
        return p;
    }

    private LinearLayout.LayoutParams lp(int width, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                width == 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : width,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = top;
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }
}
