package com.quectel.otatest;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.UpdateEngine;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.Button;
import com.quectel.otatest.UpdateParser;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.os.Message;
import android.os.UpdateEngineCallback;
import android.provider.Settings;
import android.os.Environment;
import android.net.Uri;
import android.content.pm.PackageManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class demo extends Activity {
    TextView buildIdText, statusText;
    EditText urlEdit;
    Button installBtn;
    
    String downloadPath = "/storage/emulated/0/update.zip";
    String installPath = "/data/ota_package/update.zip";
    WakeLock mWakelock;
    String TAG="OTATEST";
    Context mContext;
    private ProgressDialog progressDialog;

    UpdateEngineCallback mUpdateEngineCallback = new UpdateEngineCallback(){
        @Override
        public void onStatusUpdate(int status,float percent){
            Log.d(TAG, "UpdateEngineCallback - onStatusUpdate: status=" + status + ", percent=" + percent);
            if (status == UpdateEngine.UpdateStatusConstants.DOWNLOADING) {
                Log.d(TAG, "update progress: " + percent+";"+(int)(percent*100));
                handler.sendEmptyMessage((int)(percent*100));
            }
        }
        @Override
        public void onPayloadApplicationComplete(int errorCode) {
            Log.i(TAG, "UpdateEngineCallback - onPayloadApplicationComplete: errorCode=" + errorCode);
            if (errorCode == UpdateEngine.ErrorCodeConstants.SUCCESS) {
                Log.i(TAG, "UPDATE SUCCESS!");
                progressDialog.dismiss();
                showRebootConfirmation();
            } else {
                Log.e(TAG, "UPDATE FAILED with error code: " + errorCode);
                progressDialog.dismiss();
                showStatus("Update failed with error code: " + errorCode);
                installBtn.setEnabled(true);
            }
        }
    };
    
    Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if(progressDialog != null && !progressDialog.isShowing()){
                progressDialog.show();
            }
            if(progressDialog != null) {
                progressDialog.setProgress(msg.what);
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate - Activity starting");
        mContext = this;
        setContentView(R.layout.activity_demo);

        PowerManager pm = (PowerManager)this.getSystemService(Context.POWER_SERVICE);
        mWakelock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "OTA Wakelock");
        
        buildIdText = (TextView) findViewById(R.id.build_id);
        statusText = (TextView) findViewById(R.id.status);
        urlEdit = (EditText) findViewById(R.id.url);
        installBtn = (Button) findViewById(R.id.install_btn);
        
        // Display current build ID
        String buildId = Build.ID + " (" + Build.VERSION.RELEASE + ")";
        buildIdText.setText("Current Build: " + buildId);
        
        Log.i(TAG, "onCreate - Initialized with build: " + buildId);
    }

    public void installUpdate(View v) {
        Log.i(TAG, "installUpdate - Install button clicked");
        String urlString = urlEdit.getText().toString().trim();
        
        if(urlString.isEmpty()){
            showStatus("Please enter a valid URL");
            return;
        }
        
        installBtn.setEnabled(false);
        showStatus("Starting installation...");
        
        new Thread(new Runnable() {
            public void run() { 
                performFullInstall(urlString);
            }
        }).start();
    }

    private void performFullInstall(String urlString) {
        try {
            // Step 1: Download
            showStatus("Downloading update...");
            if(!downloadFile(urlString)) {
                return;
            }
            
            // Step 2: Set permissions
            showStatus("Setting permissions...");
            if(!setFilePermissions(downloadPath)) {
                return;
            }
            
            // Step 3: Move file
            showStatus("Moving to install directory...");
            if(!moveFile(downloadPath, installPath)) {
                return;
            }
            
            // Step 4: Install
            showStatus("Installing update...");
            installUpdate();
            
        } catch (Exception e) {
            Log.e(TAG, "performFullInstall - Error during installation", e);
            runOnUiThread(() -> {
                showStatus("Installation failed: " + e.getMessage());
                installBtn.setEnabled(true);
            });
        }
    }

    private boolean downloadFile(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();
            
            int responseCode = connection.getResponseCode();
            if(responseCode != HttpURLConnection.HTTP_OK){
                runOnUiThread(() -> showStatus("Server error: " + responseCode));
                return false;
            }
            
            int fileLength = connection.getContentLength();
            InputStream input = connection.getInputStream();
            FileOutputStream output = new FileOutputStream(downloadPath);
            
            byte[] buffer = new byte[4096];
            long total = 0;
            int count;
            
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    final int finalProgress = progress;
                    runOnUiThread(() -> updateProgress("Downloading: " + finalProgress + "%", finalProgress));
                }
                output.write(buffer, 0, count);
            }
            
            output.close();
            input.close();
            connection.disconnect();
            
            Log.i(TAG, "Download completed: " + total + " bytes");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            runOnUiThread(() -> {
                showStatus("Download failed: " + e.getMessage());
                installBtn.setEnabled(true);
            });
            return false;
        }
    }

    private boolean setFilePermissions(String filePath) {
        try {
            Process process = Runtime.getRuntime().exec("chmod 644 " + filePath);
            int result = process.waitFor();
            
            if (result == 0) {
                Log.i(TAG, "Successfully set permissions: chmod 644 " + filePath);
                return true;
            } else {
                Log.e(TAG, "chmod failed with code: " + result);
                runOnUiThread(() -> {
                    showStatus("Failed to set file permissions");
                    installBtn.setEnabled(true);
                });
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "setFilePermissions failed", e);
            runOnUiThread(() -> {
                showStatus("Permission error: " + e.getMessage());
                installBtn.setEnabled(true);
            });
            return false;
        }
    }

    private boolean moveFile(String sourcePath, String destPath) {
        try {
            Process process = Runtime.getRuntime().exec("mv " + sourcePath + " " + destPath);
            int result = process.waitFor();
            
            if (result == 0) {
                Log.i(TAG, "Successfully moved file to: " + destPath);
                return true;
            } else {
                Log.e(TAG, "mv command failed with code: " + result);
                runOnUiThread(() -> {
                    showStatus("Failed to move file to install directory");
                    installBtn.setEnabled(true);
                });
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "moveFile failed", e);
            runOnUiThread(() -> {
                showStatus("Move error: " + e.getMessage());
                installBtn.setEnabled(true);
            });
            return false;
        }
    }

    private void installUpdate() {
        File file = new File(installPath);
        if(!file.exists()){
            runOnUiThread(() -> {
                showStatus("Update file not found after move operation");
                installBtn.setEnabled(true);
            });
            return;
        }
        
        UpdateParser.ParsedUpdate result;
        try {
            result = UpdateParser.parse(file);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse update file", e);
            runOnUiThread(() -> {
                showStatus("Failed to parse update file");
                installBtn.setEnabled(true);
            });
            return;
        }

        if (result == null || !result.isValid()) {
            runOnUiThread(() -> {
                showStatus("Update verification failed");
                installBtn.setEnabled(true);
            });
            return;
        }
        
        mWakelock.acquire();
        
        UpdateEngine mUpdateEngine = new UpdateEngine();
        
        runOnUiThread(() -> {
            progressDialog = new ProgressDialog(this);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setTitle("Installing Update");
            progressDialog.setMax(100);
            progressDialog.setCancelable(false);
            showStatus("Installing update...");
        });
        
        try {
            mUpdateEngine.bind(mUpdateEngineCallback);
            mUpdateEngine.applyPayload(result.mUrl, result.mOffset, result.mSize, result.mProps);
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply payload", e);
            runOnUiThread(() -> {
                showStatus("Failed to start update installation");
                installBtn.setEnabled(true);
            });
            mWakelock.release();
        }
    }

    private void showRebootConfirmation() {
        runOnUiThread(() -> {
            showStatus("Update installed successfully!");
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Update Complete");
            builder.setMessage("Update has been installed successfully. Reboot now to complete the installation?");
            builder.setPositiveButton("Reboot Now", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    rebootDevice();
                }
            });
            builder.setNegativeButton("Later", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    showStatus("Update complete. Please reboot to finish installation.");
                    installBtn.setEnabled(true);
                }
            });
            builder.setCancelable(false);
            builder.show();
        });
    }

    private void rebootDevice() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            pm.reboot("OTA Update Complete");
        } catch (Exception e) {
            Log.e(TAG, "Failed to reboot device", e);
            showStatus("Failed to reboot. Please reboot manually.");
        }
    }

    private void showStatus(final String message) {
        Log.d(TAG, "Status: " + message);
        runOnUiThread(() -> {
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
            statusText.setText(message);
        });
    }

    private void updateProgress(final String message, final int progress) {
        runOnUiThread(() -> {
            statusText.setText(message);
            if(progressDialog == null) {
                progressDialog = new ProgressDialog(this);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setTitle("Processing");
                progressDialog.setMax(100);
                progressDialog.setCancelable(false);
            }
            if(!progressDialog.isShowing()) {
                progressDialog.show();
            }
            progressDialog.setProgress(progress);
        });
    }
}