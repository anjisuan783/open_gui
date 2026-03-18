package com.opencode.voiceassist.manager;

import com.opencode.voiceassist.model.TranscriptionResult;

import java.io.File;

public interface AsrEngine {
    interface AsrCallback {
        void onSuccess(TranscriptionResult result);
        void onError(String error);
    }
    
    void transcribe(File wavFile, AsrCallback callback);
    void transcribe(byte[] pcmData, AsrCallback callback);
    void cancel();
    void release();
}