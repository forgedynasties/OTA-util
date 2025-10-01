package com.quectel.otatest;

import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;

public class DeviceUtils {
    private static final String TAG = "DeviceUtils";
    
    /**
     * Get the device build ID from system property ro.build.id
     * @return Build ID string or fallback if not available
     */
    public static String getBuildId() {
        Log.d(TAG, "Retrieving device build ID from ro.build.id");
        
        try {
            Process process = Runtime.getRuntime().exec("getprop ro.build.id");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String buildId = reader.readLine();
            reader.close();
            process.waitFor();
            
            if (buildId != null && !buildId.trim().isEmpty()) {
                buildId = buildId.trim();
                Log.i(TAG, "✓ Retrieved build ID: " + buildId);
                return buildId;
            } else {
                Log.w(TAG, "Build ID is empty, using fallback");
                return "auto-update-lessgo";
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to get build ID from ro.build.id: " + e.getMessage(), e);
            Log.w(TAG, "Using fallback build ID");
            return "auto-update-lessgo";
        }
    }
    
    /**
     * Calculate SHA256 checksum of a file
     * @param filePath Path to the file
     * @return SHA256 checksum as hex string, or null if failed
     */
    public static String calculateSHA256Checksum(String filePath) {
        Log.i(TAG, "=== Calculating SHA256 Checksum ===");
        Log.d(TAG, "File path: " + filePath);
        
        long startTime = System.currentTimeMillis();
        MessageDigest digest = null;
        FileInputStream fis = null;
        
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                Log.e(TAG, "File does not exist: " + filePath);
                return null;
            }
            
            long fileSize = file.length();
            Log.d(TAG, "File size: " + fileSize + " bytes (" + (fileSize / 1024.0 / 1024.0) + " MB)");
            
            digest = MessageDigest.getInstance("SHA-256");
            fis = new FileInputStream(file);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;
            long lastProgressTime = System.currentTimeMillis();
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                
                // Log progress every 5 seconds or 10MB
                if (System.currentTimeMillis() - lastProgressTime > 5000 || totalBytesRead % (10 * 1024 * 1024) == 0) {
                    int progress = (int) (totalBytesRead * 100 / fileSize);
                    Log.d(TAG, "Checksum calculation progress: " + progress + "% (" + totalBytesRead + "/" + fileSize + " bytes)");
                    lastProgressTime = System.currentTimeMillis();
                }
            }
            
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            String checksum = hexString.toString();
            long duration = System.currentTimeMillis() - startTime;
            
            Log.i(TAG, "✓ Checksum calculation completed in " + duration + "ms");
            Log.d(TAG, "SHA256 checksum: " + checksum);
            
            return checksum;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            Log.e(TAG, "Checksum calculation failed after " + duration + "ms: " + e.getMessage(), e);
            return null;
            
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                    Log.d(TAG, "File input stream closed");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing file input stream: " + e.getMessage());
            }
            Log.i(TAG, "=== Checksum Calculation Complete ===");
        }
    }
    
    /**
     * Get Android Build information for debugging
     * @return String with build information
     */
    public static String getBuildInfo() {
        Log.d(TAG, "Gathering build information");
        
        StringBuilder info = new StringBuilder();
        try {
            info.append("Build ID: ").append(getBuildId()).append("\n");
            info.append("Build Display: ").append(android.os.Build.DISPLAY).append("\n");
            info.append("Build Fingerprint: ").append(android.os.Build.FINGERPRINT).append("\n");
            info.append("Build Time: ").append(android.os.Build.TIME).append("\n");
            info.append("Build Type: ").append(android.os.Build.TYPE).append("\n");
            info.append("Build Tags: ").append(android.os.Build.TAGS).append("\n");
            
            Log.d(TAG, "Build information collected");
            return info.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error gathering build info: " + e.getMessage(), e);
            return "Error gathering build information: " + e.getMessage();
        }
    }
}