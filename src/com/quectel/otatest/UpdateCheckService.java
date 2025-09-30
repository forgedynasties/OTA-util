package com.quectel.otatest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;

public class UpdateCheckService extends Service {
    private static final String TAG = "UpdateCheckService";
    private static final String CHANNEL_ID = "OTA_UPDATE_CHANNEL";
    private static final String PREFS_NAME = "ota_prefs";
    private static final String KEY_LAST_CHECK = "last_check_time";
    private static final String KEY_NOTIFICATION_SHOWN = "notification_shown";
    
    // Configuration
    private static final String UPDATE_URL = "http://10.32.1.11:8080/update.zip";
    private static final long CHECK_INTERVAL = 60 * 1000; // 1 minute
    private static final int NOTIFICATION_ID = 1001;
    private static final int FOREGROUND_SERVICE_ID = 1002;
    
    private Handler handler;
    private ExecutorService executor;
    private Runnable checkRunnable;
    private boolean isServiceRunning = false;
    private PowerManager.WakeLock wakeLock;
    private NotificationManager notificationManager;
    
    @Override
    public void onCreate() {
        super.onCreate();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        Log.i(TAG, "=== UPDATE CHECK SERVICE CREATED ===");
        Log.i(TAG, "Service created at: " + timestamp);
        Log.i(TAG, "Process ID: " + android.os.Process.myPid());
        Log.i(TAG, "Thread ID: " + Thread.currentThread().getId());
        
        handler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Acquire wake lock to prevent system from sleeping
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UpdateCheckService::WakeLock");
        wakeLock.acquire();
        
        createNotificationChannel();
        setupCheckRunnable();
        
        Log.d(TAG, "UpdateCheckService initialized with wake lock");
        
        // Show immediate notification to verify service is running
        showServiceStartedNotification();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        Log.i(TAG, "=== UPDATE CHECK SERVICE STARTED ===");
        Log.i(TAG, "Service started at: " + timestamp);
        
        if (intent != null) {
            String startSource = intent.getStringExtra("start_source");
            int attemptNumber = intent.getIntExtra("attempt_number", 0);
            String startTimestamp = intent.getStringExtra("start_timestamp");
            
            Log.i(TAG, "Start source: " + startSource);
            Log.i(TAG, "Attempt number: " + attemptNumber);
            Log.i(TAG, "Start timestamp: " + startTimestamp);
        }
        
        // Start as foreground service to avoid battery optimization
        startForeground(FOREGROUND_SERVICE_ID, createForegroundNotification());
        
        if (!isServiceRunning) {
            isServiceRunning = true;
            startPeriodicCheck();
            Log.i(TAG, "Periodic update checks started");
        } else {
            Log.i(TAG, "Service already running, not starting duplicate checks");
        }
        
        // Return START_STICKY to restart service if killed
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "UpdateCheckService destroyed");
        
        isServiceRunning = false;
        if (handler != null && checkRunnable != null) {
            handler.removeCallbacks(checkRunnable);
        }
        if (executor != null) {
            executor.shutdown();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        // Restart the service immediately if destroyed
        Intent restartIntent = new Intent(getApplicationContext(), UpdateCheckService.class);
        startService(restartIntent);
        Log.d(TAG, "Service restart scheduled");
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel for update notifications
            NotificationChannel updateChannel = new NotificationChannel(
                CHANNEL_ID,
                "OTA Update Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            updateChannel.setDescription("Notifications for available OTA updates");
            
            // Channel for foreground service
            NotificationChannel serviceChannel = new NotificationChannel(
                "OTA_SERVICE_CHANNEL",
                "OTA Background Service",
                NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Keeps OTA update checker running in background");
            
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(updateChannel);
                notificationManager.createNotificationChannel(serviceChannel);
            }
        }
    }
    
    private void setupCheckRunnable() {
        checkRunnable = new Runnable() {
            @Override
            public void run() {
                if (isServiceRunning) {
                    performUpdateCheck();
                    // Schedule next check
                    handler.postDelayed(this, CHECK_INTERVAL);
                }
            }
        };
    }
    
    private void startPeriodicCheck() {
        Log.d(TAG, "Starting periodic update checks");
        // Start immediately, then repeat every CHECK_INTERVAL
        handler.post(checkRunnable);
    }
    
    private void performUpdateCheck() {
        Log.d(TAG, "Performing update check");
        
        // Check if already notified in this session
        if (isNotificationAlreadyShown()) {
            Log.d(TAG, "Notification already shown in this session, skipping");
            return;
        }
        
        // Check WiFi connectivity
        if (!isWifiConnected()) {
            Log.d(TAG, "WiFi not connected, skipping update check");
            return;
        }
        
        // Check for update in background thread
        executor.execute(() -> {
            try {
                boolean updateAvailable = checkUpdateUrl();
                if (updateAvailable) {
                    Log.i(TAG, "Update available! Showing notification");
                    showUpdateNotification();
                    markNotificationShown();
                } else {
                    Log.d(TAG, "No update available");
                }
                
                updateLastCheckTime();
                
            } catch (Exception e) {
                Log.e(TAG, "Error checking for updates", e);
            }
        });
    }
    
    private boolean isWifiConnected() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork == null || !activeNetwork.isConnected()) {
                return false;
            }
            
            // Check if it's WiFi
            return activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking WiFi connectivity", e);
            return false;
        }
    }
    
    private boolean checkUpdateUrl() throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(UPDATE_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD"); // Only check if file exists, don't download
            connection.setConnectTimeout(10000); // 10 seconds
            connection.setReadTimeout(10000);
            connection.connect();
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Update URL response code: " + responseCode);
            
            // File exists if we get 200 OK
            return responseCode == HttpURLConnection.HTTP_OK;
            
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    private void showUpdateNotification() {
        // Create intent to open MainActivity when notification is clicked
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT :
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        // Build notification
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        
        Notification notification = builder
            .setContentTitle("OTA Update Available")
            .setContentText("A new system update is ready to download")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build();
        
        // Show notification
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }
    
    private Notification createForegroundNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT :
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, "OTA_SERVICE_CHANNEL");
        } else {
            builder = new Notification.Builder(this);
        }
        
        return builder
            .setContentTitle("OTA Update Checker")
            .setContentText("Monitoring for system updates...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }
    
    private boolean isNotificationAlreadyShown() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_NOTIFICATION_SHOWN, false);
    }
    
    private void markNotificationShown() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_NOTIFICATION_SHOWN, true).apply();
    }
    
    private void updateLastCheckTime() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
    }
    
    // Method to reset notification flag (call when app is opened)
    public static void resetNotificationFlag(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_NOTIFICATION_SHOWN, false).apply();
        Log.d(TAG, "Notification flag reset");
    }
    
    private void showServiceStartedNotification() {
        // Show a temporary notification to verify service started
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT :
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        Notification notification = builder
            .setContentTitle("OTA Service Started")
            .setContentText("UpdateCheckService started successfully at " + timestamp)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build();
        
        // Show notification for 10 seconds then auto-cancel
        if (notificationManager != null) {
            notificationManager.notify(999, notification);
            
            // Auto-cancel after 10 seconds
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                if (notificationManager != null) {
                    notificationManager.cancel(999);
                }
            }, 10000);
        }
        
        Log.i(TAG, "Service started notification displayed");
    }
}