package com.quectel.otatest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "Received broadcast: " + action);
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_USER_PRESENT.equals(action) ||
            Intent.ACTION_SCREEN_ON.equals(action)) {
            
            Log.i(TAG, "System event detected, ensuring UpdateCheckService is running: " + action);
            
            try {
                Intent serviceIntent = new Intent(context, UpdateCheckService.class);
                
                // Always try to start as foreground service for persistence
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
                
                Log.d(TAG, "UpdateCheckService start command sent");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to start UpdateCheckService", e);
                // Try again after a short delay
                android.os.Handler handler = new android.os.Handler();
                handler.postDelayed(() -> {
                    try {
                        Intent retryIntent = new Intent(context, UpdateCheckService.class);
                        context.startService(retryIntent);
                        Log.d(TAG, "UpdateCheckService retry successful");
                    } catch (Exception retryException) {
                        Log.e(TAG, "UpdateCheckService retry also failed", retryException);
                    }
                }, 5000); // Retry after 5 seconds
            }
        }
    }
}