package com.opencode.voiceassist.manager;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import android.opengl.GLES20;

import com.opencode.voiceassist.utils.Constants;
import com.opencode.voiceassist.utils.FileManager;
import com.opencode.voiceassist.model.TranscriptionResult;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Scanner;

import com.whispercppdemo.whisper.Whisper;


public class WhisperManager {
    
    private Context context;
    private FileManager fileManager;
    private Whisper whisper;
    private boolean modelLoaded = false;
    private InitializationCallback callback;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executor;
    private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
    private final AtomicInteger pendingTasks = new AtomicInteger(0);
    
    public interface TranscriptionCallback {
        void onTranscriptionComplete(TranscriptionResult result);
        void onTranscriptionError(String error);
    }
    
    public interface InitializationCallback {
        void onInitialized(boolean success, String message);
    }
    
    public WhisperManager(Context context, FileManager fileManager, InitializationCallback callback) {
        this.context = context;
        this.fileManager = fileManager;
        this.callback = callback;
        
        // Single-threaded executor for sequential task processing
        this.executor = new ThreadPoolExecutor(
            1, // core pool size
            1, // maximum pool size
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new ThreadFactory() {
                private final AtomicInteger threadCount = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "WhisperWorker-" + threadCount.incrementAndGet());
                    thread.setPriority(Thread.NORM_PRIORITY);
                    return thread;
                }
            }
        );
    }
    
    public void initialize(String modelFilename) {
        // Debug: Check native library directory
        try {
            File nativeLibDir = new File(context.getApplicationInfo().nativeLibraryDir);
            Log.d("WhisperManager", "Native library dir: " + nativeLibDir.getAbsolutePath());
            if (nativeLibDir.exists()) {
                String[] files = nativeLibDir.list();
                if (files != null) {
                    Log.d("WhisperManager", "Native lib files: " + java.util.Arrays.toString(files));
                } else {
                    Log.d("WhisperManager", "Native lib dir is empty or not accessible");
                }
            } else {
                Log.d("WhisperManager", "Native lib dir does not exist");
            }
        } catch (Exception e) {
            Log.e("WhisperManager", "Error checking native lib dir", e);
        }
        
        File modelFile = fileManager.getModelFile(modelFilename);
        
        if (modelFile.exists()) {
            // Verify file integrity
            if (verifyModelIntegrity(modelFile)) {
                loadModel(modelFile);
            } else {
                // Delete corrupted file and copy from assets
                modelFile.delete();
                if (copyModelFromAssets(modelFilename)) {
                    loadModel(modelFile);
                } else {
                    // Model should be bundled in APK, show error
                    showAssetModelError();
                }
            }
        } else {
            // Copy model from assets (bundled in APK)
            if (copyModelFromAssets(modelFilename)) {
                loadModel(modelFile);
            } else {
                // Model should be bundled in APK, show error
                showAssetModelError();
            }
        }
    }
    
    /**
     * Initialize with default model (backward compatibility)
     */
    public void initialize() {
        initialize(Constants.DEFAULT_WHISPER_MODEL);
    }
    
    /**
     * Show error when asset model is missing (should be bundled in APK)
     */
    private void showAssetModelError() {
        modelLoaded = false;
        mainHandler.post(() -> {
            Toast.makeText(context, Constants.TOAST_ASSET_MODEL_ERROR, Toast.LENGTH_LONG).show();
            if (callback != null) {
                callback.onInitialized(false, Constants.TOAST_ASSET_MODEL_ERROR);
            }
        });
    }
    
    private boolean copyModelFromAssets(String modelFilename) {
        try {
            String assetPath = "whisper/" + modelFilename;
            Log.d("WhisperManager", "Copying model from assets: " + assetPath);
            InputStream is = context.getAssets().open(assetPath);
            File modelFile = fileManager.getModelFile(modelFilename);
            Log.d("WhisperManager", "Target model file: " + modelFile.getAbsolutePath());
            
            FileOutputStream fos = new FileOutputStream(modelFile);
            byte[] buffer = new byte[8192];
            int read;
            long total = 0;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
                total += read;
            }
            fos.close();
            is.close();
            Log.d("WhisperManager", "Model copied successfully, size: " + total + " bytes");
            return true;
        } catch (IOException e) {
            Log.e("WhisperManager", "Failed to copy model from assets", e);
            // Asset not found
            return false;
        }
    }
    
    /**
     * Multi-source model download with fallback and skip mechanism
     * Tries URL1 -> URL2 -> URL3 in sequence
     * If all fail, skip download and continue app initialization
     */
    private void downloadModelMultiSource(String modelFilename) {
        boolean downloadSuccess = false;
        
        for (int i = 0; i < Constants.WHISPER_MODEL_URLS.length; i++) {
            final int currentIndex = i + 1;
            final String url = Constants.WHISPER_MODEL_URLS[i];
            
            // Show download progress toast
            final int finalCurrentIndex = currentIndex;
            mainHandler.post(() -> 
                Toast.makeText(context, "正在下载模型(" + finalCurrentIndex + "/3)...", Toast.LENGTH_SHORT).show()
            );
            
            if (downloadFromUrl(url, modelFilename)) {
                downloadSuccess = true;
                break;
            }
            // If failed, continue to next source (no individual error message)
        }
        
        if (downloadSuccess) {
            loadModel(fileManager.getModelFile(modelFilename));
        } else {
            // All sources failed - skip download and continue
            skipDownload();
        }
    }
    
    /**
     * Download model from specific URL
     * @param urlString Download URL
     * @return true if download successful
     */
    private boolean downloadFromUrl(String urlString, String modelFilename) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(Constants.MODEL_SINGLE_TIMEOUT * 1000);
            connection.setReadTimeout(Constants.MODEL_SINGLE_TIMEOUT * 1000);
            connection.connect();
            
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return false;
            }
            
            File modelFile = fileManager.getModelFile(modelFilename);
            File tempFile = new File(modelFile.getParent(), modelFile.getName() + ".tmp");
            
            InputStream is = new BufferedInputStream(connection.getInputStream());
            FileOutputStream fos = new FileOutputStream(tempFile);
            
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            
            fos.close();
            is.close();
            
            // Rename temp file to final name
            tempFile.renameTo(modelFile);
            return true;
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Skip download when all sources fail
     * Continue app initialization, disable recording only
     */
    private void skipDownload() {
        modelLoaded = false;
        mainHandler.post(() -> {
            Toast.makeText(context, Constants.TOAST_MODEL_DOWNLOAD_SKIP, Toast.LENGTH_LONG).show();
            if (callback != null) {
                callback.onInitialized(false, Constants.TOAST_MODEL_DOWNLOAD_SKIP);
            }
        });
    }
    
    private boolean verifyModelIntegrity(File modelFile) {
        // Basic check: file size should be reasonable (tiny.en is ~75MB)
        long minSize = 50 * 1024 * 1024; // 50MB minimum
        long maxSize = 200 * 1024 * 1024; // 200MB maximum
        return modelFile.length() >= minSize && modelFile.length() <= maxSize;
    }
    
    /**
     * Detect NPU (Neural Processing Unit) support on the device
     * @return true if NPU acceleration is likely available
     */
    private boolean detectNpuSupport() {
        try {
            // Method 0: Check if NNAPI is available (Android 8.1+ / API 27+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                // NNAPI is available, which can utilize NPU, GPU, or DSP
                // Note: This doesn't guarantee NPU, but increases likelihood on Snapdragon devices
                Log.d("WhisperManager", "NNAPI available (API " + Build.VERSION.SDK_INT + ")");
                // On Snapdragon devices with NPU, NNAPI often routes to NPU
            }
            
            // Method 1: Check system properties for NPU support
            Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method getMethod = systemPropertiesClass.getMethod("get", String.class);
            
            // Check for Qualcomm NPU (Hexagon) - common for Snapdragon 8+ Gen 1
            String chipName = (String) getMethod.invoke(null, "ro.chipname");
            String hardwareNpu = (String) getMethod.invoke(null, "ro.hardware.npu");
            String boardPlatform = (String) getMethod.invoke(null, "ro.board.platform");
            String productBoard = (String) getMethod.invoke(null, "ro.product.board");
            
            Log.d("WhisperManager", "NPU detection - chipname: " + chipName + 
                  ", hardware.npu: " + hardwareNpu + 
                  ", board.platform: " + boardPlatform + 
                  ", product.board: " + productBoard);
            
            // Check for Snapdragon 8+ Gen 1 (codenames: taro, kalama, etc.)
            // Check chipName, boardPlatform, and productBoard for Snapdragon identifiers
            boolean isSnapdragon8Gen1 = false;
            if (chipName != null) {
                isSnapdragon8Gen1 = chipName.contains("taro") || 
                                   chipName.contains("kalama") ||
                                   chipName.contains("sm8475") ||  // Snapdragon 8+ Gen 1 model number
                                   chipName.contains("sm8550");    // Snapdragon 8 Gen 2
            }
            // Also check boardPlatform and productBoard for Snapdragon codenames
            if (!isSnapdragon8Gen1 && boardPlatform != null) {
                isSnapdragon8Gen1 = boardPlatform.contains("taro") || 
                                   boardPlatform.contains("kalama") ||
                                   boardPlatform.contains("sm8475") || 
                                   boardPlatform.contains("sm8550");
            }
            if (!isSnapdragon8Gen1 && productBoard != null) {
                isSnapdragon8Gen1 = productBoard.contains("taro") || 
                                   productBoard.contains("kalama") ||
                                   productBoard.contains("sm8475") || 
                                   productBoard.contains("sm8550");
            }
            
            // Check for NPU hardware property
            boolean hasNpuHardware = hardwareNpu != null && !hardwareNpu.isEmpty();
            
            // Check board platform for Snapdragon
            boolean isSnapdragonPlatform = false;
            if (boardPlatform != null) {
                isSnapdragonPlatform = boardPlatform.contains("qcom") ||
                                      boardPlatform.contains("sm") ||
                                      boardPlatform.startsWith("msm") ||
                                      boardPlatform.startsWith("apq") ||
                                      boardPlatform.contains("taro") ||   // Snapdragon 8+ Gen 1
                                      boardPlatform.contains("kalama") || // Snapdragon 8 Gen 2
                                      boardPlatform.contains("lahaina") || // Snapdragon 888
                                      boardPlatform.contains("kona") ||   // Snapdragon 865
                                      boardPlatform.contains("lito") ||   // Snapdragon 765/690
                                      boardPlatform.contains("shima") ||  // Snapdragon 780/778
                                      boardPlatform.contains("yupik");    // Snapdragon 7 series
            }
            
            // Additional check: read /proc/cpuinfo for NPU features
            try {
                java.io.File cpuInfoFile = new java.io.File("/proc/cpuinfo");
                if (cpuInfoFile.exists()) {
                    java.util.Scanner scanner = new java.util.Scanner(cpuInfoFile);
                    String cpuInfo = scanner.useDelimiter("\\A").next();
                    scanner.close();
                    
                    // Check for NPU/Hexagon references in CPU info
                    boolean hasNpuInCpuInfo = cpuInfo.contains("hexagon") || 
                                             cpuInfo.contains("npu") || 
                                             cpuInfo.contains("NPU");
                    if (hasNpuInCpuInfo) {
                        Log.d("WhisperManager", "NPU detected in /proc/cpuinfo");
                        return true;
                    }
                }
            } catch (Exception e) {
                Log.w("WhisperManager", "Failed to read /proc/cpuinfo for NPU detection", e);
            }
            
            // Final decision: if we have NPU hardware property OR it's a Snapdragon 8+ Gen 1
            // Also consider NNAPI availability on Snapdragon platforms
            boolean npuDetected = hasNpuHardware || isSnapdragon8Gen1;
            
            // If we have NNAPI and Snapdragon platform, increase confidence
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && isSnapdragonPlatform) {
                Log.d("WhisperManager", "NNAPI available on Snapdragon platform - NPU likely accessible");
                npuDetected = true;  // Assume NPU is accessible via NNAPI
            }
            
            Log.d("WhisperManager", "NPU detection result: " + npuDetected + 
                  " (hasNpuHardware: " + hasNpuHardware + 
                  ", isSnapdragon8Gen1: " + isSnapdragon8Gen1 + 
                  ", NNAPI+Snapdragon: " + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && isSnapdragonPlatform) + ")");
            
            return npuDetected;
            
        } catch (Exception e) {
            Log.w("WhisperManager", "NPU detection failed, assuming no NPU", e);
            return false;
        }
    }
    
    /**
     * Detect GPU support on the device
     * @return true if GPU acceleration is likely available
     */
    private boolean detectGpuSupport() {
        try {
            // Check if OpenGL ES 3.0 is supported (indicates capable GPU)
            Class<?> glClass = Class.forName("android.opengl.GLES30");
            // If class exists, device likely supports OpenGL ES 3.0
            return true;
        } catch (ClassNotFoundException e) {
            // Fall back to checking OpenGL ES 2.0
            try {
                Class<?> glClass = Class.forName("android.opengl.GLES20");
                // If GLES20 exists, device has some GPU capability
                return true;
            } catch (ClassNotFoundException e2) {
                return false;
            }
        }
    }
    
    private void loadModel(File modelFile) {
        executor.execute(() -> {
            try {
                Log.d("WhisperManager", "Loading model from: " + modelFile.getAbsolutePath());
                Log.d("WhisperManager", "Model file exists: " + modelFile.exists() + ", size: " + modelFile.length());
                
                // Check if native library is loaded
                if (!com.whispercppdemo.whisper.WhisperLib.isLibraryLoaded()) {
                    Log.e("WhisperManager", "Whisper native library not loaded");
                    throw new UnsatisfiedLinkError("Whisper native library not loaded");
                }
                
                // Direct instantiation of Whisper using builder
                boolean hasNpu = detectNpuSupport();
                boolean useGpu = detectGpuSupport();
                boolean gpuFailed = false;
                
                Log.d("WhisperManager", "Acceleration detection - NPU: " + hasNpu + ", GPU: " + useGpu);
                
                // First attempt: try with detected GPU setting
                try {
                    String accelerationMode = "CPU";
                    if (useGpu) {
                        accelerationMode = hasNpu ? "NPU/GPU" : "GPU";
                    }
                    Log.d("WhisperManager", "Creating Whisper instance, acceleration: " + accelerationMode + " (NPU detected: " + hasNpu + ")");
                    whisper = Whisper.builder()
                        .setModelPath(modelFile.getAbsolutePath())
                        .setUseGpu(useGpu)
                        .build();
                    modelLoaded = true;
                     String loadedMode = useGpu ? (hasNpu ? "NPU/GPU" : "GPU") : "CPU";
                     Log.d("WhisperManager", "Model loaded successfully with " + loadedMode + " acceleration");
                } catch (Exception e) {
                    Log.w("WhisperManager", "First attempt failed with GPU=" + useGpu + ", error: " + e.getMessage());
                    gpuFailed = true;
                    whisper = null;
                }
                
                // If GPU attempt failed, try without GPU
                if (gpuFailed && useGpu) {
                    Log.d("WhisperManager", "Retrying without GPU acceleration...");
                    try {
                        whisper = Whisper.builder()
                            .setModelPath(modelFile.getAbsolutePath())
                            .setUseGpu(false)
                            .build();
                        modelLoaded = true;
                         Log.d("WhisperManager", "Model loaded successfully with CPU (GPU/NPU fallback)");
                    } catch (Exception e2) {
                        Log.e("WhisperManager", "Fallback attempt also failed", e2);
                        modelLoaded = false;
                        whisper = null;
                    }
                }
                
                if (modelLoaded) {
                    final boolean finalUseGpu = useGpu;
                    final boolean finalGpuFailed = gpuFailed;
                    final boolean finalHasNpu = hasNpu;
                    mainHandler.post(() -> {
                        if (callback != null) {
                            String modeMessage;
                            if (finalUseGpu && !finalGpuFailed) {
                                modeMessage = finalHasNpu ? " (NPU/GPU加速)" : " (GPU加速)";
                            } else {
                                modeMessage = " (CPU模式)";
                            }
                            callback.onInitialized(true, "模型加载成功" + modeMessage);
                        }
                    });
                } else {
                    // Both attempts failed, exception will be caught by outer catch block
                    throw new RuntimeException("Failed to load Whisper model with both GPU and CPU modes");
                }
            } catch (UnsatisfiedLinkError e) {
                Log.e("WhisperManager", "UnsatisfiedLinkError loading model", e);
                e.printStackTrace();
                // Whisper library not available - disable recording
                modelLoaded = false;
                whisper = null;
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onInitialized(false, "Whisper原生库加载失败，录音功能已禁用");
                    }
                });
            } catch (Exception e) {
                Log.e("WhisperManager", "Exception loading model", e);
                e.printStackTrace();
                // Whisper library not available - disable recording
                modelLoaded = false;
                whisper = null;
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onInitialized(false, "Whisper库未找到，录音功能已禁用: " + e.getMessage());
                    }
                });
            }
        });
    }
    
    public TranscriptionResult transcribe(File wavFile) {
        return transcribe(wavFile, null);
    }
    
    public TranscriptionResult transcribe(File wavFile, TranscriptionCallback callback) {
        Log.d("WhisperManager", "QUEUE SYSTEM ACTIVE - Sequential transcription task");
        Log.d("WhisperManager", "Starting transcription for file: " + wavFile.getAbsolutePath());
        Log.d("WhisperManager", "Pending tasks in queue: " + pendingTasks.get());
        
        if (!modelLoaded) {
            String error = "Model not loaded, cannot transcribe";
            Log.e("WhisperManager", error);
            if (callback != null) {
                mainHandler.post(() -> callback.onTranscriptionError(error));
            }
            return null;
        }
        
        if (whisper == null) {
            String error = "Whisper instance is null";
            Log.e("WhisperManager", error);
            if (callback != null) {
                mainHandler.post(() -> callback.onTranscriptionError(error));
            }
            return null;
        }
        
        if (wavFile == null || !wavFile.exists()) {
            String error = "WAV file does not exist: " + (wavFile == null ? "null" : wavFile.getAbsolutePath());
            Log.e("WhisperManager", error);
            if (callback != null) {
                mainHandler.post(() -> callback.onTranscriptionError(error));
            }
            return null;
        }
        
        Log.d("WhisperManager", "WAV file exists, size: " + wavFile.length() + " bytes");
        
        if (wavFile.length() == 0) {
            String error = "WAV file is empty";
            Log.e("WhisperManager", error);
            if (callback != null) {
                mainHandler.post(() -> callback.onTranscriptionError(error));
            }
            return null;
        }
        
        // Increment pending tasks counter
        int currentPending = pendingTasks.incrementAndGet();
        Log.d("WhisperManager", "Task queued. Total pending tasks: " + currentPending);
        
        try {
            // Submit transcription task to the sequential executor
            Future<TranscriptionResult> future = executor.submit(new Callable<TranscriptionResult>() {
                @Override
                public TranscriptionResult call() throws Exception {
                    try {
                        Log.d("WhisperManager", "Starting transcription task for: " + wavFile.getAbsolutePath());
                        Log.d("WhisperManager", "Calling whisper.transcribe()...");
                        TranscriptionResult result = whisper.transcribe(wavFile.getAbsolutePath());
                        if (result != null) {
                            Log.d("WhisperManager", "Transcription completed, text length: " + result.getText().length());
                            Log.d("WhisperManager", "Performance: audio=" + String.format("%.2f", result.getAudioLengthSeconds()) + "s, " +
                                  "processing=" + result.getProcessingTimeMs() + "ms, " +
                                  "realtime factor=" + String.format("%.1f", result.getRealtimeFactor()) + "x");
                        } else {
                            Log.d("WhisperManager", "Transcription completed, result is null");
                        }
                        return result;
                    } finally {
                        // Decrement pending tasks counter when task completes
                        int remaining = pendingTasks.decrementAndGet();
                        Log.d("WhisperManager", "Task completed. Remaining pending tasks: " + remaining);
                    }
                }
            });
            
            // Wait for result with timeout (300 seconds = 5 minutes for very slow devices)
            TranscriptionResult result = future.get(300, TimeUnit.SECONDS);
            
            // Notify callback if provided
            if (callback != null) {
                if (result != null) {
                    mainHandler.post(() -> callback.onTranscriptionComplete(result));
                } else {
                    mainHandler.post(() -> callback.onTranscriptionError("Transcription returned null"));
                }
            }
            
            return result;
            
        } catch (TimeoutException e) {
            Log.e("WhisperManager", "Transcription timeout after 90 seconds", e);
            String error = "Transcription timeout";
            if (callback != null) {
                mainHandler.post(() -> callback.onTranscriptionError(error));
            }
            return null;
        } catch (Exception e) {
            Log.e("WhisperManager", "Exception during transcription", e);
            e.printStackTrace();
            String error = "Transcription error: " + e.getMessage();
            if (callback != null) {
                mainHandler.post(() -> callback.onTranscriptionError(error));
            }
            return null;
        } finally {
            // Ensure pending tasks counter is decremented if task wasn't submitted successfully
            // (Note: if task was submitted, it will decrement in the finally block above)
        }
    }
    
    public boolean isModelLoaded() {
        return modelLoaded;
    }
    
    public void release() {
        Log.d("WhisperManager", "Releasing WhisperManager, pending tasks: " + pendingTasks.get());
        
        if (whisper != null) {
            whisper.release();
            whisper = null;
        }
        
        // Shutdown executor gracefully
        executor.shutdown();
        try {
            // Wait for existing tasks to complete
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                Log.w("WhisperManager", "Executor did not terminate in 30 seconds, forcing shutdown");
                executor.shutdownNow();
                // Wait a bit more for cancellation
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    Log.e("WhisperManager", "Executor did not terminate after shutdownNow");
                }
            }
        } catch (InterruptedException e) {
            Log.e("WhisperManager", "Interrupted while shutting down executor", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Reset pending tasks counter
        pendingTasks.set(0);
        Log.d("WhisperManager", "WhisperManager released successfully");
    }
    
    /**
     * Reinitialize with a new model
     */
    public void reinitialize(String modelFilename) {
        Log.d("WhisperManager", "Reinitializing WhisperManager with model: " + modelFilename);
        
        // Release current resources
        release();
        
        // Reset state
        modelLoaded = false;
        whisper = null;
        
        // Recreate executor (since release() shuts it down)
        this.executor = new ThreadPoolExecutor(
            1, // core pool size
            1, // maximum pool size
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new ThreadFactory() {
                private final AtomicInteger threadCount = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "WhisperWorker-" + threadCount.incrementAndGet());
                    thread.setPriority(Thread.NORM_PRIORITY);
                    return thread;
                }
            }
        );
        
        // Initialize with new model
        initialize(modelFilename);
    }
}
