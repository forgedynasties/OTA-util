package com.quectel.otatest;

import android.util.Log;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static final String UPDATE_URL = "http://10.32.1.11:8080/update.zip";
    private static final int TIMEOUT_MS = 10000; // 10 seconds
    
    /**
     * Check if the update file exists
     * @return true if file exists and is accessible, false otherwise
     */
    public static boolean checkUpdateExists() {
        HttpURLConnection connection = null;
        long startTime = System.currentTimeMillis();
        
        try {
            Log.i(TAG, "Starting update check for URL: " + UPDATE_URL);
            Log.d(TAG, "Connection timeout: " + TIMEOUT_MS + "ms, Read timeout: " + TIMEOUT_MS + "ms");
            
            URL url = new URL(UPDATE_URL);
            Log.d(TAG, "Created URL object, opening connection...");
            
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            
            Log.d(TAG, "Connection configured, sending HEAD request...");
            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();
            
            boolean exists = (responseCode == HttpURLConnection.HTTP_OK);
            long duration = System.currentTimeMillis() - startTime;
            
            Log.i(TAG, "Update check completed in " + duration + "ms");
            Log.d(TAG, "Response: " + responseCode + " " + responseMessage + ", update exists: " + exists);
            
            if (exists) {
                Log.i(TAG, "✓ Update file is available at server");
            } else {
                Log.w(TAG, "✗ Update file not found (HTTP " + responseCode + ")");
            }
            
            return exists;
            
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - startTime;
            Log.e(TAG, "Update check failed after " + duration + "ms: " + e.getMessage());
            Log.e(TAG, "Exception type: " + e.getClass().getSimpleName());
            return false;
        } finally {
            if (connection != null) {
                Log.d(TAG, "Closing HTTP connection");
                connection.disconnect();
            }
        }
    }
}