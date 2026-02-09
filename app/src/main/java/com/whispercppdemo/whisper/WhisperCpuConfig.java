package com.whispercppdemo.whisper;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

public class WhisperCpuConfig {
    private static final String TAG = "WhisperCpuConfig";
    
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static int getPreferredThreadCount() {
        // Try to get optimal thread count for Whisper transcription
        // On Huawei Mate9 (Kirin 960): 4 high-performance cores + 4 low-power cores
        // Use all available cores for maximum performance
        Log.d(TAG, "getPreferredThreadCount() called");
        try {
            int totalCores = Runtime.getRuntime().availableProcessors();
            int highPerfCores = CpuInfo.getHighPerfCpuCount();
            
            Log.d(TAG, "CPU configuration - Total cores: " + totalCores + 
                  ", High-performance cores: " + highPerfCores);
            
            // Strategy: Use all cores for maximum throughput
            // Whisper benefits from parallelization, especially on older devices
            int recommendedThreads = totalCores;
            
            // Cap at 8 threads to avoid diminishing returns
            if (recommendedThreads > 8) {
                recommendedThreads = 8;
            }
            
            // Ensure at least 2 threads
            recommendedThreads = Math.max(recommendedThreads, 2);
            
            Log.d(TAG, "Recommended thread count: " + recommendedThreads);
            Log.d(TAG, "Returning thread count: " + recommendedThreads);
            return recommendedThreads;
            
        } catch (Exception e) {
            Log.d(TAG, "Error getting CPU info, using fallback", e);
            // Fallback: use 4 threads (reasonable for most devices)
            Log.d(TAG, "Fallback returning 4 threads");
            return 4;
        }
    }
}