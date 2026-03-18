package com.opencode.voiceassist.utils;

import android.content.Context;

import java.io.File;

public class FileManager {
    
    private Context context;
    private File tempWavFile;
    
    public FileManager(Context context) {
        this.context = context;
        this.tempWavFile = new File(context.getCacheDir(), "temp_recording.wav");
    }
    
    public File getTempWavFile() {
        return tempWavFile;
    }
    
    public void deleteTempWavFile() {
        if (tempWavFile.exists()) {
            tempWavFile.delete();
        }
    }
}