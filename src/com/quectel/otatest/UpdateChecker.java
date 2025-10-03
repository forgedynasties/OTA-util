package com.quectel.otatest;

import android.os.Build;
import android.util.Log;

public class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static String cachedBuildId = null;
    private static OTAApiClient.UpdateResponse lastUpdateResponse = null;
    
    /**
     * Get the current build ID for this device using getprop ro.build.id
     * @return The current build ID from system property
     */
    public static String getCurrentBuildId() {
        if (cachedBuildId == null) {
            try {
                Log.d(TAG, "=== Retrieving Build ID from System Properties ===");
                
                // First try to get ro.build.id using getprop command
                Process process = Runtime.getRuntime().exec("getprop ro.build.id");
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
                
                String buildId = reader.readLine();
                reader.close();
                process.waitFor();
                
                if (buildId != null && !buildId.trim().isEmpty()) {
                    cachedBuildId = buildId.trim();
                    Log.i(TAG, "✅ Build ID from getprop ro.build.id: " + cachedBuildId);
                } else {
                    Log.w(TAG, "⚠️ getprop ro.build.id returned empty, using fallback");
                    // Fallback to Build.ID if getprop fails
                    cachedBuildId = Build.ID;
                    Log.i(TAG, "📋 Fallback Build ID from Build.ID: " + cachedBuildId);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to get build ID via getprop: " + e.getMessage());
                Log.e(TAG, "Using Build.ID as fallback");
                
                // Fallback to Build.ID if getprop command fails
                cachedBuildId = Build.ID;
                Log.i(TAG, "📋 Fallback Build ID from Build.ID: " + cachedBuildId);
            }
            
            Log.i(TAG, "=== Build ID Information ===");
            Log.i(TAG, "Final Build ID: " + cachedBuildId);
            Log.d(TAG, "Android Build.DISPLAY: " + Build.DISPLAY);
            Log.d(TAG, "Android Build.ID: " + Build.ID);
            Log.d(TAG, "Android Build.VERSION.INCREMENTAL: " + Build.VERSION.INCREMENTAL);
            Log.d(TAG, "Android Build.FINGERPRINT: " + Build.FINGERPRINT);
            
            // Also try to log the getprop command result for debugging
            try {
                Process debugProcess = Runtime.getRuntime().exec("getprop ro.build.id");
                java.io.BufferedReader debugReader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(debugProcess.getInputStream()));
                String debugResult = debugReader.readLine();
                debugReader.close();
                debugProcess.waitFor();
                Log.d(TAG, "getprop ro.build.id result: '" + debugResult + "'");
            } catch (Exception debugE) {
                Log.d(TAG, "Could not log getprop result: " + debugE.getMessage());
            }
            
            Log.i(TAG, "===========================");
        }
        return cachedBuildId;
    }
    
    /**
     * Check if an update is available using the new REST API
     * @return true if update is available, false otherwise
     */
    public static boolean checkUpdateExists() {
        Log.i(TAG, "=== Starting Update Check (Legacy Method) ===");
        Log.d(TAG, "This method now uses the new REST API internally");
        
        OTAApiClient.UpdateResponse response = checkForUpdate();
        if (response != null) {
            boolean updateAvailable = response.isUpdateAvailable();
            Log.i(TAG, "Legacy method returning: " + updateAvailable);
            return updateAvailable;
        }
        
        Log.w(TAG, "API call failed, returning false for legacy compatibility");
        return false;
    }
    
    /**
     * Check for updates using the new REST API
     * @return UpdateResponse object with full update information, or null if request failed
     */
    public static OTAApiClient.UpdateResponse checkForUpdate() {
        Log.i(TAG, "=== Starting Full Update Check ===");
        
        String currentBuildId = getCurrentBuildId();
        Log.i(TAG, "Current Build ID: " + currentBuildId);
        Log.d(TAG, "Calling OTAApiClient.checkForUpdate()...");
        
        long startTime = System.currentTimeMillis();
        OTAApiClient.UpdateResponse response = OTAApiClient.checkForUpdate(currentBuildId);
        long duration = System.currentTimeMillis() - startTime;
        
        if (response != null) {
            Log.i(TAG, "✓ Update check completed successfully in " + duration + "ms");
            Log.i(TAG, "=== Update Check Results ===");
            Log.i(TAG, "Status: " + response.status);
            Log.i(TAG, "Current Build ID: " + currentBuildId);
            Log.i(TAG, "Server Build ID: " + response.buildId);
            Log.i(TAG, "Package URL: " + response.packageUrl);
            Log.i(TAG, "Full Download URL: " + response.getFullPackageUrl());
            Log.i(TAG, "Patch Notes: " + response.patchNotes);
            Log.i(TAG, "Message: " + response.message);
            
            if (response.isUpdateAvailable()) {
                Log.i(TAG, "🎉 UPDATE AVAILABLE!");
                Log.i(TAG, "📦 Download URL: " + response.getFullPackageUrl());
                Log.i(TAG, "🔄 New Build: " + response.buildId);
                Log.i(TAG, "📝 Patch Notes: " + response.patchNotes);
            } else if (response.isUpToDate()) {
                Log.i(TAG, "✅ System is up to date");
            } else if (response.isError()) {
                Log.w(TAG, "❌ Error: " + response.message);
            }
            Log.i(TAG, "===========================");
            
            lastUpdateResponse = response;
        } else {
            Log.e(TAG, "❌ Update check failed after " + duration + "ms");
            Log.e(TAG, "API request returned null response");
            lastUpdateResponse = null;
        }
        
        return response;
    }
    
    /**
     * Get the last update response (cached)
     * @return The last UpdateResponse, or null if no check has been performed
     */
    public static OTAApiClient.UpdateResponse getLastUpdateResponse() {
        Log.d(TAG, "Returning cached update response: " + (lastUpdateResponse != null ? lastUpdateResponse.status : "null"));
        return lastUpdateResponse;
    }
    
    /**
     * Clear the cached update response
     */
    public static void clearCache() {
        Log.d(TAG, "Clearing cached update response and build ID");
        lastUpdateResponse = null;
        cachedBuildId = null;
    }
}