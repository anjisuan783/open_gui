package com.opencode.voiceassist.manager;

import android.content.Context;
import android.util.Log;

import com.opencode.voiceassist.model.TranscriptionResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class FunAsrWebSocketManager implements AsrEngine {
    private static final String TAG = "FunAsrWebSocketManager";
    
    private final Context context;
    private String serverHost;
    private int serverPort;
    private String mode;
    private final OkHttpClient httpClient;
    
    private WebSocket webSocket;
    private AsrCallback currentCallback;
    private File currentAudioFile;
    private long startTime;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    
    private ByteArrayOutputStream streamingBuffer;
    
    public FunAsrWebSocketManager(Context context, String host, int port, String mode) {
        this.context = context;
        if (host != null && host.contains(":")) {
            this.serverHost = host.split(":")[0];
        } else {
            this.serverHost = host;
        }
        this.serverPort = port;
        this.mode = mode;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    public void updateSettings(String host, int port, String mode) {
        if (host != null && host.contains(":")) {
            this.serverHost = host.split(":")[0];
        } else {
            this.serverHost = host;
        }
        this.serverPort = port;
        this.mode = mode;
        disconnect();
    }
    
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Normal closure");
            webSocket = null;
        }
        isConnected.set(false);
    }
    
    private void connectIfNeeded(AsrCallback callback) {
        if (webSocket != null && isConnected.get()) {
            return;
        }
        
        disconnect();
        
        String wsUrl = "ws://" + serverHost + ":" + serverPort;
        Log.d(TAG, "Connecting to FunASR WebSocket: " + wsUrl);
        
        Request request = new Request.Builder()
                .url(wsUrl)
                .addHeader("Sec-WebSocket-Protocol", "binary")
                .build();
        
        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "WebSocket connection opened");
                isConnected.set(true);
            }
            
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "Received text message: " + text);
                handleTextMessage(text);
            }
            
            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                Log.d(TAG, "Received binary message: " + bytes.size() + " bytes");
            }
            
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket connection failed", t);
                if (response != null) {
                    Log.d(TAG, "Response code: " + response.code() + ", message: " + response.message());
                }
                isConnected.set(false);
                if (currentCallback != null && isProcessing.get()) {
                    currentCallback.onError("WebSocket连接失败: " + t.getMessage());
                    resetState();
                }
            }
            
            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket connection closed: " + code + " - " + reason);
                isConnected.set(false);
            }
        });
        
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Override
    public void transcribe(File audioFile, AsrCallback callback) {
        if (audioFile == null || !audioFile.exists()) {
            callback.onError("音频文件不存在");
            return;
        }
        
        if (isProcessing.get()) {
            callback.onError("已有转录正在进行");
            return;
        }
        
        this.currentAudioFile = audioFile;
        this.currentCallback = callback;
        this.startTime = System.currentTimeMillis();
        isProcessing.set(true);
        
        new Thread(() -> {
            try {
                connectIfNeeded(callback);
                
                for (int i = 0; i < 10; i++) {
                    if (isConnected.get()) break;
                    Thread.sleep(100);
                }
                
                if (!isConnected.get()) {
                    if (isProcessing.get()) {
                        callback.onError("WebSocket连接失败，请检查服务器地址和端口");
                        resetState();
                    }
                    return;
                }
                
                String testMode = "offline";
                JSONObject initJson = new JSONObject();
                try {
                    initJson.put("reqid", "app_" + System.currentTimeMillis());
                    initJson.put("mode", testMode);
                    initJson.put("wav_name", audioFile.getName());
                    initJson.put("is_speaking", true);
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to create initial JSON", e);
                }
                
                webSocket.send(initJson.toString());
                Log.d(TAG, "Sent initial JSON (offline mode): " + initJson.toString());
                
                byte[] pcmData = extractPcmFromWav(audioFile);
                if (pcmData == null || pcmData.length == 0) {
                    if (isProcessing.get()) {
                        callback.onError("无法从WAV文件中提取PCM数据");
                        resetState();
                    }
                    return;
                }
                
                webSocket.send(ByteString.of(pcmData));
                Log.d(TAG, "Sent audio data: " + pcmData.length + " bytes");
                
                JSONObject endJson = new JSONObject();
                try {
                    endJson.put("is_speaking", false);
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to create end JSON", e);
                }
                webSocket.send(endJson.toString());
                Log.d(TAG, "Sent end JSON: " + endJson.toString());
                
                int timeoutMs = 30000;
                long waitStart = System.currentTimeMillis();
                while (isProcessing.get() && System.currentTimeMillis() - waitStart < timeoutMs) {
                    Thread.sleep(100);
                }
                
                if (isProcessing.get()) {
                    callback.onError("转录超时");
                    resetState();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Transcription failed", e);
                if (isProcessing.get()) {
                    callback.onError("转录失败: " + e.getMessage());
                    resetState();
                }
            }
        }).start();
    }
    
    @Override
    public void transcribe(byte[] pcmData, AsrCallback callback) {
        if (pcmData == null || pcmData.length == 0) {
            callback.onError("音频数据为空");
            return;
        }
        
        if (isProcessing.get()) {
            callback.onError("已有转录正在进行");
            return;
        }
        
        this.currentCallback = callback;
        this.startTime = System.currentTimeMillis();
        isProcessing.set(true);
        
        new Thread(() -> {
            try {
                connectIfNeeded(callback);
                
                for (int i = 0; i < 10; i++) {
                    if (isConnected.get()) break;
                    Thread.sleep(100);
                }
                
                if (!isConnected.get()) {
                    if (isProcessing.get()) {
                        callback.onError("WebSocket连接失败");
                        resetState();
                    }
                    return;
                }
                
                String testMode = "offline";
                JSONObject initJson = new JSONObject();
                try {
                    initJson.put("reqid", "app_" + System.currentTimeMillis());
                    initJson.put("mode", testMode);
                    initJson.put("wav_name", "streaming.pcm");
                    initJson.put("is_speaking", true);
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to create initial JSON", e);
                }
                
                webSocket.send(initJson.toString());
                Log.d(TAG, "Sent initial JSON for streaming");
                
                webSocket.send(ByteString.of(pcmData));
                Log.d(TAG, "Sent PCM data: " + pcmData.length + " bytes");
                
                JSONObject endJson = new JSONObject();
                try {
                    endJson.put("is_speaking", false);
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to create end JSON", e);
                }
                webSocket.send(endJson.toString());
                Log.d(TAG, "Sent end JSON");
                
                int timeoutMs = 30000;
                long waitStart = System.currentTimeMillis();
                while (isProcessing.get() && System.currentTimeMillis() - waitStart < timeoutMs) {
                    Thread.sleep(100);
                }
                
                if (isProcessing.get()) {
                    callback.onError("转录超时");
                    resetState();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Transcription failed", e);
                if (isProcessing.get()) {
                    callback.onError("转录失败: " + e.getMessage());
                    resetState();
                }
            }
        }).start();
    }
    
    @Override
    public void cancel() {
        if (isProcessing.get()) {
            Log.d(TAG, "Cancelling current transcription");
            if (webSocket != null) {
                webSocket.close(1000, "Transcription cancelled by user");
                webSocket = null;
            }
            isConnected.set(false);
            
            if (currentCallback != null) {
                AsrCallback callback = currentCallback;
                currentCallback = null;
                callback.onError("转录被取消");
            }
            
            resetState();
        }
    }
    
    @Override
    public void release() {
        cancel();
        disconnect();
        streamingBuffer = null;
    }
    
    private void handleTextMessage(String text) {
        if (!isProcessing.get() || currentCallback == null) {
            Log.d(TAG, "Received message but not processing, ignoring: " + text);
            return;
        }
        
        try {
            JSONObject json = new JSONObject(text);
            boolean isFinal = json.optBoolean("is_final", false);
            String transcribedText = json.optString("text", "");
            String responseMode = json.optString("mode", "");
            
            Log.d(TAG, "Received FunASR response: is_final=" + isFinal + 
                    ", mode=" + responseMode + ", text=" + transcribedText);
            
            boolean shouldTreatAsFinal = isFinal || 
                    (responseMode.equals("offline") && !transcribedText.isEmpty());
            
            if (shouldTreatAsFinal) {
                long processingTime = System.currentTimeMillis() - startTime;
                
                double audioLengthSeconds = 1.0;
                if (currentAudioFile != null && currentAudioFile.length() > 44) {
                    audioLengthSeconds = (currentAudioFile.length() - 44) / 32000.0;
                }
                
                double realtimeFactor = processingTime / 1000.0 / audioLengthSeconds;
                
                String displayText = transcribedText;
                if (transcribedText == null || transcribedText.trim().isEmpty()) {
                    displayText = "...";
                }
                
                TranscriptionResult result = new TranscriptionResult(
                        displayText, audioLengthSeconds, processingTime, realtimeFactor);
                
                currentCallback.onSuccess(result);
                resetState();
            } else if (!transcribedText.isEmpty()) {
                Log.d(TAG, "Intermediate result: " + transcribedText);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse FunASR JSON response", e);
            if (!text.trim().isEmpty() && !text.startsWith("{") && !text.startsWith("[")) {
                long processingTime = System.currentTimeMillis() - startTime;
                double audioLengthSeconds = 1.0;
                double realtimeFactor = processingTime / 1000.0 / audioLengthSeconds;
                
                TranscriptionResult result = new TranscriptionResult(
                        text.trim(), audioLengthSeconds, processingTime, realtimeFactor);
                
                currentCallback.onSuccess(result);
                resetState();
            }
        }
    }
    
    private byte[] extractPcmFromWav(File wavFile) {
        try (FileInputStream fis = new FileInputStream(wavFile)) {
            byte[] header = new byte[44];
            int bytesRead = fis.read(header);
            if (bytesRead < 44) {
                Log.e(TAG, "WAV file too small");
                return null;
            }
            
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') {
                Log.e(TAG, "Not a valid WAV file (missing RIFF header)");
            }
            
            long pcmSize = wavFile.length() - 44;
            if (pcmSize > Integer.MAX_VALUE) {
                Log.e(TAG, "WAV file too large");
                return null;
            }
            
            byte[] pcmData = new byte[(int) pcmSize];
            int totalRead = 0;
            while (totalRead < pcmData.length) {
                int read = fis.read(pcmData, totalRead, pcmData.length - totalRead);
                if (read == -1) break;
                totalRead += read;
            }
            
            Log.d(TAG, "Extracted " + totalRead + " bytes of PCM data from WAV file");
            return pcmData;
        } catch (IOException e) {
            Log.e(TAG, "Failed to read WAV file", e);
            return null;
        }
    }
    
    private void resetState() {
        isProcessing.set(false);
        currentCallback = null;
        currentAudioFile = null;
        disconnect();
        Log.d(TAG, "State reset and WebSocket disconnected");
    }
}