package com.quectel.otatest;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class ApiTestActivity extends Activity {
    private static final String TAG = "ApiTestActivity";
    
    private TextView statusText;
    private Button testConnectivityButton;
    private Button testUpdateCheckButton;
    private Button testChecksumButton;
    private Button getBuildInfoButton;
    private Handler mainHandler;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "=== API Test Activity Starting ===");
        
        setContentView(R.layout.activity_api_test);
        
        mainHandler = new Handler(Looper.getMainLooper());
        
        initViews();
        Log.i(TAG, "=== API Test Activity Created ===");
    }
    
    private void initViews() {
        statusText = findViewById(R.id.api_status_text);
        testConnectivityButton = findViewById(R.id.test_connectivity_button);
        testUpdateCheckButton = findViewById(R.id.test_update_check_button);
        testChecksumButton = findViewById(R.id.test_checksum_button);
        getBuildInfoButton = findViewById(R.id.get_build_info_button);
        
        testConnectivityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testApiConnectivity();
            }
        });
        
        testUpdateCheckButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testUpdateCheck();
            }
        });
        
        testChecksumButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testChecksumValidation();
            }
        });
        
        getBuildInfoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBuildInfo();
            }
        });
        
        statusText.setText("API Testing Interface\\n\\nUse the buttons below to test different API endpoints and functionality.");
        
        Log.d(TAG, "Views initialized successfully");
    }
    
    private void testApiConnectivity() {
        Log.i(TAG, "=== User initiated API connectivity test ===");
        
        statusText.setText("Testing API connectivity...\\n\\nChecking connection to OTA server...");
        disableButtons();
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Starting connectivity test in background thread");
                    
                    final boolean isConnected = OTAApiClient.testApiConnectivity();
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isConnected) {
                                statusText.setText("✓ API Connectivity Test PASSED\\n\\n" +
                                    "• Server: http://10.32.1.11:8000\\n" +
                                    "• Authentication: Working\\n" +
                                    "• Network: Connected\\n\\n" +
                                    "The OTA API server is reachable and responding correctly.");
                                
                                Toast.makeText(ApiTestActivity.this, "API connectivity test successful!", Toast.LENGTH_SHORT).show();
                                Log.i(TAG, "✓ API connectivity test passed");
                            } else {
                                statusText.setText("✗ API Connectivity Test FAILED\\n\\n" +
                                    "Possible issues:\\n" +
                                    "• Server unreachable\\n" +
                                    "• Invalid API key\\n" +
                                    "• Network connectivity\\n" +
                                    "• Firewall blocking connection\\n\\n" +
                                    "Check logs for detailed error information.");
                                    
                                Toast.makeText(ApiTestActivity.this, "API connectivity test failed", Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "✗ API connectivity test failed");
                            }
                            
                            enableButtons();
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Exception during connectivity test: " + e.getMessage(), e);
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("✗ API Connectivity Test ERROR\\n\\n" +
                                "Exception occurred during test:\\n" + e.getMessage() + "\\n\\n" +
                                "Check logs for full stack trace.");
                            enableButtons();
                            Toast.makeText(ApiTestActivity.this, "Connectivity test error", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
    
    private void testUpdateCheck() {
        Log.i(TAG, "=== User initiated update check test ===");
        
        statusText.setText("Testing update check API...\\n\\nQuerying server for available updates...");
        disableButtons();
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Starting update check test in background thread");
                    
                    final UpdateChecker.UpdateCheckResult result = UpdateChecker.checkUpdateExistsDetailed();
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            StringBuilder resultText = new StringBuilder();
                            
                            if (result.errorMessage != null) {
                                resultText.append("✗ Update Check Test FAILED\\n\\n");
                                resultText.append("Error: ").append(result.errorMessage).append("\\n\\n");
                                resultText.append("Check API key and server connectivity.");
                                
                                Toast.makeText(ApiTestActivity.this, "Update check test failed", Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "✗ Update check test failed: " + result.errorMessage);
                                
                            } else if (result.updateAvailable) {
                                resultText.append("✓ Update Check Test PASSED\\n\\n");
                                resultText.append("UPDATE AVAILABLE!\\n\\n");
                                resultText.append("Current Build: ").append(DeviceUtils.getBuildId()).append("\\n");
                                resultText.append("New Build: ").append(result.newBuildId).append("\\n");
                                resultText.append("Package URL: ").append(result.packageUrl).append("\\n\\n");
                                if (result.patchNotes != null) {
                                    resultText.append("Patch Notes: ").append(result.patchNotes);
                                }
                                
                                Toast.makeText(ApiTestActivity.this, "Update available!", Toast.LENGTH_SHORT).show();
                                Log.i(TAG, "✓ Update check test passed - update available");
                                
                            } else {
                                resultText.append("✓ Update Check Test PASSED\\n\\n");
                                resultText.append("NO UPDATE AVAILABLE\\n\\n");
                                resultText.append("Current Build: ").append(DeviceUtils.getBuildId()).append("\\n");
                                resultText.append("System is up to date.\\n\\n");
                                resultText.append("The API successfully confirmed that no updates are available.");
                                
                                Toast.makeText(ApiTestActivity.this, "System is up to date", Toast.LENGTH_SHORT).show();
                                Log.i(TAG, "✓ Update check test passed - system up to date");
                            }
                            
                            statusText.setText(resultText.toString());
                            enableButtons();
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Exception during update check test: " + e.getMessage(), e);
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("✗ Update Check Test ERROR\\n\\n" +
                                "Exception occurred during test:\\n" + e.getMessage() + "\\n\\n" +
                                "Check logs for full stack trace.");
                            enableButtons();
                            Toast.makeText(ApiTestActivity.this, "Update check test error", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
    
    private void testChecksumValidation() {
        Log.i(TAG, "=== User initiated checksum validation test ===");
        
        statusText.setText("Testing checksum validation...\\n\\nThis will test the checksum validation API with sample data.");
        disableButtons();
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Starting checksum validation test");
                    
                    // Test with sample data
                    String testBuildId = "test-build-id";
                    String testChecksum = "a487654c7bb3c984177a863bd5ba6f35fa1eb7f1b56155fa593333f8c78c2ad0";
                    
                    final OTAApiClient.ChecksumValidationResponse response = 
                        OTAApiClient.validateChecksum(testBuildId, testChecksum);
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            StringBuilder resultText = new StringBuilder();
                            
                            if (response.errorMessage != null) {
                                resultText.append("✗ Checksum Validation Test FAILED\\n\\n");
                                resultText.append("Error: ").append(response.errorMessage).append("\\n\\n");
                                resultText.append("This is expected if the test build ID doesn't exist on the server.");
                                
                                Log.i(TAG, "Checksum validation test completed (expected failure for test data)");
                                
                            } else {
                                resultText.append("✓ Checksum Validation Test PASSED\\n\\n");
                                resultText.append("API Response:\\n");
                                resultText.append("Status: ").append(response.status).append("\\n");
                                resultText.append("Valid: ").append(response.isValid).append("\\n");
                                resultText.append("Message: ").append(response.message).append("\\n\\n");
                                resultText.append("The checksum validation API is working correctly.");
                                
                                Log.i(TAG, "✓ Checksum validation test passed");
                            }
                            
                            statusText.setText(resultText.toString());
                            enableButtons();
                            Toast.makeText(ApiTestActivity.this, "Checksum validation test completed", Toast.LENGTH_SHORT).show();
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Exception during checksum validation test: " + e.getMessage(), e);
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("✗ Checksum Validation Test ERROR\\n\\n" +
                                "Exception occurred during test:\\n" + e.getMessage() + "\\n\\n" +
                                "Check logs for full stack trace.");
                            enableButtons();
                            Toast.makeText(ApiTestActivity.this, "Checksum validation test error", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
    
    private void showBuildInfo() {
        Log.i(TAG, "=== User requested build information ===");
        
        statusText.setText("Gathering build information...");
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String buildInfo = DeviceUtils.getBuildInfo();
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("Device Build Information:\\n\\n" + buildInfo + 
                                "\\nThis information is used for API calls to check for updates.");
                            
                            Log.i(TAG, "Build information displayed to user");
                            Toast.makeText(ApiTestActivity.this, "Build information retrieved", Toast.LENGTH_SHORT).show();
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Exception getting build info: " + e.getMessage(), e);
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("Error gathering build information:\\n" + e.getMessage());
                            Toast.makeText(ApiTestActivity.this, "Error getting build info", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
    
    private void disableButtons() {
        testConnectivityButton.setEnabled(false);
        testUpdateCheckButton.setEnabled(false);
        testChecksumButton.setEnabled(false);
        getBuildInfoButton.setEnabled(false);
    }
    
    private void enableButtons() {
        testConnectivityButton.setEnabled(true);
        testUpdateCheckButton.setEnabled(true);
        testChecksumButton.setEnabled(true);
        getBuildInfoButton.setEnabled(true);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "=== API Test Activity Destroyed ===");
    }
}