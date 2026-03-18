package com.opencode.voiceassist.manager;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.opencode.voiceassist.model.TranscriptionResult;
import com.opencode.voiceassist.utils.Constants;
import com.opencode.voiceassist.utils.FileManager;

import java.io.File;
import java.util.Locale;

public class RecordingManager implements AudioProcessorCallback {
    private static final String TAG = "RecordingManager";
    
    private final Activity activity;
    private final Handler mainHandler;
    private final RecordingCallback callback;
    
    private AudioRecorder audioRecorder;
    private FileManager fileManager;
    
    private AsrEngine currentAsrEngine;
    private AudioProcessor audioProcessor;
    
    private boolean isRecording = false;
    private boolean isCancelled = false;
    private boolean isUserStoppedRecording = false;
    private float startY = 0;
    private static final float CANCEL_THRESHOLD_DP = 50;
    
    private View recordButton;
    private View recordProgress;
    
    public interface RecordingCallback {
        void onRecordingStateChanged(ButtonState state);
        void onTranscriptionComplete(TranscriptionResult result);
        void onTranscriptionError(String error);
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
    
    public void setManagers(AudioRecorder audioRecorder, FileManager fileManager) {
        this.audioRecorder = audioRecorder;
        this.fileManager = fileManager;
    }
    
    public void setAsrEngine(AsrEngine asrEngine) {
        this.currentAsrEngine = asrEngine;
    }
    
    public void setAudioProcessor(AudioProcessor processor) {
        this.audioProcessor = processor;
        if (audioProcessor != null) {
            audioProcessor.setCallback(this);
        }
    }
    
    public void setUiReferences(View recordButton, View recordProgress) {
        this.recordButton = recordButton;
        this.recordProgress = recordProgress;
    }
    
    public boolean isRecording() {
        return isRecording;
    }
    
    public void cancelOngoingTasks() {
        Log.d(TAG, "Cancelling ongoing transcription tasks");
        
        if (currentAsrEngine != null) {
            currentAsrEngine.cancel();
        }
        
        Log.d(TAG, "Ongoing transcription tasks cancelled");
    }
    
    public void setupRecordButton() {
        if (recordButton == null) {
            Log.e(TAG, "Record button not set");
            return;
        }
        
        recordButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startY = event.getY();
                    cancelOngoingTasks();
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
                    if (isRecording) {
                        isUserStoppedRecording = true;
                        stopRecording();
                    }
                    return true;
                    
                case MotionEvent.ACTION_CANCEL:
                    Log.d(TAG, "ACTION_CANCEL received - ignoring to protect recording");
                    return true;
            }
            return false;
        });
    }
    
    private void startRecording() {
        Log.d(TAG, "startRecording() called");
        
        cancelOngoingTasks();
        
        isRecording = true;
        isCancelled = false;
        updateButtonState(ButtonState.RECORDING);
        
        File wavFile = fileManager.getTempWavFile();
        Log.d(TAG, "WAV file path: " + wavFile.getAbsolutePath());
        
        if (wavFile.exists()) {
            Log.d(TAG, "Deleting previous WAV file");
            wavFile.delete();
        }
        
        if (audioProcessor != null) {
            audioRecorder.setAudioProcessor(audioProcessor);
        }
        
        audioRecorder.startRecording(wavFile);
        Log.d(TAG, "AudioRecorder started");
    }
    
    public void stopRecording() {
        Log.d(TAG, "stopRecording() called");
        
        if (!isUserStoppedRecording) {
            Log.d(TAG, "Recording stop blocked - not user initiated");
            isUserStoppedRecording = false;
            return;
        }
        
        isRecording = false;
        isUserStoppedRecording = false;
        
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
        
        // Wait for recorder to finish writing file
        int waitCount = 0;
        while (!audioRecorder.isReady() && waitCount < 50) {
            try {
                Thread.sleep(100);
                waitCount++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        Log.d(TAG, "AudioRecorder stopped after " + waitCount + " waits");
        
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
        
        if (wavFile.length() < 44) {
            Log.e(TAG, "WAV file too small for header: " + wavFile.length() + " bytes");
            mainHandler.post(() -> Toast.makeText(activity, "录音文件格式错误", Toast.LENGTH_SHORT).show());
            updateButtonState(ButtonState.DEFAULT);
            return;
        }
        
        Log.d(TAG, "WAV file looks good, starting transcription...");
        
        startTranscription(wavFile);
    }
    
    private void startTranscription(File wavFile) {
        if (currentAsrEngine == null) {
            Log.e(TAG, "No ASR engine configured");
            mainHandler.post(() -> {
                Toast.makeText(activity, "未配置ASR引擎", Toast.LENGTH_SHORT).show();
                updateButtonState(ButtonState.DEFAULT);
            });
            fileManager.deleteTempWavFile();
            return;
        }
        
        currentAsrEngine.transcribe(wavFile, new AsrEngine.AsrCallback() {
            @Override
            public void onSuccess(TranscriptionResult result) {
                fileManager.deleteTempWavFile();
                Log.d(TAG, "ASR result: " + result.getText());
                mainHandler.post(() -> processTranscribedText(result));
            }
            
            @Override
            public void onError(String error) {
                fileManager.deleteTempWavFile();
                Log.e(TAG, "ASR error: " + error);
                mainHandler.post(() -> {
                    Toast.makeText(activity, "语音识别失败: " + error, Toast.LENGTH_SHORT).show();
                    updateButtonState(ButtonState.DEFAULT);
                    if (callback != null) {
                        callback.onTranscriptionError(error);
                    }
                });
            }
        });
    }
    
    private void processTranscribedText(TranscriptionResult result) {
        String text = result.getText();
        Log.d(TAG, "Processing transcribed text: " + text);
        
        updateButtonState(ButtonState.DEFAULT);
        
        if (callback != null) {
            callback.onTranscriptionComplete(result);
        }
    }
    
    @Override
    public void onAudioDataReady(byte[] pcmData) {
        Log.d(TAG, "Audio data ready: " + pcmData.length + " bytes");
    }
    
    @Override
    public void onRecordingComplete() {
        Log.d(TAG, "Recording complete (from AudioProcessor)");
    }
    
    @Override
    public void onError(String error) {
        Log.e(TAG, "AudioProcessor error: " + error);
    }
    
    public void updateButtonState(ButtonState state) {
        if (callback != null) {
            callback.onRecordingStateChanged(state);
        }
    }
    
    private float dpToPx(float dp) {
        return dp * activity.getResources().getDisplayMetrics().density;
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
    
    private void releaseRecordingResources() {
        Log.d(TAG, "Releasing recording resources...");

        if (audioRecorder != null) {
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

        Log.d(TAG, "Recording resources released");
    }

    public void release() {
        if (audioRecorder != null) {
            audioRecorder.release();
        }
        if (audioProcessor != null) {
            audioProcessor.release();
        }
        if (currentAsrEngine != null) {
            currentAsrEngine.release();
        }
        if (fileManager != null) {
            fileManager.deleteTempWavFile();
        }
    }
}