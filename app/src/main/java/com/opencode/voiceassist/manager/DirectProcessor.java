package com.opencode.voiceassist.manager;

import android.util.Log;

public class DirectProcessor implements AudioProcessor {
    private static final String TAG = "DirectProcessor";
    
    private AudioProcessorCallback callback;
    
    @Override
    public void setCallback(AudioProcessorCallback callback) {
        this.callback = callback;
    }
    
    @Override
    public void processAudio(byte[] pcmData) {
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
        return "DirectProcessor";
    }
}