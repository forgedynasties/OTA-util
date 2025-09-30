package com.quectel.otatest;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String TAG = "OTATEST";
    
    private UIManager uiManager;
    private DownloadManager downloadManager;
    private UpdateManager updateManager;
    private ShellManager shellManager;
    
    private ExecutorService executor;
    private Handler mainHandler;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Activity starting");
        
        setContentView(R.layout.activity_main);
        
        initializeComponents();
        
        Log.i(TAG, "Activity initialized successfully");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    private void initializeComponents() {
        // Initialize threading
        executor = Executors.newCachedThreadPool();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize power management
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "OTA:Wakelock");
        
        // Initialize managers
        uiManager = new UIManager(this);
        downloadManager = new DownloadManager(mainHandler);
        updateManager = new UpdateManager(mainHandler, wakeLock);
        shellManager = new ShellManager();
        
        uiManager.hideKeyboard();
        
        Log.d(TAG, "All components initialized");
    }

    private void cleanup() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (uiManager != null) {
            uiManager.dismissDialogs();
        }
        Log.d(TAG, "Resources cleaned up");
    }

    // === BUTTON HANDLERS ===

    public void download(View v) {
        Log.i(TAG, "Download initiated");
        String url = uiManager.getUrlText();
        
        if (url.isEmpty()) {
            uiManager.showMessage("Please enter a valid URL");
            return;
        }
        
        uiManager.setDownloadButtonEnabled(false);
        uiManager.showDownloadDialog();
        
        executor.execute(() -> downloadManager.download(url, new DownloadManager.DownloadCallback() {
            @Override
            public void onProgress(int progress) {
                uiManager.updateDownloadProgress(progress);
            }
            
            @Override
            public void onSuccess() {
                uiManager.dismissDialogs();
                uiManager.setDownloadButtonEnabled(true);
                uiManager.showMessage("Download completed successfully!");
            }
            
            @Override
            public void onError(String error) {
                uiManager.dismissDialogs();
                uiManager.setDownloadButtonEnabled(true);
                uiManager.showMessage("Download failed: " + error);
            }
        }));
    }

    public void update(View v) {
        Log.i(TAG, "Update initiated");
        
        uiManager.setUpdateButtonEnabled(false);
        uiManager.showUpdateDialog();
        
        executor.execute(() -> updateManager.performUpdate(new UpdateManager.UpdateCallback() {
            @Override
            public void onProgress(int progress) {
                uiManager.updateUpdateProgress(progress);
            }
            
            @Override
            public void onSuccess() {
                uiManager.dismissDialogs();
                uiManager.showMessage("Update completed successfully!");
            }
            
            @Override
            public void onError(String error) {
                uiManager.dismissDialogs();
                uiManager.setUpdateButtonEnabled(true);
                uiManager.showMessage(error);
            }
        }));
    }

    public void testShell(View v) {
        Log.i(TAG, "Shell test initiated");
        executor.execute(() -> {
            String output = shellManager.executeCommand("echo 'Shell test successful' && date");
            mainHandler.post(() -> uiManager.setShellOutputText("Shell Test Output:\n" + output));
        });
    }

    public void executeShell(View v) {
        String command = uiManager.getShellCommandText();
        if (command.isEmpty()) {
            uiManager.setShellOutputText("Please enter a command");
            return;
        }
        
        Log.i(TAG, "Executing shell command: " + command);
        executor.execute(() -> {
            String output = shellManager.executeCommand(command);
            mainHandler.post(() -> 
                uiManager.setShellOutputText("Command: " + command + "\n\nOutput:\n" + output)
            );
        });
    }

    public void checkPermissions(View v) {
        Log.i(TAG, "Checking permissions");
        executor.execute(() -> {
            String permInfo = shellManager.getPermissionInfo();
            mainHandler.post(() -> uiManager.setShellOutputText(permInfo));
        });
    }
}