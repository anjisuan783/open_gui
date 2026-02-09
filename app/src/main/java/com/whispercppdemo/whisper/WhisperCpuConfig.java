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
            
            // 检测是否为骁龙 8+ Gen 1 (taro 平台)
            boolean isSnapdragon8Gen1 = false;
            try {
                Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");
                java.lang.reflect.Method getMethod = systemPropertiesClass.getMethod("get", String.class);
                String boardPlatform = (String) getMethod.invoke(null, "ro.board.platform");
                String productBoard = (String) getMethod.invoke(null, "ro.product.board");
                
                isSnapdragon8Gen1 = (boardPlatform != null && boardPlatform.contains("taro")) ||
                                   (productBoard != null && productBoard.contains("taro"));
                
                if (isSnapdragon8Gen1) {
                    Log.d(TAG, "检测到骁龙 8+ Gen 1 (taro) 平台，应用异构架构优化");
                }
            } catch (Exception e) {
                Log.d(TAG, "系统属性检测失败，使用通用策略", e);
            }
            
            int recommendedThreads;
            
            if (isSnapdragon8Gen1) {
                // 骁龙 8+ Gen 1 专用优化: 1×X2 + 3×A710 + 4×A510
                // 策略: 使用所有核心 (8线程)，优先大核负载
                // whisper.cpp 内部会进行负载均衡
                recommendedThreads = 8; // 全部8个核心
                Log.d(TAG, "骁龙 8+ Gen 1 优化: 使用 " + recommendedThreads + " 线程 (全部核心)");
            } else {
                // 通用策略: 使用所有核心，但上限为8
                recommendedThreads = totalCores;
                
                // Cap at 8 threads to avoid diminishing returns
                if (recommendedThreads > 8) {
                    recommendedThreads = 8;
                }
                
                // Ensure at least 2 threads
                recommendedThreads = Math.max(recommendedThreads, 2);
                
                Log.d(TAG, "通用策略: 推荐线程数: " + recommendedThreads);
            }
            
            Log.d(TAG, "最终线程数: " + recommendedThreads);
            return recommendedThreads;
            
        } catch (Exception e) {
            Log.d(TAG, "Error getting CPU info, using fallback", e);
            // Fallback: use 4 threads (reasonable for most devices)
            Log.d(TAG, "Fallback returning 4 threads");
            return 4;
        }
    }
}