package com.opencode.voiceassist.manager;

public interface AudioProcessor {
    void setCallback(AudioProcessorCallback callback);
    void processAudio(byte[] pcmData);
    void flush();
    void release();
    String getName();
}