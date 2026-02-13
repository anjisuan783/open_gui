package com.opencode.voiceassist.manager;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CameraPermissionManager {
    private static final String TAG = "CameraPermissionManager";
    
    private final Activity activity;
    private final Handler mainHandler;
    private final CameraCallback callback;
    
    // Camera state
    private Uri cameraPhotoUri;
    private boolean isCameraCapturePending = false;
    
    // Permission request codes
    public static final int REQUEST_CAMERA = 1005;
    public static final int REQUEST_STORAGE_PERMISSION = 1003;
    
    public interface CameraCallback {
        void onCameraPermissionGranted();
        void onCameraPermissionDenied();
        void onStoragePermissionGranted();
        void onStoragePermissionDenied();
        void onCameraPhotoCaptured(Uri photoUri);
        void onCameraError(String error);
    }
    
    public CameraPermissionManager(Activity activity, CameraCallback callback) {
        this.activity = activity;
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public void openCamera(boolean isRecording) {
        Log.d(TAG, "openCamera called, isRecording=" + isRecording + ", isCameraCapturePending=" + isCameraCapturePending);
        
        // Prevent camera during recording to avoid interruption
        if (isRecording) {
            Log.d(TAG, "Camera blocked - recording in progress");
            mainHandler.post(() -> Toast.makeText(activity, "录音进行中，无法拍照", Toast.LENGTH_SHORT).show());
            return;
        }
        
        // Prevent multiple camera launches
        if (isCameraCapturePending) {
            Log.w(TAG, "Camera capture already pending, ignoring request");
            return;
        }
        
        if (checkCameraPermission()) {
            launchCamera();
        } else {
            Log.d(TAG, "Requesting camera permission");
            requestCameraPermission();
        }
    }
    
    public boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(activity, 
            Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
    
    public void requestCameraPermission() {
        ActivityCompat.requestPermissions(activity, 
            new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
    }
    
    public void launchCamera() {
        Log.d(TAG, "launchCamera called, isCameraCapturePending=" + isCameraCapturePending);
        try {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            Log.d(TAG, "Created ACTION_IMAGE_CAPTURE intent");
            
            // Ensure there's a camera activity to handle the intent
            if (takePictureIntent.resolveActivity(activity.getPackageManager()) != null) {
                Log.d(TAG, "Camera activity available");
                // Create the File where the photo should go
                File photoFile = createImageFile();
                if (photoFile != null) {
                    Log.d(TAG, "Created image file: " + photoFile.getAbsolutePath());
                    cameraPhotoUri = FileProvider.getUriForFile(activity,
                        activity.getApplicationContext().getPackageName() + ".fileprovider",
                        photoFile);
                    Log.d(TAG, "Created cameraPhotoUri: " + cameraPhotoUri);
                    
                    // Grant temporary read permission to the camera app
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
                    // Try to make camera finish after taking one photo (some camera apps respect this)
                    takePictureIntent.putExtra("android.intent.extras.CAMERA_FACING", 0); // Rear camera
                    // Some camera apps respect this flag to finish after capture
                    takePictureIntent.putExtra("finishOnCompletion", true);
                    // Try to prevent camera from staying open for multiple shots
                    takePictureIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                    
                    Log.d(TAG, "Starting camera activity with flags: " + takePictureIntent.getFlags());
                    // Start the camera activity
                    activity.startActivityForResult(takePictureIntent, REQUEST_CAMERA);
                    isCameraCapturePending = true;
                    Log.d(TAG, "Camera activity started, isCameraCapturePending=true");
                } else {
                    Log.e(TAG, "Failed to create image file");
                    mainHandler.post(() -> Toast.makeText(activity, "无法创建照片文件", Toast.LENGTH_SHORT).show());
                }
            } else {
                Log.e(TAG, "No camera activity found");
                mainHandler.post(() -> Toast.makeText(activity, "未找到相机应用", Toast.LENGTH_SHORT).show());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error launching camera", e);
            mainHandler.post(() -> Toast.makeText(activity, "启动相机失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            if (callback != null) {
                callback.onCameraError("Error launching camera: " + e.getMessage());
            }
        }
    }
    
    /**
     * Create a temporary image file for camera capture
     */
    private File createImageFile() {
        try {
            // Create an image file name
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";
            
            // Use cache directory for temporary storage
            File storageDir = activity.getExternalCacheDir();
            if (storageDir == null) {
                storageDir = activity.getCacheDir(); // Fallback to internal cache
            }
            
            File imageFile = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
            );
            
            return imageFile;
        } catch (IOException e) {
            Log.e(TAG, "Error creating image file", e);
            return null;
        }
    }
    
    /**
     * Trigger file upload from camera - sets pending flag for WebView file chooser
     */
    public void triggerFileUploadFromCamera(Uri photoUri) {
        Log.d(TAG, "triggerFileUploadFromCamera called: " + photoUri + 
            " (callback: " + (callback != null ? "not null" : "NULL") + ")");
        
        if (callback != null) {
            Log.d(TAG, "Calling callback.onCameraPhotoCaptured");
            callback.onCameraPhotoCaptured(photoUri);
        } else {
            Log.e(TAG, "Callback is null! Cannot notify about captured photo");
        }
        
        // User must manually click the attachment button in WebView
        // WebView's onShowFileChooser will handle the pending upload
        mainHandler.post(() -> Toast.makeText(activity, "拍照完成，请在网页中点击附件按钮上传照片", Toast.LENGTH_SHORT).show());
    }
    
    /**
     * Clean up temporary camera file after processing
     */
    public void cleanupTempCameraFile(Uri fileUri) {
        if (fileUri == null) return;
        
        // Check if this is a camera temp file (from cache directory)
        String uriPath = fileUri.toString();
        if (uriPath.contains("cache") || uriPath.contains("temp")) {
            mainHandler.postDelayed(() -> {
                try {
                    File file = new File(fileUri.getPath());
                    if (file.exists()) {
                        boolean deleted = file.delete();
                        Log.d(TAG, "Cleaned up temp camera file: " + 
                            file.getAbsolutePath() + " deleted=" + deleted);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to cleanup temp camera file", e);
                }
            }, 30000); // Delay 30 seconds to allow WebView to process
        }
    }
    
    /**
     * Check storage permission based on Android version
     */
    public boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires READ_MEDIA_IMAGES permission
            return ContextCompat.checkSelfPermission(activity, 
                Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12 requires READ_EXTERNAL_STORAGE permission
            return ContextCompat.checkSelfPermission(activity,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 10 and below
            return ContextCompat.checkSelfPermission(activity,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    /**
     * Request storage permission
     */
    public void requestStoragePermission() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES};
        } else {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        }
        
        ActivityCompat.requestPermissions(activity, permissions, REQUEST_STORAGE_PERMISSION);
    }
    
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, launch camera
                if (callback != null) {
                    callback.onCameraPermissionGranted();
                }
                launchCamera();
            } else {
                // Permission denied
                mainHandler.post(() -> Toast.makeText(activity, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show());
                if (callback != null) {
                    callback.onCameraPermissionDenied();
                }
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (callback != null) {
                    callback.onStoragePermissionGranted();
                }
            } else {
                mainHandler.post(() -> {
                    if (permissions.length > 0) {
                        String deniedPermission = permissions[0];
                        // Check if user permanently denied the permission
                        boolean shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                            activity, deniedPermission);
                        
                        if (!shouldShowRationale && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            // Permission permanently denied, guide user to settings
                            Toast.makeText(activity, "存储权限被永久拒绝，请在设置中启用", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(activity, "需要存储权限才能选择文件", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(activity, "需要存储权限才能选择文件", Toast.LENGTH_SHORT).show();
                    }
                });
                if (callback != null) {
                    callback.onStoragePermissionDenied();
                }
            }
        }
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode + 
            ", data=" + data + ", isCameraCapturePending=" + isCameraCapturePending + 
            ", cameraPhotoUri=" + cameraPhotoUri);
        
        if (requestCode == REQUEST_CAMERA) {
            Log.d(TAG, "Camera result received, resultCode: " + resultCode + 
                " (RESULT_OK=" + Activity.RESULT_OK + ")");
            isCameraCapturePending = false;
            
            if (resultCode == Activity.RESULT_OK) {
                // Check if we have a photo URI from EXTRA_OUTPUT
                Uri photoUri = cameraPhotoUri;
                if (photoUri == null && data != null && data.getData() != null) {
                    // Some camera apps return the URI in the data intent
                    photoUri = data.getData();
                    Log.d(TAG, "Using photo URI from data intent: " + photoUri);
                }
                
                if (photoUri != null) {
                    Log.d(TAG, "Processing captured photo: " + photoUri);
                    
                    // Trigger WebView file upload with URI
                    triggerFileUploadFromCamera(photoUri);
                    
                    // Clear the URI after passing to upload handler
                    cameraPhotoUri = null;
                    Log.d(TAG, "Cleared cameraPhotoUri after triggering upload");
                } else {
                    Log.w(TAG, "Camera photo URI is null, data intent: " + data);
                    mainHandler.post(() -> Toast.makeText(activity, "拍照失败，未获取到照片", Toast.LENGTH_SHORT).show());
                }
            } else {
                Log.d(TAG, "Camera cancelled or failed (resultCode: " + resultCode + ")");
                // User cancelled or camera failed
                if (cameraPhotoUri != null) {
                    // Try to delete the temporary file
                    try {
                        File photoFile = new File(cameraPhotoUri.getPath());
                        if (photoFile.exists()) {
                            boolean deleted = photoFile.delete();
                            Log.d(TAG, "Deleted temp photo file: " + deleted + ", path: " + photoFile.getAbsolutePath());
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to delete temp photo file", e);
                    }
                    cameraPhotoUri = null;
                    Log.d(TAG, "Cleared cameraPhotoUri after cancellation");
                }
            }
        } else {
            Log.d(TAG, "Not a camera request, ignoring");
        }
    }
    
    public Uri getCameraPhotoUri() {
        return cameraPhotoUri;
    }
    
    public boolean isCameraCapturePending() {
        return isCameraCapturePending;
    }
}