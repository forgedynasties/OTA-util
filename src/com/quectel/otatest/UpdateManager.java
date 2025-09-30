package com.quectel.otatest;

import android.os.Handler;
import android.os.PowerManager;
import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.util.Log;

import java.io.File;

public class UpdateManager {
    private static final String TAG = "UpdateManager";
    private static final String DEST_PATH = "/data/ota_package/update.zip";
    
    private Handler mainHandler;
    private PowerManager.WakeLock wakeLock;
    private UpdateEngine updateEngine;
    
    public UpdateManager(Handler mainHandler, PowerManager.WakeLock wakeLock) {
        this.mainHandler = mainHandler;
        this.wakeLock = wakeLock;
        this.updateEngine = new UpdateEngine();
    }
    
    public void performUpdate(UpdateCallback callback) {
        try {
            File updateFile = new File(DEST_PATH);
            if (!updateFile.exists()) {
                callback.onError("Update file not found");
                return;
            }
            
            Log.d(TAG, "Parsing update file");
            UpdateParser.ParsedUpdate result = UpdateParser.parse(updateFile);
            
            if (result == null || !result.isValid()) {
                throw new RuntimeException("Update verification failed");
            }
            
            Log.i(TAG, "Update parsed successfully: " + result.toString());
            applyUpdate(result, callback);
            
        } catch (Exception e) {
            Log.e(TAG, "Update failed", e);
            mainHandler.post(() -> callback.onError(e.getMessage()));
        }
    }
    
    private void applyUpdate(UpdateParser.ParsedUpdate result, UpdateCallback callback) {
        mainHandler.post(() -> {
            try {
                wakeLock.acquire();
                
                UpdateEngineCallback engineCallback = new UpdateEngineCallback() {
                    @Override
                    public void onStatusUpdate(int status, float percent) {
                        Log.d(TAG, "Update status: " + status + ", progress: " + percent);
                        if (status == UpdateEngine.UpdateStatusConstants.DOWNLOADING) {
                            mainHandler.post(() -> callback.onProgress((int) (percent * 100)));
                        }
                    }
                    
                    @Override
                    public void onPayloadApplicationComplete(int errorCode) {
                        Log.i(TAG, "Update complete with error code: " + errorCode);
                        mainHandler.post(() -> {
                            if (errorCode == UpdateEngine.ErrorCodeConstants.SUCCESS) {
                                callback.onSuccess();
                            } else {
                                callback.onError("Update failed with error code: " + errorCode);
                            }
                        });
                        
                        if (wakeLock.isHeld()) {
                            wakeLock.release();
                        }
                    }
                };
                
                updateEngine.bind(engineCallback);
                updateEngine.applyPayload(result.mUrl, result.mOffset, result.mSize, result.mProps);
                
                Log.i(TAG, "Update payload applied");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to apply update", e);
                callback.onError("Failed to apply update: " + e.getMessage());
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
            }
        });
    }
    
    public interface UpdateCallback {
        void onProgress(int progress);
        void onSuccess();
        void onError(String error);
    }
}