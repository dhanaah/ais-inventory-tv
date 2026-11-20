package com.aisglass.inventorytv;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

/**
 * If the app ever crashes, relaunch the dashboard 2 s later instead of leaving
 * the TV on its Home screen. Developed by RYZ Systems.
 */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler((thread, err) -> {
            try {
                Intent i = new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                int fl = PendingIntent.FLAG_CANCEL_CURRENT
                        | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
                PendingIntent pi = PendingIntent.getActivity(this, 1, i, fl);
                AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                if (am != null) am.set(AlarmManager.RTC, System.currentTimeMillis() + 2000, pi);
            } catch (Throwable ignored) { }
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        });
    }
}
