package com.opencode.voiceassist.manager;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import android.opengl.GLES20;

import com.opencode.voiceassist.utils.Constants;
import com.opencode.voiceassist.utils.FileManager;

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
        void onTranscriptionComplete(String result);
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
    
    public void initialize() {
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
        
        File modelFile = fileManager.getModelFile();
        
        if (modelFile.exists()) {
            // Verify file integrity
            if (verifyModelIntegrity(modelFile)) {
                loadModel(modelFile);
            } else {
                // Delete corrupted file and copy from assets
                modelFile.delete();
                if (copyModelFromAssets()) {
                    loadModel(modelFile);
                } else {
                    // Model should be bundled in APK, show error
                    showAssetModelError();
                }
            }
        } else {
            // Copy model from assets (bundled in APK)
            if (copyModelFromAssets()) {
                loadModel(modelFile);
            } else {
                // Model should be bundled in APK, show error
                showAssetModelError();
            }
        }
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
    
    private boolean copyModelFromAssets() {
        try {
            String assetPath = "whisper/" + Constants.WHISPER_MODEL_FILENAME;
            Log.d("WhisperManager", "Copying model from assets: " + assetPath);
            InputStream is = context.getAssets().open(assetPath);
            File modelFile = fileManager.getModelFile();
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
    private void downloadModelMultiSource() {
        boolean downloadSuccess = false;
        
        for (int i = 0; i < Constants.WHISPER_MODEL_URLS.length; i++) {
            final int currentIndex = i + 1;
            final String url = Constants.WHISPER_MODEL_URLS[i];
            
            // Show download progress toast
            final int finalCurrentIndex = currentIndex;
            mainHandler.post(() -> 
                Toast.makeText(context, "正在下载模型(" + finalCurrentIndex + "/3)...", Toast.LENGTH_SHORT).show()
            );
            
            if (downloadFromUrl(url)) {
                downloadSuccess = true;
                break;
            }
            // If failed, continue to next source (no individual error message)
        }
        
        if (downloadSuccess) {
            loadModel(fileManager.getModelFile());
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
    private boolean downloadFromUrl(String urlString) {
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
            
            File modelFile = fileManager.getModelFile();
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
                boolean useGpu = detectGpuSupport();
                // Temporarily disable GPU for debugging
                useGpu = false;
                Log.d("WhisperManager", "Creating Whisper instance, GPU: " + useGpu + " (forced false for debugging)");
                whisper = Whisper.builder()
                    .setModelPath(modelFile.getAbsolutePath())
                    .setUseGpu(useGpu)
                    .build();
                modelLoaded = true;
                Log.d("WhisperManager", "Model loaded successfully");
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onInitialized(true, "模型加载成功");
                    }
                });
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
    
    public String transcribe(File wavFile) {
        return transcribe(wavFile, null);
    }
    
    public String transcribe(File wavFile, TranscriptionCallback callback) {
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
            Future<String> future = executor.submit(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    try {
                        Log.d("WhisperManager", "Starting transcription task for: " + wavFile.getAbsolutePath());
                        Log.d("WhisperManager", "Calling whisper.transcribe()...");
                        String result = whisper.transcribe(wavFile.getAbsolutePath());
                        Log.d("WhisperManager", "Transcription completed, result length: " + 
                              (result != null ? result.length() : "null"));
                        if (result != null) {
                            Log.d("WhisperManager", "Transcription result: " + result);
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
            String result = future.get(300, TimeUnit.SECONDS);
            
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
}
