package com.aisglass.inventorytv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Starts the dashboard when the TV powers on (and after an app update).
 * Developed by RYZ Systems.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (Prefs.url(c).isEmpty()) return;          // not set up yet
        if (Prefs.keepFront(c)) KeepFrontService.start(c);
        try {
            c.startActivity(new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) {
            // Android 10+ may block this unless the app is the Home app or has
            // "Display over other apps"; the watchdog / Home role covers it.
        }
    }
}
