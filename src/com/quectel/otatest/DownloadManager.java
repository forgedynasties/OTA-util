package com.quectel.otatest;

import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadManager {
    private static final String TAG = "DownloadManager";
    private static final String DEST_PATH = "/data/ota_package/update.zip";
    private static final int BUFFER_SIZE = 4096;
    
    private Handler mainHandler;
    
    public DownloadManager(Handler mainHandler) {
        this.mainHandler = mainHandler;
    }
    
    public void download(String urlString, DownloadCallback callback) {
        try {
            Log.d(TAG, "Starting download from: " + urlString);
            
            createOtaDirectory();
            downloadFile(urlString, callback);
            
            mainHandler.post(() -> callback.onSuccess());
            
        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            mainHandler.post(() -> callback.onError(e.getMessage()));
        }
    }
    
    private void createOtaDirectory() {
        File otaDir = new File("/data/ota_package");
        if (!otaDir.exists()) {
            otaDir.mkdirs();
            Log.i(TAG, "OTA directory created");
        }
    }
    
    private void downloadFile(String urlString, DownloadCallback callback) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.connect();
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Server error: " + responseCode);
        }
        
        int fileLength = connection.getContentLength();
        Log.i(TAG, "File size: " + fileLength + " bytes");
        
        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(DEST_PATH)) {
            
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0;
            int count;
            
            while ((count = input.read(buffer)) != -1) {
                total += count;
                output.write(buffer, 0, count);
                
                if (fileLength > 0) {
                    final int progress = (int) (total * 100 / fileLength);
                    mainHandler.post(() -> callback.onProgress(progress));
                }
            }
            
            Log.i(TAG, "File transfer completed. Total bytes: " + total);
        }
    }
    
    public interface DownloadCallback {
        void onProgress(int progress);
        void onSuccess();
        void onError(String error);
    }
}