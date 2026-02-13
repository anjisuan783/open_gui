package com.opencode.voiceassist.manager;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ConsoleMessage;
import android.webkit.HttpAuthHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.opencode.voiceassist.utils.Constants;
import com.opencode.voiceassist.utils.UrlUtils;
import com.opencode.voiceassist.utils.WebViewTextInjector;

import java.io.File;
import java.util.Map;

import android.database.Cursor;
import android.provider.OpenableColumns;

public class WebViewManager {
    private static final String TAG = "WebViewManager";
    
    private final Activity activity;
    private final Handler mainHandler;
    private WebView webView;
    private WebViewTextInjector webViewInjector;
    private WebViewCallback callback;
    
    // File upload related fields
    private ValueCallback<Uri[]> filePathCallback;
    private WebChromeClient.FileChooserParams fileChooserParams;
    private boolean isCameraUploadPending = false;
    private Uri cameraUploadUri = null;
    
    // UI references (to be set from MainActivity)
    private View bottomContainer;
    private boolean isRecording = false;
    
    public interface WebViewCallback {
        void onLoginFailureDetected();
        void onFileUploadRequested(WebChromeClient.FileChooserParams params);
        void onCameraUploadPending(Uri photoUri);
        void onShowReloginDialog();
        void onInputFocusChanged(boolean hasFocus);
        void onPageLoadError(String description);
        void onAttachmentReady(boolean success, String filename, String message);
    }
    
    public WebViewManager(Activity activity, WebView webView, WebViewCallback callback) {
        this.activity = activity;
        this.webView = webView;
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public void setBottomContainer(View bottomContainer) {
        this.bottomContainer = bottomContainer;
    }
    
    public void setRecording(boolean recording) {
        this.isRecording = recording;
    }
    
    public void setCameraUploadPending(boolean pending, Uri uri) {
        Log.d(TAG, "setCameraUploadPending: " + pending + ", uri: " + uri + 
            " (current: " + isCameraUploadPending + ", " + cameraUploadUri + ")");
        this.isCameraUploadPending = pending;
        this.cameraUploadUri = uri;
    }
    
    public void configureWebView() {
        Log.d(TAG, "Configuring WebView...");
        
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
                Log.d(TAG, "HTTP authentication requested for: " + host + ", realm: " + realm);
                // Use hardcoded credentials for now
                handler.proceed("opencode_linaro_dev", "abcd@1234");
            }
            
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                Log.d(TAG, "Page started loading: " + url);
                super.onPageStarted(view, url, favicon);
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "Page finished loading: " + url);
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
                            Log.w(TAG, "Login failure detected on page");
                            if (callback != null) {
                                callback.onLoginFailureDetected();
                            }
                        }
                    });
                }, 500); // 500ms delay to ensure page is fully loaded
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "WebView error: " + errorCode + " - " + description + " for URL: " + failingUrl);
                super.onReceivedError(view, errorCode, description, failingUrl);
                if (callback != null) {
                    callback.onPageLoadError(description);
                }
            }
        });
        
        // Configure WebChromeClient for console logging and file upload
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG, "WebView console: " + consoleMessage.message() + 
                        " at " + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber());
                return true;
            }
            
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, 
                    FileChooserParams fileChooserParams) {
                Log.d(TAG, "WebView file chooser requested, callback: " + (callback != null ? "not null" : "NULL"));
                Log.d(TAG, "Camera upload pending: " + isCameraUploadPending + ", cameraUploadUri: " + cameraUploadUri);
                
                // Store the callback
                WebViewManager.this.filePathCallback = filePathCallback;
                WebViewManager.this.fileChooserParams = fileChooserParams;
                
                // Check if this is a camera upload request
                if (isCameraUploadPending && cameraUploadUri != null) {
                    Log.d(TAG, "Camera upload pending, returning photo URI: " + cameraUploadUri);
                    Uri photoUri = cameraUploadUri; // Save reference
                    filePathCallback.onReceiveValue(new Uri[]{photoUri});
                    isCameraUploadPending = false;
                    cameraUploadUri = null;
                    
                    // Schedule cleanup of temporary file after WebView processes it
                    cleanupTempCameraFile(photoUri);
                    return true;
                }
                
                // Notify MainActivity to handle file picker
                if (callback != null) {
                    Log.d(TAG, "Notifying MainActivity to handle file picker");
                    callback.onFileUploadRequested(fileChooserParams);
                } else {
                    Log.e(TAG, "Callback is null! Cannot handle file picker");
                }
                
                return true;
            }
        });
        
        // Register JavaScript interface for bidirectional communication
        webView.addJavascriptInterface(new JavaScriptInterface(), "AndroidVoiceAssist");
        
        // Initialize WebView text injector
        webViewInjector = new WebViewTextInjector(webView);
        
        Log.d(TAG, "WebView configuration completed");
    }
    
    /**
     * JavaScript interface for communication between WebView and Android
     */
    public class JavaScriptInterface {
        @android.webkit.JavascriptInterface
        public void showToast(String message) {
            Log.d(TAG, "JavaScript toast: " + message);
            mainHandler.post(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
        }
        
        @android.webkit.JavascriptInterface
        public void logToAndroid(String message) {
            Log.d(TAG, "JavaScript log: " + message);
        }
        
        @android.webkit.JavascriptInterface
        public String getLastTranscribedText() {
            // This will return the last transcribed text from speech recognition
            // To be implemented when we have a variable to store it
            return "";
        }
        
        @android.webkit.JavascriptInterface
        public void onInputFocus(boolean hasFocus) {
            Log.d(TAG, "Input focus changed: " + hasFocus);
            mainHandler.post(() -> {
                if (bottomContainer != null) {
                    if (hasFocus) {
                        // Hide record area when input is focused (keyboard shown)
                        bottomContainer.setVisibility(View.GONE);
                        Log.d(TAG, "Record area hidden (input focused)");
                    } else {
                        // Show record area when input loses focus (keyboard hidden)
                        bottomContainer.setVisibility(View.VISIBLE);
                        Log.d(TAG, "Record area shown (input blurred)");
                    }
                }
                if (callback != null) {
                    callback.onInputFocusChanged(hasFocus);
                }
            });
        }
        
        @android.webkit.JavascriptInterface
        public void injectAttachment(String base64Data, String filename, String mimeType) {
            Log.d(TAG, "JavaScriptInterface.injectAttachment called (Base64 method disabled): " + 
                filename + ", mimeType: " + mimeType + ", data length: " + 
                (base64Data != null ? base64Data.length() : 0));
            
            // Base64 injection method disabled - use camera photo upload instead
            mainHandler.post(() -> {
                Toast.makeText(activity, "Base64附件上传已禁用，请使用相机拍照上传", Toast.LENGTH_SHORT).show();
            });
        }
        
        @android.webkit.JavascriptInterface
        public void onAttachmentReady(boolean success, String filename, String message) {
            Log.d(TAG, "JavaScriptInterface.onAttachmentReady: success=" + 
                success + ", filename=" + filename + ", message=" + message);
            
            mainHandler.post(() -> {
                // Don't show toast if recording to avoid interrupting
                if (!isRecording) {
                    if (success) {
                        Toast.makeText(activity, "已添加: " + filename, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, "添加失败: " + filename + " - " + message, Toast.LENGTH_SHORT).show();
                    }
                }
                if (callback != null) {
                    callback.onAttachmentReady(success, filename, message);
                }
            });
        }
    }
    
    public void loadOpenCodePage() {
        loadOpenCodePage(false);
    }
    
    public void loadOpenCodePage(boolean bypassCache) {
        Log.d(TAG, "Loading OpenCode page..." + (bypassCache ? " (with cache bypass)" : ""));
        
        String defaultIp = Constants.DEFAULT_OPENCODE_IP;
        int defaultPort = Constants.DEFAULT_OPENCODE_PORT;
        Log.d(TAG, "Constants.DEFAULT_OPENCODE_IP = " + defaultIp);
        Log.d(TAG, "Constants.DEFAULT_OPENCODE_PORT = " + defaultPort);
        
        // Debug: list all settings keys
        android.content.SharedPreferences prefs = activity.getSharedPreferences("settings", Activity.MODE_PRIVATE);
        Map<String, ?> allPrefs = prefs.getAll();
        Log.d(TAG, "All settings keys: " + allPrefs.keySet());
        for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
            Log.d(TAG, "  " + entry.getKey() + " = " + entry.getValue());
        }
        
        String ip = prefs.getString("opencode_ip", defaultIp);
        int port = prefs.getInt("opencode_port", defaultPort);
        
        Log.d(TAG, "Retrieved IP: " + ip + ", Retrieved port: " + port);
        String url = "http://" + ip + ":" + port;
        if (bypassCache) {
            url = url + "?t=" + System.currentTimeMillis();
        }
        Log.d(TAG, "Loading URL: " + url);
        
        webView.loadUrl(url);
    }
    
    /**
     * Inject JavaScript helper functions and prepare for text injection
     */
    public void injectInputDetectionScript() {
        Log.d(TAG, "Injecting input detection script using WebViewTextInjector");
        
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
                        Log.d(TAG, "WebView injector test passed");
                    }
                    
                    @Override
                    public void onFailure(String error) {
                        Log.w(TAG, "WebView injector test failed (expected on empty page): " + error);
                    }
                    
                    @Override
                    public void onRetry(int attempt, int maxRetries) {
                        Log.d(TAG, "WebView injector retry: " + attempt + "/" + maxRetries);
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
            Log.d(TAG, "Focus listener injection result: " + result);
        });
    }
    
    /**
     * Inject transcribed text into OpenCode web page input field
     * Uses WebViewTextInjector for robust injection with retry logic
     */
    public void injectTranscribedText(String text) {
        Log.d(TAG, "Injecting transcribed text: " + text);
        
        if (webViewInjector == null) {
            Log.e(TAG, "WebViewInjector not initialized");
            mainHandler.post(() -> Toast.makeText(activity, "注入器未初始化", Toast.LENGTH_SHORT).show());
            return;
        }
        
        // Check auto-send setting
        final boolean autoSend = activity.getSharedPreferences("settings", Activity.MODE_PRIVATE)
                .getBoolean(Constants.KEY_AUTO_SEND, Constants.DEFAULT_AUTO_SEND);
        
        // Use the new injector with retry and error handling
        // If auto-send is enabled, don't set focus to avoid keyboard popup
        webViewInjector.injectText(text, !autoSend, new WebViewTextInjector.InjectionCallback() {
            @Override
            public void onSuccess(String injectedText) {
                mainHandler.post(() -> {
                    Log.i(TAG, "Text injection successful");
                    
                    if (autoSend) {
                        // Auto-send enabled, trigger send after a short delay
                        mainHandler.postDelayed(() -> {
                            webViewInjector.triggerSend(success -> {
                                if (success) {
                                    Log.i(TAG, "Message sent automatically");
                                } else {
                                    Log.w(TAG, "Auto-send failed");
                                }
                                // Notify MainActivity to update button state
                                // This should be handled by MainActivity via callback
                            });
                        }, 100); // Small delay to ensure text is properly set
                    } else {
                        // Auto-send disabled, just show toast
                        Toast.makeText(activity, "文本已注入", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onFailure(String error) {
                mainHandler.post(() -> {
                    Log.e(TAG, "Text injection failed: " + error);
                    Toast.makeText(activity, "注入失败: " + error, Toast.LENGTH_LONG).show();
                });
            }
            
            @Override
            public void onRetry(int attempt, int maxRetries) {
                Log.d(TAG, "Text injection retrying: " + attempt + "/" + maxRetries);
            }
        });
    }
    
    public void handleFileChooserResult(Uri[] results) {
        Log.d(TAG, "handleFileChooserResult called, filePathCallback: " + 
            (filePathCallback != null ? "not null" : "NULL") + ", results: " + 
            (results != null ? results.length + " files" : "null"));
        
        if (filePathCallback != null) {
            Log.d(TAG, "Calling filePathCallback.onReceiveValue with " + 
                (results != null ? results.length + " files" : "null result"));
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            fileChooserParams = null;
            Log.d(TAG, "Cleared filePathCallback and fileChooserParams");
        } else {
            Log.e(TAG, "filePathCallback is null! Cannot deliver file chooser result");
        }
    }
    
    public void clearWebViewData() {
        clearWebViewData(null);
    }
    
    public void clearWebViewData(Runnable onComplete) {
        Log.d(TAG, "Clearing all WebView data");
        
        // Clear cache
        webView.clearCache(true);
        webView.clearHistory();
        webView.clearFormData();
        
        // Clear JavaScript storage (localStorage, sessionStorage, cookies via JS)
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
                Log.d(TAG, "JS storage cleared: " + jsResult);
                
                // Clear cookies via CookieManager (including HttpOnly cookies)
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.removeAllCookies(value -> {
                    Log.d(TAG, "Cookies removed: " + value);
                    
                    // Clear WebView database
                    webView.clearCache(true);
                    activity.deleteDatabase("webview.db");
                    activity.deleteDatabase("webviewCache.db");
                    
                    // Call completion callback
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            }
        );
    }
    
    public void reloadPage() {
        Log.d(TAG, "Reloading WebView page");
        loadOpenCodePage(true);
    }
    
    private void cleanupTempCameraFile(Uri fileUri) {
        if (fileUri == null) return;
        
        // Check if this is a camera temp file (from cache directory)
        String uriPath = fileUri.toString();
        if (uriPath.contains("cache") || uriPath.contains("temp")) {
            mainHandler.postDelayed(() -> {
                try {
                    File file = new File(fileUri.getPath());
                    if (file.exists()) {
                        boolean deleted = file.delete();
                        Log.d(TAG, "Cleaned up temp camera file: " + 
                            file.getAbsolutePath() + " deleted=" + deleted);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to cleanup temp camera file", e);
                }
            }, 30000); // Delay 30 seconds to allow WebView to process
        }
    }
    
    public void injectProcessAttachmentFunction(String jsFilename, String jsMimeType, Runnable onComplete) {
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
            Log.d(TAG, "Function injection result: " + result);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }
    
    public void sendBase64Chunks(String base64Data, String filename, String mimeType) {
        if (base64Data == null || base64Data.isEmpty()) {
            Log.e(TAG, "Base64 data is empty");
            return;
        }
        
        // Chunk size: 30000 characters (leaving room for JavaScript overhead)
        final int CHUNK_SIZE = 30000;
        final int totalLength = base64Data.length();
        final int totalChunks = (int) Math.ceil((double) totalLength / CHUNK_SIZE);
        
        Log.d(TAG, "Sending " + totalChunks + " chunks, total size: " + totalLength);
        
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
            Log.d(TAG, "All chunks sent, triggering processing");
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
            Log.d(TAG, "Chunk " + (chunkIndex + 1) + "/" + totalChunks + " result: " + result);
            
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
            Log.d(TAG, "Processing result: " + result);
        });
    }
    
    public String buildImageInjectionJs(String base64Data, String filename, String mimeType) {
        // Escape strings for JavaScript string literals
        String jsFilename = escapeForJavaScript(filename);
        String jsMimeType = escapeForJavaScript(mimeType);
        String jsBase64 = escapeForJavaScript(base64Data);
        
        // Debug: Log JavaScript code information
        Log.d(TAG, "buildImageInjectionJs called");
        Log.d(TAG, "Filename: " + filename + " -> js: " + 
            (jsFilename.length() > 50 ? jsFilename.substring(0, 50) + "..." : jsFilename));
        Log.d(TAG, "MIME type: " + mimeType + " -> js: " + jsMimeType);
        Log.d(TAG, "Base64 length: " + base64Data.length() + 
            " -> js length: " + jsBase64.length());
        if (jsBase64.length() > 0) {
            int previewLength = Math.min(100, jsBase64.length());
            Log.d(TAG, "Base64 preview (first " + previewLength + " chars): " + 
                jsBase64.substring(0, previewLength));
            if (jsBase64.length() > 100) {
                Log.d(TAG, "Base64 preview (last 100 chars): " + 
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
    public String escapeForJavaScript(String input) {
        if (input == null) return "";
        
        // Debug: log input characteristics
        if (input.length() > 1000) {
            Log.d(TAG, "escapeForJavaScript: input length=" + input.length());
            Log.d(TAG, "escapeForJavaScript: first 50 chars=" + 
                input.substring(0, Math.min(50, input.length())));
            Log.d(TAG, "escapeForJavaScript: last 50 chars=" + 
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
            Log.d(TAG, "escapeForJavaScript: problematic chars in first 1000: " +
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
            Log.d(TAG, "escapeForJavaScript: result length=" + result.length());
            Log.d(TAG, "escapeForJavaScript: length increased by " + 
                (result.length() - input.length()));
        }
        
        return result;
    }
    
    public long getFileSize(Uri uri) {
        long size = 0;
        if ("content".equals(uri.getScheme())) {
            Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null);
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
    
    public String getFileNameFromUri(Uri uri) {
        String fileName = null;
        
        if ("content".equals(uri.getScheme())) {
            Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null);
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
}