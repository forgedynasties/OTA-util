package com.quectel.otatest;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class UpdateService extends Service {
    private static final String TAG = "UpdateService";
    private static final String CHANNEL_ID = "OTA_UPDATE_CHANNEL";
    private static final int NOTIFICATION_ID = 1001;
    private static final int CHECK_INTERVAL = 60000; // 1 minute
    private static final String SERVER_URL = "http://10.32.1.11:8080";
    private static final String UPDATE_FILE = "update.zip";
    private static final String FULL_UPDATE_URL = SERVER_URL + "/" + UPDATE_FILE;
    
    private Handler handler;
    private Runnable checkUpdatesRunnable;
    private NotificationManager notificationManager;
    private ConnectivityManager connectivityManager;
    private List<String> availableUpdates = new ArrayList<>();
    private boolean isCheckingUpdates = false;
    
    private BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction()) ||
                WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction()) ||
                "com.quectel.otatest.REFRESH_UPDATES".equals(intent.getAction())) {
                
                Log.d(TAG, "Network state changed or refresh requested");
                if (isWifiConnected()) {
                    checkForUpdates();
                }
            }
        }
    };
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "UpdateService created");
        
        handler = new Handler(Looper.getMainLooper());
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        
        createNotificationChannel();
        startForegroundService();
        registerNetworkReceiver();
        startUpdateCheck();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "UpdateService started");
        return START_STICKY; // Restart if killed
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "OTA Update Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background service for OTA updates");
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private void registerNetworkReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction("com.quectel.otatest.REFRESH_UPDATES");
        registerReceiver(networkReceiver, filter);
    }
    
    private boolean isWifiConnected() {
        try {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            boolean isConnected = networkInfo != null && networkInfo.isConnected() && 
                                networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
            Log.d(TAG, "WiFi connected: " + isConnected);
            return isConnected;
        } catch (Exception e) {
            Log.e(TAG, "Error checking WiFi status", e);
            return false;
        }
    }
    
    private void startForegroundService() {
        updateNotification("OTA Update Service", "Waiting for WiFi connection...");
    }
    
    private void updateNotification(String title, String text) {
        Intent notificationIntent = new Intent(this, demo.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
            
        startForeground(NOTIFICATION_ID, notification);
    }
    
    private void startUpdateCheck() {
        checkUpdatesRunnable = new Runnable() {
            @Override
            public void run() {
                if (isWifiConnected()) {
                    checkForUpdates();
                } else {
                    Log.d(TAG, "WiFi not connected, skipping update check");
                    updateNotification("OTA Update Service", "Waiting for WiFi connection...");
                }
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        handler.post(checkUpdatesRunnable);
    }
    
    private void checkForUpdates() {
        if (isCheckingUpdates) {
            Log.d(TAG, "Update check already in progress");
            return;
        }
        
        isCheckingUpdates = true;
        updateNotification("OTA Update Service", "Checking for updates...");
        
        new Thread(() -> {
            try {
                Log.d(TAG, "Checking for updates on server...");
                List<String> updates = getAvailableUpdates();
                
                if (!updates.isEmpty() && !updates.equals(availableUpdates)) {
                    availableUpdates = new ArrayList<>(updates);
                    Log.i(TAG, "Found " + updates.size() + " updates: " + updates);
                    showUpdateNotification(updates);
                    
                    // Broadcast to activity if running
                    Intent broadcast = new Intent("com.quectel.otatest.UPDATES_FOUND");
                    broadcast.putStringArrayListExtra("updates", new ArrayList<>(updates));
                    sendBroadcast(broadcast);
                } else if (updates.isEmpty()) {
                    Log.d(TAG, "No updates found");
                    updateNotification("OTA Update Service", "No updates available");
                } else {
                    Log.d(TAG, "Same updates as before, no notification needed");
                    updateNotification("OTA Update Service", updates.size() + " updates available");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error checking for updates", e);
                updateNotification("OTA Update Service", "Error checking updates");
            } finally {
                isCheckingUpdates = false;
            }
        }).start();
    }
    
    private List<String> getAvailableUpdates() throws Exception {
        List<String> updates = new ArrayList<>();
        
        // Check if specific update.zip file exists
        URL url = new URL(FULL_UPDATE_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("HEAD"); // Use HEAD to check existence without downloading
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        
        int responseCode = connection.getResponseCode();
        connection.disconnect();
        
        if (responseCode == HttpURLConnection.HTTP_OK) {
            updates.add(UPDATE_FILE);
            Log.d(TAG, "Found update file: " + UPDATE_FILE);
        } else {
            Log.d(TAG, "Update file not found, response code: " + responseCode);
        }
        
        return updates;
    }
    
    private void showUpdateNotification(List<String> updates) {
        // Auto-install if update found
        if (!updates.isEmpty()) {
            Log.i(TAG, "Update available, starting auto-installation");
            startAutoInstallation();
        }
        
        Intent intent = new Intent(this, demo.class);
        intent.putStringArrayListExtra("available_updates", new ArrayList<>(updates));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String contentText = "Update available - Installing automatically";
            
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("OTA Update Found")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .build();
            
        notificationManager.notify(NOTIFICATION_ID + 1, notification);
        updateNotification("OTA Update Service", contentText);
    }
    
    private void startAutoInstallation() {
        updateNotification("OTA Update Service", "Auto-installing update...");
        
        // Broadcast to demo activity to start installation
        Intent broadcast = new Intent("com.quectel.otatest.AUTO_INSTALL");
        broadcast.putExtra("update_url", FULL_UPDATE_URL);
        sendBroadcast(broadcast);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && checkUpdatesRunnable != null) {
            handler.removeCallbacks(checkUpdatesRunnable);
        }
        if (networkReceiver != null) {
            try {
                unregisterReceiver(networkReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering network receiver", e);
            }
        }
        Log.i(TAG, "UpdateService destroyed");
    }
}
}
