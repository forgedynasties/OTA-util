package com.quectel.otatest;

import android.util.Log;

public class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    
    /**
     * Response class for update check results
     */
    public static class UpdateCheckResult {
        public boolean updateAvailable;
        public String packageUrl;
        public String newBuildId;
        public String patchNotes;
        public String errorMessage;
        
        public UpdateCheckResult(boolean available) {
            this.updateAvailable = available;
        }
    }
    
    /**
     * Check if updates are available using OTA API
     * @return true if update is available, false otherwise (for backward compatibility)
     */
    public static boolean checkUpdateExists() {
        Log.i(TAG, "=== Starting API-based Update Check ===");
        
        try {
            // Get current device build ID
            String currentBuildId = DeviceUtils.getBuildId();
            Log.d(TAG, "Current device build ID: " + currentBuildId);
            
            // Call API to check for updates
            OTAApiClient.UpdateCheckResponse response = OTAApiClient.checkForUpdates(currentBuildId);
            
            if (response.errorMessage != null) {
                Log.e(TAG, "API update check failed: " + response.errorMessage);
                return false;
            }
            
            Log.i(TAG, "API update check completed - Update available: " + response.updateAvailable);
            return response.updateAvailable;
            
        } catch (Exception e) {
            Log.e(TAG, "Exception during API update check: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Check for updates using OTA API with detailed results
     * @return UpdateCheckResult with detailed information
     */
    public static UpdateCheckResult checkUpdateExistsDetailed() {
        Log.i(TAG, "=== Starting Detailed API Update Check ===");
        
        try {
            // Get current device build ID
            String currentBuildId = DeviceUtils.getBuildId();
            Log.d(TAG, "Current device build ID for detailed check: " + currentBuildId);
            
            // Call API to check for updates
            OTAApiClient.UpdateCheckResponse apiResponse = OTAApiClient.checkForUpdates(currentBuildId);
            
            UpdateCheckResult result = new UpdateCheckResult(apiResponse.updateAvailable);
            
            if (apiResponse.errorMessage != null) {
                Log.e(TAG, "Detailed API update check failed: " + apiResponse.errorMessage);
                result.errorMessage = apiResponse.errorMessage;
                return result;
            }
            
            if (apiResponse.updateAvailable) {
                Log.d(TAG, "Processing available update - API response fields:");
                Log.d(TAG, "  apiResponse.packageUrl: " + (apiResponse.packageUrl != null ? apiResponse.packageUrl : "NULL"));
                Log.d(TAG, "  apiResponse.newBuildId: " + (apiResponse.newBuildId != null ? apiResponse.newBuildId : "NULL"));
                Log.d(TAG, "  apiResponse.patchNotes: " + (apiResponse.patchNotes != null ? apiResponse.patchNotes : "NULL"));
                
                result.packageUrl = apiResponse.packageUrl;
                result.newBuildId = apiResponse.newBuildId;
                result.patchNotes = apiResponse.patchNotes;
                
                Log.d(TAG, "Assigned to result object:");
                Log.d(TAG, "  result.packageUrl: " + (result.packageUrl != null ? result.packageUrl : "NULL"));
                Log.d(TAG, "  result.newBuildId: " + (result.newBuildId != null ? result.newBuildId : "NULL"));
                Log.d(TAG, "  result.patchNotes: " + (result.patchNotes != null ? result.patchNotes : "NULL"));
                
                Log.i(TAG, "✓ Detailed update check - Update available!");
                Log.d(TAG, "New build ID: " + result.newBuildId);
                Log.d(TAG, "Package URL: " + result.packageUrl);
                Log.d(TAG, "Patch notes: " + result.patchNotes);
            } else {
                Log.i(TAG, "✓ Detailed update check - System is up to date");
            }
            
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Exception during detailed API update check: " + e.getMessage(), e);
            UpdateCheckResult result = new UpdateCheckResult(false);
            result.errorMessage = "Exception: " + e.getMessage();
            return result;
        }
    }
    
    /**
     * Validate downloaded package using API checksum validation
     * @param filePath Path to downloaded file
     * @param buildId Build ID of the package
     * @return true if checksum is valid, false otherwise
     */
    public static boolean validateDownloadedPackage(String filePath, String buildId) {
        Log.i(TAG, "=== Starting Package Validation ===");
        Log.d(TAG, "File path: " + filePath);
        Log.d(TAG, "Build ID: " + buildId);
        
        try {
            // Calculate SHA256 checksum
            Log.d(TAG, "Calculating file checksum...");
            String checksum = DeviceUtils.calculateSHA256Checksum(filePath);
            
            if (checksum == null) {
                Log.e(TAG, "Failed to calculate checksum - validation failed");
                return false;
            }
            
            Log.d(TAG, "File checksum calculated: " + checksum);
            
            // Validate with server
            Log.d(TAG, "Validating checksum with server...");
            OTAApiClient.ChecksumValidationResponse response = OTAApiClient.validateChecksum(buildId, checksum);
            
            if (response.errorMessage != null) {
                Log.e(TAG, "Checksum validation API call failed: " + response.errorMessage);
                return false;
            }
            
            if (response.isValid) {
                Log.i(TAG, "✓ Package validation successful - checksum is valid");
            } else {
                Log.w(TAG, "✗ Package validation failed - checksum mismatch");
                Log.w(TAG, "Server message: " + response.message);
            }
            
            return response.isValid;
            
        } catch (Exception e) {
            Log.e(TAG, "Exception during package validation: " + e.getMessage(), e);
            return false;
        }
    }
}