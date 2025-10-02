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

public class MyService extends Service {
    private static final String TAG = "MyService";
    private static final String CHANNEL_ID = "OTA_SERVICE_CHANNEL";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "=== OTA Service Creation Started ===");
        Log.d(TAG, "Service process ID: " + android.os.Process.myPid());
        Log.d(TAG, "Service thread ID: " + Thread.currentThread().getId());
        
        try {
            createNotificationChannel();
            Log.d(TAG, "Notification channel created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to create notification channel: " + e.getMessage(), e);
        }
        
        Log.i(TAG, "=== OTA Service Created Successfully ===");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "=== OTA Service Start Command ===");
        Log.d(TAG, "Intent: " + (intent != null ? intent.toString() : "null"));
        Log.d(TAG, "Flags: " + flags + ", StartId: " + startId);
        
        try {
            Log.d(TAG, "Starting foreground service with notification ID: " + NOTIFICATION_ID);
            // Start the service as a foreground service
            startForeground(NOTIFICATION_ID, createSuccessNotification());
            Log.i(TAG, "✓ Service running in foreground mode");
            
            // Check for updates in background
            Log.d(TAG, "Initiating background update check...");
            checkForUpdates();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand: " + e.getMessage(), e);
        }
        
        Log.d(TAG, "Returning START_STICKY for service restart capability");
        Log.i(TAG, "=== OTA Service Started Successfully ===");
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
        Log.i(TAG, "=== OTA Service Destruction Started ===");
        Log.d(TAG, "Service uptime: " + (System.currentTimeMillis() - System.currentTimeMillis()) + "ms");
        
        try {
            super.onDestroy();
            Log.d(TAG, "Parent onDestroy() called successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy: " + e.getMessage(), e);
        }
        
        Log.i(TAG, "=== OTA Service Destroyed ===");
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
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle("OTA Service Started")
                .setContentText("Background service started successfully after boot")
                .setSmallIcon(R.drawable.easyftptest)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.setPriority(Notification.PRIORITY_DEFAULT);
        }

        return builder.build();
    }

    /**
     * Show a temporary notification indicating successful service start
     */
    private void showSuccessNotification() {
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (notificationManager != null) {
            Intent notificationIntent = new Intent(this, MainActivity.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(this, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(this);
            }

            builder.setContentTitle("OTA Service Status")
                    .setContentText("Service started successfully! Tap to open app.")
                    .setSmallIcon(R.drawable.easyftptest)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                builder.setPriority(Notification.PRIORITY_HIGH);
            }

            Notification successNotification = builder.build();

            // Show the success notification with a different ID
            notificationManager.notify(NOTIFICATION_ID + 1, successNotification);
            
            Log.d(TAG, "Success notification displayed");
        }
    }

    /**
     * Check for updates in background thread
     */
    private void checkForUpdates() {
        Log.i(TAG, "=== Starting Update Check Process ===");
        Log.d(TAG, "Creating background thread for update check...");
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Update check thread started (ID: " + Thread.currentThread().getId() + ")");
                long checkStartTime = System.currentTimeMillis();
                
                try {
                    Log.i(TAG, "Initiating detailed API update check...");
                    UpdateChecker.UpdateCheckResult result = UpdateChecker.checkUpdateExistsDetailed();
                    
                    long checkDuration = System.currentTimeMillis() - checkStartTime;
                    Log.d(TAG, "Detailed update check completed in " + checkDuration + "ms");
                    
                    if (result.errorMessage != null) {
                        Log.e(TAG, "API update check failed: " + result.errorMessage);
                        showUpdateCheckErrorNotification();
                    } else if (result.updateAvailable) {
                        Log.i(TAG, "✓ UPDATE AVAILABLE - Notifying user");
                        Log.d(TAG, "Update details - New build: " + result.newBuildId + ", Package: " + result.packageUrl);
                        showUpdateAvailableNotification(result);
                    } else {
                        Log.i(TAG, "✓ System is up to date - No update needed");
                        showNoUpdateNotification();
                    }
                } catch (Exception e) {
                    long checkDuration = System.currentTimeMillis() - checkStartTime;
                    Log.e(TAG, "Update check failed after " + checkDuration + "ms: " + e.getMessage(), e);
                    Log.e(TAG, "Exception class: " + e.getClass().getName());
                    showUpdateCheckErrorNotification();
                }
                
                Log.d(TAG, "Update check thread completed");
            }
        }).start();
        
        Log.d(TAG, "Background update check thread launched successfully");
    }

    /**
     * Show notification when update is available
     */
    private void showUpdateAvailableNotification(UpdateChecker.UpdateCheckResult result) {
        Log.i(TAG, "=== Creating Update Available Notification ===");
        
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (notificationManager != null) {
            Log.d(TAG, "NotificationManager obtained successfully");
            
            try {
                Intent updateIntent = new Intent(this, UpdateActivity.class);
                updateIntent.putExtra("update_available", true);
                updateIntent.putExtra("package_url", result.packageUrl);
                updateIntent.putExtra("new_build_id", result.newBuildId);
                updateIntent.putExtra("patch_notes", result.patchNotes);
                Log.d(TAG, "Created intent with full update details:");
                Log.d(TAG, "  package_url: " + result.packageUrl);
                Log.d(TAG, "  new_build_id: " + result.newBuildId);
                Log.d(TAG, "  patch_notes: " + result.patchNotes);
                
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, updateIntent, flags);
                Log.d(TAG, "PendingIntent created with flags: " + flags);

                Notification.Builder builder;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder = new Notification.Builder(this, CHANNEL_ID);
                    Log.d(TAG, "Using notification builder with channel ID: " + CHANNEL_ID);
                } else {
                    builder = new Notification.Builder(this);
                    Log.d(TAG, "Using legacy notification builder (pre-O)");
                }

                builder.setContentTitle("Update Available!")
                        .setContentText("New OTA update is available. Tap to install.")
                        .setSmallIcon(R.drawable.easyftptest)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true);
                        
                Log.d(TAG, "Notification content configured");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    builder.setPriority(Notification.PRIORITY_HIGH);
                    Log.d(TAG, "Set notification priority to HIGH");
                }

                int notificationId = NOTIFICATION_ID + 2;
                notificationManager.notify(notificationId, builder.build());
                Log.i(TAG, "✓ Update available notification displayed (ID: " + notificationId + ")");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to create update available notification: " + e.getMessage(), e);
            }
        } else {
            Log.e(TAG, "NotificationManager is null - cannot show update notification");
        }
    }

    /**
     * Show notification when no update is available
     */
    private void showNoUpdateNotification() {
        Log.i(TAG, "=== Creating No Update Available Notification ===");
        
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (notificationManager != null) {
            Log.d(TAG, "NotificationManager obtained for no-update notification");
            
            try {
                Notification.Builder builder;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder = new Notification.Builder(this, CHANNEL_ID);
                    Log.d(TAG, "Using notification builder with channel: " + CHANNEL_ID);
                } else {
                    builder = new Notification.Builder(this);
                    Log.d(TAG, "Using legacy notification builder");
                }

                builder.setContentTitle("No Update Available")
                        .setContentText("Your system is up to date.")
                        .setSmallIcon(R.drawable.easyftptest)
                        .setAutoCancel(true);
                        
                Log.d(TAG, "No-update notification content set");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    builder.setPriority(Notification.PRIORITY_DEFAULT);
                    Log.d(TAG, "Set notification priority to DEFAULT");
                }

                int notificationId = NOTIFICATION_ID + 3;
                notificationManager.notify(notificationId, builder.build());
                Log.i(TAG, "✓ No update notification displayed (ID: " + notificationId + ")");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to create no-update notification: " + e.getMessage(), e);
            }
        } else {
            Log.e(TAG, "NotificationManager is null - cannot show no-update notification");
        }
    }

    /**
     * Show notification when update check fails
     */
    private void showUpdateCheckErrorNotification() {
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (notificationManager != null) {
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(this, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(this);
            }

            builder.setContentTitle("Update Check Failed")
                    .setContentText("Could not check for updates. Please try again later.")
                    .setSmallIcon(R.drawable.easyftptest)
                    .setAutoCancel(true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                builder.setPriority(Notification.PRIORITY_DEFAULT);
            }

            notificationManager.notify(NOTIFICATION_ID + 4, builder.build());
            Log.d(TAG, "Update check error notification displayed");
        }
    }
}