package com.opencode.voiceassist.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * WebView 文本注入管理器 - 简化版
 * 提供稳健的 JavaScript 注入功能
 */
public class WebViewTextInjector {
    private static final String TAG = "WebViewTextInjector";
    
    public interface InjectionCallback {
        void onSuccess(String text);
        void onFailure(String error);
        void onRetry(int attempt, int maxRetries);
    }
    
    private final WebView webView;
    private final Handler mainHandler;
    
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 500;
    
    public WebViewTextInjector(WebView webView) {
        this.webView = webView;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public void injectText(String text, InjectionCallback callback) {
        injectWithRetry(text, true, 0, callback);
    }
    
    public void injectText(String text, boolean setFocus, InjectionCallback callback) {
        injectWithRetry(text, setFocus, 0, callback);
    }
    
    private void injectWithRetry(String text, boolean setFocus, int attempt, InjectionCallback callback) {
        if (attempt > 0 && callback != null) {
            callback.onRetry(attempt, MAX_RETRIES);
        }
        
        performInjection(text, setFocus, success -> {
            if (success) {
                Log.i(TAG, "Text injected successfully");
                if (callback != null) {
                    callback.onSuccess(text);
                }
            } else {
                if (attempt < MAX_RETRIES) {
                    Log.w(TAG, "Injection failed, retrying... (attempt " + (attempt + 1) + "/" + MAX_RETRIES + ")");
                    mainHandler.postDelayed(() -> injectWithRetry(text, setFocus, attempt + 1, callback), RETRY_DELAY_MS);
                } else {
                    Log.e(TAG, "Injection failed after max retries");
                    if (callback != null) {
                        callback.onFailure("注入失败");
                    }
                }
            }
        });
    }
    
    private void performInjection(String text, boolean setFocus, InjectionResultCallback callback) {
        // 使用 JSON 安全地转义文本
        String jsonText = JSONObject.quote(text);
        
        // 简化版 JavaScript，避免复杂语法
        String jsCode = "(function(){" +
            "try{" +
            "var input=document.querySelector('[data-component=\"prompt-input\"]');" +
            "if(!input)return false;" +
            "input.innerHTML='';" +
            "var text=" + jsonText + ";" +
            "var lines=text.split('\\n');" +
            "for(var i=0;i<lines.length;i++){" +
            "if(lines[i])input.appendChild(document.createTextNode(lines[i]));" +
            "if(i<lines.length-1)input.appendChild(document.createElement('br'));" +
            "}" +
            "input.dispatchEvent(new Event('input',{bubbles:true}));" +
            "input.dispatchEvent(new Event('change',{bubbles:true}));" +
            (setFocus ? "input.focus();" : "") +
            "return true;" +
            "}catch(e){" +
            "console.error('Injection error:',e);" +
            "return false;" +
            "}" +
            "})();";
        
        webView.evaluateJavascript(jsCode, result -> {
            boolean success = "true".equals(result);
            callback.onResult(success);
        });
    }
    
    /**
     * 预注入 JavaScript 函数到页面
     */
    public void injectHelperFunctions() {
        // 简化的帮助函数
        String jsCode = "(function(){" +
            "window.perfectTextInjection=function(text){" +
            "try{" +
            "var input=document.querySelector('[data-component=\"prompt-input\"]');" +
            "if(!input)return false;" +
            "input.innerHTML='';" +
            "var lines=text.split('\\n');" +
            "for(var i=0;i<lines.length;i++){" +
            "if(lines[i])input.appendChild(document.createTextNode(lines[i]));" +
            "if(i<lines.length-1)input.appendChild(document.createElement('br'));" +
            "}" +
            "input.dispatchEvent(new Event('input',{bubbles:true}));" +
            "input.dispatchEvent(new Event('change',{bubbles:true}));" +
            "input.focus();" +
            "return true;" +
            "}catch(e){" +
            "console.error('Injection error:',e);" +
            "return false;" +
            "}" +
            "};" +
            "console.log('WebViewTextInjector: Helper functions injected');" +
            "})();";
        
        webView.evaluateJavascript(jsCode, null);
    }
    
    /**
     * 触发发送操作（模拟回车键）
     */
    public void triggerSend(SendResultCallback callback) {
        String jsCode = "(function(){" +
            "try{" +
            "var input=document.querySelector('[data-component=\"prompt-input\"]');" +
            "if(!input)return false;" +
            "var event=new KeyboardEvent('keydown',{" +
            "key:'Enter'," +
            "code:'Enter'," +
            "keyCode:13," +
            "which:13," +
            "bubbles:true," +
            "cancelable:true" +
            "});" +
            "input.dispatchEvent(event);" +
            "return true;" +
            "}catch(e){" +
            "console.error('Send trigger error:',e);" +
            "return false;" +
            "}" +
            "})();";
        
        webView.evaluateJavascript(jsCode, result -> {
            boolean success = "true".equals(result);
            if (callback != null) {
                callback.onResult(success);
            }
        });
    }
    
    private interface InjectionResultCallback {
        void onResult(boolean success);
    }
    
    public interface SendResultCallback {
        void onResult(boolean success);
    }
}
