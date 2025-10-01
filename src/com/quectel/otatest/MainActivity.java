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
            String buildId = DeviceUtils.getBuildId();
            buildIdText.setText("Current Build ID: " + buildId);
            Log.d(TAG, "Build ID displayed: " + buildId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get build ID: " + e.getMessage());
            buildIdText.setText("Unable to retrieve build ID");
        }
    }

    public void checkForUpdates(View v) {
        Log.i(TAG, "Check for updates initiated");
        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Background thread started for update check");
                
                final boolean updateAvailable = UpdateChecker.checkUpdateExists();
                Log.d(TAG, "Update check result: " + updateAvailable);
                
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (updateAvailable) {
                            Log.i(TAG, "Update available - launching UpdateActivity");
                            Intent intent = new Intent(MainActivity.this, UpdateActivity.class);
                            intent.putExtra("update_available", true);
                            startActivity(intent);
                        } else {
                            Log.i(TAG, "No update available");
                            Toast.makeText(MainActivity.this, "No updates available. Your system is up to date.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }
    
    public void openApiTesting(View v) {
        Log.i(TAG, "API Testing button clicked - launching ApiTestActivity");
        Intent intent = new Intent(MainActivity.this, ApiTestActivity.class);
        startActivity(intent);
    }
}