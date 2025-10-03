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
        Log.i(TAG, "Current Build ID: " + UpdateChecker.getCurrentBuildId());
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Update check thread started (ID: " + Thread.currentThread().getId() + ")");
                long checkStartTime = System.currentTimeMillis();
                
                try {
                    Log.i(TAG, "Initiating API-based update check...");
                    OTAApiClient.UpdateResponse response = UpdateChecker.checkForUpdate();
                    
                    long checkDuration = System.currentTimeMillis() - checkStartTime;
                    Log.i(TAG, "Update check completed in " + checkDuration + "ms");
                    
                    if (response != null) {
                        Log.i(TAG, "=== Service Update Check Results ===");
                        Log.i(TAG, "API Status: " + response.status);
                        Log.i(TAG, "Build ID: " + response.buildId);
                        Log.i(TAG, "Package URL: " + response.packageUrl);
                        
                        if (response.isUpdateAvailable()) {
                            Log.i(TAG, "✓ UPDATE AVAILABLE - Preparing user notification");
                            Log.i(TAG, "📦 Download URL will be: " + response.getFullPackageUrl());
                            Log.i(TAG, "🔄 New Build: " + response.buildId);
                            Log.i(TAG, "📝 Patch Notes: " + response.patchNotes);
                            showUpdateAvailableNotification(response);
                        } else if (response.isUpToDate()) {
                            Log.i(TAG, "✅ System is up to date - No update needed");
                            showNoUpdateNotification();
                        } else if (response.isError()) {
                            Log.w(TAG, "❌ Server returned error: " + response.message);
                            showUpdateCheckErrorNotification("Server Error: " + response.message);
                        } else {
                            Log.w(TAG, "⚠️ Unexpected status: " + response.status);
                            showUpdateCheckErrorNotification("Unexpected response: " + response.status);
                        }
                        Log.i(TAG, "=================================");
                    } else {
                        Log.e(TAG, "❌ Update check failed - API returned null response");
                        showUpdateCheckErrorNotification("API request failed");
                    }
                } catch (Exception e) {
                    long checkDuration = System.currentTimeMillis() - checkStartTime;
                    Log.e(TAG, "Update check failed after " + checkDuration + "ms: " + e.getMessage(), e);
                    Log.e(TAG, "Exception class: " + e.getClass().getName());
                    Log.e(TAG, "Stack trace:", e);
                    showUpdateCheckErrorNotification("Exception: " + e.getMessage());
                }
                
                Log.d(TAG, "Update check thread completed");
            }
        }).start();
        
        Log.d(TAG, "Background update check thread launched successfully");
    }

    /**
     * Show notification when update is available
     */
    private void showUpdateAvailableNotification(OTAApiClient.UpdateResponse response) {
        Log.i(TAG, "=== Creating Update Available Notification ===");
        Log.i(TAG, "📦 Update Package URL: " + response.getFullPackageUrl());
        Log.i(TAG, "🔄 New Build ID: " + response.buildId);
        Log.i(TAG, "📝 Patch Notes: " + response.patchNotes);
        
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (notificationManager != null) {
            Log.d(TAG, "NotificationManager obtained successfully");
            
            try {
                Intent updateIntent = new Intent(this, UpdateActivity.class);
                updateIntent.putExtra("update_available", true);
                updateIntent.putExtra("download_url", response.getFullPackageUrl());
                updateIntent.putExtra("build_id", response.buildId);
                updateIntent.putExtra("patch_notes", response.patchNotes);
                Log.d(TAG, "Created intent for UpdateActivity with update info");
                Log.d(TAG, "Intent extras - download_url: " + response.getFullPackageUrl());
                Log.d(TAG, "Intent extras - build_id: " + response.buildId);
                
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
    private void showUpdateCheckErrorNotification(String errorMessage) {
        Log.i(TAG, "=== Creating Update Check Error Notification ===");
        Log.e(TAG, "Error message: " + errorMessage);
        
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (notificationManager != null) {
            Log.d(TAG, "NotificationManager obtained for error notification");
            
            try {
                Notification.Builder builder;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder = new Notification.Builder(this, CHANNEL_ID);
                    Log.d(TAG, "Using notification builder with channel: " + CHANNEL_ID);
                } else {
                    builder = new Notification.Builder(this);
                    Log.d(TAG, "Using legacy notification builder");
                }

                String contentText = errorMessage != null ? errorMessage : "Could not check for updates. Please try again later.";
                builder.setContentTitle("Update Check Failed")
                        .setContentText(contentText)
                        .setSmallIcon(R.drawable.easyftptest)
                        .setAutoCancel(true);
                        
                Log.d(TAG, "Error notification content set: " + contentText);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    builder.setPriority(Notification.PRIORITY_DEFAULT);
                    Log.d(TAG, "Set notification priority to DEFAULT");
                }

                int errorNotificationId = NOTIFICATION_ID + 4;
                notificationManager.notify(errorNotificationId, builder.build());
                Log.i(TAG, "✓ Error notification displayed (ID: " + errorNotificationId + ")");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to create error notification: " + e.getMessage(), e);
            }
        } else {
            Log.e(TAG, "NotificationManager is null - cannot show error notification");
        }
    }
    
    /**
     * Show notification when update check fails (legacy method)
     */
    private void showUpdateCheckErrorNotification() {
        showUpdateCheckErrorNotification(null);
    }
}