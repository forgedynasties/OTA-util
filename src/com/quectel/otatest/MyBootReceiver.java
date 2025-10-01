package com.quectel.otatest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MyBootReceiver extends BroadcastReceiver {
    private static final String TAG = "MyBootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive called with action: " + (intent != null ? intent.getAction() : "null"));
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(TAG, "Boot completed event received - starting OTA service");
            Log.d(TAG, "Creating service intent for MyService");
            
            try {
                Intent serviceIntent = new Intent(context, MyService.class);
                Log.d(TAG, "Starting foreground service...");
                context.startForegroundService(serviceIntent);
                Log.i(TAG, "MyService started successfully after boot");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start MyService after boot: " + e.getMessage(), e);
            }
        } else {
            Log.w(TAG, "Received non-boot intent: " + intent.getAction());
        }
    }
}