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

import com.opencode.voiceassist.manager.AudioRecorder;
import com.opencode.voiceassist.manager.CameraPermissionManager;
import com.opencode.voiceassist.manager.CloudAsrManager;
import com.opencode.voiceassist.manager.FunAsrWebSocketManager;
import com.opencode.voiceassist.manager.OpenCodeManager;
import com.opencode.voiceassist.manager.RecordingManager;
import com.opencode.voiceassist.manager.SettingsManager;
import com.opencode.voiceassist.manager.WebViewManager;
import com.opencode.voiceassist.manager.WhisperManager;
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
    // Removed - managed by WebViewManager
    // private ValueCallback<Uri[]> filePathCallback;
    // private WebChromeClient.FileChooserParams fileChooserParams;
    
    private View recordButton;
    private TextView tvRecordHint;
    private View recordButtonContainer;
    private View recordProgress;
    private View bottomContainer;
    
    private WhisperManager whisperManager;
    private OpenCodeManager openCodeManager;
    private AudioRecorder audioRecorder;
    private FileManager fileManager;
    private CloudAsrManager cloudAsrManager;
    private FunAsrWebSocketManager funAsrManager;
    private WebViewTextInjector webViewInjector;
    
    // New modular managers
    private WebViewManager webViewManager;
    private RecordingManager recordingManager;
    private CameraPermissionManager cameraPermissionManager;
    private SettingsManager settingsManager;

    private ImageButton btnCamera;
    private Uri cameraPhotoUri;
    private boolean isCameraCapturePending = false;
    private boolean isCameraUploadPending = false;
    private Uri cameraUploadUri = null;
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isKeyboardVisible = false;
    private View rootView;
    private boolean transcriptionTested = true; // Default to skip test on first launch
    
    private boolean isRecording = false;
    private boolean isCancelled = false;
    private boolean isUserStoppedRecording = false; // Flag to distinguish user stop from system interrupt
    private float startY = 0;
    private static final float CANCEL_THRESHOLD_DP = 50;
    
    // ButtonState enum moved to RecordingManager
    // Use RecordingManager.ButtonState instead
    


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Hide ActionBar to maximize screen space
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        initViews();
        initManagers();
        checkPermissions();
        
        // Configure WebView and set bottom container reference
        if (webViewManager != null) {
            webViewManager.setBottomContainer(bottomContainer);
            webViewManager.configureWebView();
            webViewManager.loadOpenCodePage();
        }
        
        // Set up record button with manager
        if (recordingManager != null) {
            recordingManager.setUiReferences(recordButton, recordProgress);
            recordingManager.setupRecordButton();
        }
        
        // Initialize keyboard visibility detection
        rootView = getWindow().getDecorView().findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(this::checkKeyboardVisibility);
        updateButtonState(RecordingManager.ButtonState.DEFAULT);
        
        // Set recording flag for WebViewManager
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
        
        // Initialize camera button
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
    
    /**
     * Configure WebView settings and authentication
     */

    
    /**
     * Load OpenCode web page using configured server address
     */

    
    /**
     * Inject JavaScript helper functions and prepare for text injection
     */

    
    /**
     * Inject JavaScript to listen for input focus/blur events
     */

    

    
    /**
     * Parse server URL string into host and port
     * Supports formats: "http://host:port", "https://host:port", "host:port", "host"
     * Returns array where [0] = host, [1] = port string
     */

    
    private void initManagers() {
        fileManager = new FileManager(this);
        whisperManager = new WhisperManager(this, fileManager, (success, message) -> {
            // Forward to recording manager
            if (recordingManager != null) {
                recordingManager.onWhisperInitialized(success, message);
            }
        });
        // TODO: Temporarily disabled OpenCode integration
        openCodeManager = null; // Set to null to avoid NPE
        audioRecorder = new AudioRecorder();
        
        // Initialize Cloud ASR manager
        String cloudAsrUrl = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("cloud_asr_url", Constants.DEFAULT_CLOUD_ASR_URL);
        String[] cloudAsrParts = UrlUtils.parseAsrUrl(cloudAsrUrl, "http");
        cloudAsrManager = new CloudAsrManager(this, cloudAsrParts[0], Integer.parseInt(cloudAsrParts[1]));
        
        // Initialize FunASR WebSocket manager
        String funAsrUrl = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("funasr_url", Constants.DEFAULT_FUNASR_URL);
        String[] funAsrParts = UrlUtils.parseAsrUrl(funAsrUrl, "ws");
        String funAsrMode = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("funasr_mode", Constants.DEFAULT_FUNASR_MODE);
        funAsrManager = new FunAsrWebSocketManager(this, funAsrParts[0], Integer.parseInt(funAsrParts[1]), funAsrMode);
        
        // Create modular managers
        webViewManager = new WebViewManager(this, webView, webViewCallback);
        recordingManager = new RecordingManager(this, recordingCallback);
        cameraPermissionManager = new CameraPermissionManager(this, cameraCallback);
        settingsManager = new SettingsManager(this, settingsCallback);
        
        // Set up manager dependencies
        recordingManager.setManagers(whisperManager, audioRecorder, cloudAsrManager, funAsrManager, fileManager);
        settingsManager.setManagers(cloudAsrManager, funAsrManager, whisperManager, recordingManager);
        
        // Initialize Whisper model (will skip download if fails)
        String modelFilename = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("whisper_model", Constants.DEFAULT_WHISPER_MODEL);
        new Thread(() -> whisperManager.initialize(modelFilename)).start();
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
    
    // =================================================================
    // Callback implementations for modular managers
    // =================================================================
    
    // WebViewManager callbacks
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
            // This is called when camera photo is ready for upload
            // Set camera upload pending flag for WebViewManager
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
            // Update bottom container visibility
            if (bottomContainer != null) {
                bottomContainer.setVisibility(hasFocus ? View.GONE : View.VISIBLE);
            }
        }
        
        @Override
        public void onPageLoadError(String description) {
            Toast.makeText(MainActivity.this, "页面加载失败: " + description, Toast.LENGTH_SHORT).show();
        }
        
        @Override
        public void onAttachmentReady(boolean success, String filename, String message) {
            // Don't show toast if recording to avoid interrupting
            if (!isRecording) {
                if (success) {
                    Toast.makeText(MainActivity.this, "已添加: " + filename, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "添加失败: " + filename + " - " + message, Toast.LENGTH_SHORT).show();
                }
            }
        }
    };
    
    // RecordingManager callbacks
    private final RecordingManager.RecordingCallback recordingCallback = new RecordingManager.RecordingCallback() {
        @Override
        public void onRecordingStateChanged(RecordingManager.ButtonState state) {
            updateButtonState(state);
        }
        
        @Override
        public void onTranscriptionComplete(TranscriptionResult result) {
            // Inject transcribed text into WebView
            if (webViewManager != null) {
                webViewManager.injectTranscribedText(result.getText());
            }
        }
        
        @Override
        public void onTranscriptionError(String error) {
            // Already handled in RecordingManager with toast
        }
        
        @Override
        public void onWhisperInitialized(boolean success, String message) {
            // Already handled in RecordingManager with toast
        }
        
        @Override
        public void onOpenCodeInitialized(boolean success, String message) {
            // Already handled in RecordingManager with toast
        }
    };
    
    // CameraPermissionManager callbacks
    private final CameraPermissionManager.CameraCallback cameraCallback = new CameraPermissionManager.CameraCallback() {
        @Override
        public void onCameraPermissionGranted() {
            // Permission granted, launch camera
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
            // Storage permission granted, handle pending file picker if any
            // This will be handled in onRequestPermissionsResult
        }
        
        @Override
        public void onStoragePermissionDenied() {
            // Already handled in CameraPermissionManager with toast
        }
        
        @Override
        public void onCameraPhotoCaptured(Uri photoUri) {
            // Notify WebViewManager about camera photo
            if (webViewManager != null) {
                webViewManager.setCameraUploadPending(true, photoUri);
            }
        }
        
        @Override
        public void onCameraError(String error) {
            Toast.makeText(MainActivity.this, "相机错误: " + error, Toast.LENGTH_SHORT).show();
        }
    };
    
    // SettingsManager callbacks
    private final SettingsManager.SettingsCallback settingsCallback = new SettingsManager.SettingsCallback() {
        @Override
        public void onSettingsSaved(SettingsManager.SettingsData settings) {
            // Save settings to SharedPreferences
            settingsManager.saveSettings(settings);
            
            // Update managers with new settings
            if (cloudAsrManager != null) {
                cloudAsrManager.updateSettings(settings.cloudAsrHost, settings.cloudAsrPort);
            }
            if (funAsrManager != null) {
                funAsrManager.updateSettings(settings.funAsrHost, settings.funAsrPort, settings.funAsrMode);
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
        public void onReinitializeWhisper(String model) {
            if (whisperManager != null) {
                new Thread(() -> {
                    try {
                        whisperManager.reinitialize(model);
                    } catch (Exception e) {
                        android.util.Log.e("MainActivity", "Failed to reinitialize model", e);
                        mainHandler.post(() -> {
                            Toast.makeText(MainActivity.this, 
                                "模型重新初始化失败，请重启应用", 
                                Toast.LENGTH_LONG).show();
                        });
                    }
                }).start();
            }
        }
        
        @Override
        public void onShowToast(String message, int duration) {
            Toast.makeText(MainActivity.this, message, duration).show();
        }
    };

    

    

    

    

    

    

    
    private void showReloginDialog() {
        mainHandler.post(() -> {
            // Avoid showing multiple dialogs
            if (isFinishing() || isDestroyed()) {
                return;
            }
            
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("登录失败")
                .setMessage("检测到登录失败或认证错误。是否清除登录状态并重新登录？")
                .setPositiveButton("重新登录", (dialog, which) -> {
                    android.util.Log.d("MainActivity", "Clearing all authentication data and reloading");
                    
                    if (webViewManager != null) {
                        webViewManager.clearWebViewData(() -> {
                            mainHandler.postDelayed(() -> {
                                android.util.Log.d("MainActivity", "Reloading page with cache bypass");
                                if (webViewManager != null) {
                                    webViewManager.reloadPage();
                                }
                                Toast.makeText(MainActivity.this, "已清除所有登录状态，请重新登录", Toast.LENGTH_LONG).show();
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
                if (whisperManager != null && !whisperManager.isModelLoaded()) {
                    tvRecordHint.setText("模型未部署，请手动放置后重启");
                } else {
                    tvRecordHint.setText("OpenCode连接失败，请检查配置");
                }
                tvRecordHint.setTextColor(getResources().getColor(android.R.color.darker_gray));
                recordButton.setEnabled(false);
                break;
        }
    }
    
    /**
     * Check keyboard visibility and show/hide bottom container accordingly
     */
    private void checkKeyboardVisibility() {
        if (rootView == null) return;
        
        Rect r = new Rect();
        rootView.getWindowVisibleDisplayFrame(r);
        int screenHeight = rootView.getRootView().getHeight();
        int keypadHeight = screenHeight - r.height();
        
        // Threshold for keyboard visibility (150dp)
        int threshold = (int) (150 * getResources().getDisplayMetrics().density);
        
        boolean keyboardVisible = keypadHeight > threshold;
        if (keyboardVisible != isKeyboardVisible) {
            isKeyboardVisible = keyboardVisible;
            android.util.Log.d("MainActivity", "Keyboard visibility changed: " + keyboardVisible);
            
            if (keyboardVisible) {
                // Keyboard shown - hide bottom container if input is focused
                // The onInputFocus method will handle this, but we ensure it's hidden
                bottomContainer.setVisibility(View.GONE);
            } else {
                // Keyboard hidden - show bottom container
                bottomContainer.setVisibility(View.VISIBLE);
            }
        }
    }
    
    /**
     * Show re-login dialog when login failure is detected
     */

    
    /**
     * Show popup menu with settings and refresh options
     */

    

    
    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
    
    /**
     * Run a test transcription using the sample JFK WAV file
     * This helps verify that Whisper is working correctly
     */

    
    // =================================================================
    // Attachment Upload Methods (Phase 1)
    // =================================================================
    
    /**
     * Open file picker for WebView file chooser
     */
    private void openFilePickerForWebView(WebChromeClient.FileChooserParams params) {
        android.util.Log.d("MainActivity", "Opening file picker for WebView");
        
        if (cameraPermissionManager != null && cameraPermissionManager.checkStoragePermission()) {
            Intent intent = params.createIntent();
            // Fallback if createIntent fails
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
                // Cancel the file chooser on error
                if (webViewManager != null) {
                    webViewManager.handleFileChooserResult(null);
                }
            }
        } else {
            if (cameraPermissionManager != null) {
                cameraPermissionManager.requestStoragePermission();
            }
            // Cancel the file chooser if no permission
            if (webViewManager != null) {
                webViewManager.handleFileChooserResult(null);
            }
        }
    }
    


    /**
     * Open camera to take photo
     */


    /**
     * Check camera permission
     */


    /**
     * Request camera permission
     */


    /**
     * Launch system camera with FileProvider
     */


    /**
     * Create a temporary image file for camera capture
     */


    /**
     * Trigger file upload from camera - sets pending flag for WebView file chooser
     */


    /**
     * Clean up temporary camera file after processing
     */

    
    /**
     * Check storage permission based on Android version
     */

    
    /**
     * Request storage permission
     */

    
    /**
     * Handle file picker result
     */
    @Override
     protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // Delegate to CameraPermissionManager
        if (requestCode == CameraPermissionManager.REQUEST_CAMERA) {
            if (cameraPermissionManager != null) {
                cameraPermissionManager.onActivityResult(requestCode, resultCode, data);
            }
        }
        
        // Handle WebView file chooser result
        if (requestCode == REQUEST_WEBVIEW_FILE_CHOOSER) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    // Multiple files
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    // Single file
                    results = new Uri[]{data.getData()};
                }
            }
            
            android.util.Log.d("MainActivity", "WebView file chooser result: " + 
                (results != null ? results.length + " files" : "cancelled"));
            
            // Pass result to WebViewManager
            if (webViewManager != null) {
                webViewManager.handleFileChooserResult(results);
            }
        }
    }
    

    
    /**
     * Process a single file: read, convert to Base64, inject to WebView
     */

    
    /**
     * Convert file to Base64 string
     */


    

    
    /**
     * Inject image to WebView by simulating paste event using chunked transfer
     * This method splits large Base64 data into smaller chunks to avoid WebView limitations
     */

    
    /**
     * Inject JavaScript receiver that can handle chunked Base64 data
     */

    
    /**
     * Inject the processAttachment function - simplified version
     */



    

    

    

    

    
    /**
     * Handle permission request results
     */
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
            // Delegate to CameraPermissionManager
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
        if (whisperManager != null) {
            whisperManager.release();
        }
        if (recordingManager != null) {
            recordingManager.release();
        }
        if (fileManager != null) {
            fileManager.deleteTempWavFile();
        }
    }
}
