package com.opencode.voiceassist.manager;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.opencode.voiceassist.model.TranscriptionResult;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CloudAsrManager implements AsrEngine {
    private static final String TAG = "CloudAsrManager";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    private final Context context;
    private String serverIp;
    private int serverPort;
    private final OkHttpClient httpClient;
    
    private Call currentCall;
    
    public CloudAsrManager(Context context, String ip, int port) {
        this.context = context;
        this.serverIp = ip;
        this.serverPort = port;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    public void updateSettings(String ip, int port) {
        this.serverIp = ip;
        this.serverPort = port;
    }
    
    @Override
    public void transcribe(File audioFile, AsrCallback callback) {
        if (audioFile == null || !audioFile.exists()) {
            Log.e(TAG, "Audio file is null or does not exist");
            callback.onError("音频文件不存在");
            return;
        }
        
        String url = "http://" + serverIp + ":" + serverPort + "/api/asr";
        Log.d(TAG, "=== Cloud ASR Request ===");
        Log.d(TAG, "URL: " + url);
        Log.d(TAG, "Audio file: " + audioFile.getAbsolutePath());
        Log.d(TAG, "Audio file size: " + audioFile.length() + " bytes");
        
        new Thread(() -> {
            try {
                byte[] audioBytes = readFile(audioFile);
                Log.d(TAG, "Read audio bytes: " + audioBytes.length);
                
                if (audioBytes.length < 44) {
                    Log.e(TAG, "Audio data too small for WAV file");
                    callback.onError("音频数据太小");
                    return;
                }
                
                String wavBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP);
                Log.d(TAG, "Base64 encoded size: " + wavBase64.length() + " chars");
                Log.d(TAG, "Base64 preview: " + wavBase64.substring(0, Math.min(50, wavBase64.length())) + "...");
                
                JSONObject payload = new JSONObject();
                payload.put("wav_base64", wavBase64);
                
                String jsonStr = payload.toString();
                Log.d(TAG, "JSON payload size: " + jsonStr.length() + " chars");
                
                RequestBody requestBody = RequestBody.create(jsonStr, JSON);
                
                Request request = new Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build();
                
                long startTime = System.currentTimeMillis();
                
                currentCall = httpClient.newCall(request);
                
                Log.d(TAG, "Executing HTTP request...");
                Response response = currentCall.execute();
                long processingTime = System.currentTimeMillis() - startTime;
                Log.d(TAG, "Response received in " + processingTime + "ms");
                
                if (currentCall.isCanceled()) {
                    Log.d(TAG, "Cloud ASR request was cancelled");
                    return;
                }
                
                Log.d(TAG, "Response code: " + response.code());
                Log.d(TAG, "Response headers: " + response.headers());
                
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "Cloud ASR returned error: " + response.code() + " - " + errorBody);
                    callback.onError("云端ASR返回错误: " + response.code());
                    return;
                }
                
                String responseBody = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "=== Cloud ASR Response ===");
                Log.d(TAG, "Body length: " + responseBody.length());
                Log.d(TAG, "Body: " + responseBody);
                
                String text = parseTranscriptionResponse(responseBody);
                Log.d(TAG, "Parsed text: " + text);
                
                if (text != null && !text.isEmpty()) {
                    double audioLengthSeconds = audioFile.length() / 32000.0;
                    double realtimeFactor = processingTime / 1000.0 / audioLengthSeconds;
                    
                    TranscriptionResult result = new TranscriptionResult(
                            text, audioLengthSeconds, processingTime, realtimeFactor);
                    Log.d(TAG, "Success! Text: " + text);
                    callback.onSuccess(result);
                } else {
                    Log.e(TAG, "Empty transcription result");
                    callback.onError("云端ASR返回空结果");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Cloud ASR request failed", e);
                callback.onError("云端ASR请求失败: " + e.getMessage());
            }
        }).start();
    }
    
    @Override
    public void transcribe(byte[] pcmData, AsrCallback callback) {
        callback.onError("云端ASR暂不支持流式传输");
    }
    
    @Override
    public void cancel() {
        if (currentCall != null && !currentCall.isCanceled()) {
            Log.d(TAG, "Cancelling current Cloud ASR request");
            currentCall.cancel();
            currentCall = null;
        }
    }
    
    @Override
    public void release() {
        cancel();
    }
    
    private byte[] readFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = fis.read(data);
            if (read != data.length) {
                Log.w(TAG, "File read " + read + " bytes, expected " + data.length);
            }
            return data;
        }
    }
    
    private String parseTranscriptionResponse(String json) {
        try {
            JSONObject result = new JSONObject(json);
            
            int code = result.optInt("code", -1);
            if (code != 0 && code != 200) {
                String error = result.optString("error", "unknown error");
                Log.e(TAG, "ASR returned error code: " + code + ", error: " + error);
                return "";
            }
            
            String text = result.optString("text", "");
            if (text.isEmpty()) {
                text = result.optString("results", "");
            }
            if (text.isEmpty()) {
                text = result.optString("result", "");
            }
            
            return text;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse transcription response", e);
            return "";
        }
    }
}