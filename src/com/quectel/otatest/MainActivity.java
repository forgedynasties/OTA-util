package com.quectel.otatest;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "OTATEST";
    
    private TextView buildIdText;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "MainActivity starting");
        
        setContentView(R.layout.activity_main);
        
        initializeComponents();
        displayCurrentBuildId();
        
        Log.i(TAG, "MainActivity initialized successfully");
    }

    private void initializeComponents() {
        mainHandler = new Handler(Looper.getMainLooper());
        buildIdText = findViewById(R.id.build_id_text);
        
        Log.d(TAG, "Components initialized");
    }

    private void displayCurrentBuildId() {
        try {
            String buildId = SystemProperties.get("ro.build.display.id", "Unknown");
            buildIdText.setText(buildId);
            Log.d(TAG, "Build ID displayed: " + buildId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get build ID: " + e.getMessage());
            buildIdText.setText("Unable to retrieve build ID");
        }
    }

    public void checkForUpdates(View v) {
        Log.i(TAG, "=== Manual Update Check Initiated ===");
        Log.i(TAG, "Current Build ID: " + UpdateChecker.getCurrentBuildId());
        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Background thread started for manual update check");
                Log.d(TAG, "Thread ID: " + Thread.currentThread().getId());
                long startTime = System.currentTimeMillis();
                
                try {
                    Log.i(TAG, "Calling UpdateChecker.checkForUpdate()...");
                    final OTAApiClient.UpdateResponse response = UpdateChecker.checkForUpdate();
                    
                    long duration = System.currentTimeMillis() - startTime;
                    Log.i(TAG, "Manual update check completed in " + duration + "ms");
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "Updating UI with manual check results");
                            
                            if (response != null) {
                                Log.i(TAG, "=== Manual Update Check Results ===");
                                Log.i(TAG, "Status: " + response.status);
                                Log.i(TAG, "Build ID: " + response.buildId);
                                Log.i(TAG, "Package URL: " + response.packageUrl);
                                Log.i(TAG, "Full Download URL: " + response.getFullPackageUrl());
                                Log.i(TAG, "Patch Notes: " + response.patchNotes);
                                Log.i(TAG, "Message: " + response.message);
                                
                                if (response.isUpdateAvailable()) {
                                    Log.i(TAG, "🎉 Update available - launching UpdateActivity");
                                    Log.i(TAG, "📦 Download URL: " + response.getFullPackageUrl());
                                    
                                    Intent intent = new Intent(MainActivity.this, UpdateActivity.class);
                                    intent.putExtra("update_available", true);
                                    intent.putExtra("download_url", response.getFullPackageUrl());
                                    intent.putExtra("build_id", response.buildId);
                                    intent.putExtra("patch_notes", response.patchNotes);
                                    
                                    Log.d(TAG, "Starting UpdateActivity with extras:");
                                    Log.d(TAG, "- download_url: " + response.getFullPackageUrl());
                                    Log.d(TAG, "- build_id: " + response.buildId);
                                    Log.d(TAG, "- patch_notes: " + response.patchNotes);
                                    
                                    startActivity(intent);
                                } else if (response.isUpToDate()) {
                                    Log.i(TAG, "✅ System is up to date");
                                    Toast.makeText(MainActivity.this, "No updates available. Your system is up to date.", Toast.LENGTH_LONG).show();
                                } else if (response.isError()) {
                                    Log.w(TAG, "❌ Server error: " + response.message);
                                    Toast.makeText(MainActivity.this, "Update check failed: " + response.message, Toast.LENGTH_LONG).show();
                                } else {
                                    Log.w(TAG, "⚠️ Unexpected status: " + response.status);
                                    Toast.makeText(MainActivity.this, "Unexpected response: " + response.status, Toast.LENGTH_LONG).show();
                                }
                                Log.i(TAG, "================================");
                            } else {
                                Log.e(TAG, "❌ Update check failed - API returned null");
                                Toast.makeText(MainActivity.this, "Update check failed. Please check your connection and try again.", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - startTime;
                    Log.e(TAG, "Manual update check failed after " + duration + "ms: " + e.getMessage(), e);
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Update check failed due to an error. Please try again.", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }
}