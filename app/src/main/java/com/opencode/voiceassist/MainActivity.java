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
import com.opencode.voiceassist.utils.FileManager;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    private WebView webView;
    
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
        
        // Configure WebChromeClient for console logging
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                android.util.Log.d("MainActivity", "WebView console: " + consoleMessage.message() + 
                        " at " + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber());
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
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
