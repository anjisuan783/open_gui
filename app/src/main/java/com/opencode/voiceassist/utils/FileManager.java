package com.opencode.voiceassist.utils;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileManager {
    
    private static final String TAG = "FileManager";
    
    private Context context;
    private File tempWavFile;
    private File recordingsDir;
    
    public FileManager(Context context) {
        this.context = context;
        this.tempWavFile = new File(context.getCacheDir(), "temp_recording.wav");
        this.recordingsDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recordings");
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs();
        }
    }
    
    public File getTempWavFile() {
        return tempWavFile;
    }
    
    public void deleteTempWavFile() {
        if (tempWavFile.exists()) {
            tempWavFile.delete();
        }
    }
    
    public File saveRecordingCopy(boolean withNoiseSuppression) {
        if (!tempWavFile.exists()) {
            android.util.Log.w(TAG, "Temp WAV file does not exist");
            return null;
        }
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String suffix = withNoiseSuppression ? "_NS_on" : "_NS_off";
        String filename = "recording_" + timestamp + suffix + ".wav";
        File destFile = new File(recordingsDir, filename);
        
        try {
            copyFile(tempWavFile, destFile);
            android.util.Log.d(TAG, "Saved recording copy to: " + destFile.getAbsolutePath());
            return destFile;
        } catch (IOException e) {
            android.util.Log.e(TAG, "Failed to save recording copy: " + e.getMessage());
            return null;
        }
    }
    
    private void copyFile(File src, File dst) throws IOException {
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
    }
    
    public File getRecordingsDir() {
        return recordingsDir;
    }
    
    public String getRecordingsDirPath() {
        return recordingsDir.getAbsolutePath();
    }
}