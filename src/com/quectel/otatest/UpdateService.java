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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.File;
import android.content.SharedPreferences;

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
    private boolean isDownloading = false;
    private boolean isInstalling = false;
    private int downloadProgress = 0;
    private int installProgress = 0;
    private String currentUpdateUrl = "";
    private SharedPreferences syncPrefs;
    private static final String SYNC_PREFS = "ota_sync";
    private static final String KEY_DOWNLOAD_PROGRESS = "download_progress";
    private static final String KEY_INSTALL_PROGRESS = "install_progress";
    private static final String KEY_CURRENT_STATE = "current_state";
    private static final String KEY_STATUS_MESSAGE = "status_message";
    
    private BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action) ||
                WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action) ||
                "com.quectel.otatest.REFRESH_UPDATES".equals(action)) {
                
                Log.d(TAG, "Network state changed or refresh requested");
                if (isWifiConnected()) {
                    checkForUpdates();
                }
            } else if ("com.quectel.otatest.CONTINUE_INSTALL".equals(action)) {
                // User clicked notification to continue installation
                startInstallationProcess();
            } else if ("com.quectel.otatest.INSTALL_PROGRESS".equals(action)) {
                // Update installation progress from demo activity
                int progress = intent.getIntExtra("progress", 0);
                String message = intent.getStringExtra("message");
                updateInstallProgress(progress, message != null ? message : "Installing...");
            } else if ("com.quectel.otatest.INSTALL_COMPLETE".equals(action)) {
                // Installation completed
                boolean success = intent.getBooleanExtra("success", false);
                String message = intent.getStringExtra("message");
                handleInstallationComplete(success, message);
            } else if ("com.quectel.otatest.SYNC_REQUEST".equals(action)) {
                // Demo activity requesting current status
                sendCurrentStatusToDemo();
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
        syncPrefs = getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE);
        
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
        filter.addAction("com.quectel.otatest.CONTINUE_INSTALL");
        filter.addAction("com.quectel.otatest.INSTALL_PROGRESS");
        filter.addAction("com.quectel.otatest.INSTALL_COMPLETE");
        filter.addAction("com.quectel.otatest.SYNC_REQUEST");
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
        // Start download if update found
        if (!updates.isEmpty()) {
            Log.i(TAG, "Update available, starting download");
            currentUpdateUrl = FULL_UPDATE_URL;
            startDownload();
        }
        
        Intent intent = new Intent(this, demo.class);
        intent.putStringArrayListExtra("available_updates", new ArrayList<>(updates));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String contentText = "Update found - Starting download...";
            
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("OTA Update Found")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setPriority(Notification.PRIORITY_HIGH)
            .build();
            
        notificationManager.notify(NOTIFICATION_ID + 1, notification);
        updateNotification("OTA Update Service", contentText);
    }
    
    private void startDownload() {
        if (isDownloading) {
            Log.d(TAG, "Download already in progress");
            return;
        }
        
        isDownloading = true;
        downloadProgress = 0;
        updateNotification("OTA Update Service", "Starting download...");
        
        new Thread(() -> {
            try {
                downloadUpdateFile(currentUpdateUrl);
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                handleDownloadError(e.getMessage());
            }
        }).start();
    }
    
    private void downloadUpdateFile(String urlString) throws Exception {
        String downloadPath = "/storage/emulated/0/update.zip";
        
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        connection.connect();
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new Exception("Server returned HTTP " + responseCode);
        }
        
        int fileLength = connection.getContentLength();
        java.io.InputStream input = connection.getInputStream();
        java.io.FileOutputStream output = new java.io.FileOutputStream(downloadPath);
        
        byte[] buffer = new byte[4096];
        long total = 0;
        int count;
        
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (fileLength > 0) {
                downloadProgress = (int) (total * 100 / fileLength);
                updateDownloadProgress(downloadProgress);
            }
            output.write(buffer, 0, count);
        }
        
        output.close();
        input.close();
        connection.disconnect();
        
        Log.i(TAG, "Download completed: " + total + " bytes");
        handleDownloadComplete();
    }
    
    private void updateDownloadProgress(int progress) {
        String message = "Downloading update... " + progress + "%";
        
        // Save state to SharedPreferences for sync
        syncPrefs.edit()
            .putInt(KEY_DOWNLOAD_PROGRESS, progress)
            .putString(KEY_CURRENT_STATE, "download")
            .putString(KEY_STATUS_MESSAGE, message)
            .apply();
        
        // Update notification
        Intent intent = new Intent(this, demo.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading OTA Update")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
            
        notificationManager.notify(NOTIFICATION_ID + 1, notification);
        updateNotification("OTA Update Service", message);
        
        // Broadcast to demo activity
        Intent broadcast = new Intent("com.quectel.otatest.DOWNLOAD_PROGRESS");
        broadcast.putExtra("progress", progress);
        broadcast.putExtra("message", message);
        sendBroadcast(broadcast);
    }
    
    private void handleDownloadComplete() {
        isDownloading = false;
        downloadProgress = 100;
        
        // Create notification asking user to continue installation
        Intent continueIntent = new Intent("com.quectel.otatest.CONTINUE_INSTALL");
        PendingIntent continuePendingIntent = PendingIntent.getBroadcast(
            this, 0, continueIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Intent openAppIntent = new Intent(this, demo.class);
        openAppIntent.putExtra("download_complete", true);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Update Download Complete")
            .setContentText("Tap to continue installation")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_media_play, "Install Now", continuePendingIntent)
            .build();
            
        notificationManager.notify(NOTIFICATION_ID + 1, notification);
        updateNotification("OTA Update Service", "Download complete - Ready to install");
        
        // Broadcast download completion to demo activity
        Intent broadcast = new Intent("com.quectel.otatest.DOWNLOAD_COMPLETE");
        sendBroadcast(broadcast);
    }
    
    private void handleDownloadError(String error) {
        isDownloading = false;
        
        String message = "Download failed: " + error;
        updateNotification("OTA Update Service", message);
        
        // Broadcast error to demo activity
        Intent broadcast = new Intent("com.quectel.otatest.DOWNLOAD_ERROR");
        broadcast.putExtra("error", error);
        sendBroadcast(broadcast);
    }
    
    private void startInstallationProcess() {
        if (isInstalling) {
            Log.d(TAG, "Installation already in progress");
            return;
        }
        
        isInstalling = true;
        installProgress = 0;
        updateNotification("OTA Update Service", "Starting installation...");
        
        // Broadcast to demo activity to start installation
        Intent broadcast = new Intent("com.quectel.otatest.START_INSTALL");
        broadcast.putExtra("update_url", currentUpdateUrl);
        sendBroadcast(broadcast);
    }
    
    private void updateInstallProgress(int progress, String message) {
        installProgress = progress;
        
        // Save state to SharedPreferences for sync
        syncPrefs.edit()
            .putInt(KEY_INSTALL_PROGRESS, progress)
            .putString(KEY_CURRENT_STATE, "install")
            .putString(KEY_STATUS_MESSAGE, message)
            .apply();
        
        // Update notification
        Intent intent = new Intent(this, demo.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Installing OTA Update")
            .setContentText(message + " " + progress + "%")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
            
        notificationManager.notify(NOTIFICATION_ID + 1, notification);
        updateNotification("OTA Update Service", message + " " + progress + "%");
    }
    
    private void handleInstallationComplete(boolean success, String message) {
        isInstalling = false;
        
        String title = success ? "Installation Complete" : "Installation Failed";
        String text = message != null ? message : (success ? "Reboot required" : "Installation error");
        
        Intent intent = new Intent(this, demo.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        int icon = success ? android.R.drawable.stat_sys_upload_done : android.R.drawable.stat_notify_error;
        
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(icon)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .build();
            
        notificationManager.notify(NOTIFICATION_ID + 1, notification);
        updateNotification("OTA Update Service", text);
    }
    
    private void sendCurrentStatusToDemo() {
        // Send current service status to demo activity
        String currentState = syncPrefs.getString(KEY_CURRENT_STATE, "");
        String statusMessage = syncPrefs.getString(KEY_STATUS_MESSAGE, "Ready");
        int progress = 0;
        
        if ("download".equals(currentState)) {
            progress = syncPrefs.getInt(KEY_DOWNLOAD_PROGRESS, 0);
        } else if ("install".equals(currentState)) {
            progress = syncPrefs.getInt(KEY_INSTALL_PROGRESS, 0);
        }
        
        if (!currentState.isEmpty() && progress > 0) {
            Intent broadcast = new Intent("com.quectel.otatest.SERVICE_STATUS");
            broadcast.putExtra("state", currentState);
            broadcast.putExtra("progress", progress);
            broadcast.putExtra("message", statusMessage);
            sendBroadcast(broadcast);
            Log.d(TAG, "Sent current status to demo: " + currentState + " " + progress + "% - " + statusMessage);
        }
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
