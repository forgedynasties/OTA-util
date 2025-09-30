package com.quectel.otatest;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
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
    private static final String UPDATE_URL = "http://10.32.1.11:8080/update.zip";
    
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
        
        if (isUpdateAvailable) {
            Log.i(TAG, "Update available from intent - configuring UI for installation");
            statusText.setText("Update Available!\n\nA new OTA update is ready to install. This will update your system to the latest version.");
            installButton.setText("Install Update");
            installButton.setEnabled(true);
            Log.d(TAG, "UI configured for available update");
        } else {
            Log.i(TAG, "No update info from intent - performing server check");
            statusText.setText("Checking for updates...");
            installButton.setEnabled(false);
            Log.d(TAG, "UI set to checking state");
            
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Background thread started for update check");
                    
                    final boolean available = UpdateChecker.checkUpdateExists();
                    Log.d(TAG, "Server check result: " + available);
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "Updating UI with server check results");
                            
                            if (available) {
                                Log.i(TAG, "Server confirms update available - enabling installation");
                                statusText.setText("Update Available!\n\nA new OTA update is ready to install.");
                                installButton.setText("Install Update");
                                installButton.setEnabled(true);
                                isUpdateAvailable = true;
                            } else {
                                Log.i(TAG, "Server confirms no update - system is current");
                                statusText.setText("No Update Available\n\nYour system is up to date.");
                                installButton.setText("Check Again");
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
                    Log.d(TAG, "Starting download process");
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.setMessage("Step 1: Downloading update...");
                            progressDialog.setProgress(0);
                        }
                    });
                    
                    // Create DownloadManager instance and start download
                    DownloadManager downloadManager = new DownloadManager();
                    boolean downloadSuccess = downloadManager.downloadFile(UPDATE_URL, "/data/ota_package/update.zip", 
                        new DownloadManager.DownloadCallback() {
                            @Override
                            public void onProgress(final int progress) {
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
                                Log.d(TAG, "Download completed successfully");
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        progressDialog.setProgress(100);
                                        progressDialog.setMessage("Step 1: Download complete!");
                                    }
                                });
                                
                                // Wait a moment then start installation
                                mainHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        startInstallation();
                                    }
                                }, 1000);
                            }
                            
                            @Override
                            public void onError(final String error) {
                                Log.e(TAG, "Download failed: " + error);
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
                            statusText.setText("System update completed successfully!\n\nThe device will reboot shortly to complete the installation.");
                            Toast.makeText(UpdateActivity.this, "Update completed! Device will reboot...", Toast.LENGTH_LONG).show();
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