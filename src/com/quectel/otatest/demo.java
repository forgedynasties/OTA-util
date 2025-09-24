package com.quectel.otatest;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.UpdateEngine;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
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

public class demo extends Activity {
    TextView destEdit,info;
    EditText urlEdit;
    Button downloadBtn;
    //String dest=Environment.getExternalStorageDirectory().getPath()+"/update.zip";
    String dest = "/data/ota_package/update.zip";
    WakeLock mWakelock;
    String TAG="OTATEST";
    Context mContext;
    private ProgressDialog dialog;
    private ProgressDialog downloadDialog;

    private static final int REQUEST_CODE = 1024;

    /**
     * 存储权限
     * */
    private static final int REQUEST_EXTERNAL_STORAGE = 1 ;
    private static String[] PERMISSON_STORAGE = {"android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE"};

    UpdateEngineCallback mUpdateEngineCallback = new UpdateEngineCallback(){

        @Override
        public void onStatusUpdate(int status,float percent){
            Log.d(TAG, "UpdateEngineCallback - onStatusUpdate: status=" + status + ", percent=" + percent);
            if (status == UpdateEngine.UpdateStatusConstants.DOWNLOADING) {
//            DecimalFormat df = new DecimalFormat("#");
//            String progress = df.format(percent * 100);
                Log.d(TAG, "update progress: " + percent+";"+(int)(percent*100));
                handler.sendEmptyMessage((int)(percent*100));
            }
        }
        @Override
        public void onPayloadApplicationComplete(int errorCode) {
            Log.i(TAG, "UpdateEngineCallback - onPayloadApplicationComplete: errorCode=" + errorCode);
            if (errorCode == UpdateEngine.ErrorCodeConstants.SUCCESS) {// 回调状态
                Log.i(TAG, "UPDATE SUCCESS!");
                dialog.dismiss();
            } else {
                Log.e(TAG, "UPDATE FAILED with error code: " + errorCode);
            }
        }
    };
    
    Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.d(TAG, "Update progress handler - progress: " + msg.what);
            if(!dialog.isShowing()){
                Log.d(TAG, "Showing update progress dialog");
                dialog.show();
            }
            dialog.setProgress(msg.what);
            Log.i(TAG,"setProgress==="+msg.what);
        }
    };
    
    Handler downloadHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.d(TAG, "Download progress handler - progress: " + msg.what);
            if(!downloadDialog.isShowing()){
                Log.d(TAG, "Showing download progress dialog");
                downloadDialog.show();
            }
            downloadDialog.setProgress(msg.what);
            Log.i(TAG,"download setProgress==="+msg.what);
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate - Activity starting");
        mContext=this;
        setContentView(R.layout.activity_demo);

        PowerManager pm = (PowerManager)this.getSystemService(Context.POWER_SERVICE);
        mWakelock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "OTA Wakelock");
        Log.d(TAG, "onCreate - WakeLock initialized");
        
        //Removing Auto-focus of keyboard
        View view = this.getCurrentFocus();
        if (view != null) { //Removing on screen keyboard if still active
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            Log.d(TAG, "onCreate - Hidden keyboard");
        }
        
        destEdit=(TextView) findViewById(R.id.dest);
        info=(TextView) findViewById(R.id.info);
        urlEdit=(EditText) findViewById(R.id.url);
        downloadBtn=(Button) findViewById(R.id.download);
        destEdit.setText(dest);
        
        Log.i(TAG, "onCreate - UI components initialized, destination: " + dest);
    }

    void downloadUpdate(){
        Log.i(TAG, "downloadUpdate - Starting download process");
        String urlString = urlEdit.getText().toString().trim();
        Log.d(TAG, "downloadUpdate - URL: " + urlString);
        
        if(urlString.isEmpty()){
            Log.w(TAG, "downloadUpdate - URL is empty");
            showMessage("Please enter a valid URL");
            return;
        }
        
        // Remove existing update.zip files in /data/ota_package/
        Log.d(TAG, "downloadUpdate - Removing existing update files");
        removeExistingUpdateFiles();
        
        // Create directory with proper permissions
        File otaDir = new File("/data/ota_package");
        Log.d(TAG, "downloadUpdate - Checking directory: " + otaDir.getAbsolutePath());
        if(!otaDir.exists()){
            Log.i(TAG, "downloadUpdate - Creating OTA directory");
            otaDir.mkdirs();
            // Set directory permissions
            try {
                Runtime.getRuntime().exec("chmod 755 /data/ota_package");
                Log.i(TAG, "downloadUpdate - Set directory permissions: chmod 755 /data/ota_package");
            } catch (Exception e) {
                Log.e(TAG, "downloadUpdate - Failed to set directory permissions", e);
            }
        } else {
            Log.d(TAG, "downloadUpdate - Directory already exists");
        }
        
        try {
            Log.d(TAG, "downloadUpdate - Opening connection to: " + urlString);
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "downloadUpdate - Server response code: " + responseCode);
            
            if(responseCode != HttpURLConnection.HTTP_OK){
                Log.e(TAG, "downloadUpdate - Server error: " + responseCode);
                showMessage("Server error: " + responseCode);
                return;
            }
            
            int fileLength = connection.getContentLength();
            Log.i(TAG, "downloadUpdate - File size: " + fileLength + " bytes");
            
            InputStream input = connection.getInputStream();
            FileOutputStream output = new FileOutputStream(dest);
            Log.d(TAG, "downloadUpdate - Created output stream for: " + dest);
            
            byte[] buffer = new byte[4096];
            long total = 0;
            int count;
            
            Log.i(TAG, "downloadUpdate - Starting file transfer");
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    downloadHandler.sendEmptyMessage(progress);
                }
                output.write(buffer, 0, count);
            }
            
            Log.i(TAG, "downloadUpdate - File transfer completed. Total bytes: " + total);
            output.close();
            input.close();
            connection.disconnect();
            Log.d(TAG, "downloadUpdate - Closed all streams and connections");
            
            // Set proper file permissions after download
            Log.d(TAG, "downloadUpdate - Setting file permissions");
            setFilePermissions(dest);
            
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, "downloadUpdate - Download completed successfully");
                    downloadDialog.dismiss();
                    downloadBtn.setEnabled(true);
                    showMessage("Download completed successfully!");
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "downloadUpdate - Download error", e);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    downloadDialog.dismiss();
                    downloadBtn.setEnabled(true);
                    showMessage("Download failed: " + e.getMessage());
                }
            });
        }
    }
    
    // Add this method to set file permissions
    private void setFilePermissions(String filePath) {
        Log.d(TAG, "setFilePermissions - Setting permissions for: " + filePath);
        try {
            File file = new File(filePath);
            if (file.exists()) {
                Log.d(TAG, "setFilePermissions - File exists, setting permissions");
                
                // Method 1: Using Java File API
                boolean readable = file.setReadable(true, false);  // readable by all
                boolean writable = file.setWritable(true, false);  // writable by all
                Log.d(TAG, "setFilePermissions - Java API: readable=" + readable + ", writable=" + writable);
                
                // Method 2: Using chmod command (more reliable for system apps)
                Process process = Runtime.getRuntime().exec("chmod 644 " + filePath);
                int result = process.waitFor();
                
                if (result == 0) {
                    Log.i(TAG, "setFilePermissions - Successfully set file permissions: chmod 644 " + filePath);
                } else {
                    Log.w(TAG, "setFilePermissions - chmod command returned: " + result);
                }
                
                // Method 3: Alternative with more specific permissions
                // Runtime.getRuntime().exec("chmod 664 " + filePath);  // rw-rw-r--
                
            } else {
                Log.e(TAG, "setFilePermissions - File does not exist: " + filePath);
            }
        } catch (Exception e) {
            Log.e(TAG, "setFilePermissions - Failed to set file permissions", e);
        }
    }
    
    void removeExistingUpdateFiles(){
        Log.d(TAG, "removeExistingUpdateFiles - Checking for existing update files");
        File otaDir = new File("/data/ota_package");
        if(otaDir.exists()){
            File[] files = otaDir.listFiles();
            if(files != null){
                Log.d(TAG, "removeExistingUpdateFiles - Found " + files.length + " files in directory");
                for(File file : files){
                    if(file.getName().equals("update.zip")){
                        if(file.delete()){
                            Log.i(TAG, "removeExistingUpdateFiles - Removed existing update.zip file");
                        } else {
                            Log.w(TAG, "removeExistingUpdateFiles - Failed to remove existing update.zip file");
                        }
                    }
                }
            } else {
                Log.d(TAG, "removeExistingUpdateFiles - Directory is empty or cannot list files");
            }
        } else {
            Log.d(TAG, "removeExistingUpdateFiles - OTA directory does not exist");
        }
    }

    void update2(){
        Log.i(TAG, "update2 - Starting update process");
        File file = new File(dest);
        
        // Check if file exists
        if(!file.exists()){
            Log.e(TAG, "update2 - Update file not found: " + dest);
            showMessage("Update file not found. Please download first.");
            findViewById(R.id.update).setEnabled(true);
            return;
        }
        
        Log.d(TAG, "update2 - Update file exists, size: " + file.length() + " bytes");
        
        UpdateParser.ParsedUpdate result;
        try {
            Log.d(TAG, "update2 - Parsing update file");
            result=UpdateParser.parse(file);
            } catch (Exception e) {
                Log.e(TAG, "update2 - Failed to parse update file: " + file, e);
                findViewById(R.id.update).setEnabled(true);
                return ;
            }

        if (result == null || !result.isValid()) {
            Log.e(TAG, "update2 - Update verification failed");
            showMessage("verify_failure");
            Log.e(TAG, "Failed verification "+ (result != null ? result.toString() : "null"));
            findViewById(R.id.update).setEnabled(true);
            return;
        }
        
        Log.i(TAG, "update2 - Update parsed successfully: " + result.toString());
        Log.d(TAG, "update2 - Acquiring wake lock");
        mWakelock.acquire();
        
        UpdateEngine mUpdateEngine = new UpdateEngine();
        Log.i(TAG, "update2 - Created UpdateEngine instance");
        Log.e(TAG, "applyPayload start ");
        
        try {
            Log.d(TAG, "update2 - Binding UpdateEngine callback");
            mUpdateEngine.bind(mUpdateEngineCallback);
            
            Log.i(TAG, "update2 - Applying payload: URL=" + result.mUrl + ", Offset=" + result.mOffset + ", Size=" + result.mSize);
            mUpdateEngine.applyPayload(result.mUrl, result.mOffset, result.mSize, result.mProps);
            
            } catch (Exception e) {
                Log.e(TAG, "update2 - Failed to apply payload for file: " + file, e);
                findViewById(R.id.update).setEnabled(true);
                mWakelock.release();
                Log.d(TAG, "update2 - Released wake lock due to error");
                return ;
            }
        Log.e(TAG, "applyPayload end ");
        Log.d(TAG, "update2 - Released wake lock");
        mWakelock.release();
    }

    public void download(View v){
        Log.i(TAG, "download - Download button clicked");
        downloadBtn.setEnabled(false);
        downloadDialog = new ProgressDialog(this);
        downloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        downloadDialog.setTitle("Downloading Update");
        downloadDialog.setMax(100);
        downloadDialog.setCancelable(false);
        
        Log.d(TAG, "download - Created download progress dialog");
        
        new Thread(new Runnable() {
            public void run() { 
                Log.d(TAG, "download - Starting download thread");
                downloadUpdate(); 
            }
        }).start();
    }

    public  void update(View v){
        Log.i(TAG, "update - Update button clicked");
        File file = new File(dest);
        if(!file.exists()){
            Log.w(TAG, "update - Update file not found at: " + dest);
            showMessage("Update file not found at " + dest + ". Please download first.");
            return;
        }
        
        Log.d(TAG, "update - Update file found, starting update process");
        findViewById(R.id.update).setEnabled(false);
        dialog = new ProgressDialog(this);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setTitle("Installing Update");
        dialog.setMax(100);
        dialog.setCancelable(false);
        
        Log.d(TAG, "update - Created update progress dialog");
        
        new Thread(new Runnable() {
            public void run() { 
                Log.d(TAG, "update - Starting update thread");
                update2(); 
            }
        }).start();
    }

    void showMessage(final String str){
        Log.d(TAG, "showMessage - " + str);
        runOnUiThread(new Runnable() {
            public void run() {
                    Toast.makeText(mContext,str,Toast.LENGTH_SHORT).show();
                    info.setText(str);
                }
            });
    }
}