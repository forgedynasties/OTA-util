package com.quectel.otatest;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class UpdateActivity extends Activity {
    private static final String TAG = "UpdateActivity";
    private static final String FALLBACK_UPDATE_URL = "http://10.32.1.11:8080/update.zip"; // Fallback URL if API doesn't provide one
    
    private TextView statusText;
    private Button installButton;
    private ProgressDialog progressDialog;
    private Handler mainHandler;
    private ShellManager shellManager;
    private UpdateManager updateManager;
    private PowerManager.WakeLock wakeLock;
    private boolean isUpdateAvailable = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "=== UpdateActivity Starting ===");
        Log.d(TAG, "Setting content view to activity_update layout");
        
        try {
            setContentView(R.layout.activity_update);
            Log.d(TAG, "Content view set successfully");
            
            mainHandler = new Handler(Looper.getMainLooper());
            shellManager = new ShellManager();
            
            // Initialize power management and update manager
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "OTA:UpdateActivity");
            updateManager = new UpdateManager(mainHandler, wakeLock);
            
            Log.d(TAG, "Handler, ShellManager, and UpdateManager initialized");
            
            initViews();
            Log.d(TAG, "Views initialized");
            
            checkUpdateStatus();
            Log.d(TAG, "Update status check initiated");
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
        }
        
        Log.i(TAG, "=== UpdateActivity Created Successfully ===");
    }
    
    private void initViews() {
        statusText = findViewById(R.id.status_text);
        installButton = findViewById(R.id.install_button);
        
        installButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startUpdateProcess();
            }
        });
    }
    
    private void checkUpdateStatus() {
        Log.i(TAG, "=== Checking Update Status ===");
        
        isUpdateAvailable = getIntent().getBooleanExtra("update_available", false);
        Log.d(TAG, "Intent extra 'update_available': " + isUpdateAvailable);
        
        // Check for update details passed from other activities
        String downloadUrl = getIntent().getStringExtra("download_url");
        String buildId = getIntent().getStringExtra("build_id");
        String patchNotes = getIntent().getStringExtra("patch_notes");
        
        Log.d(TAG, "Intent extras received:");
        Log.d(TAG, "- download_url: " + downloadUrl);
        Log.d(TAG, "- build_id: " + buildId);
        Log.d(TAG, "- patch_notes: " + patchNotes);
        
        if (isUpdateAvailable && downloadUrl != null) {
            Log.i(TAG, "Update available from intent with download URL - configuring UI");
            Log.i(TAG, "📦 Download URL: " + downloadUrl);
            Log.i(TAG, "🔄 Build ID: " + buildId);
            
            String statusMessage = "Update Available!\n\n";
            statusMessage += "New Build: " + (buildId != null ? buildId : "Unknown") + "\n";
            if (patchNotes != null && !patchNotes.isEmpty()) {
                statusMessage += "\nPatch Notes:\n" + patchNotes;
            }
            statusMessage += "\n\nReady to install the latest version.";
            
            statusText.setText(statusMessage);
            installButton.setText("Install Update");
            installButton.setEnabled(true);
            Log.d(TAG, "UI configured for available update with details");
        } else {
            Log.i(TAG, "No complete update info - performing API server check");
            statusText.setText("Checking for updates...");
            installButton.setEnabled(false);
            Log.d(TAG, "UI set to checking state");
            
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Background thread started for API update check");
                    Log.i(TAG, "Current Build ID: " + UpdateChecker.getCurrentBuildId());
                    
                    final OTAApiClient.UpdateResponse response = UpdateChecker.checkForUpdate();
                    Log.d(TAG, "API update check completed");
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "Updating UI with API check results");
                            
                            if (response != null) {
                                Log.i(TAG, "=== UpdateActivity API Results ===");
                                Log.i(TAG, "Status: " + response.status);
                                Log.i(TAG, "Build ID: " + response.buildId);
                                Log.i(TAG, "Package URL: " + response.packageUrl);
                                Log.i(TAG, "Download URL: " + response.getFullPackageUrl());
                                Log.i(TAG, "Patch Notes: " + response.patchNotes);
                                
                                if (response.isUpdateAvailable()) {
                                    Log.i(TAG, "🎉 API confirms update available - enabling installation");
                                    Log.i(TAG, "📦 Download URL: " + response.getFullPackageUrl());
                                    
                                    String statusMessage = "Update Available!\n\n";
                                    statusMessage += "New Build: " + response.buildId + "\n";
                                    if (response.patchNotes != null && !response.patchNotes.isEmpty()) {
                                        statusMessage += "\nPatch Notes:\n" + response.patchNotes;
                                    }
                                    statusMessage += "\n\nReady to install the latest version.";
                                    
                                    statusText.setText(statusMessage);
                                    installButton.setText("Install Update");
                                    installButton.setEnabled(true);
                                    isUpdateAvailable = true;
                                    
                                    // Store update info for download process
                                    getIntent().putExtra("download_url", response.getFullPackageUrl());
                                    getIntent().putExtra("build_id", response.buildId);
                                    getIntent().putExtra("patch_notes", response.patchNotes);
                                    
                                } else if (response.isUpToDate()) {
                                    Log.i(TAG, "✅ API confirms system is up to date");
                                    statusText.setText("No Update Available\n\nYour system is up to date.");
                                    installButton.setText("Check Again");
                                    installButton.setEnabled(true);
                                } else if (response.isError()) {
                                    Log.w(TAG, "❌ API returned error: " + response.message);
                                    statusText.setText("Update Check Failed\n\n" + response.message);
                                    installButton.setText("Retry");
                                    installButton.setEnabled(true);
                                } else {
                                    Log.w(TAG, "⚠️ Unexpected API status: " + response.status);
                                    statusText.setText("Unexpected Response\n\n" + response.status);
                                    installButton.setText("Retry");
                                    installButton.setEnabled(true);
                                }
                                Log.i(TAG, "=================================");
                            } else {
                                Log.e(TAG, "❌ API check failed - null response");
                                statusText.setText("Update Check Failed\n\nCould not connect to server. Please check your connection and try again.");
                                installButton.setText("Retry");
                                installButton.setEnabled(true);
                            }
                        }
                    });
                }
            }).start();
        }
    }
    
    private void startUpdateProcess() {
        if (!isUpdateAvailable) {
            checkUpdateStatus();
            return;
        }
        
        Log.d(TAG, "Starting update process...");
        installButton.setEnabled(false);
        
        // Show progress dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Installing Update");
        progressDialog.setMessage("Step 1: Downloading update...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        statusText.setText("Installing update...\nDo not turn off the device during this process.");
        
        // Start download process
        downloadUpdate();
    }
    
    private void downloadUpdate() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "=== Starting Update Download Process ===");
                    
                    // Get download URL from intent or use fallback
                    String downloadUrl = getIntent().getStringExtra("download_url");
                    if (downloadUrl == null || downloadUrl.isEmpty()) {
                        Log.w(TAG, "No download URL in intent, using fallback URL");
                        downloadUrl = FALLBACK_UPDATE_URL; // Fallback to old URL
                    }
                    
                    Log.i(TAG, "📦 Download URL: " + downloadUrl);
                    Log.i(TAG, "📂 Target file: /data/ota_package/update.zip");
                    Log.i(TAG, "🔄 Build ID: " + getIntent().getStringExtra("build_id"));
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.setMessage("Step 1: Downloading update...");
                            progressDialog.setProgress(0);
                        }
                    });
                    
                    // Create DownloadManager instance and start download
                    Log.d(TAG, "Creating DownloadManager instance...");
                    DownloadManager downloadManager = new DownloadManager();
                    
                    Log.i(TAG, "Starting download from: " + downloadUrl);
                    final String finalDownloadUrl = downloadUrl;
                    boolean downloadSuccess = downloadManager.downloadFile(downloadUrl, "/data/ota_package/update.zip", 
                        new DownloadManager.DownloadCallback() {
                            @Override
                            public void onProgress(final int progress) {
                                Log.v(TAG, "Download progress: " + progress + "% from " + finalDownloadUrl);
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        progressDialog.setProgress(progress);
                                        progressDialog.setMessage("Step 1: Downloading update... " + progress + "%");
                                    }
                                });
                            }
                            
                            @Override
                            public void onSuccess() {
                                Log.i(TAG, "✅ Download completed successfully!");
                                Log.i(TAG, "📦 Downloaded from: " + finalDownloadUrl);
                                Log.i(TAG, "📂 Saved to: /data/ota_package/update.zip");
                                Log.i(TAG, "🔄 Build ID: " + getIntent().getStringExtra("build_id"));
                                
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        progressDialog.setProgress(100);
                                        progressDialog.setMessage("Step 1: Download complete!");
                                    }
                                });
                                
                                // Wait a moment then start installation
                                Log.d(TAG, "Scheduling installation start in 1 second...");
                                mainHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        startInstallation();
                                    }
                                }, 1000);
                            }
                            
                            @Override
                            public void onError(final String error) {
                                Log.e(TAG, "❌ Download failed from: " + finalDownloadUrl);
                                Log.e(TAG, "Error details: " + error);
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        progressDialog.dismiss();
                                        showError("Download failed: " + error);
                                    }
                                });
                            }
                        }
                    );
                    
                } catch (Exception e) {
                    Log.e(TAG, "Download error: " + e.getMessage());
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            showError("Download error: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }
    
    private void startInstallation() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Starting installation process");
                    
                    // Step 2: Move file to /storage/emulated/0/
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.setMessage("Step 2: Preparing installation...");
                            progressDialog.setProgress(0);
                        }
                    });
                    
                    String moveCommand1 = "mv /data/ota_package/update.zip /storage/emulated/0/update.zip";
                    String result1 = shellManager.executeCommand(moveCommand1);
                    Log.d(TAG, "Move to storage result: " + result1);
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.setProgress(25);
                            progressDialog.setMessage("Step 2: Setting permissions...");
                        }
                    });
                    
                    // Step 3: Change permissions
                    String chmodCommand = "chmod 644 /storage/emulated/0/update.zip";
                    String result2 = shellManager.executeCommand(chmodCommand);
                    Log.d(TAG, "Chmod result: " + result2);
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.setProgress(50);
                            progressDialog.setMessage("Step 2: Moving back to installation directory...");
                        }
                    });
                    
                    // Step 4: Move back to installation directory
                    String moveCommand2 = "mv /storage/emulated/0/update.zip /data/ota_package/update.zip";
                    String result3 = shellManager.executeCommand(moveCommand2);
                    Log.d(TAG, "Move back result: " + result3);
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.setProgress(75);
                            progressDialog.setMessage("Step 2: Starting system update...");
                        }
                    });
                    
                    // Step 5: Start actual update installation
                    startSystemUpdate();
                    
                } catch (Exception e) {
                    Log.e(TAG, "Installation preparation failed: " + e.getMessage());
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            showError("Installation preparation failed: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }
    
    private void startSystemUpdate() {
        try {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    progressDialog.setProgress(100);
                    progressDialog.setMessage("Step 2: Installation prepared successfully!");
                }
            });
            
            // Wait a moment then start the actual update process
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Update UI for system update phase
                    progressDialog.setMessage("Step 3: Installing system update...");
                    progressDialog.setProgress(0);
                    
                    statusText.setText("Installing system update...\n\nThe device will reboot automatically when complete. Do not turn off the device during this process.");
                    
                    Toast.makeText(UpdateActivity.this, "System update installation starting...", Toast.LENGTH_LONG).show();
                    Log.i(TAG, "Starting actual system update with UpdateManager");
                    
                    // Use UpdateManager to perform the actual system update
                    updateManager.performUpdate(new UpdateManager.UpdateCallback() {
                        @Override
                        public void onProgress(int progress) {
                            Log.d(TAG, "System update progress: " + progress + "%");
                            progressDialog.setProgress(progress);
                            progressDialog.setMessage("Step 3: Installing system update... " + progress + "%");
                        }
                        
                        @Override
                        public void onSuccess() {
                            Log.i(TAG, "System update completed successfully");
                            progressDialog.dismiss();
                            showRebootPrompt();
                        }
                        
                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "System update failed: " + error);
                            progressDialog.dismiss();
                            showError("System update failed: " + error);
                        }
                    });
                }
            }, 1500);
            
        } catch (Exception e) {
            Log.e(TAG, "System update start failed: " + e.getMessage());
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    progressDialog.dismiss();
                    showError("System update start failed: " + e.getMessage());
                }
            });
        }
    }
    
    /**
     * Show reboot prompt after successful update installation
     */
    private void showRebootPrompt() {
        Log.i(TAG, "=== Update Installation Completed Successfully ===");
        
        statusText.setText("Update Installation Complete!\n\nThe system update has been installed successfully. The device needs to reboot to complete the installation process.");
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Update Complete");
        builder.setMessage("The system update has been installed successfully!\n\nYour device needs to reboot now to complete the installation. All unsaved data will be lost.\n\nWould you like to reboot now?");
        builder.setIcon(R.drawable.easyftptest);
        
        // Reboot Now button
        builder.setPositiveButton("Reboot Now", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.i(TAG, "User confirmed reboot - initiating device reboot");
                dialog.dismiss();
                performReboot();
            }
        });
        
        // Reboot Later button
        builder.setNegativeButton("Reboot Later", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.i(TAG, "User chose to reboot later");
                dialog.dismiss();
                showRebootLaterMessage();
            }
        });
        
        builder.setCancelable(false); // Prevent dismissing without choice
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        Log.d(TAG, "Reboot confirmation dialog displayed");
    }
    
    /**
     * Perform device reboot using shell command
     */
    private void performReboot() {
        Log.i(TAG, "=== Initiating Device Reboot ===");
        
        try {
            // Show reboot countdown
            final ProgressDialog rebootDialog = new ProgressDialog(this);
            rebootDialog.setTitle("Rebooting Device");
            rebootDialog.setMessage("Device will reboot in 5 seconds...");
            rebootDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            rebootDialog.setMax(5);
            rebootDialog.setCancelable(false);
            rebootDialog.show();
            
            // Countdown before reboot
            final Handler countdownHandler = new Handler(Looper.getMainLooper());
            for (int i = 0; i < 5; i++) {
                final int countdown = 5 - i;
                countdownHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        rebootDialog.setProgress(5 - countdown);
                        rebootDialog.setMessage("Device will reboot in " + countdown + " seconds...");
                        
                        if (countdown == 0) {
                            rebootDialog.setMessage("Rebooting now...");
                            Log.i(TAG, "Executing reboot command");
                            
                            // Execute reboot command
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        String rebootResult = shellManager.executeCommand("reboot");
                                        Log.d(TAG, "Reboot command result: " + rebootResult);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Failed to execute reboot command: " + e.getMessage(), e);
                                        // Fallback reboot methods
                                        try {
                                            Runtime.getRuntime().exec("su -c reboot");
                                        } catch (Exception ex) {
                                            Log.e(TAG, "Fallback reboot also failed: " + ex.getMessage(), ex);
                                        }
                                    }
                                }
                            }).start();
                        }
                    }
                }, i * 1000);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error during reboot process: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to reboot device. Please reboot manually.", Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Show message when user chooses to reboot later
     */
    private void showRebootLaterMessage() {
        Log.i(TAG, "Displaying reboot later message");
        
        statusText.setText("Update Installation Complete!\n\nThe system update has been installed successfully. Please reboot your device manually when convenient to complete the installation.\n\nGo to Settings → System → Restart or hold the power button to restart.");
        
        installButton.setText("Reboot Now");
        installButton.setEnabled(true);
        installButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performReboot();
            }
        });
        
        Toast.makeText(this, "Please reboot your device to complete the update installation.", Toast.LENGTH_LONG).show();
    }
    
    private void showError(String error) {
        statusText.setText("Update Failed\n\n" + error);
        installButton.setText("Try Again");
        installButton.setEnabled(true);
        Toast.makeText(this, "Update failed: " + error, Toast.LENGTH_LONG).show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}