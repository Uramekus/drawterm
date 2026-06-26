package org.echoline.drawterm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class DrawtermService extends Service {
    private android.os.PowerManager.WakeLock wakeLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("stop", false)) {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && "ACTION_TOGGLE_WAKELOCK".equals(intent.getAction())) {
            if (wakeLock == null) {
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Drawterm:WakeLock");
            }
            if (wakeLock.isHeld()) {
                wakeLock.release();
            } else {
                wakeLock.acquire();
            }
        }

        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("disconnect", true);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent piDisconnect = PendingIntent.getActivity(this, 1, i, piFlags);

        Intent wlIntent = new Intent(this, DrawtermService.class);
        wlIntent.setAction("ACTION_TOGGLE_WAKELOCK");
        PendingIntent piWakeLock = PendingIntent.getService(this, 2, wlIntent, piFlags);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("drawterm_conn", "Drawterm Connection", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, "drawterm_conn");
        } else {
            builder = new Notification.Builder(this);
        }

        boolean wlHeld = wakeLock != null && wakeLock.isHeld();
        String wlTitle = wlHeld ? "Release WakeLock" : "Acquire WakeLock";

        if (Build.VERSION.SDK_INT >= 20) {
            Notification.Action disconnectAction = new Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", piDisconnect).build();
            Notification.Action wakeLockAction = new Notification.Action.Builder(
                    android.R.drawable.ic_menu_manage, wlTitle, piWakeLock).build();
            builder.addAction(wakeLockAction);
            builder.addAction(disconnectAction);
        } else {
            builder.addAction(android.R.drawable.ic_menu_manage, wlTitle, piWakeLock);
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", piDisconnect);
        }

        Intent resumeIntent = new Intent(this, MainActivity.class);
        resumeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent piResume = PendingIntent.getActivity(this, 3, resumeIntent, piFlags);

        builder.setSmallIcon(R.drawable.ic_small)
            .setContentTitle("Drawterm Connected")
            .setContentText("WakeLock: " + (wlHeld ? "ON" : "OFF"))
            .setContentIntent(piResume)
            .setOngoing(true);

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1001, builder.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1001, builder.build());
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
