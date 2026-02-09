package com.whispercppdemo.whisper;

import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.opencode.voiceassist.model.TranscriptionResult;

public class WhisperContext {

    private static final String LOG_TAG = "LibWhisper";
    private long ptr;
    private final ExecutorService executorService;

    private WhisperContext(long ptr) {
        this.ptr = ptr;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public TranscriptionResult transcribeData(float[] data) throws ExecutionException, InterruptedException {
        try {
            return executorService.submit(new Callable<TranscriptionResult>() {
                @RequiresApi(api = Build.VERSION_CODES.O)
                @Override
                public TranscriptionResult call() throws Exception {
                    if (ptr == 0L) {
                        throw new IllegalStateException();
                    }
                    // Use optimal thread count based on device CPU configuration
                    int numThreads = WhisperCpuConfig.getPreferredThreadCount();
                    Log.d(LOG_TAG, "Selecting " + numThreads + " threads (optimal for device)");
                    double audioSeconds = data.length / 16000.0;
                    Log.d(LOG_TAG, "Audio data length: " + data.length + " samples (" + 
                          audioSeconds + " seconds)");
                    
                    long startTime = System.currentTimeMillis();
                    WhisperLib.fullTranscribe(ptr, numThreads, data);
                    long endTime = System.currentTimeMillis();
                    
                    int textCount = WhisperLib.getTextSegmentCount(ptr);
                    long transcriptionTime = endTime - startTime;
                    double realtimeFactor = transcriptionTime / (audioSeconds * 1000.0);
                    Log.d(LOG_TAG, "Transcription complete, segments: " + textCount);
                    Log.d(LOG_TAG, "Transcription time: " + transcriptionTime + " ms (" + 
                          (transcriptionTime / 1000.0) + " seconds)");
                    Log.d(LOG_TAG, "Realtime factor: " + String.format("%.1f", realtimeFactor) + "x (lower is faster)");
                    StringBuilder result = new StringBuilder();
                    for (int i = 0; i < textCount; i++) {
                        String segment = WhisperLib.getTextSegment(ptr, i);
                        result.append(segment);
                        Log.d(LOG_TAG, "Segment " + i + ": " + segment);
                    }
                    return new TranscriptionResult(result.toString(), audioSeconds, transcriptionTime, realtimeFactor);
                }
            }).get(300, TimeUnit.SECONDS); // 300 second (5 minute) timeout for very slow devices
        } catch (TimeoutException e) {
            Log.e(LOG_TAG, "Transcription timeout after 300 seconds", e);
            throw new RuntimeException("Transcription timeout", e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public String benchMemory(int nthreads) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> WhisperLib.benchMemcpy(nthreads)).get();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public String benchGgmlMulMat(int nthreads) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> WhisperLib.benchGgmlMulMat(nthreads)).get();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void release() throws ExecutionException, InterruptedException {
        executorService.submit(() -> {
            if (ptr != 0L) {
                WhisperLib.freeContext(ptr);
                ptr = 0;
            }
        }).get();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static WhisperContext createContextFromFile(String filePath) {
        return createContextFromFile(filePath, false);
    }
    
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static WhisperContext createContextFromFile(String filePath, boolean useGpu) {
        Log.d(LOG_TAG, "Creating Whisper context with GPU: " + useGpu);
        // Check if native library is loaded before calling JNI
        if (!WhisperLib.isLibraryLoaded()) {
            throw new UnsatisfiedLinkError("Whisper native library not loaded");
        }
        long ptr = WhisperLib.initContext(filePath);
        if (ptr == 0L) {
            throw new RuntimeException("Couldn't create context with path " + filePath);
        }
        return new WhisperContext(ptr);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static WhisperContext createContextFromInputStream(InputStream stream) {
        long ptr = WhisperLib.initContextFromInputStream(stream);
        if (ptr == 0L) {
            throw new RuntimeException("Couldn't create context from input stream");
        }
        return new WhisperContext(ptr);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static WhisperContext createContextFromAsset(AssetManager assetManager, String assetPath) {
        long ptr = WhisperLib.initContextFromAsset(assetManager, assetPath);
        if (ptr == 0L) {
            throw new RuntimeException("Couldn't create context from asset " + assetPath);
        }
        return new WhisperContext(ptr);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static String getSystemInfo() {
        return WhisperLib.getSystemInfo();
    }
}