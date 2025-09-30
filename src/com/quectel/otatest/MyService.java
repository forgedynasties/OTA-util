package com.quectel.otatest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class MyService extends Service {
    private static final String TAG = "MyService";
    private static final String CHANNEL_ID = "OTA_SERVICE_CHANNEL";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate called");

        createNotificationChannel();

        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("OTA Service")
                    .setContentText("Running after boot…")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();
        } else {
            notification = new Notification.Builder(this)
                    .setContentTitle("OTA Service")
                    .setContentText("Running after boot…")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();
        }

        startForeground(1, notification); // REQUIRED
        Log.d(TAG, "Service started as foreground service");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started after boot");
        // TODO: your OTA logic here
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "OTA Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
                Log.d(TAG, "Notification channel created");
            }
        }
    }
}
        }
    }
}
