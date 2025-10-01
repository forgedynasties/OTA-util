package com.quectel.otatest;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadManager {
    private static final String TAG = "DownloadManager";
    private static final int BUFFER_SIZE = 4096;
    private static final int TIMEOUT_MS = 30000; // 30 seconds
    
    public DownloadManager() {
        // Default constructor
    }
    
    public boolean downloadFile(String urlString, String destPath, DownloadCallback callback) {
        Log.i(TAG, "=== Starting File Download ===");
        Log.d(TAG, "Source URL: " + urlString);
        Log.d(TAG, "Destination: " + destPath);
        Log.d(TAG, "Buffer size: " + BUFFER_SIZE + " bytes");
        Log.d(TAG, "Timeout: " + TIMEOUT_MS + "ms");
        
        long downloadStartTime = System.currentTimeMillis();
        HttpURLConnection connection = null;
        
        try {
            createDirectoryIfNeeded(destPath);
            Log.d(TAG, "Directory structure verified");
            
            URL url = new URL(urlString);
            Log.d(TAG, "URL object created, establishing connection...");
            
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            Log.d(TAG, "Connection timeouts configured");
            
            Log.d(TAG, "Connecting to server...");
            connection.connect();
            
            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();
            Log.d(TAG, "Server response: " + responseCode + " " + responseMessage);
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String error = "Server error: " + responseCode + " " + responseMessage;
                Log.e(TAG, error);
                if (callback != null) callback.onError(error);
                return false;
            }
            
            int fileLength = connection.getContentLength();
            Log.i(TAG, "File size: " + fileLength + " bytes (" + (fileLength / 1024.0 / 1024.0) + " MB)");
            
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(destPath)) {
                
                Log.d(TAG, "Starting data transfer...");
                byte[] buffer = new byte[BUFFER_SIZE];
                long total = 0;
                int count;
                long lastProgressTime = System.currentTimeMillis();
                int lastProgress = -1;
                
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    output.write(buffer, 0, count);
                    
                    if (fileLength > 0 && callback != null) {
                        final int progress = (int) (total * 100 / fileLength);
                        
                        // Log progress every 10% or every 5 seconds
                        if (progress != lastProgress && (progress % 10 == 0 || 
                            System.currentTimeMillis() - lastProgressTime > 5000)) {
                            Log.d(TAG, "Download progress: " + progress + "% (" + total + "/" + fileLength + " bytes)");
                            lastProgressTime = System.currentTimeMillis();
                            lastProgress = progress;
                        }
                        
                        callback.onProgress(progress);
                    }
                }
                
                long downloadDuration = System.currentTimeMillis() - downloadStartTime;
                double downloadSpeed = (total / 1024.0 / 1024.0) / (downloadDuration / 1000.0);
                
                Log.i(TAG, "✓ File transfer completed successfully");
                Log.d(TAG, "Total bytes transferred: " + total);
                Log.d(TAG, "Download duration: " + downloadDuration + "ms");
                Log.d(TAG, "Average speed: " + String.format("%.2f MB/s", downloadSpeed));
            }
            
            if (callback != null) {
                callback.onSuccess();
            }
            
            Log.i(TAG, "=== Download Completed Successfully ===");
            return true;
            
        } catch (Exception e) {
            long downloadDuration = System.currentTimeMillis() - downloadStartTime;
            Log.e(TAG, "Download failed after " + downloadDuration + "ms: " + e.getMessage(), e);
            Log.e(TAG, "Exception class: " + e.getClass().getName());
            
            if (callback != null) {
                callback.onError(e.getMessage());
            }
            return false;
        } finally {
            if (connection != null) {
                Log.d(TAG, "Closing HTTP connection");
                connection.disconnect();
            }
        }
    }
    
    public void download(String urlString, DownloadCallback callback) {
        downloadFile(urlString, "/data/ota_package/update.zip", callback);
    }
    
    private void createDirectoryIfNeeded(String filePath) {
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
            Log.i(TAG, "Directory created: " + parentDir.getPath());
        }
    }
    
    public interface DownloadCallback {
        void onProgress(int progress);
        void onSuccess();
        void onError(String error);
    }
}