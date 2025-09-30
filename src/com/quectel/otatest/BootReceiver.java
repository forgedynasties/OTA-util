package com.quectel.otatest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final int RETRY_DELAY_MS = 3000;
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        
        Log.i(TAG, "=== BOOT RECEIVER TRIGGERED ===");
        Log.i(TAG, "Timestamp: " + timestamp);
        Log.i(TAG, "Received broadcast: " + action);
        Log.i(TAG, "Android Version: " + Build.VERSION.SDK_INT);
        Log.i(TAG, "Device Model: " + Build.MODEL);
        Log.i(TAG, "Process ID: " + android.os.Process.myPid());
        
        // Show toast to verify receiver is working (for debugging)
        try {
            Toast.makeText(context, "BootReceiver triggered: " + action, Toast.LENGTH_LONG).show();
            Log.d(TAG, "Toast displayed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to show toast", e);
        }
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_USER_PRESENT.equals(action) ||
            Intent.ACTION_SCREEN_ON.equals(action)) {
            
            Log.i(TAG, "=== SYSTEM EVENT DETECTED ===");
            Log.i(TAG, "Event type: " + action);
            Log.i(TAG, "Starting UpdateCheckService with extensive retry mechanism");
            
            // Start service immediately
            startServiceWithRetry(context, 0);
            
            // Also schedule additional startup attempts as backup
            Handler mainHandler = new Handler(Looper.getMainLooper());
            for (int delay = 5000; delay <= 30000; delay += 5000) {
                mainHandler.postDelayed(() -> {
                    Log.d(TAG, "Backup service start attempt at " + new Date());
                    startServiceWithRetry(context, 0);
                }, delay);
            }
        } else {
            Log.w(TAG, "Unhandled broadcast action: " + action);
        }
        
        Log.i(TAG, "=== BOOT RECEIVER FINISHED ===");
    }
    
    private void startServiceWithRetry(Context context, int attemptNumber) {
        if (attemptNumber >= MAX_RETRY_ATTEMPTS) {
            Log.e(TAG, "Maximum retry attempts reached (" + MAX_RETRY_ATTEMPTS + "). Service startup failed.");
            return;
        }
        
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        Log.i(TAG, "=== SERVICE START ATTEMPT " + (attemptNumber + 1) + " ===");
        Log.i(TAG, "Attempt timestamp: " + timestamp);
        
        try {
            Intent serviceIntent = new Intent(context, UpdateCheckService.class);
            serviceIntent.putExtra("start_source", "BootReceiver");
            serviceIntent.putExtra("attempt_number", attemptNumber + 1);
            serviceIntent.putExtra("start_timestamp", timestamp);
            
            Log.d(TAG, "Creating service intent...");
            Log.d(TAG, "Service class: " + UpdateCheckService.class.getName());
            Log.d(TAG, "Context: " + context.getClass().getName());
            
            // Always try to start as foreground service for Android 8+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Starting foreground service (Android 8+)");
                context.startForegroundService(serviceIntent);
            } else {
                Log.d(TAG, "Starting regular service (Android < 8)");
                context.startService(serviceIntent);
            }
            
            Log.i(TAG, "Service start command sent successfully - Attempt " + (attemptNumber + 1));
            
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException starting service - Attempt " + (attemptNumber + 1), e);
            scheduleRetry(context, attemptNumber + 1);
        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException starting service - Attempt " + (attemptNumber + 1), e);
            scheduleRetry(context, attemptNumber + 1);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception starting service - Attempt " + (attemptNumber + 1), e);
            scheduleRetry(context, attemptNumber + 1);
        }
    }
    
    private void scheduleRetry(Context context, int nextAttempt) {
        if (nextAttempt >= MAX_RETRY_ATTEMPTS) {
            Log.e(TAG, "No more retry attempts remaining");
            return;
        }
        
        Log.w(TAG, "Scheduling retry attempt " + (nextAttempt + 1) + " in " + RETRY_DELAY_MS + "ms");
        
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            Log.d(TAG, "Executing scheduled retry attempt " + (nextAttempt + 1));
            startServiceWithRetry(context, nextAttempt);
        }, RETRY_DELAY_MS);
    }
}