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
        
        URL url = new URL(SERVER_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new Exception("Server error: " + responseCode);
        }
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        
        while ((line = reader.readLine()) != null) {
            response.append(line).append("\n");
        }
        reader.close();
        connection.disconnect();
        
        // Parse HTML response to find update*.zip files
        String html = response.toString();
        Pattern pattern = Pattern.compile("href=[\"'](update[^\"']*\\.zip)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        
        while (matcher.find()) {
            String filename = matcher.group(1);
            if (!updates.contains(filename)) {
                updates.add(filename);
                Log.d(TAG, "Found update file: " + filename);
            }
        }
        
        return updates;
    }
    
    private void showUpdateNotification(List<String> updates) {
        Intent intent = new Intent(this, demo.class);
        intent.putStringArrayListExtra("available_updates", new ArrayList<>(updates));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String contentText = updates.size() == 1 ? 
            "1 update available: " + updates.get(0) : updates.size() + " updates available";
            
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("OTA Updates Available")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .build();
            
        notificationManager.notify(NOTIFICATION_ID + 1, notification);
        updateNotification("OTA Update Service", contentText);
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
