package com.opencode.voiceassist.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.opencode.voiceassist.utils.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OpenCodeManager {
    
    private static final String TAG = "OpenCodeManager";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private Context context;
    private OkHttpClient client;
    private String sessionId;
    private String baseUrl;
    private InitializationCallback initCallback;
    
    public interface ResponseCallback {
        void onResponse(String response);
        void onError(String error);
    }
    
    public interface InitializationCallback {
        void onInitialized(boolean success, String message);
    }
    
    public OpenCodeManager(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
        
        updateBaseUrl();
    }
    
    public void setInitializationCallback(InitializationCallback callback) {
        this.initCallback = callback;
    }
    
    public void updateSettings(String ip, int port) {
        SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        prefs.edit()
            .putString("opencode_ip", ip)
            .putInt("opencode_port", port)
            .apply();
        
        updateBaseUrl();
        // Reinitialize session with new settings
        initializeSession();
    }
    
    private void updateBaseUrl() {
        SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String ip = prefs.getString("opencode_ip", Constants.DEFAULT_OPENCODE_IP);
        int port = prefs.getInt("opencode_port", Constants.DEFAULT_OPENCODE_PORT);
        baseUrl = "http://" + ip + ":" + port;
    }
    
    public void initializeSession() {
        // Try to create a new session
        createSession();
    }
    
    private void createSession() {
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("name", "voice-assistant-session");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        Request request = new Request.Builder()
            .url(baseUrl + "/api/sessions")
            .post(RequestBody.create(requestBody.toString(), JSON))
            .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String error = "Failed to create session: " + e.getMessage();
                Log.e(TAG, error);
                if (initCallback != null) {
                    initCallback.onInitialized(false, error);
                }
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String body = response.body().string();
                        JSONObject json = new JSONObject(body);
                        sessionId = json.optString("id");
                        Log.d(TAG, "Session created: " + sessionId);
                        if (initCallback != null) {
                            initCallback.onInitialized(true, "OpenCode连接成功");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        if (initCallback != null) {
                            initCallback.onInitialized(false, "JSON解析错误: " + e.getMessage());
                        }
                    }
                } else {
                    String error = "Session creation failed: " + response.code();
                    Log.e(TAG, error);
                    if (initCallback != null) {
                        initCallback.onInitialized(false, error);
                    }
                }
            }
        });
    }
    
    public void sendMessage(String message, ResponseCallback callback) {
        Log.d(TAG, "Sending message to OpenCode: " + message + ", sessionId: " + sessionId);
        if (sessionId == null) {
            // Try to create session first
            Log.d(TAG, "No session ID, creating session first");
            createSession();
            callback.onError("OpenCode连接失败，请点击右上角齿轮图标配置服务器地址");
            return;
        }
        
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("message", message);
            requestBody.put("session_id", sessionId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        Request request = new Request.Builder()
            .url(baseUrl + "/api/messages")
            .post(RequestBody.create(requestBody.toString(), JSON))
            .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("网络请求失败: " + e.getMessage() + "\n请检查OpenCode服务器地址和端口配置");
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d(TAG, "OpenCode response received, code: " + response.code());
                if (response.isSuccessful()) {
                    try {
                        String body = response.body().string();
                        Log.d(TAG, "OpenCode response body: " + body);
                        JSONObject json = new JSONObject(body);
                        String content = json.optString("content", body);
                        callback.onResponse(content);
                    } catch (JSONException e) {
                        Log.d(TAG, "JSON parse error, returning raw body");
                        callback.onResponse(response.body().string());
                    }
                } else {
                    String error = "服务器错误: " + response.code();
                    Log.d(TAG, error);
                    callback.onError(error);
                }
            }
        });
    }
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public boolean isSessionActive() {
        return sessionId != null;
    }
}
