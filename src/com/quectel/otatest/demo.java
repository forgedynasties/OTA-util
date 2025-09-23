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
    String TAG="demo";
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
            if (status == UpdateEngine.UpdateStatusConstants.DOWNLOADING) {
//            DecimalFormat df = new DecimalFormat("#");
//            String progress = df.format(percent * 100);
                Log.d(TAG, "update progress: " + percent+";"+(int)(percent*100));
                handler.sendEmptyMessage((int)(percent*100));
            }
        }
        @Override
        public void onPayloadApplicationComplete(int errorCode) {
            if (errorCode == UpdateEngine.ErrorCodeConstants.SUCCESS) {// 回调状态
                Log.i(TAG, "UPDATE SUCCESS!");
                dialog.dismiss();
            }
        }
    };
    
    Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if(!dialog.isShowing()){
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
            if(!downloadDialog.isShowing()){
                downloadDialog.show();
            }
            downloadDialog.setProgress(msg.what);
            Log.i(TAG,"download setProgress==="+msg.what);
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext=this;
        setContentView(R.layout.activity_demo);

        PowerManager pm = (PowerManager)this.getSystemService(Context.POWER_SERVICE);
        mWakelock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "OTA Wakelock");
        //Removing Auto-focus of keyboard
        View view = this.getCurrentFocus();
        if (view != null) { //Removing on screen keyboard if still active
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
        destEdit=(TextView) findViewById(R.id.dest);
        info=(TextView) findViewById(R.id.info);
        urlEdit=(EditText) findViewById(R.id.url);
        downloadBtn=(Button) findViewById(R.id.download);
        destEdit.setText(dest);
    }

    void downloadUpdate(){
        String urlString = urlEdit.getText().toString().trim();
        if(urlString.isEmpty()){
            showMessage("Please enter a valid URL");
            return;
        }
        
        // Remove existing update.zip files in /data/ota_package/
        removeExistingUpdateFiles();
        
        // Create directory if it doesn't exist
        File otaDir = new File("/data/ota_package");
        if(!otaDir.exists()){
            otaDir.mkdirs();
        }
        
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();
            
            if(connection.getResponseCode() != HttpURLConnection.HTTP_OK){
                showMessage("Server error: " + connection.getResponseCode());
                return;
            }
            
            int fileLength = connection.getContentLength();
            InputStream input = connection.getInputStream();
            FileOutputStream output = new FileOutputStream(dest);
            
            byte[] buffer = new byte[4096];
            long total = 0;
            int count;
            
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    downloadHandler.sendEmptyMessage(progress);
                }
                output.write(buffer, 0, count);
            }
            
            output.close();
            input.close();
            connection.disconnect();
            
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    downloadDialog.dismiss();
                    downloadBtn.setEnabled(true);
                    showMessage("Download completed successfully!");
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Download error", e);
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
    
    void removeExistingUpdateFiles(){
        File otaDir = new File("/data/ota_package");
        if(otaDir.exists()){
            File[] files = otaDir.listFiles();
            if(files != null){
                for(File file : files){
                    if(file.getName().equals("update.zip")){
                        if(file.delete()){
                            Log.i(TAG, "Removed existing update.zip file");
                        }
                    }
                }
            }
        }
    }

    void update2(){
        File file = new File(dest);
        
        // Check if file exists
        if(!file.exists()){
            showMessage("Update file not found. Please download first.");
            findViewById(R.id.update).setEnabled(true);
            return;
        }
        
        UpdateParser.ParsedUpdate result;
        try {
            result=UpdateParser.parse(file);
            } catch (Exception e) {
                Log.e(TAG, String.format("For file %s", file), e);
                findViewById(R.id.update).setEnabled(true);
                return ;
            }

        if (result == null || !result.isValid()) {
        showMessage("verify_failure");
                Log.e(TAG, "Failed verification "+ result.toString());
                findViewById(R.id.update).setEnabled(true);
                return;
    }
    Log.i(TAG, result.toString());
    mWakelock.acquire();
    UpdateEngine mUpdateEngine = new UpdateEngine();
    Log.e(TAG, "applyPayload start ");
        try {
            mUpdateEngine.bind(mUpdateEngineCallback);
        mUpdateEngine.applyPayload(result.mUrl, result.mOffset, result.mSize, result.mProps);
            } catch (Exception e) {
                Log.e(TAG, String.format("For file %s", file), e);
                findViewById(R.id.update).setEnabled(true);
                return ;
            }
    Log.e(TAG, "applyPayload end ");
    mWakelock.release();
    }

    public void download(View v){
        downloadBtn.setEnabled(false);
        downloadDialog = new ProgressDialog(this);
        downloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        downloadDialog.setTitle("Downloading Update");
        downloadDialog.setMax(100);
        downloadDialog.setCancelable(false);
        
        new Thread(new Runnable() {
            public void run() { downloadUpdate(); }
        }).start();
    }

    public  void update(View v){
        File file = new File(dest);
        if(!file.exists()){
            showMessage("Update file not found at " + dest + ". Please download first.");
            return;
        }
        
    findViewById(R.id.update).setEnabled(false);
        dialog = new ProgressDialog(this);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setTitle("Installing Update");
        dialog.setMax(100);
        dialog.setCancelable(false);
    new Thread(new Runnable() {
            public void run() { update2(); }
                }).start();
    }

    void showMessage(final String str){
        runOnUiThread(new Runnable() {
            public void run() {
                    Toast.makeText(mContext,str,Toast.LENGTH_SHORT).show();
                    info.setText(str);
                }
            });
    }
}