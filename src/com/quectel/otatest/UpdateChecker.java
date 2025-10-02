package com.quectel.otatest;

import android.os.Build;
import android.util.Log;

public class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static String cachedBuildId = null;
    private static OtaApiClient.UpdateResponse lastUpdateResponse = null;
    
    /**
     * Get the current build ID for this device
     * This uses Android's Build.DISPLAY which typically contains build information
     * You can customize this method to use your specific build ID scheme
     * @return The current build ID
     */
    public static String getCurrentBuildId() {
        if (cachedBuildId == null) {
            // You can customize this logic based on how your build IDs are structured
            // For now, using a combination of Build properties that are commonly used
            cachedBuildId = "build-" + Build.DISPLAY.replaceAll("[^a-zA-Z0-9-]", "-").toLowerCase();
            
            // Alternative options you might want to use instead:
            // cachedBuildId = Build.ID; // Build ID from the build system
            // cachedBuildId = "build-" + Build.VERSION.INCREMENTAL; // Incremental version
            // cachedBuildId = Build.FINGERPRINT; // Full build fingerprint
            
            Log.i(TAG, "=== Build ID Information ===");
            Log.i(TAG, "Generated Build ID: " + cachedBuildId);
            Log.d(TAG, "Android Build.DISPLAY: " + Build.DISPLAY);
            Log.d(TAG, "Android Build.ID: " + Build.ID);
            Log.d(TAG, "Android Build.VERSION.INCREMENTAL: " + Build.VERSION.INCREMENTAL);
            Log.d(TAG, "Android Build.FINGERPRINT: " + Build.FINGERPRINT);
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
        
        OtaApiClient.UpdateResponse response = checkForUpdate();
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
    public static OtaApiClient.UpdateResponse checkForUpdate() {
        Log.i(TAG, "=== Starting Full Update Check ===");
        
        String currentBuildId = getCurrentBuildId();
        Log.i(TAG, "Current Build ID: " + currentBuildId);
        Log.d(TAG, "Calling OtaApiClient.checkForUpdate()...");
        
        long startTime = System.currentTimeMillis();
        OtaApiClient.UpdateResponse response = OtaApiClient.checkForUpdate(currentBuildId);
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
    public static OtaApiClient.UpdateResponse getLastUpdateResponse() {
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