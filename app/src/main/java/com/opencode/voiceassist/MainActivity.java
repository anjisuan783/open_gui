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
import com.opencode.voiceassist.manager.CloudAsrManager;
import com.opencode.voiceassist.manager.FunAsrWebSocketManager;
import com.opencode.voiceassist.manager.OpenCodeManager;
import com.opencode.voiceassist.manager.WhisperManager;
import com.opencode.voiceassist.model.Message;
import com.opencode.voiceassist.model.TranscriptionResult;
import com.opencode.voiceassist.ui.MessageAdapter;
import com.opencode.voiceassist.utils.Constants;
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
import android.util.Base64;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int REQUEST_FILE_PICKER = 1002;
    private static final int REQUEST_STORAGE_PERMISSION = 1003;
    private static final int REQUEST_WEBVIEW_FILE_CHOOSER = 1004;
    
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private WebChromeClient.FileChooserParams fileChooserParams;
    
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
    private ImageButton btnAttachment;
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isKeyboardVisible = false;
    private View rootView;
    private boolean transcriptionTested = true; // Default to skip test on first launch
    
    private boolean isRecording = false;
    private boolean isCancelled = false;
    private float startY = 0;
    private static final float CANCEL_THRESHOLD_DP = 50;
    
    private enum ButtonState {
        DEFAULT, RECORDING, CANCEL, PROCESSING, DISABLED
    }
    
    /**
     * JavaScript interface for communication between WebView and Android
     */
    public class JavaScriptInterface {
        @android.webkit.JavascriptInterface
        public void showToast(String message) {
            android.util.Log.d("MainActivity", "JavaScript toast: " + message);
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        }
        
        @android.webkit.JavascriptInterface
        public void logToAndroid(String message) {
            android.util.Log.d("MainActivity", "JavaScript log: " + message);
        }
        
        @android.webkit.JavascriptInterface
        public String getLastTranscribedText() {
            // This will return the last transcribed text from speech recognition
            // To be implemented when we have a variable to store it
            return "";
        }
        
        @android.webkit.JavascriptInterface
        public void onInputFocus(boolean hasFocus) {
            android.util.Log.d("MainActivity", "Input focus changed: " + hasFocus);
            mainHandler.post(() -> {
                if (bottomContainer != null) {
                    if (hasFocus) {
                        // Hide record area when input is focused (keyboard shown)
                        bottomContainer.setVisibility(View.GONE);
                        android.util.Log.d("MainActivity", "Record area hidden (input focused)");
                    } else {
                        // Show record area when input loses focus (keyboard hidden)
                        bottomContainer.setVisibility(View.VISIBLE);
                        android.util.Log.d("MainActivity", "Record area shown (input blurred)");
                    }
                }
            });
        }
        
        @android.webkit.JavascriptInterface
        public void injectAttachment(String base64Data, String filename, String mimeType) {
            android.util.Log.d("MainActivity", "JavaScriptInterface.injectAttachment called: " + 
                filename + ", mimeType: " + mimeType + ", data length: " + 
                (base64Data != null ? base64Data.length() : 0));
            
            // This method receives data from JavaScript and needs to inject it back
            // We'll handle this on the UI thread
            mainHandler.post(() -> {
                injectImageToWebView(base64Data, filename, mimeType);
            });
        }
        
        @android.webkit.JavascriptInterface
        public void onAttachmentReady(boolean success, String filename, String message) {
            android.util.Log.d("MainActivity", "JavaScriptInterface.onAttachmentReady: success=" + 
                success + ", filename=" + filename + ", message=" + message);
            
            mainHandler.post(() -> {
                if (success) {
                    Toast.makeText(MainActivity.this, "已添加: " + filename, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "添加失败: " + filename + " - " + message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

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
    }
    
    private void initViews() {
        webView = findViewById(R.id.webview_opencode);
        recordButton = findViewById(R.id.btn_record);
        tvRecordHint = findViewById(R.id.tv_record_hint);
        recordButtonContainer = findViewById(R.id.record_button_container);
        recordProgress = findViewById(R.id.record_progress);
        bottomContainer = findViewById(R.id.bottom_container);
        
        // Initialize attachment button
        btnAttachment = findViewById(R.id.btn_attachment);
        btnAttachment.setOnClickListener(v -> openFilePicker());

        TextView btnMenu = findViewById(R.id.btn_menu);
        btnMenu.setOnClickListener(v -> showPopupMenu(v));
        
        configureWebView();
        loadOpenCodePage();
        
        setupRecordButton();
        // Initialize keyboard visibility detection
        rootView = getWindow().getDecorView().findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(this::checkKeyboardVisibility);
        updateButtonState(ButtonState.DEFAULT);
    }
    
    /**
     * Configure WebView settings and authentication
     */
    private void configureWebView() {
        android.util.Log.d("MainActivity", "Configuring WebView...");
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        
        // Configure WebViewClient for authentication
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedHttpAuthRequest(WebView view,
                    HttpAuthHandler handler, String host, String realm) {
                android.util.Log.d("MainActivity", "HTTP authentication requested for: " + host + ", realm: " + realm);
                // Use hardcoded credentials for now
                handler.proceed("opencode_linaro_dev", "abcd@1234");
            }
            
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                android.util.Log.d("MainActivity", "Page started loading: " + url);
                super.onPageStarted(view, url, favicon);
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                android.util.Log.d("MainActivity", "Page finished loading: " + url);
                super.onPageFinished(view, url);
                // Inject input detection script after page loads
                injectInputDetectionScript();
                
                // Check for login failure
                checkForLoginFailure();
            }
            
            private void checkForLoginFailure() {
                // Delay slightly to ensure page content is fully rendered
                mainHandler.postDelayed(() -> {
                    String jsCode = "(function() {" +
                        "var bodyText = document.body ? document.body.innerText : '';" +
                        "var hasInvalid = bodyText.toLowerCase().indexOf('invalid') !== -1;" +
                        "var hasUnauthorized = bodyText.toLowerCase().indexOf('unauthorized') !== -1;" +
                        "var hasLoginFailed = bodyText.toLowerCase().indexOf('登录失败') !== -1 || " +
                        "                   bodyText.toLowerCase().indexOf('login failed') !== -1 || " +
                        "                   bodyText.toLowerCase().indexOf('authentication failed') !== -1 || " +
                        "                   bodyText.toLowerCase().indexOf('认证失败') !== -1;" +
                        "var hasErrorCode = document.querySelector('[data-error-code]') !== null;" +
                        "return hasInvalid || hasUnauthorized || hasLoginFailed || hasErrorCode;" +
                        "})();";
                    
                    webView.evaluateJavascript(jsCode, result -> {
                        if ("true".equals(result)) {
                            android.util.Log.w("MainActivity", "Login failure detected on page");
                            showReloginDialog();
                        }
                    });
                }, 500); // 500ms delay to ensure page is fully loaded
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                android.util.Log.e("MainActivity", "WebView error: " + errorCode + " - " + description + " for URL: " + failingUrl);
                super.onReceivedError(view, errorCode, description, failingUrl);
                Toast.makeText(MainActivity.this, "页面加载失败: " + description, Toast.LENGTH_SHORT).show();
            }
        });
        
        // Configure WebChromeClient for console logging and file upload
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                android.util.Log.d("MainActivity", "WebView console: " + consoleMessage.message() + 
                        " at " + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber());
                return true;
            }
            
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, 
                    FileChooserParams fileChooserParams) {
                android.util.Log.d("MainActivity", "WebView file chooser requested");
                
                // Store the callback
                MainActivity.this.filePathCallback = filePathCallback;
                
                // Launch file picker
                openFilePickerForWebView(fileChooserParams);
                
                return true;
            }
        });
        
        // Register JavaScript interface for bidirectional communication
        webView.addJavascriptInterface(new JavaScriptInterface(), "AndroidVoiceAssist");
        
        // Initialize WebView text injector
        webViewInjector = new WebViewTextInjector(webView);
        
        android.util.Log.d("MainActivity", "WebView configuration completed");
    }
    
    /**
     * Load OpenCode web page using configured server address
     */
    private void loadOpenCodePage() {
        android.util.Log.d("MainActivity", "Loading OpenCode page...");
        
        String defaultIp = Constants.DEFAULT_OPENCODE_IP;
        int defaultPort = Constants.DEFAULT_OPENCODE_PORT;
        android.util.Log.d("MainActivity", "Constants.DEFAULT_OPENCODE_IP = " + defaultIp);
        android.util.Log.d("MainActivity", "Constants.DEFAULT_OPENCODE_PORT = " + defaultPort);
        
        // Debug: list all settings keys
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        java.util.Map<String, ?> allPrefs = prefs.getAll();
        android.util.Log.d("MainActivity", "All settings keys: " + allPrefs.keySet());
        for (java.util.Map.Entry<String, ?> entry : allPrefs.entrySet()) {
            android.util.Log.d("MainActivity", "  " + entry.getKey() + " = " + entry.getValue());
        }
        
        String ip = prefs.getString("opencode_ip", defaultIp);
        int port = prefs.getInt("opencode_port", defaultPort);
        
        android.util.Log.d("MainActivity", "Retrieved IP: " + ip + ", Retrieved port: " + port);
        String url = "http://" + ip + ":" + port;
        android.util.Log.d("MainActivity", "Loading URL: " + url);
        
        webView.loadUrl(url);
    }
    
    /**
     * Inject JavaScript helper functions and prepare for text injection
     */
    private void injectInputDetectionScript() {
        android.util.Log.d("MainActivity", "Injecting input detection script using WebViewTextInjector");
        
        if (webViewInjector != null) {
            // Inject helper functions using the new injector
            webViewInjector.injectHelperFunctions();
            
            // Inject focus/blur listener for input field
            injectFocusListener();
            
            // Test if injection is working
            mainHandler.postDelayed(() -> {
                webViewInjector.injectText("", false, new WebViewTextInjector.InjectionCallback() {
                    @Override
                    public void onSuccess(String text) {
                        android.util.Log.d("MainActivity", "WebView injector test passed");
                    }
                    
                    @Override
                    public void onFailure(String error) {
                        android.util.Log.w("MainActivity", "WebView injector test failed (expected on empty page): " + error);
                    }
                    
                    @Override
                    public void onRetry(int attempt, int maxRetries) {
                        android.util.Log.d("MainActivity", "WebView injector retry: " + attempt + "/" + maxRetries);
                    }
                });
            }, 500);
        }
    }
    
    /**
     * Inject JavaScript to listen for input focus/blur events
     */
    private void injectFocusListener() {
        String jsCode = "(function(){" +
            "var input=document.querySelector('[data-component=\"prompt-input\"]');" +
            "if(!input){" +
            "  console.log('FocusListener: Input element not found, will retry');" +
            "  setTimeout(function(){" +
            "    if(window.injectFocusListener) window.injectFocusListener();" +
            "  },1000);" +
            "  return;" +
            "}" +
            "if(input._focusListenerInstalled){" +
            "  console.log('FocusListener: Already installed');" +
            "  return;" +
            "}" +
            "input.addEventListener('focus',function(){" +
            "  console.log('FocusListener: Input focused');" +
            "  if(window.AndroidVoiceAssist) window.AndroidVoiceAssist.onInputFocus(true);" +
            "});" +
            "input.addEventListener('blur',function(){" +
            "  console.log('FocusListener: Input blurred');" +
            "  if(window.AndroidVoiceAssist) window.AndroidVoiceAssist.onInputFocus(false);" +
            "});" +
            "input._focusListenerInstalled=true;" +
            "console.log('FocusListener: Installed successfully');" +
            "})();";
        
        webView.evaluateJavascript(jsCode, result -> {
            android.util.Log.d("MainActivity", "Focus listener injection result: " + result);
        });
    }
    
    /**
     * Inject transcribed text into OpenCode web page input field
     * Uses WebViewTextInjector for robust injection with retry logic
     */
    private void injectTranscribedText(String text) {
        android.util.Log.d("MainActivity", "Injecting transcribed text: " + text);
        
        if (webViewInjector == null) {
            android.util.Log.e("MainActivity", "WebViewInjector not initialized");
            Toast.makeText(this, "注入器未初始化", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show processing state
        updateButtonState(ButtonState.PROCESSING);
        
        // Check auto-send setting
        final boolean autoSend = getSharedPreferences("settings", MODE_PRIVATE)
                .getBoolean(Constants.KEY_AUTO_SEND, Constants.DEFAULT_AUTO_SEND);
        
        // Use the new injector with retry and error handling
        // If auto-send is enabled, don't set focus to avoid keyboard popup
        webViewInjector.injectText(text, !autoSend, new WebViewTextInjector.InjectionCallback() {
            @Override
            public void onSuccess(String injectedText) {
                mainHandler.post(() -> {
                    android.util.Log.i("MainActivity", "Text injection successful");
                    
                    if (autoSend) {
                        // Auto-send enabled, trigger send after a short delay
                        mainHandler.postDelayed(() -> {
                            webViewInjector.triggerSend(success -> {
                                if (success) {
                                    android.util.Log.i("MainActivity", "Message sent automatically");
                                } else {
                                    android.util.Log.w("MainActivity", "Auto-send failed");
                                }
                                updateButtonState(ButtonState.DEFAULT);
                            });
                        }, 100); // Small delay to ensure text is properly set
                    } else {
                        // Auto-send disabled, just show toast
                        Toast.makeText(MainActivity.this, "文本已注入", Toast.LENGTH_SHORT).show();
                        updateButtonState(ButtonState.DEFAULT);
                    }
                });
            }
            
            @Override
            public void onFailure(String error) {
                mainHandler.post(() -> {
                    android.util.Log.e("MainActivity", "Text injection failed: " + error);
                    Toast.makeText(MainActivity.this, "注入失败: " + error, Toast.LENGTH_LONG).show();
                    updateButtonState(ButtonState.DEFAULT);
                });
            }
            
            @Override
            public void onRetry(int attempt, int maxRetries) {
                android.util.Log.d("MainActivity", "Text injection retrying: " + attempt + "/" + maxRetries);
            }
        });
    }
    
    /**
     * Parse server URL string into host and port
     * Supports formats: "http://host:port", "https://host:port", "host:port", "host"
     * Returns array where [0] = host, [1] = port string
     */
    private static String[] parseServerUrl(String url) {
        String host = "localhost";
        String port = "4096";
        
        if (url == null || url.trim().isEmpty()) {
            return new String[]{host, port};
        }
        
        String trimmed = url.trim();
        
        // Remove http:// or https:// prefix
        if (trimmed.startsWith("http://")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("https://")) {
            trimmed = trimmed.substring(8);
        }
        
        // Split host and port
        int colonIndex = trimmed.indexOf(':');
        if (colonIndex > 0) {
            host = trimmed.substring(0, colonIndex);
            String portStr = trimmed.substring(colonIndex + 1);
            // Remove path part if any
            int slashIndex = portStr.indexOf('/');
            if (slashIndex > 0) {
                portStr = portStr.substring(0, slashIndex);
            }
            port = portStr;
        } else {
            host = trimmed;
        }
        
        // Use defaults if host is empty
        if (host.isEmpty()) {
            host = "localhost";
        }
        
        return new String[]{host, port};
    }
    
    /**
     * Format host and port into display URL string
     */
    private static String formatServerUrl(String host, int port) {
        return "http://" + host + ":" + port;
    }
    
    /**
     * Parse ASR URL (http://host:port or ws://host:port) into host and port
     * Returns array where [0] = host, [1] = port
     */
    private static String[] parseAsrUrl(String url, String defaultProtocol) {
        String host = "localhost";
        String port = defaultProtocol.equals("http") ? "8080" : "10095";
        
        if (url == null || url.trim().isEmpty()) {
            return new String[]{host, port};
        }
        
        String trimmed = url.trim();
        
        // Remove protocol prefix
        if (trimmed.startsWith("http://")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("https://")) {
            trimmed = trimmed.substring(8);
        } else if (trimmed.startsWith("ws://")) {
            trimmed = trimmed.substring(5);
        } else if (trimmed.startsWith("wss://")) {
            trimmed = trimmed.substring(6);
        }
        
        // Split host and port
        int colonIndex = trimmed.indexOf(':');
        if (colonIndex > 0) {
            host = trimmed.substring(0, colonIndex);
            String portStr = trimmed.substring(colonIndex + 1);
            // Remove path part if any
            int slashIndex = portStr.indexOf('/');
            if (slashIndex > 0) {
                portStr = portStr.substring(0, slashIndex);
            }
            if (!portStr.isEmpty()) {
                port = portStr;
            }
        } else {
            host = trimmed;
        }
        
        // Use defaults if host is empty
        if (host.isEmpty()) {
            host = "localhost";
        }
        
        return new String[]{host, port};
    }
    
    private void initManagers() {
        fileManager = new FileManager(this);
        whisperManager = new WhisperManager(this, fileManager, this::onWhisperInitialized);
        // TODO: Temporarily disabled OpenCode integration
        // openCodeManager = new OpenCodeManager(this);
        // openCodeManager.setInitializationCallback(this::onOpenCodeInitialized);
        openCodeManager = null; // Set to null to avoid NPE
        audioRecorder = new AudioRecorder();
        
        // Initialize Cloud ASR manager
        String cloudAsrUrl = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("cloud_asr_url", Constants.DEFAULT_CLOUD_ASR_URL);
        String[] cloudAsrParts = parseAsrUrl(cloudAsrUrl, "http");
        cloudAsrManager = new CloudAsrManager(this, cloudAsrParts[0], Integer.parseInt(cloudAsrParts[1]));
        
        // Initialize FunASR WebSocket manager
        String funAsrUrl = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("funasr_url", Constants.DEFAULT_FUNASR_URL);
        String[] funAsrParts = parseAsrUrl(funAsrUrl, "ws");
        String funAsrMode = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("funasr_mode", Constants.DEFAULT_FUNASR_MODE);
        funAsrManager = new FunAsrWebSocketManager(this, funAsrParts[0], Integer.parseInt(funAsrParts[1]), funAsrMode);
        
        // Initialize Whisper model (will skip download if fails)
        String modelFilename = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("whisper_model", Constants.DEFAULT_WHISPER_MODEL);
        new Thread(() -> whisperManager.initialize(modelFilename)).start();
        
        // Initialize OpenCode session (temporarily disabled)
        // new Thread(() -> openCodeManager.initializeSession()).start();
    }
    
    private void onWhisperInitialized(boolean success, String message) {
        mainHandler.post(() -> {
            if (success) {
                // Check settings
                boolean autoTestEnabled = getSharedPreferences("settings", MODE_PRIVATE)
                        .getBoolean("auto_test_on_model_change", true);
                String asrBackend = getSharedPreferences("settings", MODE_PRIVATE)
                        .getString("asr_backend", Constants.DEFAULT_ASR_BACKEND);
                
                String asrMode;
                switch (asrBackend) {
                    case Constants.ASR_BACKEND_CLOUD_HTTP:
                        asrMode = "【云端ASR】";
                        break;
                    case Constants.ASR_BACKEND_FUNASR_WS:
                        asrMode = "【FunASR】";
                        break;
                    default:
                        asrMode = "【本地Whisper】";
                        break;
                }
                
                // Only show test info for local backend
                String testInfo = "";
                if (asrBackend.equals(Constants.ASR_BACKEND_LOCAL)) {
                    testInfo = autoTestEnabled ? "（将自动测试）" : "（已跳过测试）";
                }
                Toast.makeText(this, asrMode + "录音功能已启用" + testInfo, Toast.LENGTH_SHORT).show();
                updateButtonState(ButtonState.DEFAULT);

                // Automatic transcription test to verify performance - only for local backend
                if (asrBackend.equals(Constants.ASR_BACKEND_LOCAL) && autoTestEnabled) {
                    mainHandler.postDelayed(() -> {
                        runTranscriptionTest();
                    }, 3000); // Wait 3 seconds for everything to settle
                }
            } else {
                // Model initialization failed/skipped - disable recording
                updateButtonState(ButtonState.DISABLED);
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private void onOpenCodeInitialized(boolean success, String message) {
        mainHandler.post(() -> {
            if (success) {
                Toast.makeText(this, "OpenCode连接成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "OpenCode连接失败: " + message + "\n请点击右上角齿轮图标配置服务器地址", Toast.LENGTH_LONG).show();
            }
        });
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
    

    
    private void setupRecordButton() {
        recordButton.setOnTouchListener((v, event) -> {
            if (whisperManager == null || !whisperManager.isModelLoaded()) {
                return false; // Button is disabled
            }
            
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startY = event.getY();
                    startRecording();
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    if (isRecording) {
                        float deltaY = startY - event.getY();
                        float thresholdPx = dpToPx(CANCEL_THRESHOLD_DP);
                        
                        if (deltaY >= thresholdPx && !isCancelled) {
                            isCancelled = true;
                            updateButtonState(ButtonState.CANCEL);
                        } else if (deltaY < thresholdPx && isCancelled) {
                            isCancelled = false;
                            updateButtonState(ButtonState.RECORDING);
                        }
                    }
                    return true;
                    
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isRecording) {
                        stopRecording();
                    }
                    return true;
            }
            return false;
        });
    }
    
    private void startRecording() {
        android.util.Log.d("MainActivity", "startRecording() called");
        isRecording = true;
        isCancelled = false;
        updateButtonState(ButtonState.RECORDING);
        
        File wavFile = fileManager.getTempWavFile();
        android.util.Log.d("MainActivity", "WAV file path: " + wavFile.getAbsolutePath());
        
        // Delete previous file if exists
        if (wavFile.exists()) {
            android.util.Log.d("MainActivity", "Deleting previous WAV file");
            wavFile.delete();
        }
        
        audioRecorder.startRecording(wavFile);
        android.util.Log.d("MainActivity", "AudioRecorder started");
    }
    
    private void stopRecording() {
        android.util.Log.d("MainActivity", "stopRecording() called");
        isRecording = false;
        
        if (isCancelled) {
            android.util.Log.d("MainActivity", "Recording was cancelled");
            audioRecorder.stopRecording();
            fileManager.deleteTempWavFile();
            Toast.makeText(this, "已取消录音", Toast.LENGTH_SHORT).show();
            updateButtonState(ButtonState.DEFAULT);
            return;
        }
        
        updateButtonState(ButtonState.PROCESSING);
        android.util.Log.d("MainActivity", "Stopping audio recorder...");
        audioRecorder.stopRecording();
        
        // Wait a bit for recorder to finish writing file
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        File wavFile = fileManager.getTempWavFile();
        android.util.Log.d("MainActivity", "Checking WAV file: " + wavFile.getAbsolutePath());
        android.util.Log.d("MainActivity", "File exists: " + wavFile.exists() + ", size: " + (wavFile.exists() ? wavFile.length() : 0) + " bytes");
        
        if (!wavFile.exists() || wavFile.length() == 0) {
            android.util.Log.e("MainActivity", "WAV file is empty or doesn't exist");
            Toast.makeText(this, "录音失败，请重试", Toast.LENGTH_SHORT).show();
            updateButtonState(ButtonState.DEFAULT);
            return;
        }
        
        // Verify WAV file format
        if (wavFile.length() < 44) {
            android.util.Log.e("MainActivity", "WAV file too small for header: " + wavFile.length() + " bytes");
            Toast.makeText(this, "录音文件格式错误", Toast.LENGTH_SHORT).show();
            updateButtonState(ButtonState.DEFAULT);
            return;
        }
        
        android.util.Log.d("MainActivity", "WAV file looks good, starting transcription...");
        
        // Get ASR backend setting
        String asrBackend = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("asr_backend", Constants.DEFAULT_ASR_BACKEND);
        
        android.util.Log.d("MainActivity", "Using ASR backend: " + asrBackend);
        
        if (asrBackend.equals(Constants.ASR_BACKEND_CLOUD_HTTP)) {
            // Use cloud ASR HTTP
            android.util.Log.d("MainActivity", "Using cloud HTTP ASR for transcription");
            cloudAsrManager.transcribe(wavFile, new CloudAsrManager.TranscriptionCallback() {
                @Override
                public void onSuccess(TranscriptionResult result) {
                    fileManager.deleteTempWavFile();
                    android.util.Log.d("MainActivity", "Cloud ASR result: " + result.getText());
                    mainHandler.post(() -> processTranscribedText(result));
                }
                
                @Override
                public void onError(String error) {
                    fileManager.deleteTempWavFile();
                    android.util.Log.e("MainActivity", "Cloud ASR error: " + error);
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "云端ASR失败: " + error, Toast.LENGTH_SHORT).show();
                        updateButtonState(ButtonState.DEFAULT);
                    });
                }
            });
        } else if (asrBackend.equals(Constants.ASR_BACKEND_FUNASR_WS)) {
            // Use FunASR WebSocket
            android.util.Log.d("MainActivity", "Using FunASR WebSocket for transcription");
            funAsrManager.transcribe(wavFile, new FunAsrWebSocketManager.TranscriptionCallback() {
                @Override
                public void onSuccess(TranscriptionResult result) {
                    fileManager.deleteTempWavFile();
                    android.util.Log.d("MainActivity", "FunASR result: " + result.getText());
                    mainHandler.post(() -> processTranscribedText(result));
                }
                
                @Override
                public void onError(String error) {
                    fileManager.deleteTempWavFile();
                    android.util.Log.e("MainActivity", "FunASR error: " + error);
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "FunASR失败: " + error, Toast.LENGTH_SHORT).show();
                        updateButtonState(ButtonState.DEFAULT);
                    });
                }
            });
        } else {
            // Use local Whisper ASR (default)
            android.util.Log.d("MainActivity", "Using local Whisper ASR for transcription");
            new Thread(() -> {
                android.util.Log.d("MainActivity", "Transcription thread started");
                TranscriptionResult result = whisperManager.transcribe(wavFile);
                fileManager.deleteTempWavFile();
                
                android.util.Log.d("MainActivity", "Transcription result: " + (result != null ? result.getText() : "null"));
                
                if (result != null && result.getText() != null && !result.getText().trim().isEmpty()) {
                    android.util.Log.d("MainActivity", "Transcription successful, processing text...");
                    android.util.Log.d("MainActivity", "Performance data: audio=" + String.format("%.2f", result.getAudioLengthSeconds()) + "s, " +
                          "processing=" + result.getProcessingTimeMs() + "ms, " +
                          "realtime factor=" + String.format("%.1f", result.getRealtimeFactor()) + "x");
                    mainHandler.post(() -> processTranscribedText(result));
                } else {
                    android.util.Log.e("MainActivity", "Transcription returned null or empty");
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "语音识别失败，请重试", Toast.LENGTH_SHORT).show();
                        updateButtonState(ButtonState.DEFAULT);
                    });
                }
            }).start();
        }
    }
    
    private void processTranscribedText(TranscriptionResult result) {
        String text = result.getText();
        android.util.Log.d("MainActivity", "Processing transcribed text: " + text);
        
        // Create user message with performance data
        String userMessageContent = text + "\n\n[语音长度: " + String.format("%.2f", result.getAudioLengthSeconds()) + "秒, " +
                                   "处理耗时: " + result.getProcessingTimeMs() + "毫秒, " +
                                   "实时因子: " + String.format("%.1f", result.getRealtimeFactor()) + "x]";
        // addMessage(new Message(userMessageContent, Message.TYPE_USER));
        injectTranscribedText(text);
        
        updateButtonState(ButtonState.DEFAULT);
        
        /* Original OpenCode integration (disabled)
        // Send to OpenCode
        new Thread(() -> {
            android.util.Log.d("MainActivity", "Sending to OpenCode: " + text);
            openCodeManager.sendMessage(text, new OpenCodeManager.ResponseCallback() {
                @Override
                public void onResponse(String response) {
                    android.util.Log.d("MainActivity", "OpenCode response: " + response);
                    mainHandler.post(() -> {
            // addMessage(new Message(response, Message.TYPE_ASSISTANT));
                        updateButtonState(ButtonState.DEFAULT);
                    });
                }
                
                @Override
                public void onError(String error) {
                    android.util.Log.d("MainActivity", "OpenCode error: " + error);
                    mainHandler.post(() -> {
                        addMessage(new Message("错误: " + error, Message.TYPE_ERROR));
                        updateButtonState(ButtonState.DEFAULT);
                    });
                }
            });
        }).start();
        */
    }
    

    
    private void updateButtonState(ButtonState state) {
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
                    
                    // Step 1: Clear all WebView data
                    webView.clearCache(true);
                    webView.clearHistory();
                    webView.clearFormData();
                    
                    // Step 2: Clear localStorage, sessionStorage and cookies via JavaScript
                    webView.evaluateJavascript(
                        "(function() {" +
                        "  try {" +
                        "    localStorage.clear();" +
                        "    sessionStorage.clear();" +
                        "    var cookies = document.cookie.split(';');" +
                        "    for (var i = 0; i < cookies.length; i++) {" +
                        "      var cookie = cookies[i];" +
                        "      var eqPos = cookie.indexOf('=');" +
                        "      var name = eqPos > -1 ? cookie.substr(0, eqPos) : cookie;" +
                        "      document.cookie = name + '=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/';" +
                        "    }" +
                        "    return 'cleared';" +
                        "  } catch(e) {" +
                        "    return 'error: ' + e.message;" +
                        "  }" +
                        "})();",
                        jsResult -> {
                            android.util.Log.d("MainActivity", "JS storage cleared: " + jsResult);
                            
                            // Step 3: Clear Android CookieManager (including HttpOnly cookies)
                            CookieManager cookieManager = CookieManager.getInstance();
                            cookieManager.removeAllCookies(value -> {
                                android.util.Log.d("MainActivity", "Cookies removed: " + value);
                                
                                // Step 4: Clear WebView database (localStorage, etc.)
                                webView.clearCache(true);
                                deleteDatabase("webview.db");
                                deleteDatabase("webviewCache.db");
                                
                                // Step 5: Reload with cache bypass
                                mainHandler.postDelayed(() -> {
                                    android.util.Log.d("MainActivity", "Reloading page with cache bypass");
                                    String url = "http://" + Constants.DEFAULT_OPENCODE_IP + ":" + Constants.DEFAULT_OPENCODE_PORT;
                                    webView.loadUrl(url + "?t=" + System.currentTimeMillis()); // Add timestamp to bypass cache
                                    Toast.makeText(MainActivity.this, "已清除所有登录状态，请重新登录", Toast.LENGTH_LONG).show();
                                }, 300);
                            });
                        }
                    );
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    android.util.Log.d("MainActivity", "User cancelled re-login");
                })
                .setCancelable(true)
                .show();
        });
    }
    
    /**
     * Show popup menu with settings and refresh options
     */
    private void showPopupMenu(View anchorView) {
        androidx.appcompat.widget.PopupMenu popupMenu = new androidx.appcompat.widget.PopupMenu(this, anchorView);
        popupMenu.getMenuInflater().inflate(R.menu.menu_main, popupMenu.getMenu());
        
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_settings) {
                showSettingsDialog();
                return true;
            } else if (itemId == R.id.menu_refresh) {
                android.util.Log.d("MainActivity", "Refreshing WebView page");
                loadOpenCodePage();
                Toast.makeText(this, "页面已刷新", Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.menu_relogin) {
                android.util.Log.d("MainActivity", "Manual re-login requested");
                showReloginDialog();
                return true;
            }
            return false;
        });
        
        popupMenu.show();
    }
    
    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        
        EditText etIp = view.findViewById(R.id.et_ip);
        RadioGroup rgModel = view.findViewById(R.id.rg_model);
        RadioButton rbModelOriginal = view.findViewById(R.id.rb_model_original);
        RadioButton rbModelInt8 = view.findViewById(R.id.rb_model_int8);
        RadioButton rbModelQ5_1 = view.findViewById(R.id.rb_model_q5_1);
        android.widget.CheckBox cbAutoTest = view.findViewById(R.id.cb_auto_test);
        
        // ASR Backend controls
        RadioGroup rgAsrBackend = view.findViewById(R.id.rg_asr_backend);
        RadioButton rbAsrLocal = view.findViewById(R.id.rb_asr_local);
        RadioButton rbAsrCloudHttp = view.findViewById(R.id.rb_asr_cloud_http);
        RadioButton rbAsrFunasrWs = view.findViewById(R.id.rb_asr_funasr_ws);
        
        // Cloud HTTP ASR configuration
        TextView tvCloudAsrConfigLabel = view.findViewById(R.id.tv_cloud_asr_config_label);
        EditText etCloudAsrUrl = view.findViewById(R.id.et_cloud_asr_url);
        
        // FunASR WebSocket configuration
        TextView tvFunasrConfigLabel = view.findViewById(R.id.tv_funasr_config_label);
        EditText etFunasrUrl = view.findViewById(R.id.et_funasr_url);
        TextView tvFunasrModeLabel = view.findViewById(R.id.tv_funasr_mode_label);
        RadioGroup rgFunasrMode = view.findViewById(R.id.rg_funasr_mode);
        RadioButton rbFunasrModeOffline = view.findViewById(R.id.rb_funasr_mode_offline);
        RadioButton rbFunasrMode2pass = view.findViewById(R.id.rb_funasr_mode_2pass);

        // WebView settings
        android.widget.CheckBox cbAutoSend = view.findViewById(R.id.cb_auto_send);
        
        // Load saved settings
        String savedIp = getSharedPreferences("settings", MODE_PRIVATE).getString("opencode_ip", Constants.DEFAULT_OPENCODE_IP);
        int savedPort = getSharedPreferences("settings", MODE_PRIVATE).getInt("opencode_port", Constants.DEFAULT_OPENCODE_PORT);
        String savedModel = getSharedPreferences("settings", MODE_PRIVATE).getString("whisper_model", Constants.DEFAULT_WHISPER_MODEL);
        boolean autoTestEnabled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("auto_test_on_model_change", true);
        boolean autoSendEnabled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean(Constants.KEY_AUTO_SEND, Constants.DEFAULT_AUTO_SEND);
        
        // Load ASR backend settings
        String asrBackend = getSharedPreferences("settings", MODE_PRIVATE).getString("asr_backend", Constants.DEFAULT_ASR_BACKEND);
        String cloudAsrUrl = getSharedPreferences("settings", MODE_PRIVATE).getString("cloud_asr_url", Constants.DEFAULT_CLOUD_ASR_URL);
        String funAsrUrl = getSharedPreferences("settings", MODE_PRIVATE).getString("funasr_url", Constants.DEFAULT_FUNASR_URL);
        String funAsrMode = getSharedPreferences("settings", MODE_PRIVATE).getString("funasr_mode", Constants.DEFAULT_FUNASR_MODE);
        
        etIp.setText(formatServerUrl(savedIp, savedPort));
        
        // Set ASR backend radio button
        if (asrBackend.equals(Constants.ASR_BACKEND_CLOUD_HTTP)) {
            rbAsrCloudHttp.setChecked(true);
        } else if (asrBackend.equals(Constants.ASR_BACKEND_FUNASR_WS)) {
            rbAsrFunasrWs.setChecked(true);
        } else {
            rbAsrLocal.setChecked(true);
        }
        
        // Set Cloud ASR URL
        etCloudAsrUrl.setText(cloudAsrUrl);
        
        // Set FunASR URL
        etFunasrUrl.setText(funAsrUrl);
        if (funAsrMode.equals("2pass")) {
            rbFunasrMode2pass.setChecked(true);
        } else {
            rbFunasrModeOffline.setChecked(true);
        }
        
        // Set WebView settings
        cbAutoSend.setChecked(autoSendEnabled);
        
        // Function to update UI visibility based on ASR backend selection
        java.util.function.Consumer<String> updateBackendUI = (backend) -> {
            boolean isLocal = backend.equals(Constants.ASR_BACKEND_LOCAL);
            boolean isCloudHttp = backend.equals(Constants.ASR_BACKEND_CLOUD_HTTP);
            boolean isFunasrWs = backend.equals(Constants.ASR_BACKEND_FUNASR_WS);
            
            // Update local model options visibility and state
            rbModelOriginal.setEnabled(isLocal);
            rbModelInt8.setEnabled(isLocal);
            rbModelQ5_1.setEnabled(isLocal);
            rgModel.setAlpha(isLocal ? 1.0f : 0.5f);
            cbAutoTest.setEnabled(isLocal);
            cbAutoTest.setAlpha(isLocal ? 1.0f : 0.5f);
            
            // Auto-test is only relevant for local backend
            if (!isLocal) {
                cbAutoTest.setChecked(false);
            }
            
            // Update Cloud ASR config visibility
            int cloudVisibility = isCloudHttp ? View.VISIBLE : View.GONE;
            tvCloudAsrConfigLabel.setVisibility(cloudVisibility);
            etCloudAsrUrl.setVisibility(cloudVisibility);
            
            // Update FunASR config visibility
            int funasrVisibility = isFunasrWs ? View.VISIBLE : View.GONE;
            tvFunasrConfigLabel.setVisibility(funasrVisibility);
            etFunasrUrl.setVisibility(funasrVisibility);
            tvFunasrModeLabel.setVisibility(funasrVisibility);
            rgFunasrMode.setVisibility(funasrVisibility);
        };
        
        // Apply initial state
        updateBackendUI.accept(asrBackend);
        
        // Listen for ASR backend changes
        rgAsrBackend.setOnCheckedChangeListener((group, checkedId) -> {
            String backend = Constants.ASR_BACKEND_LOCAL;
            if (checkedId == R.id.rb_asr_cloud_http) {
                backend = Constants.ASR_BACKEND_CLOUD_HTTP;
            } else if (checkedId == R.id.rb_asr_funasr_ws) {
                backend = Constants.ASR_BACKEND_FUNASR_WS;
            }
            updateBackendUI.accept(backend);
        });
        
        builder.setView(view)
            .setTitle("配置设置")
            .setPositiveButton("保存", (dialog, which) -> {
                 String url = etIp.getText().toString().trim();
                 String[] serverParts = parseServerUrl(url);
                 String ip = serverParts[0];
                 int port;
                 try {
                     port = Integer.parseInt(serverParts[1]);
                 } catch (NumberFormatException e) {
                     port = Constants.DEFAULT_OPENCODE_PORT;
                 }
                
                // Determine selected model
                String selectedModel;
                if (rbModelOriginal.isChecked()) {
                    selectedModel = "ggml-tiny.en.bin";
                } else if (rbModelInt8.isChecked()) {
                    selectedModel = "ggml-tiny.en-q8_0.bin";
                } else {
                    selectedModel = "ggml-tiny.en-q5_1.bin";
                }
                
                // Get previous model to check if it changed
                String previousModel = getSharedPreferences("settings", MODE_PRIVATE).getString("whisper_model", Constants.DEFAULT_WHISPER_MODEL);
                boolean modelChanged = !selectedModel.equals(previousModel);
                boolean autoTestOnChange = cbAutoTest.isChecked();
                boolean autoSendOn = cbAutoSend.isChecked();
                
                // Determine selected ASR backend
                String newAsrBackend = Constants.ASR_BACKEND_LOCAL;
                if (rbAsrCloudHttp.isChecked()) {
                    newAsrBackend = Constants.ASR_BACKEND_CLOUD_HTTP;
                } else if (rbAsrFunasrWs.isChecked()) {
                    newAsrBackend = Constants.ASR_BACKEND_FUNASR_WS;
                }
                
                // Get URLs from input fields
                String newCloudAsrUrl = etCloudAsrUrl.getText().toString().trim();
                String newFunAsrUrl = etFunasrUrl.getText().toString().trim();
                String newFunAsrMode = rbFunasrMode2pass.isChecked() ? "2pass" : "offline";
                
                // Parse URLs to get host and port for backward compatibility
                String[] cloudAsrParts = parseAsrUrl(newCloudAsrUrl, "http");
                String[] funAsrParts = parseAsrUrl(newFunAsrUrl, "ws");

                // Save all settings
                getSharedPreferences("settings", MODE_PRIVATE)
                    .edit()
                    .putString("opencode_ip", ip)
                    .putInt("opencode_port", port)
                    .putString("whisper_model", selectedModel)
                    .putBoolean("auto_test_on_model_change", autoTestOnChange)
                    .putString("asr_backend", newAsrBackend)
                    .putString("cloud_asr_url", newCloudAsrUrl)
                    .putString("cloud_asr_ip", cloudAsrParts[0])
                    .putInt("cloud_asr_port", Integer.parseInt(cloudAsrParts[1]))
                    .putString("funasr_url", newFunAsrUrl)
                    .putString("funasr_host", funAsrParts[0])
                    .putInt("funasr_port", Integer.parseInt(funAsrParts[1]))
                    .putString("funasr_mode", newFunAsrMode)
                    .putBoolean(Constants.KEY_AUTO_SEND, autoSendOn)
                    .apply();
                
                // Update managers with new settings
                cloudAsrManager.updateSettings(cloudAsrParts[0], Integer.parseInt(cloudAsrParts[1]));
                funAsrManager.updateSettings(funAsrParts[0], Integer.parseInt(funAsrParts[1]), newFunAsrMode);
                
                // Reinitialize OpenCode with new settings (temporarily disabled)
                if (openCodeManager != null) {
                    openCodeManager.updateSettings(ip, port);
                }
                
                // Note: Page refresh removed - use menu "Refresh Page" option instead
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
                
                // Notify user about model change and reinitialize if needed
                if (modelChanged) {
                    Toast.makeText(this, 
                        "模型已更改为: " + selectedModel + "\n正在重新初始化...", 
                        Toast.LENGTH_LONG).show();
                    
                    // Disable recording while reinitializing
                    updateButtonState(ButtonState.DISABLED);

                    // Set transcription test flag based on auto test preference and backend type
                    // Auto test is only relevant for local backend
                    if (newAsrBackend.equals(Constants.ASR_BACKEND_LOCAL)) {
                        transcriptionTested = !autoTestOnChange;
                    } else {
                        transcriptionTested = true; // Skip test for non-local backends
                    }
                    
                    // Reinitialize Whisper with new model in background thread
                    new Thread(() -> {
                        try {
                            whisperManager.reinitialize(selectedModel);
                        } catch (Exception e) {
                            android.util.Log.e("MainActivity", "Failed to reinitialize model", e);
                            mainHandler.post(() -> {
                                Toast.makeText(this, 
                                    "模型重新初始化失败，请重启应用", 
                                    Toast.LENGTH_LONG).show();
                            });
                        }
                    }).start();
                } else {
                    Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
    
    /**
     * Run a test transcription using the sample JFK WAV file
     * This helps verify that Whisper is working correctly
     */
    private void runTranscriptionTest() {
        if (transcriptionTested) {
            return;
        }
        
        transcriptionTested = true;
        android.util.Log.d("MainActivity", "Starting automatic transcription test...");
        
        new Thread(() -> {
            try {
                // Copy test WAV file from assets to temp file
                String assetPath = "test/jfk.wav";
                InputStream is = getAssets().open(assetPath);
                
                File tempFile = new File(getCacheDir(), "test_jfk.wav");
                FileOutputStream fos = new FileOutputStream(tempFile);
                
                byte[] buffer = new byte[8192];
                int read;
                long total = 0;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                    total += read;
                }
                fos.close();
                is.close();
                
                android.util.Log.d("MainActivity", "Test WAV file copied, size: " + total + " bytes");
                android.util.Log.d("MainActivity", "Test file path: " + tempFile.getAbsolutePath());
                
                // Wait a bit to ensure Whisper is fully initialized
                Thread.sleep(1000);
                
                // Run transcription
                TranscriptionResult result = whisperManager.transcribe(tempFile);
                
                if (result != null && result.getText() != null && !result.getText().trim().isEmpty()) {
                    android.util.Log.d("MainActivity", "✓ Transcription test PASSED!");
                    android.util.Log.d("MainActivity", "Test transcription result: " + result.getText());
                    android.util.Log.d("MainActivity", "Performance: audio=" + String.format("%.2f", result.getAudioLengthSeconds()) + "s, " +
                          "processing=" + result.getProcessingTimeMs() + "ms, " +
                          "realtime factor=" + String.format("%.1f", result.getRealtimeFactor()) + "x");
                    
                    // Show a brief toast (optional)
                    mainHandler.post(() -> {
                        String toastText = "测试通过: " + result.getText().substring(0, Math.min(30, result.getText().length())) + "...\n" +
                                         "音频: " + String.format("%.2f", result.getAudioLengthSeconds()) + "s, " +
                                         "处理: " + result.getProcessingTimeMs() + "ms, " +
                                         "RTF: " + String.format("%.1f", result.getRealtimeFactor()) + "x";
                        Toast.makeText(this, toastText, Toast.LENGTH_LONG).show();
                    });
                } else {
                    android.util.Log.e("MainActivity", "✗ Transcription test FAILED: null or empty result");
                    
                    mainHandler.post(() -> {
                        Toast.makeText(this, "语音识别测试失败，请检查麦克风权限", 
                            Toast.LENGTH_LONG).show();
                    });
                }
                
                // Clean up
                tempFile.delete();
                
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Transcription test error", e);
                e.printStackTrace();
            }
        }).start();
    }
    
    // =================================================================
    // Attachment Upload Methods (Phase 1)
    // =================================================================
    
    /**
     * Open file picker for WebView file chooser
     */
    private void openFilePickerForWebView(WebChromeClient.FileChooserParams params) {
        android.util.Log.d("MainActivity", "Opening file picker for WebView");
        
        this.fileChooserParams = params;
        
        if (checkStoragePermission()) {
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
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                }
            }
        } else {
            requestStoragePermission();
            // Cancel the file chooser if no permission
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
                filePathCallback = null;
            }
        }
    }
    
    /**
     * Open file picker to select images or PDF files
     */
    private void openFilePicker() {
        android.util.Log.d("MainActivity", "Opening file picker");
        
        if (checkStoragePermission()) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            
            // Set MIME types accepted by OpenCode
            String[] mimeTypes = {
                "image/png", "image/jpeg", "image/gif", 
                "image/webp", "application/pdf"
            };
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            
            // Allow multiple selection
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            
            startActivityForResult(intent, REQUEST_FILE_PICKER);
        } else {
            requestStoragePermission();
        }
    }
    
    /**
     * Check storage permission based on Android version
     */
    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires READ_MEDIA_IMAGES permission
            return ContextCompat.checkSelfPermission(this, 
                Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12 requires READ_EXTERNAL_STORAGE permission
            return ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 10 and below
            return ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    /**
     * Request storage permission
     */
    private void requestStoragePermission() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES};
        } else {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        }
        
        ActivityCompat.requestPermissions(this, permissions, REQUEST_STORAGE_PERMISSION);
    }
    
    /**
     * Handle file picker result
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_FILE_PICKER && resultCode == RESULT_OK) {
            if (data == null) return;
            
            List<Uri> fileUris = new ArrayList<>();
            
            // Check if multiple files were selected
            if (data.getClipData() != null) {
                android.util.Log.d("MainActivity", "Multiple files selected");
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri fileUri = data.getClipData().getItemAt(i).getUri();
                    fileUris.add(fileUri);
                }
            } else if (data.getData() != null) {
                // Single file selected
                android.util.Log.d("MainActivity", "Single file selected");
                fileUris.add(data.getData());
            }
            
            if (!fileUris.isEmpty()) {
                processSelectedFiles(fileUris);
            } else {
                Toast.makeText(this, "未选择文件", Toast.LENGTH_SHORT).show();
            }
        }
        
        // Handle WebView file chooser result
        if (requestCode == REQUEST_WEBVIEW_FILE_CHOOSER) {
            if (filePathCallback != null) {
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
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        }
    }
    
    /**
     * Process selected files and inject them to WebView
     */
    private void processSelectedFiles(List<Uri> fileUris) {
        android.util.Log.d("MainActivity", "Processing " + fileUris.size() + " files");
        
        if (fileUris.isEmpty()) return;
        
        // Show processing message
        Toast.makeText(this, "正在处理 " + fileUris.size() + " 个文件...", 
            Toast.LENGTH_SHORT).show();
        
        // Process files sequentially to avoid overwhelming the system
        new Thread(() -> {
            for (Uri fileUri : fileUris) {
                processSingleFile(fileUri);
                
                // Small delay between files to avoid UI lag
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            
            // Show completion message
            mainHandler.post(() -> {
                Toast.makeText(MainActivity.this, 
                    "所有文件已添加", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }
    
    /**
     * Process a single file: read, convert to Base64, inject to WebView
     */
    private void processSingleFile(Uri fileUri) {
        android.util.Log.d("MainActivity", "Processing file: " + fileUri);
        
        try {
            // Get file name and MIME type
            String fileName = getFileNameFromUri(fileUri);
            String mimeType = getContentResolver().getType(fileUri);
            
            if (fileName == null || mimeType == null) {
                android.util.Log.e("MainActivity", "Failed to get file info for URI: " + fileUri);
                return;
            }
            
            // Check if file type is supported by OpenCode
            String[] acceptedTypes = {"image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf"};
            boolean isAccepted = false;
            for (String acceptedType : acceptedTypes) {
                if (acceptedType.equals(mimeType)) {
                    isAccepted = true;
                    break;
                }
            }
            
            if (!isAccepted) {
                android.util.Log.w("MainActivity", "Unsupported file type: " + mimeType);
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, 
                        "不支持的文件类型: " + mimeType, Toast.LENGTH_SHORT).show();
                });
                return;
            }
            
            // Convert file to Base64
            String base64Data = fileToBase64(fileUri);
            if (base64Data == null) {
                android.util.Log.e("MainActivity", "Failed to convert file to Base64: " + fileName);
                return;
            }
            
            // Inject to WebView on main thread
            final String finalFileName = fileName;
            final String finalMimeType = mimeType;
            final String finalBase64Data = base64Data;
            
            mainHandler.post(() -> {
                injectImageToWebView(finalBase64Data, finalFileName, finalMimeType);
            });
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error processing file: " + fileUri, e);
        }
    }
    
    /**
     * Convert file to Base64 string
     */
    private String fileToBase64(Uri fileUri) {
        try {
            // No file size limit - we use chunked transfer for large files
            InputStream inputStream = getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
                android.util.Log.e("MainActivity", "Failed to open input stream for URI: " + fileUri);
                return null;
            }
            
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
                
                // Check file size limit (10MB)
                if (totalBytes > 10 * 1024 * 1024) {
                    android.util.Log.w("MainActivity", "File too large, skipping: " + totalBytes + " bytes");
                    inputStream.close();
                    byteArrayOutputStream.close();
                    
                    mainHandler.post(() -> {
                        Toast.makeText(this, "文件过大（超过10MB），已跳过", Toast.LENGTH_SHORT).show();
                    });
                    return null;
                }
            }
            
            inputStream.close();
            byte[] fileBytes = byteArrayOutputStream.toByteArray();
            byteArrayOutputStream.close();
            
            android.util.Log.d("MainActivity", "File size: " + totalBytes + " bytes");
            String base64 = Base64.encodeToString(fileBytes, Base64.NO_WRAP);
            // Log first 100 chars of base64 for debugging
            if (base64 != null && base64.length() > 0) {
                int previewLength = Math.min(100, base64.length());
                android.util.Log.d("MainActivity", "Base64 preview (first " + previewLength + " chars): " + 
                    base64.substring(0, previewLength));
                android.util.Log.d("MainActivity", "Base64 total length: " + base64.length() + " chars");
            }
            return base64;
            
        } catch (IOException e) {
            android.util.Log.e("MainActivity", "Error reading file", e);
            return null;
        }
    }
    
    /**
     * Get file size from URI
     */
    private long getFileSize(Uri uri) {
        long size = 0;
        if ("content".equals(uri.getScheme())) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex);
                }
                cursor.close();
            }
        }
        return size;
    }
    
    /**
     * Get file name from URI
     */
    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        
        if ("content".equals(uri.getScheme())) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                fileName = cursor.getString(nameIndex);
                cursor.close();
            }
        }
        
        if (fileName == null) {
            fileName = uri.getPath();
            int cut = fileName.lastIndexOf('/');
            if (cut != -1) {
                fileName = fileName.substring(cut + 1);
            }
        }
        
        return fileName;
    }
    
    /**
     * Inject image to WebView by simulating paste event using chunked transfer
     * This method splits large Base64 data into smaller chunks to avoid WebView limitations
     */
    private void injectImageToWebView(String base64Data, String filename, String mimeType) {
        if (webView == null) {
            android.util.Log.e("MainActivity", "WebView is null");
            return;
        }
        
        android.util.Log.d("MainActivity", "Starting chunked injection for: " + filename);
        android.util.Log.d("MainActivity", "Total Base64 length: " + (base64Data != null ? base64Data.length() : 0) + " chars");
        
        // Step 1: Inject the receiver JavaScript that will handle chunked data
        injectChunkedReceiver(base64Data, filename, mimeType);
    }
    
    /**
     * Inject JavaScript receiver that can handle chunked Base64 data
     */
    private void injectChunkedReceiver(String base64Data, String filename, String mimeType) {
        final int totalLength = base64Data != null ? base64Data.length() : 0;
        final String jsFilename = escapeForJavaScript(filename);
        final String jsMimeType = escapeForJavaScript(mimeType);
        
        android.util.Log.d("MainActivity", "Step 1: Injecting simple receiver object...");
        
        // Step 1: Create minimal receiver object (very short JS)
        String step1Js = "if(!window.AndroidAttachmentReceiver){" +
            "window.AndroidAttachmentReceiver={chunks:[],receivedLength:0,addChunk:function(c){" +
            "this.chunks.push(c);this.receivedLength+=c.length;return this.receivedLength;}," +
            "getFullData:function(){return this.chunks.join('');}," +
            "isComplete:function(){return this.receivedLength>=this.totalLength;}," +
            "reset:function(){this.chunks=[];this.receivedLength=0;}" +
            "};'created'}else{'exists'}";
        
        webView.evaluateJavascript(step1Js, result1 -> {
            android.util.Log.d("MainActivity", "Step 1 result: " + result1);
            
            if (result1 == null || (!result1.contains("created") && !result1.contains("exists"))) {
                android.util.Log.e("MainActivity", "Failed to create receiver object: " + result1);
                Toast.makeText(MainActivity.this, "添加失败：无法创建接收器", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Step 2: Set metadata
            android.util.Log.d("MainActivity", "Step 2: Setting metadata...");
            String step2Js = "window.AndroidAttachmentReceiver.filename='" + jsFilename + "';" +
                "window.AndroidAttachmentReceiver.mimeType='" + jsMimeType + "';" +
                "window.AndroidAttachmentReceiver.totalLength=" + totalLength + ";" +
                "window.AndroidAttachmentReceiver.reset();'metadata-set'";
            
            webView.evaluateJavascript(step2Js, result2 -> {
                android.util.Log.d("MainActivity", "Step 2 result: " + result2);
                
                // Step 3: Inject processAttachment function separately
                android.util.Log.d("MainActivity", "Step 3: Injecting processAttachment function...");
                injectProcessAttachmentFunction(jsFilename, jsMimeType, () -> {
                    // Step 4: Start sending chunks
                    android.util.Log.d("MainActivity", "Step 4: Starting to send chunks...");
                    sendBase64Chunks(base64Data, filename, mimeType);
                });
            });
        });
    }
    
    /**
     * Inject the processAttachment function - simplified version
     */
    private void injectProcessAttachmentFunction(String jsFilename, String jsMimeType, Runnable onComplete) {
        // Simplified function - just the core logic
        String funcJs = "window.AndroidAttachmentReceiver.processAttachment=function(){" +
            "try{var d=this.getFullData();" +
            "var u='data:'+this.mimeType+';base64,'+d;" +
            "var a=u.split(',');" +
            "var m=a[0].match(/:(.*?);/)[1];" +
            "var b=atob(a[1]);" +
            "var n=b.length;" +
            "var x=new Uint8Array(n);" +
            "while(n--){x[n]=b.charCodeAt(n);}" +
            "var blob=new Blob([x],{type:m});" +
            "var file=new File([blob],this.filename,{type:this.mimeType});" +
            "var dt=new DataTransfer();" +
            "dt.items.add(file);" +
            "var input=document.querySelector('[data-component=prompt-input]');" +
            "if(input){input.focus();input.dispatchEvent(new ClipboardEvent('paste',{clipboardData:dt,bubbles:true}));}" +
            "if(window.AndroidVoiceAssist){window.AndroidVoiceAssist.onAttachmentReady(true,this.filename,'ok');}" +
            "return true;}catch(e){" +
            "if(window.AndroidVoiceAssist){window.AndroidVoiceAssist.onAttachmentReady(false,this.filename,e.message);}" +
            "return false;}};'done'";
        
        webView.evaluateJavascript(funcJs, result -> {
            android.util.Log.d("MainActivity", "Function injection result: " + result);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /**
     * Send Base64 data in chunks
     */
    private void sendBase64Chunks(String base64Data, String filename, String mimeType) {
        if (base64Data == null || base64Data.isEmpty()) {
            android.util.Log.e("MainActivity", "Base64 data is empty");
            return;
        }
        
        // Chunk size: 30000 characters (leaving room for JavaScript overhead)
        final int CHUNK_SIZE = 30000;
        final int totalLength = base64Data.length();
        final int totalChunks = (int) Math.ceil((double) totalLength / CHUNK_SIZE);
        
        android.util.Log.d("MainActivity", "Sending " + totalChunks + " chunks, total size: " + totalLength);
        
        // Send chunks sequentially using recursion to avoid overwhelming WebView
        sendChunkRecursive(base64Data, filename, mimeType, 0, CHUNK_SIZE, totalChunks);
    }
    
    /**
     * Recursively send chunks with delay to avoid overwhelming WebView
     */
    private void sendChunkRecursive(String base64Data, String filename, String mimeType, 
                                    int chunkIndex, int chunkSize, int totalChunks) {
        if (chunkIndex >= totalChunks) {
            // All chunks sent, trigger processing
            android.util.Log.d("MainActivity", "All chunks sent, triggering processing");
            triggerChunkedProcessing(filename);
            return;
        }
        
        int start = chunkIndex * chunkSize;
        int end = Math.min(start + chunkSize, base64Data.length());
        String chunk = base64Data.substring(start, end);
        
        // Escape the chunk for JavaScript
        String escapedChunk = chunk.replace("\\", "\\\\")
                                   .replace("'", "\\'")
                                   .replace("\"", "\\\"");
        
        String chunkJs = "(function() {" +
            "  try {" +
            "    if (window.AndroidAttachmentReceiver) {" +
            "      var received = window.AndroidAttachmentReceiver.addChunk('" + escapedChunk + "');" +
            "      return 'chunk-added:' + received;" +
            "    } else {" +
            "      return 'receiver-not-found';" +
            "    }" +
            "  } catch(e) {" +
            "    return 'error: ' + e.message;" +
            "  }" +
            "})();";
        
        webView.evaluateJavascript(chunkJs, result -> {
            android.util.Log.d("MainActivity", "Chunk " + (chunkIndex + 1) + "/" + totalChunks + " result: " + result);
            
            // Small delay before next chunk to allow WebView to process
            mainHandler.postDelayed(() -> {
                sendChunkRecursive(base64Data, filename, mimeType, chunkIndex + 1, chunkSize, totalChunks);
            }, 10); // 10ms delay between chunks
        });
    }
    
    /**
     * Trigger processing of all received chunks
     */
    private void triggerChunkedProcessing(String filename) {
        String processJs = "(function() {" +
            "  try {" +
            "    if (window.AndroidAttachmentReceiver) {" +
            "      if (window.AndroidAttachmentReceiver.isComplete()) {" +
            "        var result = window.AndroidAttachmentReceiver.processAttachment();" +
            "        return 'processing-result:' + result;" +
            "      } else {" +
            "        return 'incomplete-data';" +
            "      }" +
            "    } else {" +
            "      return 'receiver-not-found';" +
            "    }" +
            "  } catch(e) {" +
            "    console.error('[OpenCode] Processing error: ' + e.message);" +
            "    return 'error: ' + e.message;" +
            "  }" +
            "})();";
        
        webView.evaluateJavascript(processJs, result -> {
            android.util.Log.d("MainActivity", "Processing result: " + result);
        });
    }
    
    /**
     * Build JavaScript code to simulate paste event with image data
     */
    private String buildImageInjectionJs(String base64Data, String filename, String mimeType) {
        // Escape strings for JavaScript string literals
        String jsFilename = escapeForJavaScript(filename);
        String jsMimeType = escapeForJavaScript(mimeType);
        String jsBase64 = escapeForJavaScript(base64Data);
        
        // Debug: Log JavaScript code information
        android.util.Log.d("MainActivity", "buildImageInjectionJs called");
        android.util.Log.d("MainActivity", "Filename: " + filename + " -> js: " + 
            (jsFilename.length() > 50 ? jsFilename.substring(0, 50) + "..." : jsFilename));
        android.util.Log.d("MainActivity", "MIME type: " + mimeType + " -> js: " + jsMimeType);
        android.util.Log.d("MainActivity", "Base64 length: " + base64Data.length() + 
            " -> js length: " + jsBase64.length());
        if (jsBase64.length() > 0) {
            int previewLength = Math.min(100, jsBase64.length());
            android.util.Log.d("MainActivity", "Base64 preview (first " + previewLength + " chars): " + 
                jsBase64.substring(0, previewLength));
            if (jsBase64.length() > 100) {
                android.util.Log.d("MainActivity", "Base64 preview (last 100 chars): " + 
                    jsBase64.substring(jsBase64.length() - 100));
            }
        }
        
        return "(function() {" +
            "  console.log('[OpenCode] Starting image injection for: ' + '" + jsFilename + "');" +
            "  const result = { success: false, error: null, step: 'start' };" +
            "  " +
            "  try {" +
            "    // Step 1: Check if page is loaded" +
            "    if (!document.body || document.body.children.length === 0) {" +
            "      result.error = 'Document not fully loaded';" +
            "      result.step = 'document-check';" +
            "      console.error('[OpenCode] ' + result.error);" +
            "      return JSON.stringify(result);" +
            "    }" +
            "    " +
            "    // Step 2: Create Data URL" +
            "    result.step = 'creating-data-url';" +
            "    const dataUrl = 'data:' + '" + jsMimeType + ";base64,' + '" + jsBase64 + "';" +
            "    console.log('[OpenCode] Data URL created');" +
            "    " +
            "    // Step 3: Convert Data URL to Blob" +
            "    result.step = 'converting-to-blob';" +
            "    function dataURLtoBlob(dataurl) {" +
            "      const arr = dataurl.split(',');" +
            "      const mime = arr[0].match(/:(.*?);/)[1];" +
            "      const bstr = atob(arr[1]);" +
            "      let n = bstr.length;" +
            "      const u8arr = new Uint8Array(n);" +
            "      while (n--) {" +
            "        u8arr[n] = bstr.charCodeAt(n);" +
            "      }" +
            "      return new Blob([u8arr], { type: mime });" +
            "    }" +
            "    " +
            "    const blob = dataURLtoBlob(dataUrl);" +
            "    console.log('[OpenCode] Blob created, size: ' + blob.size + ' bytes');" +
            "    " +
            "    // Step 4: Create File object" +
            "    result.step = 'creating-file-object';" +
            "    const file = new File([blob], '" + jsFilename + "', { type: '" + jsMimeType + "' });" +
            "    console.log('[OpenCode] File created: ' + file.name);" +
            "    " +
            "    // Step 5: Create DataTransfer" +
            "    result.step = 'creating-datatransfer';" +
            "    const dataTransfer = new DataTransfer();" +
            "    dataTransfer.items.add(file);" +
            "    console.log('[OpenCode] DataTransfer created with file');" +
            "    " +
            "    // Step 6: Create paste event" +
            "    result.step = 'creating-paste-event';" +
            "    const pasteEvent = new ClipboardEvent('paste', {" +
            "      clipboardData: dataTransfer," +
            "      bubbles: true," +
            "      cancelable: true" +
            "    });" +
            "    console.log('[OpenCode] Paste event created');" +
            "    " +
            "    // Step 7: Find OpenCode input field" +
            "    result.step = 'finding-input-field';" +
            "    let promptInput = document.querySelector('[data-component=\"prompt-input\"]');" +
            "    if (!promptInput) {" +
            "      // Try alternative selectors" +
            "      const alternativeSelectors = [" +
            "        'textarea'," +
            "        'input[type=\"text\"]'," +
            "        '.prompt-input'," +
            "        '[role=\"textbox\"]'," +
            "        '.ProseMirror'," +
            "        '.chat-input'," +
            "        '.input-area'," +
            "        'div[contenteditable=\"true\"]'" +
            "      ];" +
            "      " +
            "      for (const selector of alternativeSelectors) {" +
            "        const element = document.querySelector(selector);" +
            "        if (element) {" +
            "          promptInput = element;" +
            "          console.log('[OpenCode] Found input using alternative selector: ' + selector);" +
            "          break;" +
            "        }" +
            "      }" +
            "    }" +
            "    " +
            "    if (!promptInput) {" +
            "      result.error = 'OpenCode input field not found. Please focus the input field first.';" +
            "      result.step = 'input-not-found';" +
            "      console.error('[OpenCode] ' + result.error);" +
            "      return JSON.stringify(result);" +
            "    }" +
            "    " +
            "    console.log('[OpenCode] Found input field');" +
            "    " +
            "    // Step 8: Focus input field first (important for paste to work)" +
            "    result.step = 'focusing-input';" +
            "    promptInput.focus();" +
            "    console.log('[OpenCode] Input field focused');" +
            "    " +
            "    // Step 9: Trigger paste event" +
            "    result.step = 'triggering-paste-event';" +
            "    promptInput.dispatchEvent(pasteEvent);" +
            "    console.log('[OpenCode] Paste event dispatched');" +
            "    " +
            "    result.success = true;" +
            "    result.step = 'completed';" +
            "    console.log('[OpenCode] Image injection successful');" +
            "    return JSON.stringify(result);" +
            "    " +
            "  } catch (error) {" +
            "    result.error = 'Exception: ' + error.toString() + '\\nStack: ' + (error.stack || 'no stack');" +
            "    result.step = 'exception';" +
            "    console.error('[OpenCode] Image injection failed:', error);" +
            "    return JSON.stringify(result);" +
            "  }" +
            "})();";
    }
    
    /**
     * Escape string for use in JavaScript string literal
     */
    private String escapeForJavaScript(String input) {
        if (input == null) return "";
        
        // Debug: log input characteristics
        if (input.length() > 1000) {
            android.util.Log.d("MainActivity", "escapeForJavaScript: input length=" + input.length());
            android.util.Log.d("MainActivity", "escapeForJavaScript: first 50 chars=" + 
                input.substring(0, Math.min(50, input.length())));
            android.util.Log.d("MainActivity", "escapeForJavaScript: last 50 chars=" + 
                input.substring(Math.max(0, input.length() - 50)));
            
            // Check for problematic characters
            int backslashCount = 0, singleQuoteCount = 0, doubleQuoteCount = 0, newlineCount = 0;
            for (int i = 0; i < Math.min(1000, input.length()); i++) {
                char c = input.charAt(i);
                if (c == '\\') backslashCount++;
                else if (c == '\'') singleQuoteCount++;
                else if (c == '"') doubleQuoteCount++;
                else if (c == '\n') newlineCount++;
            }
            android.util.Log.d("MainActivity", "escapeForJavaScript: problematic chars in first 1000: " +
                "\\=" + backslashCount + ", '=" + singleQuoteCount + ", \"=" + doubleQuoteCount + 
                ", \\n=" + newlineCount);
        }
        
        String result = input
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
            
        // Debug: log result characteristics
        if (input.length() > 1000) {
            android.util.Log.d("MainActivity", "escapeForJavaScript: result length=" + result.length());
            android.util.Log.d("MainActivity", "escapeForJavaScript: length increased by " + 
                (result.length() - input.length()));
        }
        
        return result;
    }
    
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
                updateButtonState(ButtonState.DISABLED);
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, open file picker
                openFilePicker();
            } else {
                if (permissions.length > 0) {
                    String deniedPermission = permissions[0];
                    // Check if user permanently denied the permission
                    boolean shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                        this, deniedPermission);
                    
                    if (!shouldShowRationale && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        // Permission permanently denied, guide user to settings
                        Toast.makeText(this, "存储权限被永久拒绝，请在设置中启用", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "需要存储权限才能选择文件", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "需要存储权限才能选择文件", Toast.LENGTH_SHORT).show();
                }
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
        fileManager.deleteTempWavFile();
    }
}
