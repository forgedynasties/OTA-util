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
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
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
import java.util.ArrayList;
import java.util.List;
import android.content.SharedPreferences;

public class demo extends Activity {
    TextView buildIdText, statusText;
    EditText urlEdit;
    Button installBtn, refreshBtn, syncBtn;
    ListView updatesList;
    
    // New progress UI elements
    LinearLayout progressSection;
    ProgressBar progressBar;
    TextView progressText, progressDetails, serviceStatus;
    
    private ArrayAdapter<String> updatesAdapter;
    private List<String> availableUpdates = new ArrayList<>();
    private String selectedUpdate = "";
    private static final String SERVER_URL = "http://10.32.1.11:8080/";
    
    String downloadPath = "/storage/emulated/0/update.zip";
    String installPath = "/data/ota_package/update.zip";
    WakeLock mWakelock;
    String TAG="OTATEST";
    Context mContext;
    private ProgressDialog progressDialog;
    private boolean isServiceDownloading = false;
    private boolean isServiceInstalling = false;
    private int currentProgress = 0;
    private SharedPreferences syncPrefs;
    private static final String SYNC_PREFS = "ota_sync";
    private static final String KEY_DOWNLOAD_PROGRESS = "download_progress";
    private static final String KEY_INSTALL_PROGRESS = "install_progress";
    private static final String KEY_CURRENT_STATE = "current_state";
    private static final String KEY_STATUS_MESSAGE = "status_message";

    UpdateEngineCallback mUpdateEngineCallback = new UpdateEngineCallback(){
        @Override
        public void onStatusUpdate(int status,float percent){
            Log.d(TAG, "UpdateEngineCallback - onStatusUpdate: status=" + status + ", percent=" + percent);
            if (status == UpdateEngine.UpdateStatusConstants.DOWNLOADING) {
                Log.d(TAG, "update progress: " + percent+";"+(int)(percent*100));
                int progress = Math.max(50, (int)(50 + percent * 50)); // Installation progress starts at 50%
                broadcastInstallProgress(progress, "Installing update...");
                handler.sendEmptyMessage((int)(percent*100));
            }
        }
        @Override
        public void onPayloadApplicationComplete(int errorCode) {
            Log.i(TAG, "UpdateEngineCallback - onPayloadApplicationComplete: errorCode=" + errorCode);
            if (errorCode == UpdateEngine.ErrorCodeConstants.SUCCESS) {
                Log.i(TAG, "UPDATE SUCCESS!");
                broadcastInstallComplete(true, "Installation completed successfully. Reboot required.");
                progressDialog.dismiss();
                showRebootConfirmation();
            } else {
                Log.e(TAG, "UPDATE FAILED with error code: " + errorCode);
                broadcastInstallComplete(false, "Update failed with error code: " + errorCode);
                progressDialog.dismiss();
                showStatus("Update failed with error code: " + errorCode);
                installBtn.setEnabled(true);
                refreshBtn.setEnabled(true);
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
    
    private BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.quectel.otatest.UPDATES_FOUND".equals(action)) {
                ArrayList<String> updates = intent.getStringArrayListExtra("updates");
                if (updates != null) {
                    runOnUiThread(() -> updateAvailableUpdates(updates));
                }
            } else if ("com.quectel.otatest.AUTO_INSTALL".equals(action)) {
                String updateUrl = intent.getStringExtra("update_url");
                if (updateUrl != null) {
                    runOnUiThread(() -> startAutoInstallation(updateUrl));
                }
            } else if ("com.quectel.otatest.DOWNLOAD_PROGRESS".equals(action)) {
                int progress = intent.getIntExtra("progress", 0);
                String message = intent.getStringExtra("message");
                runOnUiThread(() -> updateServiceProgress("download", progress, message));
            } else if ("com.quectel.otatest.DOWNLOAD_COMPLETE".equals(action)) {
                runOnUiThread(() -> handleServiceDownloadComplete());
            } else if ("com.quectel.otatest.DOWNLOAD_ERROR".equals(action)) {
                String error = intent.getStringExtra("error");
                runOnUiThread(() -> handleServiceError("Download failed: " + error));
            } else if ("com.quectel.otatest.START_INSTALL".equals(action)) {
                String updateUrl = intent.getStringExtra("update_url");
                runOnUiThread(() -> startInstallationFromService(updateUrl));
            } else if ("com.quectel.otatest.SERVICE_STATUS".equals(action)) {
                // Handle service status sync response
                String state = intent.getStringExtra("state");
                int progress = intent.getIntExtra("progress", 0);
                String message = intent.getStringExtra("message");
                if (state != null && progress > 0) {
                    runOnUiThread(() -> {
                        updateServiceProgress(state, progress, message);
                        updateServiceStatus("download".equals(state) ? "Downloading" : "Installing");
                    });
                } else {
                    runOnUiThread(() -> updateServiceStatus("Active"));
                }
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
        refreshBtn = (Button) findViewById(R.id.refresh_btn);
        syncBtn = (Button) findViewById(R.id.sync_btn);
        updatesList = (ListView) findViewById(R.id.updates_list);
        
        // Initialize new progress UI elements
        progressSection = (LinearLayout) findViewById(R.id.progress_section);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        progressText = (TextView) findViewById(R.id.progress_text);
        progressDetails = (TextView) findViewById(R.id.progress_details);
        serviceStatus = (TextView) findViewById(R.id.service_status);
        
        // Initialize progress text (using status text for now)
        // progressText is now the dedicated progress text view
        
        // Initialize sync preferences
        syncPrefs = getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE);
        
        // Restore synchronized state
        restoreSyncedState();
        
        // Setup updates list
        updatesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, availableUpdates);
        updatesList.setAdapter(updatesAdapter);
        updatesList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        updatesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedUpdate = availableUpdates.get(position);
                urlEdit.setText(SERVER_URL + selectedUpdate);
                installBtn.setEnabled(true);
            }
        });
        
        // Display current build ID
        String buildId = Build.ID + " (" + Build.VERSION.RELEASE + ")";
        buildIdText.setText("Current Build: " + buildId);
        
        // Start the update service
        Intent serviceIntent = new Intent(this, UpdateService.class);
        startForegroundService(serviceIntent);
        
        // Register broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.quectel.otatest.UPDATES_FOUND");
        filter.addAction("com.quectel.otatest.AUTO_INSTALL");
        filter.addAction("com.quectel.otatest.DOWNLOAD_PROGRESS");
        filter.addAction("com.quectel.otatest.DOWNLOAD_COMPLETE");
        filter.addAction("com.quectel.otatest.DOWNLOAD_ERROR");
        filter.addAction("com.quectel.otatest.START_INSTALL");
        filter.addAction("com.quectel.otatest.SERVICE_STATUS");
        registerReceiver(updateReceiver, filter);
        
        // Check if started from notification
        handleNotificationIntent();
        
        // Check if download is complete and ready for installation
        if (getIntent().getBooleanExtra("download_complete", false)) {
            showDownloadCompleteDialog();
        }
        
        Log.i(TAG, "onCreate - Initialized with build: " + buildId);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateReceiver != null) {
            unregisterReceiver(updateReceiver);
        }
    }
    
    private void handleNotificationIntent() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("available_updates")) {
            ArrayList<String> updates = intent.getStringArrayListExtra("available_updates");
            if (updates != null) {
                updateAvailableUpdates(updates);
            }
        }
    }
    
    private void updateAvailableUpdates(List<String> updates) {
        availableUpdates.clear();
        availableUpdates.addAll(updates);
        updatesAdapter.notifyDataSetChanged();
        showStatus("Found " + updates.size() + " updates");
    }
    
    private void updateServiceProgress(String type, int progress, String message) {
        currentProgress = progress;
        
        // Save state to SharedPreferences for sync
        syncPrefs.edit()
            .putInt("download".equals(type) ? KEY_DOWNLOAD_PROGRESS : KEY_INSTALL_PROGRESS, progress)
            .putString(KEY_CURRENT_STATE, type)
            .putString(KEY_STATUS_MESSAGE, message)
            .apply();
        
        // Update progress UI
        showProgressSection(true);
        progressBar.setProgress(progress);
        progressText.setText(progress + "%");
        progressDetails.setText(message);
        
        if ("download".equals(type)) {
            isServiceDownloading = true;
            showStatus(message);
            progressSection.findViewById(R.id.progress_title).setVisibility(View.VISIBLE);
            ((TextView) progressSection.findViewById(R.id.progress_title)).setText("Downloading Update");
            
            // Show or update progress dialog
            if (progressDialog == null || !progressDialog.isShowing()) {
                progressDialog = new ProgressDialog(this);
                progressDialog.setTitle("Downloading Update");
                progressDialog.setMessage(message);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setMax(100);
                progressDialog.setCancelable(false);
                progressDialog.show();
            }
            progressDialog.setProgress(progress);
            progressDialog.setMessage(message);
        } else if ("install".equals(type)) {
            isServiceInstalling = true;
            showStatus(message);
            ((TextView) progressSection.findViewById(R.id.progress_title)).setText("Installing Update");
            
            // Show or update installation progress dialog
            if (progressDialog == null || !progressDialog.isShowing()) {
                progressDialog = new ProgressDialog(this);
                progressDialog.setTitle("Installing Update");
                progressDialog.setMessage(message);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setMax(100);
                progressDialog.setCancelable(false);
                progressDialog.show();
            }
            progressDialog.setProgress(progress);
            progressDialog.setMessage(message);
        }
        
        // Also request service status sync
        requestServiceSync();
    }
    
    private void handleServiceDownloadComplete() {
        isServiceDownloading = false;
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        
        // Update progress UI
        progressBar.setProgress(100);
        progressText.setText("100%");
        progressDetails.setText("Download complete");
        ((TextView) progressSection.findViewById(R.id.progress_title)).setText("Download Complete");
        
        showDownloadCompleteDialog();
    }
    
    private void handleServiceError(String error) {
        isServiceDownloading = false;
        isServiceInstalling = false;
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        
        // Update progress UI
        ((TextView) progressSection.findViewById(R.id.progress_title)).setText("Error");
        progressDetails.setText(error);
        updateServiceStatus("Error");
        
        showStatus(error);
        installBtn.setEnabled(true);
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
    }
    
    private void showDownloadCompleteDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Download Complete")
            .setMessage("Update has been downloaded successfully. Do you want to install it now?")
            .setPositiveButton("Install Now", (dialog, which) -> {
                // Trigger installation via service
                Intent broadcast = new Intent("com.quectel.otatest.CONTINUE_INSTALL");
                sendBroadcast(broadcast);
                showStatus("Starting installation...");
            })
            .setNegativeButton("Later", (dialog, which) -> {
                showStatus("Installation postponed. Tap notification to install later.");
            })
            .setCancelable(false)
            .show();
    }
    
    private void startInstallationFromService(String updateUrl) {
        isServiceInstalling = true;
        urlEdit.setText(updateUrl);
        installBtn.setEnabled(false);
        refreshBtn.setEnabled(false);
        
        showStatus("Starting installation from downloaded file...");
        
        // Start the actual installation process
        new Thread(() -> performServiceInstallation()).start();
    }
    
    private void performServiceInstallation() {
        try {
            // File should already be downloaded, just need to move and install
            String downloadPath = "/storage/emulated/0/update.zip";
            String installPath = "/data/ota_package/update.zip";
            
            // Broadcast progress to service
            broadcastInstallProgress(10, "Setting permissions...");
            if(!setFilePermissions(downloadPath)) {
                return;
            }
            
            // Step 2: Move file
            broadcastInstallProgress(30, "Moving to install directory...");
            if(!moveFile(downloadPath, installPath)) {
                return;
            }
            
            // Step 3: Install
            broadcastInstallProgress(50, "Installing update...");
            installUpdate();
            
        } catch (Exception e) {
            Log.e(TAG, "performServiceInstallation - Error during installation", e);
            broadcastInstallComplete(false, "Installation failed: " + e.getMessage());
            runOnUiThread(() -> {
                showStatus("Installation failed: " + e.getMessage());
                installBtn.setEnabled(true);
                refreshBtn.setEnabled(true);
            });
        }
    }
    
    private void broadcastInstallProgress(int progress, String message) {
        Intent broadcast = new Intent("com.quectel.otatest.INSTALL_PROGRESS");
        broadcast.putExtra("progress", progress);
        broadcast.putExtra("message", message);
        sendBroadcast(broadcast);
        
        runOnUiThread(() -> {
            showStatus(message + " " + progress + "%");
            updateProgress(message, progress);
        });
    }
    
    private void broadcastInstallComplete(boolean success, String message) {
        Intent broadcast = new Intent("com.quectel.otatest.INSTALL_COMPLETE");
        broadcast.putExtra("success", success);
        broadcast.putExtra("message", message);
        sendBroadcast(broadcast);
    }
    
    private void restoreSyncedState() {
        // Restore last known state from SharedPreferences
        String currentState = syncPrefs.getString(KEY_CURRENT_STATE, "");
        String statusMessage = syncPrefs.getString(KEY_STATUS_MESSAGE, "Ready");
        
        if ("download".equals(currentState)) {
            int downloadProgress = syncPrefs.getInt(KEY_DOWNLOAD_PROGRESS, 0);
            if (downloadProgress > 0 && downloadProgress < 100) {
                isServiceDownloading = true;
                showProgressSection(true);
                progressBar.setProgress(downloadProgress);
                progressText.setText(downloadProgress + "%");
                progressDetails.setText(statusMessage);
                ((TextView) progressSection.findViewById(R.id.progress_title)).setText("Downloading Update");
                showStatus(statusMessage);
                updateServiceStatus("Downloading");
                Log.d(TAG, "Restored download state: " + downloadProgress + "% - " + statusMessage);
            }
        } else if ("install".equals(currentState)) {
            int installProgress = syncPrefs.getInt(KEY_INSTALL_PROGRESS, 0);
            if (installProgress > 0 && installProgress < 100) {
                isServiceInstalling = true;
                showProgressSection(true);
                progressBar.setProgress(installProgress);
                progressText.setText(installProgress + "%");
                progressDetails.setText(statusMessage);
                ((TextView) progressSection.findViewById(R.id.progress_title)).setText("Installing Update");
                showStatus(statusMessage);
                updateServiceStatus("Installing");
                Log.d(TAG, "Restored install state: " + installProgress + "% - " + statusMessage);
            }
        } else {
            showProgressSection(false);
            showStatus(statusMessage);
            updateServiceStatus("Active");
        }
        
        // Request current state from service
        requestServiceSync();
    }
    
    private void showProgressSection(boolean show) {
        progressSection.setVisibility(show ? View.VISIBLE : View.GONE);
    }
    
    private void updateServiceStatus(String status) {
        serviceStatus.setText(status);
        if ("Active".equals(status)) {
            serviceStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else if ("Error".equals(status)) {
            serviceStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        } else {
            serviceStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
        }
    }
    
    public void syncWithService(View v) {
        // Manual sync button clicked
        updateServiceStatus("Syncing...");
        requestServiceSync();
        Toast.makeText(this, "Requesting sync with service...", Toast.LENGTH_SHORT).show();
    }
    
    private void requestServiceSync() {
        // Request service to send current status
        Intent syncRequest = new Intent("com.quectel.otatest.SYNC_REQUEST");
        sendBroadcast(syncRequest);
    }
    
    public void refreshUpdates(View v) {
        // Trigger immediate update check
        Intent broadcast = new Intent("com.quectel.otatest.REFRESH_UPDATES");
        sendBroadcast(broadcast);
        showStatus("Refreshing updates...");
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

    private void startAutoInstallation(String updateUrl) {
        Log.i(TAG, "Automatic installation triggered - update is being downloaded by service");
        showStatus("Update found - Download in progress via background service...");
        
        // Set URL but keep controls enabled until download completes
        urlEdit.setText(updateUrl);
        
        // The service will handle the download and notify us when complete
        Toast.makeText(this, "Update download started in background", Toast.LENGTH_LONG).show();
    }
}