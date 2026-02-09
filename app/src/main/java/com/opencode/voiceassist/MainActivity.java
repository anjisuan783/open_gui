package com.opencode.voiceassist;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.opencode.voiceassist.manager.AudioRecorder;
import com.opencode.voiceassist.manager.OpenCodeManager;
import com.opencode.voiceassist.manager.WhisperManager;
import com.opencode.voiceassist.model.Message;
import com.opencode.voiceassist.ui.MessageAdapter;
import com.opencode.voiceassist.utils.Constants;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import com.opencode.voiceassist.utils.FileManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    private RecyclerView recyclerMessages;
    private MessageAdapter messageAdapter;
    private List<Message> messages = new ArrayList<>();
    
    private View recordButton;
    private TextView tvRecordHint;
    private View recordButtonContainer;
    private View recordProgress;
    
    private WhisperManager whisperManager;
    private OpenCodeManager openCodeManager;
    private AudioRecorder audioRecorder;
    private FileManager fileManager;
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean transcriptionTested = false;
    
    private boolean isRecording = false;
    private boolean isCancelled = false;
    private float startY = 0;
    private static final float CANCEL_THRESHOLD_DP = 50;
    
    private enum ButtonState {
        DEFAULT, RECORDING, CANCEL, PROCESSING, DISABLED
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        initManagers();
        checkPermissions();
    }
    
    private void initViews() {
        recyclerMessages = findViewById(R.id.recycler_messages);
        recordButton = findViewById(R.id.btn_record);
        tvRecordHint = findViewById(R.id.tv_record_hint);
        recordButtonContainer = findViewById(R.id.record_button_container);
        recordProgress = findViewById(R.id.record_progress);
        
        messageAdapter = new MessageAdapter(messages);
        recyclerMessages.setLayoutManager(new LinearLayoutManager(this));
        recyclerMessages.setAdapter(messageAdapter);
        
        ImageButton btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v -> showSettingsDialog());
        
        setupRecordButton();
    }
    
    private void initManagers() {
        fileManager = new FileManager(this);
        whisperManager = new WhisperManager(this, fileManager, this::onWhisperInitialized);
        // TODO: Temporarily disabled OpenCode integration
        // openCodeManager = new OpenCodeManager(this);
        // openCodeManager.setInitializationCallback(this::onOpenCodeInitialized);
        openCodeManager = null; // Set to null to avoid NPE
        audioRecorder = new AudioRecorder();
        
        // Initialize Whisper model (will skip download if fails)
        new Thread(() -> whisperManager.initialize()).start();
        
        // Initialize OpenCode session (temporarily disabled)
        // new Thread(() -> openCodeManager.initializeSession()).start();
    }
    
    private void onWhisperInitialized(boolean success, String message) {
        mainHandler.post(() -> {
            if (success) {
                Toast.makeText(this, "模型部署成功，录音功能已启用", Toast.LENGTH_SHORT).show();
                updateButtonState(ButtonState.DEFAULT);
                
                // DISABLED: Automatic transcription test - wait for user input
                // mainHandler.postDelayed(() -> {
                //     runTranscriptionTest();
                // }, 3000); // Wait 3 seconds for everything to settle
            } else {
                // Model initialization failed/skipped - disable recording
                updateButtonState(ButtonState.DISABLED);
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private void onOpenCodeInitialized(boolean success, String message) {
        mainHandler.post(() -> {
            if (success) {
                Toast.makeText(this, "OpenCode连接成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "OpenCode连接失败: " + message + "\n请点击右上角齿轮图标配置服务器地址", Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private void checkPermissions() {
        String[] permissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        };
        
        List<String> neededPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(permission);
            }
        }
        
        if (!neededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                neededPermissions.toArray(new String[0]), 
                PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                Toast.makeText(this, "请开启录音/网络权限后使用", Toast.LENGTH_LONG).show();
                updateButtonState(ButtonState.DISABLED);
            }
        }
    }
    
    private void setupRecordButton() {
        recordButton.setOnTouchListener((v, event) -> {
            if (whisperManager == null || !whisperManager.isModelLoaded()) {
                return false; // Button is disabled
            }
            
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startY = event.getY();
                    startRecording();
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    if (isRecording) {
                        float deltaY = startY - event.getY();
                        float thresholdPx = dpToPx(CANCEL_THRESHOLD_DP);
                        
                        if (deltaY >= thresholdPx && !isCancelled) {
                            isCancelled = true;
                            updateButtonState(ButtonState.CANCEL);
                        } else if (deltaY < thresholdPx && isCancelled) {
                            isCancelled = false;
                            updateButtonState(ButtonState.RECORDING);
                        }
                    }
                    return true;
                    
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isRecording) {
                        stopRecording();
                    }
                    return true;
            }
            return false;
        });
    }
    
    private void startRecording() {
        android.util.Log.d("MainActivity", "startRecording() called");
        isRecording = true;
        isCancelled = false;
        updateButtonState(ButtonState.RECORDING);
        
        File wavFile = fileManager.getTempWavFile();
        android.util.Log.d("MainActivity", "WAV file path: " + wavFile.getAbsolutePath());
        
        // Delete previous file if exists
        if (wavFile.exists()) {
            android.util.Log.d("MainActivity", "Deleting previous WAV file");
            wavFile.delete();
        }
        
        audioRecorder.startRecording(wavFile);
        android.util.Log.d("MainActivity", "AudioRecorder started");
    }
    
    private void stopRecording() {
        android.util.Log.d("MainActivity", "stopRecording() called");
        isRecording = false;
        
        if (isCancelled) {
            android.util.Log.d("MainActivity", "Recording was cancelled");
            audioRecorder.stopRecording();
            fileManager.deleteTempWavFile();
            Toast.makeText(this, "已取消录音", Toast.LENGTH_SHORT).show();
            updateButtonState(ButtonState.DEFAULT);
            return;
        }
        
        updateButtonState(ButtonState.PROCESSING);
        android.util.Log.d("MainActivity", "Stopping audio recorder...");
        audioRecorder.stopRecording();
        
        // Wait a bit for recorder to finish writing file
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        File wavFile = fileManager.getTempWavFile();
        android.util.Log.d("MainActivity", "Checking WAV file: " + wavFile.getAbsolutePath());
        android.util.Log.d("MainActivity", "File exists: " + wavFile.exists() + ", size: " + (wavFile.exists() ? wavFile.length() : 0) + " bytes");
        
        if (!wavFile.exists() || wavFile.length() == 0) {
            android.util.Log.e("MainActivity", "WAV file is empty or doesn't exist");
            Toast.makeText(this, "录音失败，请重试", Toast.LENGTH_SHORT).show();
            updateButtonState(ButtonState.DEFAULT);
            return;
        }
        
        // Verify WAV file format
        if (wavFile.length() < 44) {
            android.util.Log.e("MainActivity", "WAV file too small for header: " + wavFile.length() + " bytes");
            Toast.makeText(this, "录音文件格式错误", Toast.LENGTH_SHORT).show();
            updateButtonState(ButtonState.DEFAULT);
            return;
        }
        
        android.util.Log.d("MainActivity", "WAV file looks good, starting transcription...");
        
        // Transcribe in background
        new Thread(() -> {
            android.util.Log.d("MainActivity", "Transcription thread started");
            String text = whisperManager.transcribe(wavFile);
            fileManager.deleteTempWavFile();
            
            android.util.Log.d("MainActivity", "Transcription result: " + (text != null ? text : "null"));
            
            if (text != null && !text.trim().isEmpty()) {
                android.util.Log.d("MainActivity", "Transcription successful, processing text...");
                mainHandler.post(() -> processTranscribedText(text));
            } else {
                android.util.Log.e("MainActivity", "Transcription returned null or empty");
                mainHandler.post(() -> {
                    Toast.makeText(this, "语音识别失败，请重试", Toast.LENGTH_SHORT).show();
                    updateButtonState(ButtonState.DEFAULT);
                });
            }
        }).start();
    }
    
    private void processTranscribedText(String text) {
        android.util.Log.d("MainActivity", "Processing transcribed text: " + text);
        
        // Add user message
        addMessage(new Message(text, Message.TYPE_USER));
        
        // TODO: Temporarily disabled OpenCode integration
        // Directly show assistant response instead of calling OpenCode
        mainHandler.post(() -> {
            String response = "已收到您的语音输入: \"" + text + "\"\n(OpenCode功能已临时禁用)";
            addMessage(new Message(response, Message.TYPE_ASSISTANT));
            updateButtonState(ButtonState.DEFAULT);
        });
        
        /* Original OpenCode integration (disabled)
        // Send to OpenCode
        new Thread(() -> {
            android.util.Log.d("MainActivity", "Sending to OpenCode: " + text);
            openCodeManager.sendMessage(text, new OpenCodeManager.ResponseCallback() {
                @Override
                public void onResponse(String response) {
                    android.util.Log.d("MainActivity", "OpenCode response: " + response);
                    mainHandler.post(() -> {
                        addMessage(new Message(response, Message.TYPE_ASSISTANT));
                        updateButtonState(ButtonState.DEFAULT);
                    });
                }
                
                @Override
                public void onError(String error) {
                    android.util.Log.d("MainActivity", "OpenCode error: " + error);
                    mainHandler.post(() -> {
                        addMessage(new Message("错误: " + error, Message.TYPE_ERROR));
                        updateButtonState(ButtonState.DEFAULT);
                    });
                }
            });
        }).start();
        */
    }
    
    private void addMessage(Message message) {
        android.util.Log.d("MainActivity", "Adding message: type=" + message.getType() + ", content=" + message.getContent());
        messages.add(message);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        recyclerMessages.scrollToPosition(messages.size() - 1);
    }
    
    private void updateButtonState(ButtonState state) {
        switch (state) {
            case DEFAULT:
                recordButton.setBackgroundResource(R.drawable.bg_record_default);
                recordProgress.setVisibility(View.GONE);
                tvRecordHint.setVisibility(View.GONE);
                tvRecordHint.setText("");
                recordButton.setEnabled(true);
                break;
                
            case RECORDING:
                recordButton.setBackgroundResource(R.drawable.bg_record_recording);
                recordProgress.setVisibility(View.VISIBLE);
                tvRecordHint.setVisibility(View.GONE);
                break;
                
            case CANCEL:
                recordButton.setBackgroundResource(R.drawable.bg_record_cancel);
                recordProgress.setVisibility(View.GONE);
                tvRecordHint.setVisibility(View.VISIBLE);
                tvRecordHint.setText("取消");
                tvRecordHint.setTextColor(getResources().getColor(android.R.color.white));
                break;
                
            case PROCESSING:
                recordButton.setBackgroundResource(R.drawable.bg_record_processing);
                recordProgress.setVisibility(View.VISIBLE);
                tvRecordHint.setVisibility(View.GONE);
                recordButton.setEnabled(false);
                break;
                
            case DISABLED:
                recordButton.setBackgroundResource(R.drawable.bg_record_disabled);
                recordProgress.setVisibility(View.GONE);
                tvRecordHint.setVisibility(View.VISIBLE);
                if (whisperManager != null && !whisperManager.isModelLoaded()) {
                    tvRecordHint.setText("模型未部署，请手动放置后重启");
                } else {
                    tvRecordHint.setText("OpenCode连接失败，请检查配置");
                }
                tvRecordHint.setTextColor(getResources().getColor(android.R.color.darker_gray));
                recordButton.setEnabled(false);
                break;
        }
    }
    
    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        
        EditText etIp = view.findViewById(R.id.et_ip);
        EditText etPort = view.findViewById(R.id.et_port);
        
        // Load saved settings
        String savedIp = getSharedPreferences("settings", MODE_PRIVATE).getString("opencode_ip", Constants.DEFAULT_OPENCODE_IP);
        int savedPort = getSharedPreferences("settings", MODE_PRIVATE).getInt("opencode_port", Constants.DEFAULT_OPENCODE_PORT);
        
        etIp.setText(savedIp);
        etPort.setText(String.valueOf(savedPort));
        
        builder.setView(view)
            .setTitle("OpenCode配置")
            .setPositiveButton("保存", (dialog, which) -> {
                String ip = etIp.getText().toString().trim();
                int port = Integer.parseInt(etPort.getText().toString().trim());
                
                getSharedPreferences("settings", MODE_PRIVATE)
                    .edit()
                    .putString("opencode_ip", ip)
                    .putInt("opencode_port", port)
                    .apply();
                
                // Reinitialize OpenCode with new settings (temporarily disabled)
                if (openCodeManager != null) {
                    openCodeManager.updateSettings(ip, port);
                    Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "配置已保存 (OpenCode功能已临时禁用)", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
    
    /**
     * Run a test transcription using the sample JFK WAV file
     * This helps verify that Whisper is working correctly
     */
    private void runTranscriptionTest() {
        if (transcriptionTested) {
            return;
        }
        
        transcriptionTested = true;
        android.util.Log.d("MainActivity", "Starting automatic transcription test...");
        
        new Thread(() -> {
            try {
                // Copy test WAV file from assets to temp file
                String assetPath = "test/jfk.wav";
                InputStream is = getAssets().open(assetPath);
                
                File tempFile = new File(getCacheDir(), "test_jfk.wav");
                FileOutputStream fos = new FileOutputStream(tempFile);
                
                byte[] buffer = new byte[8192];
                int read;
                long total = 0;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                    total += read;
                }
                fos.close();
                is.close();
                
                android.util.Log.d("MainActivity", "Test WAV file copied, size: " + total + " bytes");
                android.util.Log.d("MainActivity", "Test file path: " + tempFile.getAbsolutePath());
                
                // Wait a bit to ensure Whisper is fully initialized
                Thread.sleep(1000);
                
                // Run transcription
                String result = whisperManager.transcribe(tempFile);
                
                if (result != null && !result.trim().isEmpty()) {
                    android.util.Log.d("MainActivity", "✓ Transcription test PASSED!");
                    android.util.Log.d("MainActivity", "Test transcription result: " + result);
                    
                    // Show a brief toast (optional)
                    mainHandler.post(() -> {
                        Toast.makeText(this, "语音识别测试通过: " + result.substring(0, Math.min(30, result.length())) + "...", 
                            Toast.LENGTH_LONG).show();
                    });
                } else {
                    android.util.Log.e("MainActivity", "✗ Transcription test FAILED: null or empty result");
                    
                    mainHandler.post(() -> {
                        Toast.makeText(this, "语音识别测试失败，请检查麦克风权限", 
                            Toast.LENGTH_LONG).show();
                    });
                }
                
                // Clean up
                tempFile.delete();
                
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Transcription test error", e);
                e.printStackTrace();
            }
        }).start();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioRecorder != null) {
            audioRecorder.release();
        }
        if (whisperManager != null) {
            whisperManager.release();
        }
        fileManager.deleteTempWavFile();
    }
}
