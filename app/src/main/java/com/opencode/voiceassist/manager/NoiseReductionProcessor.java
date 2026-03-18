package com.opencode.voiceassist.manager;

import android.util.Log;

public class NoiseReductionProcessor implements AudioProcessor {
    private static final String TAG = "NoiseReductionProcessor";
    
    private AudioProcessorCallback callback;
    
    @Override
    public void setCallback(AudioProcessorCallback callback) {
        this.callback = callback;
    }
    
    @Override
    public void processAudio(byte[] pcmData) {
        // TODO: Add noise reduction processing here
        // For now, pass through directly (same as DirectProcessor)
        if (callback != null) {
            callback.onAudioDataReady(pcmData);
        }
    }
    
    @Override
    public void flush() {
        if (callback != null) {
            callback.onRecordingComplete();
        }
    }
    
    @Override
    public void release() {
        callback = null;
    }
    
    @Override
    public String getName() {
        return "NoiseReductionProcessor";
    }
}