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
    
    private TextView statusText;
    private Button installButton;
    private ProgressDialog progressDialog;
    private Handler mainHandler;
    private ShellManager shellManager;
    private UpdateManager updateManager;
    private PowerManager.WakeLock wakeLock;
    private boolean isUpdateAvailable = false;
    
    // API-based update information
    private String updatePackageUrl = null;
    private String newBuildId = null;
    
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
            // Get update details from intent (from MyService notification)
            updatePackageUrl = getIntent().getStringExtra("package_url");
            newBuildId = getIntent().getStringExtra("new_build_id");
            String patchNotes = getIntent().getStringExtra("patch_notes");
            
            Log.i(TAG, "Update available from intent - got full update details:");
            Log.d(TAG, "  updatePackageUrl: " + (updatePackageUrl != null ? updatePackageUrl : "NULL"));
            Log.d(TAG, "  newBuildId: " + (newBuildId != null ? newBuildId : "NULL"));
            Log.d(TAG, "  patchNotes: " + (patchNotes != null ? patchNotes : "NULL"));
            
            statusText.setText("Update Available!\n\nNew build: " + newBuildId + "\n\n" + 
                (patchNotes != null ? patchNotes : "A new OTA update is ready to install."));
            installButton.setText("Install Update");
            installButton.setEnabled(true);
            Log.d(TAG, "UI configured for available update with API details");
        } else {
            Log.i(TAG, "No update info from intent - performing API server check");
            statusText.setText("Checking for updates...");
            installButton.setEnabled(false);
            Log.d(TAG, "UI set to checking state");
            
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Background thread started for API update check");
                    
                    final UpdateChecker.UpdateCheckResult result = UpdateChecker.checkUpdateExistsDetailed();
                    Log.d(TAG, "API server check result - Available: " + result.updateAvailable);
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "Updating UI with API server check results");
                            
                            if (result.errorMessage != null) {
                                Log.e(TAG, "API update check failed: " + result.errorMessage);
                                statusText.setText("Update Check Failed\n\n" + result.errorMessage + "\n\nPlease check your network connection and try again.");
                                installButton.setText("Retry");
                                installButton.setEnabled(true);
                                
                            } else if (result.updateAvailable) {
                                Log.i(TAG, "API confirms update available - enabling installation");
                                
                                // Debug the API result before assignment
                                Log.d(TAG, "API result.packageUrl: " + (result.packageUrl != null ? result.packageUrl : "NULL"));
                                Log.d(TAG, "API result.newBuildId: " + (result.newBuildId != null ? result.newBuildId : "NULL"));
                                Log.d(TAG, "API result.patchNotes: " + (result.patchNotes != null ? result.patchNotes : "NULL"));
                                
                                // Store update information for download
                                updatePackageUrl = result.packageUrl;
                                newBuildId = result.newBuildId;
                                
                                // Verify assignment worked
                                Log.d(TAG, "After assignment - updatePackageUrl: " + (updatePackageUrl != null ? updatePackageUrl : "NULL"));
                                Log.d(TAG, "After assignment - newBuildId: " + (newBuildId != null ? newBuildId : "NULL"));
                                
                                statusText.setText("Update Available!\n\nNew build: " + newBuildId + "\n\n" + 
                                    (result.patchNotes != null ? result.patchNotes : "A new OTA update is ready to install."));
                                installButton.setText("Install Update");
                                installButton.setEnabled(true);
                                isUpdateAvailable = true;
                                
                                Log.d(TAG, "Update package URL: " + updatePackageUrl);
                                Log.d(TAG, "New build ID: " + newBuildId);
                                
                            } else {
                                Log.i(TAG, "API confirms no update - system is current");
                                statusText.setText("No Update Available\n\nYour system is up to date.\n\nCurrent build: " + DeviceUtils.getBuildId());
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
        
        // Safety check: ensure we have API data before proceeding
        if (updatePackageUrl == null || newBuildId == null) {
            Log.w(TAG, "Missing API data - forcing fresh update check");
            Log.d(TAG, "updatePackageUrl: " + (updatePackageUrl != null ? updatePackageUrl : "NULL"));
            Log.d(TAG, "newBuildId: " + (newBuildId != null ? newBuildId : "NULL"));
            
            statusText.setText("Verifying update information...");
            installButton.setEnabled(false);
            
            // Force a fresh API check
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final UpdateChecker.UpdateCheckResult result = UpdateChecker.checkUpdateExistsDetailed();
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (result.errorMessage != null) {
                                showError("Update verification failed: " + result.errorMessage);
                                return;
                            }
                            
                            if (result.updateAvailable && result.packageUrl != null) {
                                updatePackageUrl = result.packageUrl;
                                newBuildId = result.newBuildId;
                                Log.i(TAG, "Fresh API check completed - packageUrl: " + updatePackageUrl);
                                
                                // Now proceed with installation
                                proceedWithInstallation();
                            } else {
                                showError("No update available or missing package information");
                            }
                        }
                    });
                }
            }).start();
            return;
        }
        
        proceedWithInstallation();
    }
    
    private void proceedWithInstallation() {
        Log.d(TAG, "Starting update process...");
        Log.d(TAG, "Final check - updatePackageUrl: " + (updatePackageUrl != null ? updatePackageUrl : "NULL"));
        Log.d(TAG, "Final check - newBuildId: " + (newBuildId != null ? newBuildId : "NULL"));
        
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
                    Log.d(TAG, "updatePackageUrl state: " + (updatePackageUrl != null ? updatePackageUrl : "NULL"));
                    Log.d(TAG, "newBuildId state: " + (newBuildId != null ? newBuildId : "NULL"));
                    Log.d(TAG, "isUpdateAvailable state: " + isUpdateAvailable);
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.setMessage("Step 1: Downloading update...");
                            progressDialog.setProgress(0);
                        }
                    });
                    
                    // Get download URL from API response
                    String downloadUrl;
                    if (updatePackageUrl != null) {
                        downloadUrl = OTAApiClient.getDownloadUrl(updatePackageUrl);
                        Log.d(TAG, "Using API download URL: " + downloadUrl);
                    } else {
                        Log.e(TAG, "No package URL available from API response");
                        Log.e(TAG, "This indicates the API check didn't properly set updatePackageUrl");
                        throw new RuntimeException("No package URL available");
                    }
                    
                    // Create DownloadManager instance and start download
                    DownloadManager downloadManager = new DownloadManager();
                    boolean downloadSuccess = downloadManager.downloadFile(downloadUrl, "/data/ota_package/update.zip", 
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
                                Log.d(TAG, "Download completed successfully - starting checksum validation");
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        progressDialog.setProgress(100);
                                        progressDialog.setMessage("Step 1: Download complete - Validating...");
                                    }
                                });
                                
                                // Validate checksum before proceeding
                                validateAndProceed();
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
    
    /**
     * Validate downloaded package checksum before proceeding with installation
     */
    private void validateAndProceed() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "=== Starting Package Checksum Validation ===");
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.setMessage("Step 1: Validating package integrity...");
                            progressDialog.setProgress(0);
                        }
                    });
                    
                    if (newBuildId == null) {
                        Log.e(TAG, "No build ID available for validation");
                        throw new RuntimeException("No build ID available for checksum validation");
                    }
                    
                    // Validate the downloaded package
                    boolean isValid = UpdateChecker.validateDownloadedPackage("/data/ota_package/update.zip", newBuildId);
                    
                    if (isValid) {
                        Log.i(TAG, "✓ Package validation successful - proceeding with installation");
                        
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.setMessage("Step 1: Package validated successfully!");
                                progressDialog.setProgress(100);
                            }
                        });
                        
                        // Wait a moment then start installation
                        mainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                startInstallation();
                            }
                        }, 1500);
                        
                    } else {
                        Log.e(TAG, "✗ Package validation failed - checksum mismatch");
                        
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.dismiss();
                                showError("Package validation failed. The downloaded file may be corrupted. Please try again.");
                            }
                        });
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Exception during package validation: " + e.getMessage(), e);
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            showError("Package validation error: " + e.getMessage());
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