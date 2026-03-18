package com.opencode.voiceassist;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.*;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.opencode.voiceassist.manager.AudioProcessor;
import com.opencode.voiceassist.manager.AudioRecorder;
import com.opencode.voiceassist.manager.AsrEngine;
import com.opencode.voiceassist.manager.CameraPermissionManager;
import com.opencode.voiceassist.manager.CloudAsrManager;
import com.opencode.voiceassist.manager.DirectProcessor;
import com.opencode.voiceassist.manager.FunAsrWebSocketManager;
import com.opencode.voiceassist.manager.NoiseReductionProcessor;
import com.opencode.voiceassist.manager.OpenCodeManager;
import com.opencode.voiceassist.manager.RecordingManager;
import com.opencode.voiceassist.manager.SettingsManager;
import com.opencode.voiceassist.manager.WebViewManager;
import com.opencode.voiceassist.model.Message;
import com.opencode.voiceassist.model.TranscriptionResult;
import com.opencode.voiceassist.ui.MessageAdapter;
import com.opencode.voiceassist.utils.Constants;
import com.opencode.voiceassist.utils.UrlUtils;
import com.opencode.voiceassist.utils.WebViewTextInjector;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import com.opencode.voiceassist.utils.FileManager;

import android.net.Uri;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.core.content.FileProvider;

public class MainActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private static final int REQUEST_STORAGE_PERMISSION = 1003;
    private static final int REQUEST_WEBVIEW_FILE_CHOOSER = 1004;
    private static final int REQUEST_CAMERA = 1005;
    
    private WebView webView;
    
    private View recordButton;
    private TextView tvRecordHint;
    private View recordButtonContainer;
    private View recordProgress;
    private View bottomContainer;
    
    private OpenCodeManager openCodeManager;
    private AudioRecorder audioRecorder;
    private FileManager fileManager;
    private CloudAsrManager cloudAsrManager;
    private FunAsrWebSocketManager funAsrManager;
    private WebViewTextInjector webViewInjector;
    
    private WebViewManager webViewManager;
    private RecordingManager recordingManager;
    private CameraPermissionManager cameraPermissionManager;
    private SettingsManager settingsManager;
    
    private AudioProcessor audioProcessor;

    private ImageButton btnCamera;
    private Uri cameraPhotoUri;
    private boolean isCameraCapturePending = false;
    private boolean isCameraUploadPending = false;
    private Uri cameraUploadUri = null;
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isKeyboardVisible = false;
    private View rootView;
    private boolean transcriptionTested = true;
    private Runnable currentReloginTimeoutRunnable = null;
    
    private boolean isRecording = false;
    private boolean isCancelled = false;
    private boolean isUserStoppedRecording = false;
    private float startY = 0;
    private static final float CANCEL_THRESHOLD_DP = 50;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        initViews();
        initManagers();
        checkPermissions();
        
        if (webViewManager != null) {
            webViewManager.setBottomContainer(bottomContainer);
            webViewManager.configureWebView();
            webViewManager.loadOpenCodePage();
        }
        
        if (recordingManager != null) {
            recordingManager.setUiReferences(recordButton, recordProgress);
            recordingManager.setupRecordButton();
        }
        
        rootView = getWindow().getDecorView().findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(this::checkKeyboardVisibility);
        updateButtonState(RecordingManager.ButtonState.DEFAULT);
        
        if (webViewManager != null && recordingManager != null) {
            webViewManager.setRecording(recordingManager.isRecording());
        }
    }
    
    private void initViews() {
        webView = findViewById(R.id.webview_opencode);
        recordButton = findViewById(R.id.btn_record);
        tvRecordHint = findViewById(R.id.tv_record_hint);
        recordButtonContainer = findViewById(R.id.record_button_container);
        recordProgress = findViewById(R.id.record_progress);
        bottomContainer = findViewById(R.id.bottom_container);
        
        btnCamera = findViewById(R.id.btn_camera);
        btnCamera.setOnClickListener(v -> {
            if (cameraPermissionManager != null) {
                boolean recordingInProgress = recordingManager != null && recordingManager.isRecording();
                cameraPermissionManager.openCamera(recordingInProgress);
            }
        });

        TextView btnMenu = findViewById(R.id.btn_menu);
        btnMenu.setOnClickListener(v -> {
            if (settingsManager != null) {
                settingsManager.showPopupMenu(v);
            }
        });
    }
    
    private void initManagers() {
        fileManager = new FileManager(this);
        openCodeManager = null;
        audioRecorder = new AudioRecorder();
        
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        
        boolean hardwareNS = prefs.getBoolean(Constants.KEY_HARDWARE_NS, Constants.DEFAULT_HARDWARE_NS);
        audioRecorder.setEnableNoiseSuppression(hardwareNS);
        
        String cloudAsrHost = prefs.getString("cloud_asr_ip", Constants.DEFAULT_CLOUD_ASR_IP);
        int cloudAsrPort = prefs.getInt("cloud_asr_port", Constants.DEFAULT_CLOUD_ASR_PORT);
        
        if (cloudAsrHost.equals(Constants.DEFAULT_CLOUD_ASR_IP) && cloudAsrPort == Constants.DEFAULT_CLOUD_ASR_PORT) {
            String cloudAsrUrl = prefs.getString("cloud_asr_url", Constants.DEFAULT_CLOUD_ASR_URL);
            String[] cloudAsrParts = UrlUtils.parseAsrUrl(cloudAsrUrl, "http");
            cloudAsrHost = cloudAsrParts[0];
            cloudAsrPort = Integer.parseInt(cloudAsrParts[1]);
        }
        android.util.Log.d("MainActivity", "Cloud ASR: " + cloudAsrHost + ":" + cloudAsrPort);
        cloudAsrManager = new CloudAsrManager(this, cloudAsrHost, cloudAsrPort);
        
        String funAsrHost = prefs.getString("funasr_host", Constants.DEFAULT_FUNASR_HOST);
        int funAsrPort = prefs.getInt("funasr_port", Constants.DEFAULT_FUNASR_PORT);
        
        if (funAsrHost.equals(Constants.DEFAULT_FUNASR_HOST) && funAsrPort == Constants.DEFAULT_FUNASR_PORT) {
            String funAsrUrl = prefs.getString("funasr_url", Constants.DEFAULT_FUNASR_URL);
            String[] funAsrParts = UrlUtils.parseAsrUrl(funAsrUrl, "ws");
            funAsrHost = funAsrParts[0];
            funAsrPort = Integer.parseInt(funAsrParts[1]);
        }
        String funAsrMode = prefs.getString("funasr_mode", Constants.DEFAULT_FUNASR_MODE);
        android.util.Log.d("MainActivity", "FunASR: " + funAsrHost + ":" + funAsrPort + " mode=" + funAsrMode);
        funAsrManager = new FunAsrWebSocketManager(this, funAsrHost, funAsrPort, funAsrMode);
        
        webViewManager = new WebViewManager(this, webView, webViewCallback);
        recordingManager = new RecordingManager(this, recordingCallback);
        cameraPermissionManager = new CameraPermissionManager(this, cameraCallback);
        settingsManager = new SettingsManager(this, settingsCallback);
        
        String audioProcessorType = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("audio_processor", Constants.DEFAULT_AUDIO_PROCESSOR);
        if (Constants.AUDIO_PROCESSOR_NOISE_REDUCTION.equals(audioProcessorType)) {
            audioProcessor = new NoiseReductionProcessor();
        } else {
            audioProcessor = new DirectProcessor();
        }
        
        String asrBackend = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("asr_backend", Constants.DEFAULT_ASR_BACKEND);
        AsrEngine currentAsrEngine;
        if (Constants.ASR_BACKEND_CLOUD_HTTP.equals(asrBackend)) {
            currentAsrEngine = cloudAsrManager;
        } else {
            currentAsrEngine = funAsrManager;
        }
        
        recordingManager.setManagers(audioRecorder, fileManager);
        recordingManager.setAsrEngine(currentAsrEngine);
        recordingManager.setAudioProcessor(audioProcessor);
        recordingManager.setHardwareNoiseSuppressionEnabled(hardwareNS);
        
        settingsManager.setManagers(cloudAsrManager, funAsrManager, recordingManager);
    }
    
    private void checkPermissions() {
        String[] permissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        };
        
        List<String> neededPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(permission);
            }
        }
        
        if (!neededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                neededPermissions.toArray(new String[0]), 
                PERMISSION_REQUEST_CODE);
        }
    }
    
    private final WebViewManager.WebViewCallback webViewCallback = new WebViewManager.WebViewCallback() {
        @Override
        public void onLoginFailureDetected() {
            showReloginDialog();
        }
        
        @Override
        public void onFileUploadRequested(WebChromeClient.FileChooserParams params) {
            openFilePickerForWebView(params);
        }
        
        @Override
        public void onCameraUploadPending(Uri photoUri) {
            if (webViewManager != null) {
                webViewManager.setCameraUploadPending(true, photoUri);
            }
        }
        
        @Override
        public void onShowReloginDialog() {
            showReloginDialog();
        }
        
        @Override
        public void onInputFocusChanged(boolean hasFocus) {
            if (bottomContainer != null) {
                bottomContainer.setVisibility(hasFocus ? View.GONE : View.VISIBLE);
            }
        }
        
        @Override
        public void onPageLoadError(String description) {
            Toast.makeText(MainActivity.this, "页面加载失败: " + description, Toast.LENGTH_SHORT).show();
            cancelReloginTimeout();
        }
        
        @Override
        public void onPageLoadComplete() {
            cancelReloginTimeout();
        }
        
        @Override
        public void onAttachmentReady(boolean success, String filename, String message) {
            if (!isRecording) {
                if (success) {
                    Toast.makeText(MainActivity.this, "已添加: " + filename, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "添加失败: " + filename + " - " + message, Toast.LENGTH_SHORT).show();
                }
            }
        }
    };
    
    private final RecordingManager.RecordingCallback recordingCallback = new RecordingManager.RecordingCallback() {
        @Override
        public void onRecordingStateChanged(RecordingManager.ButtonState state) {
            updateButtonState(state);
        }
        
        @Override
        public void onTranscriptionComplete(TranscriptionResult result) {
            if (webViewManager != null) {
                webViewManager.injectTranscribedText(result.getText());
            }
        }
        
        @Override
        public void onTranscriptionError(String error) {
        }
        
        @Override
        public void onOpenCodeInitialized(boolean success, String message) {
        }
    };
    
    private final CameraPermissionManager.CameraCallback cameraCallback = new CameraPermissionManager.CameraCallback() {
        @Override
        public void onCameraPermissionGranted() {
            if (cameraPermissionManager != null) {
                cameraPermissionManager.launchCamera();
            }
        }
        
        @Override
        public void onCameraPermissionDenied() {
            Toast.makeText(MainActivity.this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show();
        }
        
        @Override
        public void onStoragePermissionGranted() {
        }
        
        @Override
        public void onStoragePermissionDenied() {
        }
        
        @Override
        public void onCameraPhotoCaptured(Uri photoUri) {
            if (webViewManager != null) {
                webViewManager.setCameraUploadPending(true, photoUri);
            }
        }
        
        @Override
        public void onCameraError(String error) {
            Toast.makeText(MainActivity.this, "相机错误: " + error, Toast.LENGTH_SHORT).show();
        }
    };
    
    private final SettingsManager.SettingsCallback settingsCallback = new SettingsManager.SettingsCallback() {
        @Override
        public void onSettingsSaved(SettingsManager.SettingsData settings) {
            settingsManager.saveSettings(settings);
            
            if (cloudAsrManager != null) {
                cloudAsrManager.updateSettings(settings.cloudAsrHost, settings.cloudAsrPort);
            }
            if (funAsrManager != null) {
                funAsrManager.updateSettings(settings.funAsrHost, settings.funAsrPort, settings.funAsrMode);
            }
            
            AsrEngine currentAsrEngine;
            if (Constants.ASR_BACKEND_CLOUD_HTTP.equals(settings.asrBackend)) {
                currentAsrEngine = cloudAsrManager;
            } else {
                currentAsrEngine = funAsrManager;
            }
            if (recordingManager != null) {
                recordingManager.setAsrEngine(currentAsrEngine);
            }
            
            if (Constants.AUDIO_PROCESSOR_NOISE_REDUCTION.equals(settings.audioProcessor)) {
                audioProcessor = new NoiseReductionProcessor();
            } else {
                audioProcessor = new DirectProcessor();
            }
            if (recordingManager != null) {
                recordingManager.setAudioProcessor(audioProcessor);
            }
            
            if (audioRecorder != null) {
                audioRecorder.setEnableNoiseSuppression(settings.hardwareNS);
                android.util.Log.d("MainActivity", "Hardware NS updated: " + settings.hardwareNS);
            }
            if (recordingManager != null) {
                recordingManager.setHardwareNoiseSuppressionEnabled(settings.hardwareNS);
            }
        }
        
        @Override
        public void onShowReloginDialog() {
            showReloginDialog();
        }
        
        @Override
        public void onRefreshPage() {
            if (webViewManager != null) {
                webViewManager.reloadPage();
            }
        }
        
        @Override
        public void onUpdateButtonState(RecordingManager.ButtonState state) {
            updateButtonState(state);
        }
        
        @Override
        public void onShowToast(String message, int duration) {
            Toast.makeText(MainActivity.this, message, duration).show();
        }
    };
    
    private void cancelReloginTimeout() {
        if (currentReloginTimeoutRunnable != null) {
            mainHandler.removeCallbacks(currentReloginTimeoutRunnable);
            currentReloginTimeoutRunnable = null;
        }
    }
    
    private void showReloginDialog() {
        mainHandler.post(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("重新登录")
                .setMessage("是否清除所有登录状态并使用当前设置重新连接？")
                .setPositiveButton("重新登录", (dialog, which) -> {
                    android.util.Log.d("MainActivity", "Clearing all authentication data and reloading");
                    cancelReloginTimeout();
                    
                    if (webViewManager != null) {
                        Toast.makeText(MainActivity.this, "正在重新登录...", Toast.LENGTH_SHORT).show();
                        
                        webViewManager.clearWebViewData(() -> {
                            mainHandler.postDelayed(() -> {
                                android.util.Log.d("MainActivity", "Reloading page with cache bypass");
                                if (webViewManager != null) {
                                    currentReloginTimeoutRunnable = new Runnable() {
                                        @Override
                                        public void run() {
                                            mainHandler.post(() -> {
                                                Toast.makeText(MainActivity.this, "连接超时，请检查服务器地址和网络连接", Toast.LENGTH_LONG).show();
                                                currentReloginTimeoutRunnable = null;
                                            });
                                        }
                                    };
                                    
                                    mainHandler.postDelayed(currentReloginTimeoutRunnable, 5000);
                                    webViewManager.reloadPage();
                                }
                                Toast.makeText(MainActivity.this, "已清除所有登录状态，正在重新连接...", Toast.LENGTH_LONG).show();
                            }, 300);
                        });
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    android.util.Log.d("MainActivity", "User cancelled re-login");
                })
                .setCancelable(true)
                .show();
        });
    }
    
    private void updateButtonState(RecordingManager.ButtonState state) {
        switch (state) {
            case DEFAULT:
                recordButton.setBackgroundResource(R.drawable.bg_record_default);
                recordProgress.setVisibility(View.GONE);
                tvRecordHint.setVisibility(View.VISIBLE);
                tvRecordHint.setText("按住说话");
                tvRecordHint.setTextColor(getResources().getColor(android.R.color.black));
                recordButton.setEnabled(true);
                break;
                
            case RECORDING:
                recordButton.setBackgroundResource(R.drawable.bg_record_recording);
                recordProgress.setVisibility(View.VISIBLE);
                tvRecordHint.setVisibility(View.GONE);
                break;
                
            case CANCEL:
                recordButton.setBackgroundResource(R.drawable.bg_record_cancel);
                recordProgress.setVisibility(View.GONE);
                tvRecordHint.setVisibility(View.VISIBLE);
                tvRecordHint.setText("取消");
                tvRecordHint.setTextColor(getResources().getColor(android.R.color.white));
                break;
                
            case PROCESSING:
                recordButton.setBackgroundResource(R.drawable.bg_record_processing);
                recordProgress.setVisibility(View.VISIBLE);
                tvRecordHint.setVisibility(View.GONE);
                recordButton.setEnabled(false);
                break;
                
            case DISABLED:
                recordButton.setBackgroundResource(R.drawable.bg_record_disabled);
                recordProgress.setVisibility(View.GONE);
                tvRecordHint.setVisibility(View.VISIBLE);
                tvRecordHint.setText("请检查服务器配置");
                tvRecordHint.setTextColor(getResources().getColor(android.R.color.darker_gray));
                recordButton.setEnabled(false);
                break;
        }
    }
    
    private void checkKeyboardVisibility() {
        if (rootView == null) return;
        
        Rect r = new Rect();
        rootView.getWindowVisibleDisplayFrame(r);
        int screenHeight = rootView.getRootView().getHeight();
        int keypadHeight = screenHeight - r.height();
        
        int threshold = (int) (150 * getResources().getDisplayMetrics().density);
        
        boolean keyboardVisible = keypadHeight > threshold;
        if (keyboardVisible != isKeyboardVisible) {
            isKeyboardVisible = keyboardVisible;
            android.util.Log.d("MainActivity", "Keyboard visibility changed: " + keyboardVisible);
            
            if (keyboardVisible) {
                bottomContainer.setVisibility(View.GONE);
            } else {
                bottomContainer.setVisibility(View.VISIBLE);
            }
        }
    }
    
    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
    
    private void openFilePickerForWebView(WebChromeClient.FileChooserParams params) {
        android.util.Log.d("MainActivity", "Opening file picker for WebView");
        
        if (cameraPermissionManager != null && cameraPermissionManager.checkStoragePermission()) {
            Intent intent = params.createIntent();
            if (intent == null) {
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                String[] mimeTypes = {"image/*", "application/pdf"};
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            }
            
            try {
                startActivityForResult(intent, REQUEST_WEBVIEW_FILE_CHOOSER);
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Error starting WebView file picker", e);
                if (webViewManager != null) {
                    webViewManager.handleFileChooserResult(null);
                }
            }
        } else {
            if (cameraPermissionManager != null) {
                cameraPermissionManager.requestStoragePermission();
            }
            if (webViewManager != null) {
                webViewManager.handleFileChooserResult(null);
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == CameraPermissionManager.REQUEST_CAMERA) {
            if (cameraPermissionManager != null) {
                cameraPermissionManager.onActivityResult(requestCode, resultCode, data);
            }
        }
        
        if (requestCode == REQUEST_WEBVIEW_FILE_CHOOSER) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            
            android.util.Log.d("MainActivity", "WebView file chooser result: " + 
                (results != null ? results.length + " files" : "cancelled"));
            
            if (webViewManager != null) {
                webViewManager.handleFileChooserResult(results);
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                Toast.makeText(this, "请开启录音/网络权限后使用", Toast.LENGTH_LONG).show();
                updateButtonState(RecordingManager.ButtonState.DISABLED);
            }
        } else if (requestCode == CameraPermissionManager.REQUEST_STORAGE_PERMISSION || 
                   requestCode == CameraPermissionManager.REQUEST_CAMERA) {
            if (cameraPermissionManager != null) {
                cameraPermissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioRecorder != null) {
            audioRecorder.release();
        }
        if (recordingManager != null) {
            recordingManager.release();
        }
        if (fileManager != null) {
            fileManager.deleteTempWavFile();
        }
    }
}