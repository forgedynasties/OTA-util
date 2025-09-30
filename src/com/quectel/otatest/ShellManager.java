package com.quectel.otatest;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class ShellManager {
    private static final String TAG = "ShellManager";
    private static final String DEST_PATH = "/data/ota_package/update.zip";
    
    public String executeCommand(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(command);
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                
                while ((line = errorReader.readLine()) != null) {
                    output.append("ERROR: ").append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            output.append("Exit code: ").append(exitCode);
            
        } catch (Exception e) {
            Log.e(TAG, "Shell command failed", e);
            output.append("Exception: ").append(e.getMessage());
        }
        
        return output.toString();
    }
    
    public String getPermissionInfo() {
        StringBuilder permInfo = new StringBuilder();
        
        try {
            File updateFile = new File(DEST_PATH);
            if (updateFile.exists()) {
                permInfo.append("=== Update File Info ===\n");
                permInfo.append("Path: ").append(DEST_PATH).append("\n");
                permInfo.append("Size: ").append(updateFile.length()).append(" bytes\n");
                permInfo.append("Readable: ").append(updateFile.canRead()).append("\n");
                permInfo.append("Writable: ").append(updateFile.canWrite()).append("\n");
                permInfo.append("Executable: ").append(updateFile.canExecute()).append("\n");
                
                String lsOutput = executeCommand("ls -la " + DEST_PATH);
                permInfo.append("Detailed permissions:\n").append(lsOutput).append("\n");
            } else {
                permInfo.append("Update file does not exist\n");
            }
            
            File otaDir = new File("/data/ota_package");
            permInfo.append("\n=== Directory Info ===\n");
            permInfo.append("Directory exists: ").append(otaDir.exists()).append("\n");
            
            if (otaDir.exists()) {
                permInfo.append("Directory readable: ").append(otaDir.canRead()).append("\n");
                permInfo.append("Directory writable: ").append(otaDir.canWrite()).append("\n");
                permInfo.append("Directory executable: ").append(otaDir.canExecute()).append("\n");
                
                String dirOutput = executeCommand("ls -la /data/ota_package/");
                permInfo.append("Directory contents:\n").append(dirOutput);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Permission check failed", e);
            permInfo.append("Error checking permissions: ").append(e.getMessage());
        }
        
        return permInfo.toString();
    }
}