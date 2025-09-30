package com.quectel.otatest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MyBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Logger.d("MyBootReceiver", "Boot completed event received.");
            Intent serviceIntent = new Intent(context, MyService.class);
            context.startForegroundService(serviceIntent);
        }
    }
}