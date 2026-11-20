package com.aisglass.inventorytv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Watchdog: if anything (the TV's own Home screen, an input switch prompt, a
 * crashed browser...) covers the dashboard for ~45 s, bring the dashboard back.
 * Stays quiet while Setup is open, while auto-return is paused, or if the
 * option is switched off in Setup.
 *
 * On Android 10+ this needs "Display over other apps" (Setup -> Allow
 * auto-return) OR the app being the TV's Home app.
 * Developed by RYZ Systems.
 */
public class KeepFrontService extends Service {

    private static final long TICK_MS = 15_000L;
    private static final int AWAY_TICKS = 3;          // ~45 s away -> bring back
    private static final String CHANNEL = "keep_front";

    private final Handler h = new Handler(Looper.getMainLooper());
    private int awayTicks = 0;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Context c = KeepFrontService.this;
            if (!Prefs.keepFront(c)) {
                stopSelf();
                return;
            }
            boolean away = !Prefs.url(c).isEmpty()
                    && !Prefs.paused(c)
                    && !MainActivity.sResumed
                    && !SetupActivity.sOpen;
            if (away) {
                if (++awayTicks >= AWAY_TICKS) {
                    awayTicks = 0;
                    bringFront();
                }
            } else {
                awayTicks = 0;
            }
            h.postDelayed(this, TICK_MS);
        }
    };

    static void start(Context c) {
        try {
            Intent i = new Intent(c, KeepFrontService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Throwable ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        h.removeCallbacks(tick);
        h.postDelayed(tick, TICK_MS);
        return START_STICKY;
    }

    private void goForeground() {
        try {
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                    NotificationChannel ch = new NotificationChannel(CHANNEL,
                            "Keep dashboard on screen", NotificationManager.IMPORTANCE_MIN);
                    ch.setShowBadge(false);
                    nm.createNotificationChannel(ch);
                }
                b = new Notification.Builder(this, CHANNEL);
            } else {
                b = new Notification.Builder(this);
                b.setPriority(Notification.PRIORITY_MIN);
            }
            Notification n = b.setContentTitle("AIS Inventory TV")
                    .setContentText("Keeping the inventory dashboard on screen")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setOngoing(true)
                    .build();
            startForeground(7, n);
        } catch (Throwable ignored) { }
    }

    private void bringFront() {
        try {
            Intent i = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Throwable ignored) { }
    }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
