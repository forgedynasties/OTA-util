package com.quectel.otatest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "OTA_BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "Boot receiver called with action: " + action);
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            try {
                // Save boot time for verification
                SharedPreferences prefs = context.getSharedPreferences("ota_boot", Context.MODE_PRIVATE);
                prefs.edit().putLong("last_boot_time", System.currentTimeMillis()).apply();
                
                // Show toast notification
                Toast.makeText(context, "OTA Boot Receiver: Starting UpdateService", Toast.LENGTH_LONG).show();
                
                Intent serviceIntent = new Intent(context, UpdateService.class);
                context.startForegroundService(serviceIntent);
                Log.i(TAG, "UpdateService started successfully on boot at: " + System.currentTimeMillis());
            } catch (Exception e) {
                Log.e(TAG, "Failed to start UpdateService on boot", e);
                Toast.makeText(context, "OTA Boot Receiver: Failed to start service", Toast.LENGTH_LONG).show();
            }
        }
    }
}
