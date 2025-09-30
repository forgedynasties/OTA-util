package com.quectel.otatest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class MyService extends Service {
    private static final String CHANNEL_ID = "OTA_SERVICE_CHANNEL";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OTA Service")
                .setContentText("Running after boot…")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // replace with your app icon
                .build();

        startForeground(1, notification); // REQUIRED
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.d("MyService", "Service started after boot");
        // TODO: your OTA logic here
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.d("MyService", "Service destroyed");
    }

    @Nullable
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
            }
        }
    }
}
