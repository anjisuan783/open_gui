package com.opencode.voiceassist.manager;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.opencode.voiceassist.model.TranscriptionResult;
import com.opencode.voiceassist.utils.Constants;
import com.opencode.voiceassist.utils.FileManager;
import com.opencode.voiceassist.utils.UrlUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;


public class RecordingManager {
    private static final String TAG = "RecordingManager";
    
    private final Activity activity;
    private final Handler mainHandler;
    private final RecordingCallback callback;
    
    // Manager instances
    private WhisperManager whisperManager;
    private AudioRecorder audioRecorder;
    private CloudAsrManager cloudAsrManager;
    private FunAsrWebSocketManager funAsrManager;
    private FileManager fileManager;
    
    // Recording state
    private boolean isRecording = false;
    private boolean isCancelled = false;
    private boolean isUserStoppedRecording = false;
    private float startY = 0;
    private static final float CANCEL_THRESHOLD_DP = 50;
    
    // UI references (to be set from MainActivity)
    private View recordButton;
    private View recordProgress;
    
    // Transcription test state
    private boolean transcriptionTested = true;
    
    public interface RecordingCallback {
        void onRecordingStateChanged(ButtonState state);
        void onTranscriptionComplete(TranscriptionResult result);
        void onTranscriptionError(String error);
        void onWhisperInitialized(boolean success, String message);
        void onOpenCodeInitialized(boolean success, String message);
    }
    
    public enum ButtonState {
        DEFAULT, RECORDING, CANCEL, PROCESSING, DISABLED
    }
    
    public RecordingManager(Activity activity, RecordingCallback callback) {
        this.activity = activity;
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public void setManagers(WhisperManager whisperManager, AudioRecorder audioRecorder,
                           CloudAsrManager cloudAsrManager, FunAsrWebSocketManager funAsrManager,
                           FileManager fileManager) {
        this.whisperManager = whisperManager;
        this.audioRecorder = audioRecorder;
        this.cloudAsrManager = cloudAsrManager;
        this.funAsrManager = funAsrManager;
        this.fileManager = fileManager;
    }
    
    public void setUiReferences(View recordButton, View recordProgress) {
        this.recordButton = recordButton;
        this.recordProgress = recordProgress;
    }
    
    public void setTranscriptionTested(boolean tested) {
        this.transcriptionTested = tested;
    }
    
    public boolean isRecording() {
        return isRecording;
    }
    
    public void setupRecordButton() {
        if (recordButton == null) {
            Log.e(TAG, "Record button not set");
            return;
        }
        
        recordButton.setOnTouchListener((v, event) -> {
            // Check if recording is allowed based on ASR backend
            String asrBackend = activity.getSharedPreferences("settings", Activity.MODE_PRIVATE)
                    .getString("asr_backend", Constants.DEFAULT_ASR_BACKEND);
            boolean requiresLocalWhisper = asrBackend.equals(Constants.ASR_BACKEND_LOCAL);
            
            if (requiresLocalWhisper && (whisperManager == null || !whisperManager.isModelLoaded())) {
                return false; // Button is disabled for local Whisper without model
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
                    // User intentionally released the button
                    if (isRecording) {
                        isUserStoppedRecording = true; // Mark as user-initiated stop
                        stopRecording();
                    }
                    return true;
                    
                case MotionEvent.ACTION_CANCEL:
                    // System cancelled (e.g., dialog shown, activity paused)
                    // Don't stop recording, let user continue
                    Log.d(TAG, "ACTION_CANCEL received - ignoring to protect recording");
                    return true;
            }
            return false;
        });
    }
    
    private void startRecording() {
        Log.d(TAG, "startRecording() called");
        isRecording = true;
        isCancelled = false;
        updateButtonState(ButtonState.RECORDING);
        
        File wavFile = fileManager.getTempWavFile();
        Log.d(TAG, "WAV file path: " + wavFile.getAbsolutePath());
        
        // Delete previous file if exists
        if (wavFile.exists()) {
            Log.d(TAG, "Deleting previous WAV file");
            wavFile.delete();
        }
        
        audioRecorder.startRecording(wavFile);
        Log.d(TAG, "AudioRecorder started");
    }
    
    public void stopRecording() {
        Log.d(TAG, "stopRecording() called");
        
        // Check if this is a real user stop or system interrupt
        if (!isUserStoppedRecording) {
            Log.d(TAG, "Recording stop blocked - not user initiated");
            // Reset the flag for next time
            isUserStoppedRecording = false;
            return;
        }
        
        isRecording = false;
        isUserStoppedRecording = false; // Reset flag
        
        if (isCancelled) {
            Log.d(TAG, "Recording was cancelled");
            audioRecorder.stopRecording();
            fileManager.deleteTempWavFile();
            mainHandler.post(() -> Toast.makeText(activity, "已取消录音", Toast.LENGTH_SHORT).show());
            updateButtonState(ButtonState.DEFAULT);
            return;
        }
        
        updateButtonState(ButtonState.PROCESSING);
        Log.d(TAG, "Stopping audio recorder...");
        audioRecorder.stopRecording();

        // Wait a bit for recorder to finish writing file
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Release recording resources immediately
        releaseRecordingResources();
        
        File wavFile = fileManager.getTempWavFile();
        Log.d(TAG, "Checking WAV file: " + wavFile.getAbsolutePath());
        Log.d(TAG, "File exists: " + wavFile.exists() + ", size: " + (wavFile.exists() ? wavFile.length() : 0) + " bytes");
        
        if (!wavFile.exists() || wavFile.length() == 0) {
            Log.e(TAG, "WAV file is empty or doesn't exist");
            mainHandler.post(() -> Toast.makeText(activity, "录音失败，请重试", Toast.LENGTH_SHORT).show());
            updateButtonState(ButtonState.DEFAULT);
            return;
        }
        
        // Verify WAV file format
        if (wavFile.length() < 44) {
            Log.e(TAG, "WAV file too small for header: " + wavFile.length() + " bytes");
            mainHandler.post(() -> Toast.makeText(activity, "录音文件格式错误", Toast.LENGTH_SHORT).show());
            updateButtonState(ButtonState.DEFAULT);
            return;
        }
        
        Log.d(TAG, "WAV file looks good, starting transcription...");
        
        // Get ASR backend setting
        String asrBackend = activity.getSharedPreferences("settings", Activity.MODE_PRIVATE)
                .getString("asr_backend", Constants.DEFAULT_ASR_BACKEND);
        
        Log.d(TAG, "Using ASR backend: " + asrBackend);
        
        if (asrBackend.equals(Constants.ASR_BACKEND_CLOUD_HTTP)) {
            // Use cloud ASR HTTP
            Log.d(TAG, "Using cloud HTTP ASR for transcription");
            cloudAsrManager.transcribe(wavFile, new CloudAsrManager.TranscriptionCallback() {
                @Override
                public void onSuccess(TranscriptionResult result) {
                    fileManager.deleteTempWavFile();
                    Log.d(TAG, "Cloud ASR result: " + result.getText());
                    mainHandler.post(() -> processTranscribedText(result));
                }
                
                @Override
                public void onError(String error) {
                    fileManager.deleteTempWavFile();
                    Log.e(TAG, "Cloud ASR error: " + error);
                    mainHandler.post(() -> {
                        Toast.makeText(activity, "云端ASR失败: " + error, Toast.LENGTH_SHORT).show();
                        updateButtonState(ButtonState.DEFAULT);
                        if (callback != null) {
                            callback.onTranscriptionError(error);
                        }
                    });
                }
            });
        } else if (asrBackend.equals(Constants.ASR_BACKEND_FUNASR_WS)) {
            // Use FunASR WebSocket
            Log.d(TAG, "Using FunASR WebSocket for transcription");
            funAsrManager.transcribe(wavFile, new FunAsrWebSocketManager.TranscriptionCallback() {
                @Override
                public void onSuccess(TranscriptionResult result) {
                    fileManager.deleteTempWavFile();
                    Log.d(TAG, "FunASR result: " + result.getText());
                    mainHandler.post(() -> processTranscribedText(result));
                }
                
                @Override
                public void onError(String error) {
                    fileManager.deleteTempWavFile();
                    Log.e(TAG, "FunASR error: " + error);
                    mainHandler.post(() -> {
                        Toast.makeText(activity, "FunASR失败: " + error, Toast.LENGTH_SHORT).show();
                        updateButtonState(ButtonState.DEFAULT);
                        if (callback != null) {
                            callback.onTranscriptionError(error);
                        }
                    });
                }
            });
        } else {
            // Use local Whisper ASR (default)
            Log.d(TAG, "Using local Whisper ASR for transcription");
            new Thread(() -> {
                Log.d(TAG, "Transcription thread started");
                TranscriptionResult result = whisperManager.transcribe(wavFile);
                fileManager.deleteTempWavFile();
                
                Log.d(TAG, "Transcription result: " + (result != null ? result.getText() : "null"));
                
                if (result != null && result.getText() != null && !result.getText().trim().isEmpty()) {
                    Log.d(TAG, "Transcription successful, processing text...");
                    Log.d(TAG, "Performance data: audio=" + String.format("%.2f", result.getAudioLengthSeconds()) + "s, " +
                          "processing=" + result.getProcessingTimeMs() + "ms, " +
                          "realtime factor=" + String.format("%.1f", result.getRealtimeFactor()) + "x");
                    mainHandler.post(() -> processTranscribedText(result));
                } else {
                    Log.e(TAG, "Transcription returned null or empty");
                    mainHandler.post(() -> {
                        Toast.makeText(activity, "语音识别失败，请重试", Toast.LENGTH_SHORT).show();
                        updateButtonState(ButtonState.DEFAULT);
                        if (callback != null) {
                            callback.onTranscriptionError("Transcription returned null or empty");
                        }
                    });
                }
            }).start();
        }
    }
    
    private void processTranscribedText(TranscriptionResult result) {
        String text = result.getText();
        Log.d(TAG, "Processing transcribed text: " + text);
        
        // Create user message with performance data
        String userMessageContent = text + "\n\n[语音长度: " + String.format(Locale.US, "%.2f", result.getAudioLengthSeconds()) + "秒, " +
                                   "处理耗时: " + result.getProcessingTimeMs() + "毫秒, " +
                                    "实时因子: " + String.format(Locale.US, "%.1f", result.getRealtimeFactor()) + "x]";
        
        updateButtonState(ButtonState.DEFAULT);
        
        if (callback != null) {
            callback.onTranscriptionComplete(result);
        }
    }
    
    public void updateButtonState(ButtonState state) {
        if (callback != null) {
            callback.onRecordingStateChanged(state);
        }
    }
    
    private float dpToPx(float dp) {
        return dp * activity.getResources().getDisplayMetrics().density;
    }
    
    public void onWhisperInitialized(boolean success, String message) {
        mainHandler.post(() -> {
            if (success) {
                // Check settings
                boolean autoTestEnabled = activity.getSharedPreferences("settings", Activity.MODE_PRIVATE)
                        .getBoolean("auto_test_on_model_change", true);
                String asrBackend = activity.getSharedPreferences("settings", Activity.MODE_PRIVATE)
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
                Toast.makeText(activity, asrMode + "录音功能已启用" + testInfo, Toast.LENGTH_SHORT).show();
                updateButtonState(ButtonState.DEFAULT);

                // Automatic transcription test to verify performance - only for local backend
                if (asrBackend.equals(Constants.ASR_BACKEND_LOCAL) && autoTestEnabled) {
                    mainHandler.postDelayed(() -> {
                        runTranscriptionTest();
                    }, 3000); // Wait 3 seconds for everything to settle
                }
            } else {
                // Only disable recording if using local Whisper backend
                String asrBackend = activity.getSharedPreferences("settings", Activity.MODE_PRIVATE)
                        .getString("asr_backend", Constants.DEFAULT_ASR_BACKEND);
                if (asrBackend.equals(Constants.ASR_BACKEND_LOCAL)) {
                    updateButtonState(ButtonState.DISABLED);
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                } else {
                    // Cloud ASR or FunASR - enable recording anyway
                    Toast.makeText(activity, "录音功能已启用（" + asrBackend + "）", Toast.LENGTH_SHORT).show();
                    updateButtonState(ButtonState.DEFAULT);
                }
            }
            
            if (callback != null) {
                callback.onWhisperInitialized(success, message);
            }
        });
    }
    
    public void onOpenCodeInitialized(boolean success, String message) {
        mainHandler.post(() -> {
            if (success) {
                Toast.makeText(activity, "OpenCode连接成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "OpenCode连接失败: " + message + "\n请点击右上角齿轮图标配置服务器地址", Toast.LENGTH_LONG).show();
            }
            
            if (callback != null) {
                callback.onOpenCodeInitialized(success, message);
            }
        });
    }
    
    /**
     * Run a test transcription using the sample JFK WAV file
     * This helps verify that Whisper is working correctly
     */
    public void runTranscriptionTest() {
        if (transcriptionTested) {
            return;
        }
        
        transcriptionTested = true;
        Log.d(TAG, "Starting automatic transcription test...");
        
        new Thread(() -> {
            try {
                // Copy test WAV file from assets to temp file
                String assetPath = "test/jfk.wav";
                InputStream is = activity.getAssets().open(assetPath);
                
                File tempFile = new File(activity.getCacheDir(), "test_jfk.wav");
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
                
                Log.d(TAG, "Test WAV file copied, size: " + total + " bytes");
                Log.d(TAG, "Test file path: " + tempFile.getAbsolutePath());
                
                // Wait a bit to ensure Whisper is fully initialized
                Thread.sleep(1000);
                
                // Run transcription
                TranscriptionResult result = whisperManager.transcribe(tempFile);
                
                if (result != null && result.getText() != null && !result.getText().trim().isEmpty()) {
                    Log.d(TAG, "✓ Transcription test PASSED!");
                    Log.d(TAG, "Test transcription result: " + result.getText());
                    Log.d(TAG, "Performance: audio=" + String.format(Locale.US, "%.2f", result.getAudioLengthSeconds()) + "s, " +
                           "processing=" + result.getProcessingTimeMs() + "ms, " +
                           "realtime factor=" + String.format(Locale.US, "%.1f", result.getRealtimeFactor()) + "x");
                    
                    // Show a brief toast (optional)
                    mainHandler.post(() -> {
                        String toastText = "测试通过: " + result.getText().substring(0, Math.min(30, result.getText().length())) + "...\n" +
                                          "音频: " + String.format(Locale.US, "%.2f", result.getAudioLengthSeconds()) + "s, " +
                                         "处理: " + result.getProcessingTimeMs() + "ms, " +
                                          "RTF: " + String.format(Locale.US, "%.1f", result.getRealtimeFactor()) + "x";
                        Toast.makeText(activity, toastText, Toast.LENGTH_LONG).show();
                    });
                } else {
                    Log.e(TAG, "✗ Transcription test FAILED: null or empty result");
                    
                    mainHandler.post(() -> {
                        Toast.makeText(activity, "语音识别测试失败，请检查麦克风权限", 
                            Toast.LENGTH_LONG).show();
                    });
                }
                
                // Clean up
                tempFile.delete();
                
            } catch (Exception e) {
                Log.e(TAG, "Transcription test error", e);
                e.printStackTrace();
            }
        }).start();
    }
    
    /**
     * Release recording resources immediately after recording stops.
     * This ensures microphone and WebSocket connections are freed quickly
     * for the next recording.
     */
    private void releaseRecordingResources() {
        Log.d(TAG, "Releasing recording resources...");

        // Release AudioRecorder resources immediately
        if (audioRecorder != null) {
            // AudioRecorder releases resources in its thread's finally block
            // We just need to wait for it to complete
            int waitCount = 0;
            while (audioRecorder.isRecording() && waitCount < 50) {
                try {
                    Thread.sleep(10);
                    waitCount++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            Log.d(TAG, "AudioRecorder released after " + waitCount + " waits");
        }

        // Disconnect FunASR WebSocket immediately
        if (funAsrManager != null) {
            funAsrManager.disconnect();
            Log.d(TAG, "FunASR WebSocket disconnected");
        }

        Log.d(TAG, "Recording resources released");
    }

    public void release() {
        if (audioRecorder != null) {
            audioRecorder.release();
        }
        if (whisperManager != null) {
            whisperManager.release();
        }
        if (fileManager != null) {
            fileManager.deleteTempWavFile();
        }
    }
}