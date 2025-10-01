package com.quectel.otatest;

import android.util.Log;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class OTAApiClient {
    private static final String TAG = "OTAApiClient";
    private static final String BASE_URL = "http://10.32.1.11:8000";
    private static final String API_KEY = "YOUR_API_KEY_HERE"; // TODO: Replace with actual API key
    private static final int TIMEOUT_MS = 30000; // 30 seconds
    
    /**
     * Response class for update check results
     */
    public static class UpdateCheckResponse {
        public boolean updateAvailable;
        public String status;
        public String packageUrl;
        public String newBuildId;
        public String patchNotes;
        public String errorMessage;
        
        public UpdateCheckResponse(boolean available, String status) {
            this.updateAvailable = available;
            this.status = status;
        }
    }
    
    /**
     * Response class for checksum validation
     */
    public static class ChecksumValidationResponse {
        public boolean isValid;
        public String status;
        public String message;
        public String errorMessage;
        
        public ChecksumValidationResponse(boolean valid, String status, String message) {
            this.isValid = valid;
            this.status = status;
            this.message = message;
        }
    }
    
    /**
     * Check for available updates using the API
     * @param currentBuildId Current device build ID
     * @return UpdateCheckResponse with results
     */
    public static UpdateCheckResponse checkForUpdates(String currentBuildId) {
        Log.i(TAG, "=== Starting API Update Check ===");
        Log.d(TAG, "Current build ID: " + currentBuildId);
        Log.d(TAG, "API endpoint: " + BASE_URL + "/api/check-update");
        
        HttpURLConnection connection = null;
        long startTime = System.currentTimeMillis();
        
        try {
            // Create request URL
            URL url = new URL(BASE_URL + "/api/check-update");
            Log.d(TAG, "Opening connection to: " + url.toString());
            
            // Open connection
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + API_KEY);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            
            Log.d(TAG, "Request headers configured");
            Log.d(TAG, "Authorization: Bearer " + API_KEY.substring(0, Math.min(10, API_KEY.length())) + "...");
            
            // Create JSON request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("build_id", currentBuildId);
            String jsonString = requestBody.toString();
            
            Log.d(TAG, "Request body: " + jsonString);
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                Log.d(TAG, "Request body sent (" + input.length + " bytes)");
            }
            
            // Get response
            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();
            long requestDuration = System.currentTimeMillis() - startTime;
            
            Log.d(TAG, "Response received in " + requestDuration + "ms");
            Log.d(TAG, "Response code: " + responseCode + " " + responseMessage);
            
            // Read response body
            BufferedReader reader;
            if (responseCode >= 200 && responseCode < 300) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                Log.d(TAG, "Reading successful response from input stream");
            } else {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                Log.w(TAG, "Reading error response from error stream");
            }
            
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            String responseBody = response.toString();
            Log.d(TAG, "Raw response body: " + responseBody);
            
            // Handle HTTP errors
            if (responseCode == 401) {
                Log.e(TAG, "Authentication failed - invalid API key");
                UpdateCheckResponse errorResponse = new UpdateCheckResponse(false, "error");
                errorResponse.errorMessage = "Authentication failed - invalid API key";
                return errorResponse;
            } else if (responseCode == 404) {
                Log.e(TAG, "Build ID not found on server");
                UpdateCheckResponse errorResponse = new UpdateCheckResponse(false, "error");
                errorResponse.errorMessage = "Build ID not found on server";
                return errorResponse;
            } else if (responseCode >= 400) {
                Log.e(TAG, "Server error: " + responseCode + " " + responseMessage);
                UpdateCheckResponse errorResponse = new UpdateCheckResponse(false, "error");
                errorResponse.errorMessage = "Server error: " + responseCode + " " + responseMessage;
                return errorResponse;
            }
            
            // Parse JSON response
            JSONObject jsonResponse = new JSONObject(responseBody);
            String status = jsonResponse.getString("status");
            
            Log.d(TAG, "Parsed response status: " + status);
            
            if ("update-available".equals(status)) {
                Log.i(TAG, "✓ Update is available!");
                
                UpdateCheckResponse result = new UpdateCheckResponse(true, status);
                result.packageUrl = jsonResponse.getString("package_url");
                result.newBuildId = jsonResponse.getString("build_id");
                if (jsonResponse.has("patch_notes")) {
                    result.patchNotes = jsonResponse.getString("patch_notes");
                }
                
                Log.d(TAG, "New build ID: " + result.newBuildId);
                Log.d(TAG, "Package URL: " + result.packageUrl);
                Log.d(TAG, "Patch notes: " + result.patchNotes);
                
                return result;
                
            } else if ("up-to-date".equals(status)) {
                Log.i(TAG, "✓ System is up to date");
                return new UpdateCheckResponse(false, status);
                
            } else {
                Log.w(TAG, "Unknown status received: " + status);
                UpdateCheckResponse errorResponse = new UpdateCheckResponse(false, "error");
                errorResponse.errorMessage = "Unknown response status: " + status;
                return errorResponse;
            }
            
        } catch (Exception e) {
            long requestDuration = System.currentTimeMillis() - startTime;
            Log.e(TAG, "API request failed after " + requestDuration + "ms: " + e.getMessage(), e);
            Log.e(TAG, "Exception class: " + e.getClass().getName());
            
            UpdateCheckResponse errorResponse = new UpdateCheckResponse(false, "error");
            errorResponse.errorMessage = "Network error: " + e.getMessage();
            return errorResponse;
            
        } finally {
            if (connection != null) {
                Log.d(TAG, "Closing HTTP connection");
                connection.disconnect();
            }
            Log.i(TAG, "=== API Update Check Complete ===");
        }
    }
    
    /**
     * Validate downloaded package checksum with server
     * @param buildId Build ID of the package
     * @param checksum SHA256 checksum of downloaded file
     * @return ChecksumValidationResponse with validation result
     */
    public static ChecksumValidationResponse validateChecksum(String buildId, String checksum) {
        Log.i(TAG, "=== Starting Checksum Validation ===");
        Log.d(TAG, "Build ID: " + buildId);
        Log.d(TAG, "Checksum: " + checksum);
        Log.d(TAG, "API endpoint: " + BASE_URL + "/api/validate-checksum");
        
        HttpURLConnection connection = null;
        long startTime = System.currentTimeMillis();
        
        try {
            // Create request URL
            URL url = new URL(BASE_URL + "/api/validate-checksum");
            Log.d(TAG, "Opening connection to: " + url.toString());
            
            // Open connection
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + API_KEY);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            
            Log.d(TAG, "Request headers configured for checksum validation");
            
            // Create JSON request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("build_id", buildId);
            requestBody.put("checksum", checksum);
            String jsonString = requestBody.toString();
            
            Log.d(TAG, "Checksum request body: " + jsonString);
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                Log.d(TAG, "Checksum request sent (" + input.length + " bytes)");
            }
            
            // Get response
            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();
            long requestDuration = System.currentTimeMillis() - startTime;
            
            Log.d(TAG, "Checksum validation response received in " + requestDuration + "ms");
            Log.d(TAG, "Response code: " + responseCode + " " + responseMessage);
            
            // Read response body
            BufferedReader reader;
            if (responseCode >= 200 && responseCode < 300) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            }
            
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            String responseBody = response.toString();
            Log.d(TAG, "Checksum validation response: " + responseBody);
            
            // Handle HTTP errors
            if (responseCode >= 400) {
                Log.e(TAG, "Checksum validation server error: " + responseCode);
                ChecksumValidationResponse errorResponse = new ChecksumValidationResponse(false, "error", "Server error");
                errorResponse.errorMessage = "Server error: " + responseCode + " " + responseMessage;
                return errorResponse;
            }
            
            // Parse JSON response
            JSONObject jsonResponse = new JSONObject(responseBody);
            String status = jsonResponse.getString("status");
            boolean isValid = jsonResponse.getBoolean("is_valid");
            String message = jsonResponse.getString("message");
            
            Log.d(TAG, "Checksum validation result - Status: " + status + ", Valid: " + isValid + ", Message: " + message);
            
            if (isValid) {
                Log.i(TAG, "✓ Checksum validation successful - package integrity verified");
            } else {
                Log.w(TAG, "✗ Checksum validation failed - package may be corrupted");
            }
            
            return new ChecksumValidationResponse(isValid, status, message);
            
        } catch (Exception e) {
            long requestDuration = System.currentTimeMillis() - startTime;
            Log.e(TAG, "Checksum validation failed after " + requestDuration + "ms: " + e.getMessage(), e);
            
            ChecksumValidationResponse errorResponse = new ChecksumValidationResponse(false, "error", "Network error");
            errorResponse.errorMessage = "Network error: " + e.getMessage();
            return errorResponse;
            
        } finally {
            if (connection != null) {
                Log.d(TAG, "Closing checksum validation connection");
                connection.disconnect();
            }
            Log.i(TAG, "=== Checksum Validation Complete ===");
        }
    }
    
    /**
     * Get the full download URL for a package
     * @param packagePath Package path from API response (e.g., "/packages/ota-build-1002.zip")
     * @return Full download URL
     */
    public static String getDownloadUrl(String packagePath) {
        String fullUrl = BASE_URL + packagePath;
        Log.d(TAG, "Generated download URL: " + fullUrl);
        return fullUrl;
    }
    
    /**
     * Get authorization header for download requests
     * @return Authorization header value
     */
    public static String getAuthorizationHeader() {
        return "Bearer " + API_KEY;
    }
    
    /**
     * Test API connectivity and authentication
     * @return true if API is reachable and authenticated, false otherwise
     */
    public static boolean testApiConnectivity() {
        Log.i(TAG, "=== Testing API Connectivity ===");
        
        try {
            // Test with a simple check-update call
            UpdateCheckResponse response = checkForUpdates("test-build-id");
            
            if (response.errorMessage != null && response.errorMessage.contains("Authentication failed")) {
                Log.e(TAG, "API connectivity test failed - authentication error");
                return false;
            }
            
            if (response.errorMessage != null && response.errorMessage.contains("Network error")) {
                Log.e(TAG, "API connectivity test failed - network error");
                return false;
            }
            
            // If we got any valid response (even "build not found"), API is working
            Log.i(TAG, "✓ API connectivity test successful");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "API connectivity test failed: " + e.getMessage(), e);
            return false;
        }
    }
}