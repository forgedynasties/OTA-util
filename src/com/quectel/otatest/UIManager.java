package com.quectel.otatest;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class UIManager {
    private static final String DEST_PATH = "/data/ota_package/update.zip";
    
    private Activity activity;
    private TextView destEdit, info;
    private EditText urlEdit;
    private Button downloadBtn;
    private EditText shellCommandEdit;
    private TextView shellOutputText;
    private Button testShellBtn, executeShellBtn, checkPermissionsBtn;
    
    private ProgressDialog updateDialog;
    private ProgressDialog downloadDialog;
    
    public UIManager(Activity activity) {
        this.activity = activity;
        initializeUI();
    }
    
    private void initializeUI() {
        destEdit = activity.findViewById(R.id.dest);
        info = activity.findViewById(R.id.info);
        urlEdit = activity.findViewById(R.id.url);
        downloadBtn = activity.findViewById(R.id.download);
        
        shellCommandEdit = activity.findViewById(R.id.shell_command);
        shellOutputText = activity.findViewById(R.id.shell_output);
        testShellBtn = activity.findViewById(R.id.test_shell);
        executeShellBtn = activity.findViewById(R.id.execute_shell);
        checkPermissionsBtn = activity.findViewById(R.id.check_permissions);
        
        destEdit.setText(DEST_PATH);
    }
    
    public void hideKeyboard() {
        View view = activity.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
    
    public String getUrlText() {
        return urlEdit.getText().toString().trim();
    }
    
    public String getShellCommandText() {
        return shellCommandEdit.getText().toString().trim();
    }
    
    public void setDownloadButtonEnabled(boolean enabled) {
        downloadBtn.setEnabled(enabled);
    }
    
    public void setUpdateButtonEnabled(boolean enabled) {
        activity.findViewById(R.id.update).setEnabled(enabled);
    }
    
    public void setShellOutputText(String text) {
        shellOutputText.setText(text);
    }
    
    public void showMessage(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        info.setText(message);
    }
    
    public void showDownloadDialog() {
        downloadDialog = new ProgressDialog(activity);
        downloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        downloadDialog.setTitle("Downloading Update");
        downloadDialog.setMax(100);
        downloadDialog.setCancelable(false);
        downloadDialog.show();
    }
    
    public void showUpdateDialog() {
        updateDialog = new ProgressDialog(activity);
        updateDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        updateDialog.setTitle("Installing Update");
        updateDialog.setMax(100);
        updateDialog.setCancelable(false);
        updateDialog.show();
    }
    
    public void updateDownloadProgress(int progress) {
        if (downloadDialog != null && downloadDialog.isShowing()) {
            downloadDialog.setProgress(progress);
        }
    }
    
    public void updateUpdateProgress(int progress) {
        if (updateDialog != null && updateDialog.isShowing()) {
            updateDialog.setProgress(progress);
        }
    }
    
    public void dismissDialogs() {
        if (downloadDialog != null && downloadDialog.isShowing()) {
            downloadDialog.dismiss();
        }
        if (updateDialog != null && updateDialog.isShowing()) {
            updateDialog.dismiss();
        }
    }
}