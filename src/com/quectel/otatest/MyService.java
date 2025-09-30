package com.quectel.otatest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class MyService extends Service {
    private static final String TAG = "MyService";
    private static final String CHANNEL_ID = "OTA_SERVICE_CHANNEL";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        
        // Start the service as a foreground service
        startForeground(NOTIFICATION_ID, createSuccessNotification());
        
        // Show success notification
        showSuccessNotification();
        
        // Return START_STICKY so the service is restarted if it gets killed
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // This service doesn't support binding
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
    }

    /**
     * Create notification channel for Android O and above
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "OTA Service Channel";
            String description = "Channel for OTA background service notifications";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Create the notification that will be shown when service starts successfully
     */
    private Notification createSuccessNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OTA Service Started")
                .setContentText("Background service started successfully after boot")
                .setSmallIcon(R.drawable.easyftptest)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
    }

    /**
     * Show a temporary notification indicating successful service start
     */
    private void showSuccessNotification() {
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (notificationManager != null) {
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                0, 
                notificationIntent, 
                PendingIntent.FLAG_IMMUTABLE
            );

            Notification successNotification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("OTA Service Status")
                    .setContentText("Service started successfully! Tap to open app.")
                    .setSmallIcon(R.drawable.easyftptest)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build();

            // Show the success notification with a different ID
            notificationManager.notify(NOTIFICATION_ID + 1, successNotification);
            
            Log.d(TAG, "Success notification displayed");
        }
    }
}