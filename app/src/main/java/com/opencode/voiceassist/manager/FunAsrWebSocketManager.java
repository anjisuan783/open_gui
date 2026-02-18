package com.opencode.voiceassist.manager;

import android.content.Context;
import android.util.Log;

import com.opencode.voiceassist.model.TranscriptionResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class FunAsrWebSocketManager {
    private static final String TAG = "FunAsrWebSocketManager";
    
    private final Context context;
    private String serverHost;
    private int serverPort;
    private String mode; // "offline" or "2pass"
    private final OkHttpClient httpClient;
    
    private WebSocket webSocket;
    private TranscriptionCallback currentCallback;
    private File currentAudioFile;
    private long startTime;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    
    public interface TranscriptionCallback {
        void onSuccess(TranscriptionResult result);
        void onError(String error);
    }
    
    public FunAsrWebSocketManager(Context context, String host, int port, String mode) {
        this.context = context;
        // Clean host string: remove port if included
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
        // Clean host string: remove port if included
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
    
    private void connectIfNeeded(TranscriptionCallback callback) {
        if (webSocket != null && isConnected.get()) {
            return;
        }
        
        disconnect();
        
        // Connect to whisper.cpp WebSocket service
        String wsUrl = "ws://" + serverHost + ":" + serverPort;
        Log.d(TAG, "Connecting to Whisper WebSocket: " + wsUrl);
        
        Request request = new Request.Builder()
                .url(wsUrl)
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
                // FunASR typically sends text messages, not binary
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
        
        // Wait briefly for connection
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public void transcribe(File audioFile, TranscriptionCallback callback) {
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
                // Connect if needed
                connectIfNeeded(callback);
                
                // Give a moment for connection
                for (int i = 0; i < 10; i++) {
                    if (isConnected.get()) {
                        break;
                    }
                    Thread.sleep(100);
                }
                
                if (!isConnected.get()) {
                    callback.onError("WebSocket连接失败，请检查服务器地址和端口");
                    resetState();
                    return;
                }
                
                // Send initial JSON message (matching Python script)
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
                Log.d(TAG, "JSON bytes: " + initJson.toString().getBytes().length);
                
                // Extract and send PCM audio data
                byte[] pcmData = extractPcmFromWav(audioFile);
                if (pcmData == null || pcmData.length == 0) {
                    callback.onError("无法从WAV文件中提取PCM数据");
                    resetState();
                    return;
                }
                
                // Send entire PCM data as binary message (matching Python script)
                webSocket.send(ByteString.of(pcmData));
                Log.d(TAG, "Sent audio data: " + pcmData.length + " bytes");
                
                // Send end marker
                JSONObject endJson = new JSONObject();
                try {
                    endJson.put("is_speaking", false);
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to create end JSON", e);
                }
                webSocket.send(endJson.toString());
                Log.d(TAG, "Sent end JSON: " + endJson.toString());
                
                // Wait for final response with timeout
                int timeoutMs = 30000; // 30 second timeout
                long waitStart = System.currentTimeMillis();
                while (isProcessing.get() && 
                       System.currentTimeMillis() - waitStart < timeoutMs) {
                    Thread.sleep(100);
                }
                
                if (isProcessing.get()) {
                    callback.onError("转录超时");
                    resetState();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Transcription failed", e);
                callback.onError("转录失败: " + e.getMessage());
                resetState();
            }
        }).start();
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
            
            // For offline mode, treat any non-empty text as final result
            // For 2pass mode, wait for is_final=true
            boolean shouldTreatAsFinal = isFinal || 
                (responseMode.equals("offline") && !transcribedText.isEmpty());
            
            if (shouldTreatAsFinal) {
                // Calculate performance metrics
                long processingTime = System.currentTimeMillis() - startTime;
                
                // Estimate audio length from file size (16kHz, 16-bit mono)
                // WAV file has 44-byte header, then PCM data
                long fileSize = currentAudioFile.length();
                double audioLengthSeconds = (fileSize > 44) ? 
                    (fileSize - 44) / 32000.0 : 1.0; // Default 1 second if unknown
                
                double realtimeFactor = processingTime / 1000.0 / audioLengthSeconds;
                
                // If text is empty or whitespace only, show placeholder
                String displayText = transcribedText;
                if (transcribedText == null || transcribedText.trim().isEmpty()) {
                    displayText = "..."; // Placeholder to indicate server responded
                }
                
                Log.d(TAG, "Treating as final result: " + displayText + 
                      ", mode=" + responseMode + ", is_final=" + isFinal);
                
                TranscriptionResult result = new TranscriptionResult(
                        displayText, audioLengthSeconds, processingTime, realtimeFactor);
                
                currentCallback.onSuccess(result);
                resetState();
            } else if (!transcribedText.isEmpty()) {
                // Intermediate result - could be used for real-time display
                Log.d(TAG, "Intermediate result: " + transcribedText + 
                      ", mode=" + responseMode + ", is_final=" + isFinal);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse FunASR JSON response", e);
            // If not JSON, maybe it's plain text result
            if (!text.trim().isEmpty() && !text.startsWith("{") && !text.startsWith("[")) {
                // Might be direct text response
                long processingTime = System.currentTimeMillis() - startTime;
                double audioLengthSeconds = currentAudioFile.length() / 32000.0;
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
            // Skip WAV header (44 bytes for standard PCM WAV)
            byte[] header = new byte[44];
            int bytesRead = fis.read(header);
            if (bytesRead < 44) {
                Log.e(TAG, "WAV file too small");
                return null;
            }
            
            // Verify it's a valid WAV file (optional)
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') {
                Log.e(TAG, "Not a valid WAV file (missing RIFF header)");
            }
            
            // Read the rest as PCM data
            long pcmSize = wavFile.length() - 44;
            if (pcmSize > Integer.MAX_VALUE) {
                Log.e(TAG, "WAV file too large");
                return null;
            }
            
            byte[] pcmData = new byte[(int) pcmSize];
            int totalRead = 0;
            while (totalRead < pcmData.length) {
                int read = fis.read(pcmData, totalRead, pcmData.length - totalRead);
                if (read == -1) {
                    break;
                }
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
        // Disconnect WebSocket to ensure fresh connection for next transcription
        // FunASR server expects new connection for each session
        disconnect();
        Log.d(TAG, "State reset and WebSocket disconnected");
    }
}