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
import com.quectel.otatest.UpdateParser;
import java.io.File;
import android.os.Message;
import android.os.UpdateEngineCallback;
import android.provider.Settings;
import android.os.Environment;
import android.net.Uri;
import android.content.pm.PackageManager;

public class demo extends Activity {
    TextView destEdit,info;
    //String dest=Environment.getExternalStorageDirectory().getPath()+"/update.zip";
    String dest = "/data/ota_package/update.zip";
    WakeLock mWakelock;
    String TAG="demo";
    Context mContext;
    private ProgressDialog dialog;

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
        destEdit.setText(dest);
    }

    void update2(){
        File file = new File(dest);
        UpdateParser.ParsedUpdate result;
        try {
        	result=UpdateParser.parse(file);
            } catch (Exception e) {
                Log.e(TAG, String.format("For file %s", file), e);
                return ;
            }

        if (result == null || !result.isValid()) {
		showMessage("verify_failure");
                Log.e(TAG, "Failed verification "+ result.toString());
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
                return ;
            }
	Log.e(TAG, "applyPayload end ");
	mWakelock.release();
    }

    public  void update(View v){
	findViewById(R.id.update).setEnabled(false);
        dialog = new ProgressDialog(this);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setTitle("正在下载");
        dialog.setMax(100);
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
