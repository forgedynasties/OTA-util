package com.quectel.otatest;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class ShellManager {
    private static final String TAG = "ShellManager";
    private static final String DEST_PATH = "/data/ota_package/update.zip";
    
    public String executeCommand(String command) {
        Log.i(TAG, "=== Executing Shell Command ===");
        Log.d(TAG, "Command: " + command);
        
        StringBuilder output = new StringBuilder();
        long commandStartTime = System.currentTimeMillis();
        Process process = null;
        
        try {
            Log.d(TAG, "Starting process execution...");
            process = Runtime.getRuntime().exec(command);
            Log.d(TAG, "Process created, reading output streams...");
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                
                String line;
                int stdoutLines = 0;
                int stderrLines = 0;
                
                Log.d(TAG, "Reading stdout...");
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    stdoutLines++;
                    if (stdoutLines <= 5) { // Log first 5 lines
                        Log.d(TAG, "STDOUT: " + line);
                    }
                }
                
                Log.d(TAG, "Reading stderr...");
                while ((line = errorReader.readLine()) != null) {
                    output.append("ERROR: ").append(line).append("\n");
                    stderrLines++;
                    Log.w(TAG, "STDERR: " + line);
                }
                
                Log.d(TAG, "Output streams read - stdout lines: " + stdoutLines + ", stderr lines: " + stderrLines);
            }
            
            Log.d(TAG, "Waiting for process completion...");
            int exitCode = process.waitFor();
            long commandDuration = System.currentTimeMillis() - commandStartTime;
            
            output.append("Exit code: ").append(exitCode);
            
            if (exitCode == 0) {
                Log.i(TAG, "✓ Command completed successfully (exit code: 0) in " + commandDuration + "ms");
            } else {
                Log.w(TAG, "⚠ Command completed with non-zero exit code: " + exitCode + " in " + commandDuration + "ms");
            }
            
        } catch (Exception e) {
            long commandDuration = System.currentTimeMillis() - commandStartTime;
            Log.e(TAG, "Shell command failed after " + commandDuration + "ms: " + e.getMessage(), e);
            Log.e(TAG, "Exception class: " + e.getClass().getName());
            output.append("Exception: ").append(e.getMessage());
        } finally {
            if (process != null) {
                Log.d(TAG, "Destroying process");
                process.destroy();
            }
        }
        
        String result = output.toString();
        Log.d(TAG, "Command output length: " + result.length() + " characters");
        Log.i(TAG, "=== Shell Command Execution Complete ===");
        
        return result;
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