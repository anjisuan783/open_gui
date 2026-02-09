package com.whispercppdemo.whisper;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class Whisper {
    
    private static final String TAG = "Whisper";
    private WhisperContext whisperContext;
    private String modelPath;
    private boolean useGpu;
    
    private Whisper(String modelPath, boolean useGpu) {
        this.modelPath = modelPath;
        this.useGpu = useGpu;
    }
    
    public String transcribe(String audioPath) {
        if (whisperContext == null) {
            Log.e(TAG, "Whisper context not initialized");
            return null;
        }
        
        try {
            Log.d(TAG, "Starting transcription for: " + audioPath);
            float[] audioData = WaveEncoder.decodeWaveFile(new File(audioPath));
            Log.d(TAG, "Audio data decoded, length: " + audioData.length + " samples");
            // Whisper expects 16kHz mono float samples, WaveEncoder already normalizes to [-1,1]
            String result = whisperContext.transcribeData(audioData);
            Log.d(TAG, "Transcription completed, result length: " + (result != null ? result.length() : "null"));
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Transcription failed", e);
            e.printStackTrace();
            return null;
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String modelPath;
        private boolean useGpu = false;
        
        public Builder setModelPath(String modelPath) {
            this.modelPath = modelPath;
            return this;
        }
        
        public Builder setUseGpu(boolean useGpu) {
            this.useGpu = useGpu;
            return this;
        }
        
        public Whisper build() {
            Whisper whisper = new Whisper(modelPath, useGpu);
            // Initialize WhisperContext here (load model)
            try {
                whisper.whisperContext = WhisperContext.createContextFromFile(modelPath, useGpu);
                Log.i(TAG, "Whisper model loaded: " + modelPath + ", GPU: " + useGpu);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load Whisper model", e);
                throw new RuntimeException("Failed to load Whisper model", e);
            }
            return whisper;
        }
    }
    
    public void release() {
        if (whisperContext != null) {
            try {
                whisperContext.release();
            } catch (Exception e) {
                Log.e(TAG, "Failed to release Whisper context", e);
            }
            whisperContext = null;
        }
    }
}