package com.opencode.voiceassist.utils;

import android.content.Context;

import java.io.File;

public class FileManager {
    
    private Context context;
    private File whisperDir;
    private File tempWavFile;
    
    public FileManager(Context context) {
        this.context = context;
        this.whisperDir = new File(context.getFilesDir(), "whisper");
        if (!whisperDir.exists()) {
            whisperDir.mkdirs();
        }
        this.tempWavFile = new File(context.getCacheDir(), "temp_recording.wav");
    }
    
    /**
     * Get the model file path
     * Supports manual placement method 2: /Android/data/package/files/whisper/
     */
    public File getModelFile() {
        return new File(whisperDir, Constants.WHISPER_MODEL_FILENAME);
    }
    
    /**
     * Check if model file exists and is valid
     */
    public boolean isModelValid() {
        File modelFile = getModelFile();
        if (!modelFile.exists()) {
            return false;
        }
        // Basic size check (tiny.en ~75MB)
        long minSize = 50 * 1024 * 1024;
        return modelFile.length() >= minSize;
    }
    
    /**
     * Get temporary WAV file for recording
     */
    public File getTempWavFile() {
        return tempWavFile;
    }
    
    /**
     * Delete temporary WAV file
     */
    public void deleteTempWavFile() {
        if (tempWavFile.exists()) {
            tempWavFile.delete();
        }
    }
    
    /**
     * Get Whisper model directory path
     */
    public File getWhisperDir() {
        return whisperDir;
    }
    
    /**
     * Get the full path for manual placement (for display purposes)
     */
    public String getModelPathForManualPlacement() {
        return whisperDir.getAbsolutePath() + "/" + Constants.WHISPER_MODEL_FILENAME;
    }
}
