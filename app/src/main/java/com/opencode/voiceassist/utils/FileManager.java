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
     * Uses the specified model filename
     */
    public File getModelFile(String modelFilename) {
        return new File(whisperDir, modelFilename);
    }
    
    /**
     * Get the model file path using default model (backward compatibility)
     */
    public File getModelFile() {
        return getModelFile(Constants.DEFAULT_WHISPER_MODEL);
    }
    
    /**
     * Check if model file exists and is valid
     * @param modelFilename the model filename to check
     */
    public boolean isModelValid(String modelFilename) {
        File modelFile = getModelFile(modelFilename);
        if (!modelFile.exists()) {
            return false;
        }
        // Basic size check (tiny.en ~75MB)
        long minSize = 30 * 1024 * 1024; // Reduced for smaller quantized models
        return modelFile.length() >= minSize;
    }
    
    /**
     * Check if default model file exists and is valid (backward compatibility)
     */
    public boolean isModelValid() {
        return isModelValid(Constants.DEFAULT_WHISPER_MODEL);
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
     * @param modelFilename the model filename
     */
    public String getModelPathForManualPlacement(String modelFilename) {
        return whisperDir.getAbsolutePath() + "/" + modelFilename;
    }
    
    /**
     * Get the full path for manual placement using default model (backward compatibility)
     */
    public String getModelPathForManualPlacement() {
        return getModelPathForManualPlacement(Constants.DEFAULT_WHISPER_MODEL);
    }
}
