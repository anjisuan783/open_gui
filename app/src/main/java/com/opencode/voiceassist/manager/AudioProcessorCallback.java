package com.opencode.voiceassist.manager;

public interface AudioProcessorCallback {
    void onAudioDataReady(byte[] pcmData);
    void onRecordingComplete();
    void onError(String error);
}