package com.whispercppdemo.whisper;

import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.InputStream;

public class WhisperLib {
    private static final String LOG_TAG = "LibWhisper";
    private static boolean libraryLoaded = false;
    private static UnsatisfiedLinkError loadError = null;

    static {
        Log.d(LOG_TAG, "Primary ABI: " + Build.SUPPORTED_ABIS[0]);
        Log.d(LOG_TAG, "Supported ABIs: " + java.util.Arrays.toString(Build.SUPPORTED_ABIS));
        boolean loadVfpv4 = false;
        boolean loadV8fp16 = false;
        if (Utils.isArmEabiV7a()) {
            String cpuInfo = Utils.cpuInfo();
            if (cpuInfo != null) {
                Log.d(LOG_TAG, "CPU info: " + cpuInfo);
                if (cpuInfo.contains("vfpv4")) {
                    Log.d(LOG_TAG, "CPU supports vfpv4");
                    loadVfpv4 = true;
                }
            }
        } else if (Utils.isArmEabiV8a()) {
            String cpuInfo = Utils.cpuInfo();
            if (cpuInfo != null) {
                Log.d(LOG_TAG, "CPU info: " + cpuInfo);
                if (cpuInfo.contains("fphp")) {
                    Log.d(LOG_TAG, "CPU supports fp16 arithmetic");
                    loadV8fp16 = true;
                }
            }
        }

        try {
            // Try loading all possible library variants in order
            String[] libraryVariants = {"whisper", "whisper_v8fp16_va", "whisper_vfpv4"};
            boolean loaded = false;
            
            for (String libName : libraryVariants) {
                try {
                    Log.d(LOG_TAG, "Attempting to load library: " + libName);
                    System.loadLibrary(libName);
                    Log.d(LOG_TAG, "Successfully loaded library: " + libName);
                    libraryLoaded = true;
                    loaded = true;
                    break;
                } catch (UnsatisfiedLinkError e) {
                    Log.e(LOG_TAG, "Failed to load library " + libName + ": " + e.getMessage(), e);
                    // Continue to next variant
                }
            }
            
            if (!loaded) {
                // If no variant loaded successfully, try the base name again with full path
                Log.e(LOG_TAG, "All library variants failed to load");
                loadError = new UnsatisfiedLinkError("All whisper library variants failed to load");
                // Don't throw - let the app continue without native library
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Unexpected error loading native library", e);
            loadError = new UnsatisfiedLinkError("Failed to load library: " + e.getMessage());
            // Don't throw - let the app continue without native library
        }
        
        Log.d(LOG_TAG, "Library loading completed. libraryLoaded=" + libraryLoaded);
    }
    
    public static boolean isLibraryLoaded() {
        return libraryLoaded;
    }
    
    public static void checkLibraryLoaded() {
        if (!libraryLoaded) {
            if (loadError != null) {
                throw loadError;
            } else {
                throw new UnsatisfiedLinkError("Native library not loaded");
            }
        }
    }

    public static native long initContextFromInputStream(InputStream inputStream);
    public static native long initContextFromAsset(AssetManager assetManager, String assetPath);
    public static native long initContext(String modelPath);
    public static native void freeContext(long contextPtr);
    public static native void fullTranscribe(long contextPtr, int numThreads, float[] audioData);
    public static native int getTextSegmentCount(long contextPtr);
    public static native String getTextSegment(long contextPtr, int index);
    public static native String getSystemInfo();
    public static native String benchMemcpy(int nthread);
    public static native String benchGgmlMulMat(int nthread);
    
    // Helper methods to check library before JNI calls
    public static long safeInitContext(String modelPath) {
        checkLibraryLoaded();
        return initContext(modelPath);
    }
}