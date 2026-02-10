package com.opencode.voiceassist.manager;

import android.content.Context;
import android.util.Log;

import com.opencode.voiceassist.model.TranscriptionResult;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CloudAsrManager {
    private static final String TAG = "CloudAsrManager";
    private static final MediaType AUDIO_WAV = MediaType.parse("audio/wav");
    
    private final Context context;
    private String serverIp;
    private int serverPort;
    private final OkHttpClient httpClient;
    
    public interface TranscriptionCallback {
        void onSuccess(TranscriptionResult result);
        void onError(String error);
    }
    
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
    
    public void transcribe(File audioFile, TranscriptionCallback callback) {
        if (audioFile == null || !audioFile.exists()) {
            callback.onError("音频文件不存在");
            return;
        }
        
        String url = "http://" + serverIp + ":" + serverPort + "/transcribe";
        Log.d(TAG, "Sending audio to cloud ASR: " + url);
        
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("audio", audioFile.getName(),
                        RequestBody.create(AUDIO_WAV, audioFile))
                .build();
        
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        
        long startTime = System.currentTimeMillis();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Cloud ASR request failed", e);
                callback.onError("云端ASR请求失败: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                long processingTime = System.currentTimeMillis() - startTime;
                
                if (!response.isSuccessful()) {
                    callback.onError("云端ASR返回错误: " + response.code());
                    return;
                }
                
                String responseBody = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "Cloud ASR response: " + responseBody);
                
                // Parse response - assuming JSON format: {"text": "transcribed text"}
                String text = parseTranscriptionResponse(responseBody);
                
                if (text != null && !text.isEmpty()) {
                    // Estimate audio length (16kHz, 16bit, mono)
                    double audioLengthSeconds = audioFile.length() / 32000.0;
                    double realtimeFactor = processingTime / 1000.0 / audioLengthSeconds;
                    
                    TranscriptionResult result = new TranscriptionResult(
                            text, audioLengthSeconds, processingTime, realtimeFactor);
                    callback.onSuccess(result);
                } else {
                    callback.onError("云端ASR返回空结果");
                }
            }
        });
    }
    
    private String parseTranscriptionResponse(String json) {
        try {
            // Simple JSON parsing - look for "text" field
            int textStart = json.indexOf("\"text\"");
            if (textStart == -1) {
                // Try alternative field names
                textStart = json.indexOf("\"result\"");
            }
            if (textStart == -1) {
                textStart = json.indexOf("\"transcript\"");
            }
            
            if (textStart != -1) {
                int colonIndex = json.indexOf(":", textStart);
                if (colonIndex != -1) {
                    int quoteStart = json.indexOf("\"", colonIndex);
                    if (quoteStart != -1) {
                        int quoteEnd = json.indexOf("\"", quoteStart + 1);
                        if (quoteEnd != -1) {
                            return json.substring(quoteStart + 1, quoteEnd);
                        }
                    }
                }
            }
            
            // If no JSON parsing worked, return the raw response
            return json.trim();
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse transcription response", e);
            return json.trim();
        }
    }
}
