package com.quectel.otatest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "OTA_BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "Boot receiver called with action: " + action);
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            
            Log.i(TAG, "Device boot detected, starting UpdateService with delay");
            
            // Add a delay to ensure system is fully loaded
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent serviceIntent = new Intent(context, UpdateService.class);
                        context.startForegroundService(serviceIntent);
                        Log.i(TAG, "UpdateService started successfully on boot");
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to start UpdateService on boot", e);
                    }
                }
            }, 30000); // 30 second delay after boot
        }
    }
}
