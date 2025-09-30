package com.quectel.otatest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MyBootReceiver extends BroadcastReceiver {
    private static final String TAG = "MyBootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed event received.");
            Intent serviceIntent = new Intent(context, MyService.class);
            context.startForegroundService(serviceIntent);
        }
    }
}