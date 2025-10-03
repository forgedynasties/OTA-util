package com.quectel.otatest;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class OTAApiClient {
    private static final String TAG = "OTAApiClient";
    private static final String BASE_URL = "http://10.32.1.11:8000";
    private static final String CHECK_UPDATE_ENDPOINT = "/api/check-update";
    private static final int TIMEOUT_MS = 10000; // 10 seconds
    private static final String API_KEY = "YOUR_API_KEY_HERE";
    
    public static class UpdateResponse {
        public final String status;
        public final String packageUrl;
        public final String buildId;
        public final String patchNotes;
        public final String message;
        
        public UpdateResponse(String status, String packageUrl, String buildId, String patchNotes, String message) {
            this.status = status;
            this.packageUrl = packageUrl;
            this.buildId = buildId;
            this.patchNotes = patchNotes;
            this.message = message;
        }
        
        public boolean isUpdateAvailable() {
            return "update-available".equals(status);
        }
        
        public boolean isUpToDate() {
            return "up-to-date".equals(status);
        }
        
        public boolean isError() {
            return "error".equals(status);
        }
        
        public String getFullPackageUrl() {
            if (packageUrl != null && packageUrl.startsWith("/packages/")) {
                return BASE_URL + packageUrl;
            }
            return packageUrl;
        }
    }
    
    /**
     * Check for updates using the REST API
     * @param buildId The current build ID to check against
     * @return UpdateResponse with the server response, or null if request failed
     */
    public static UpdateResponse checkForUpdate(String buildId) {
        HttpURLConnection connection = null;
        
        try {
            Log.i(TAG, "Checking for updates with build ID: " + buildId);
            
            // Create URL and connection
            URL url = new URL(BASE_URL + CHECK_UPDATE_ENDPOINT);
            connection = (HttpURLConnection) url.openConnection();
            
            // Configure connection
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + API_KEY);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            
            // Create JSON payload
            JSONObject payload = new JSONObject();
            payload.put("build_id", buildId);
            String jsonPayload = payload.toString();
            
            Log.d(TAG, "Sending request: " + jsonPayload);
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Get response
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Response code: " + responseCode);
            
            // Read response body
            String responseBody;
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    responseBody = response.toString();
                }
            } else {
                // Read error response
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    responseBody = response.toString();
                }
            }
            
            Log.d(TAG, "Response body: " + responseBody);
            
            // Parse JSON response
            JSONObject jsonResponse = new JSONObject(responseBody);
            
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                Log.e(TAG, "Authentication failed: Invalid API key");
                return new UpdateResponse("error", null, null, null, "Invalid API key");
            }
            
            String status = jsonResponse.optString("status", "error");
            String packageUrl = jsonResponse.optString("package_url", null);
            String responseBuildId = jsonResponse.optString("build_id", null);
            String patchNotes = jsonResponse.optString("patch_notes", null);
            String message = jsonResponse.optString("message", null);
            
            UpdateResponse updateResponse = new UpdateResponse(status, packageUrl, responseBuildId, patchNotes, message);
            
            Log.i(TAG, "Update check completed - Status: " + status);
            if (updateResponse.isUpdateAvailable()) {
                Log.i(TAG, "✓ Update available: " + responseBuildId + " at " + updateResponse.getFullPackageUrl());
            } else if (updateResponse.isUpToDate()) {
                Log.i(TAG, "✓ System is up to date");
            } else {
                Log.w(TAG, "✗ Error: " + message);
            }
            
            return updateResponse;
            
        } catch (IOException e) {
            Log.e(TAG, "Network error during update check: " + e.getMessage());
            return null;
        } catch (JSONException e) {
            Log.e(TAG, "JSON parsing error: " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}