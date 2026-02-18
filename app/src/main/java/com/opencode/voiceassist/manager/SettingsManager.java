package com.opencode.voiceassist.manager;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;

import com.opencode.voiceassist.R;
import com.opencode.voiceassist.utils.Constants;
import com.opencode.voiceassist.utils.UrlUtils;

public class SettingsManager {
    private static final String TAG = "SettingsManager";
    
    private final Activity activity;
    private final Handler mainHandler;
    private final SettingsCallback callback;
    
    // References to other managers (for updating settings)
    private CloudAsrManager cloudAsrManager;
    private FunAsrWebSocketManager funAsrManager;
    private WhisperManager whisperManager;
    private RecordingManager recordingManager;
    
    public interface SettingsCallback {
        void onSettingsSaved(SettingsData settings);
        void onShowReloginDialog();
        void onRefreshPage();
        void onUpdateButtonState(RecordingManager.ButtonState state);
        void onReinitializeWhisper(String model);
        void onShowToast(String message, int duration);
    }
    
    public static class SettingsData {
        public String opencodeIp;
        public int opencodePort;
        public String opencodeUsername;
        public String opencodePassword;
        public String whisperModel;
        public boolean autoTestOnModelChange;
        public boolean autoSend;
        public String asrBackend;
        public String cloudAsrUrl;
        public String funAsrUrl;
        public String funAsrMode;
        
        // Derived fields
        public String cloudAsrHost;
        public int cloudAsrPort;
        public String funAsrHost;
        public int funAsrPort;
    }
    
    public SettingsManager(Activity activity, SettingsCallback callback) {
        this.activity = activity;
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public void setManagers(CloudAsrManager cloudAsrManager, FunAsrWebSocketManager funAsrManager,
                           WhisperManager whisperManager, RecordingManager recordingManager) {
        this.cloudAsrManager = cloudAsrManager;
        this.funAsrManager = funAsrManager;
        this.whisperManager = whisperManager;
        this.recordingManager = recordingManager;
    }
    
    public void showPopupMenu(View anchorView) {
        PopupMenu popupMenu = new PopupMenu(activity, anchorView);
        popupMenu.getMenuInflater().inflate(R.menu.menu_main, popupMenu.getMenu());
        
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_settings) {
                showSettingsDialog();
                return true;
            } else if (itemId == R.id.menu_refresh) {
                Log.d(TAG, "Refreshing WebView page");
                if (callback != null) {
                    callback.onRefreshPage();
                }
                mainHandler.post(() -> Toast.makeText(activity, "页面已刷新", Toast.LENGTH_SHORT).show());
                return true;
            } else if (itemId == R.id.menu_relogin) {
                Log.d(TAG, "Manual re-login requested");
                showReloginDialog();
                return true;
            }
            return false;
        });
        
        popupMenu.show();
    }
    
    public void showReloginDialog() {
        mainHandler.post(() -> {
            // Avoid showing multiple dialogs
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            
            if (callback != null) {
                callback.onShowReloginDialog();
            }
        });
    }
    
    public void showSettingsDialog() {
        mainHandler.post(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            View view = activity.getLayoutInflater().inflate(R.layout.dialog_settings, null);
            
            EditText etIp = view.findViewById(R.id.et_ip);
            EditText etUsername = view.findViewById(R.id.et_username);
            EditText etPassword = view.findViewById(R.id.et_password);
            ImageView ivPasswordToggle = view.findViewById(R.id.iv_password_toggle);
            
            // ASR Backend controls
            RadioGroup rgAsrBackend = view.findViewById(R.id.rg_asr_backend);
            RadioButton rbAsrCloudHttp = view.findViewById(R.id.rb_asr_cloud_http);
            RadioButton rbAsrFunasrWs = view.findViewById(R.id.rb_asr_funasr_ws);
            
            // Cloud HTTP ASR configuration
            TextView tvCloudAsrConfigLabel = view.findViewById(R.id.tv_cloud_asr_config_label);
            EditText etCloudAsrUrl = view.findViewById(R.id.et_cloud_asr_url);
            
            // FunASR WebSocket configuration
            TextView tvFunasrConfigLabel = view.findViewById(R.id.tv_funasr_config_label);
            EditText etFunasrUrl = view.findViewById(R.id.et_funasr_url);
            TextView tvFunasrModeLabel = view.findViewById(R.id.tv_funasr_mode_label);
            RadioGroup rgFunasrMode = view.findViewById(R.id.rg_funasr_mode);
            RadioButton rbFunasrModeOffline = view.findViewById(R.id.rb_funasr_mode_offline);
            RadioButton rbFunasrMode2pass = view.findViewById(R.id.rb_funasr_mode_2pass);

            // WebView settings
            android.widget.CheckBox cbAutoSend = view.findViewById(R.id.cb_auto_send);
            
            // Load saved settings
            SharedPreferences prefs = activity.getSharedPreferences("settings", Activity.MODE_PRIVATE);
            String savedIp = prefs.getString("opencode_ip", Constants.DEFAULT_OPENCODE_IP);
            int savedPort = prefs.getInt("opencode_port", Constants.DEFAULT_OPENCODE_PORT);
            String savedUsername = prefs.getString("opencode_username", Constants.DEFAULT_OPENCODE_USERNAME);
            String savedPassword = prefs.getString("opencode_password", Constants.DEFAULT_OPENCODE_PASSWORD);
            boolean autoTestEnabled = prefs.getBoolean("auto_test_on_model_change", true);
            boolean autoSendEnabled = prefs.getBoolean(Constants.KEY_AUTO_SEND, Constants.DEFAULT_AUTO_SEND);
            
            // Load ASR backend settings
            String asrBackend = prefs.getString("asr_backend", Constants.DEFAULT_ASR_BACKEND);
            String cloudAsrUrl = prefs.getString("cloud_asr_url", Constants.DEFAULT_CLOUD_ASR_URL);
            String funAsrUrl = prefs.getString("funasr_url", Constants.DEFAULT_FUNASR_URL);
            String funAsrMode = prefs.getString("funasr_mode", Constants.DEFAULT_FUNASR_MODE);
            
            etIp.setText(UrlUtils.formatServerUrl(savedIp, savedPort));
            etUsername.setText(savedUsername);
            etPassword.setText(savedPassword);
            
            // Setup password visibility toggle
            ivPasswordToggle.setOnTouchListener((v, event) -> {
                int action = event.getAction();
                if (action == android.view.MotionEvent.ACTION_DOWN) {
                    // Show password
                    etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                    // Hide password
                    etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
                // Move cursor to end
                etPassword.setSelection(etPassword.getText().length());
                return true;
            });
            
            // Set ASR backend radio button
            if (asrBackend.equals(Constants.ASR_BACKEND_CLOUD_HTTP)) {
                rbAsrCloudHttp.setChecked(true);
            } else if (asrBackend.equals(Constants.ASR_BACKEND_FUNASR_WS)) {
                rbAsrFunasrWs.setChecked(true);
            } else {
                // Default to cloud HTTP
                rbAsrCloudHttp.setChecked(true);
            }
            
            // Set Cloud ASR URL
            etCloudAsrUrl.setText(cloudAsrUrl);
            
            // Set FunASR URL
            etFunasrUrl.setText(funAsrUrl);
            if (funAsrMode.equals("2pass")) {
                rbFunasrMode2pass.setChecked(true);
            } else {
                rbFunasrModeOffline.setChecked(true);
            }
            
            // Set WebView settings
            cbAutoSend.setChecked(autoSendEnabled);
            
            // Function to update UI visibility based on ASR backend selection
            java.util.function.Consumer<String> updateBackendUI = (backend) -> {
                boolean isLocal = backend.equals(Constants.ASR_BACKEND_LOCAL);
                boolean isCloudHttp = backend.equals(Constants.ASR_BACKEND_CLOUD_HTTP);
                boolean isFunasrWs = backend.equals(Constants.ASR_BACKEND_FUNASR_WS);
                
                // Update Cloud ASR config visibility
                int cloudVisibility = isCloudHttp ? View.VISIBLE : View.GONE;
                tvCloudAsrConfigLabel.setVisibility(cloudVisibility);
                etCloudAsrUrl.setVisibility(cloudVisibility);
                
                // Update FunASR config visibility
                int funasrVisibility = isFunasrWs ? View.VISIBLE : View.GONE;
                tvFunasrConfigLabel.setVisibility(funasrVisibility);
                etFunasrUrl.setVisibility(funasrVisibility);
                tvFunasrModeLabel.setVisibility(funasrVisibility);
                rgFunasrMode.setVisibility(funasrVisibility);
            };
            
            // Apply initial state
            updateBackendUI.accept(asrBackend);
            
            // Listen for ASR backend changes
            rgAsrBackend.setOnCheckedChangeListener((group, checkedId) -> {
                String backend = Constants.ASR_BACKEND_LOCAL;
                if (checkedId == R.id.rb_asr_cloud_http) {
                    backend = Constants.ASR_BACKEND_CLOUD_HTTP;
                } else if (checkedId == R.id.rb_asr_funasr_ws) {
                    backend = Constants.ASR_BACKEND_FUNASR_WS;
                }
                updateBackendUI.accept(backend);
            });
            
            builder.setView(view)
                .setTitle("配置设置")
                .setPositiveButton("保存", (dialog, which) -> {
                     String url = etIp.getText().toString().trim();
                     String[] serverParts = UrlUtils.parseServerUrl(url);
                     String ip = serverParts[0];
                     int port;
                     try {
                         port = Integer.parseInt(serverParts[1]);
                     } catch (NumberFormatException e) {
                         port = Constants.DEFAULT_OPENCODE_PORT;
                     }
                    
                    // Determine selected ASR backend
                    String newAsrBackend = Constants.ASR_BACKEND_LOCAL;
                    if (rbAsrCloudHttp.isChecked()) {
                        newAsrBackend = Constants.ASR_BACKEND_CLOUD_HTTP;
                    } else if (rbAsrFunasrWs.isChecked()) {
                        newAsrBackend = Constants.ASR_BACKEND_FUNASR_WS;
                    }
                    
                    // Get URLs from input fields
                    String newCloudAsrUrl = etCloudAsrUrl.getText().toString().trim();
                    String newFunAsrUrl = etFunasrUrl.getText().toString().trim();
                    String newFunAsrMode = rbFunasrMode2pass.isChecked() ? "2pass" : "offline";
                    
                    // Parse URLs to get host and port for backward compatibility
                    String[] cloudAsrParts = UrlUtils.parseAsrUrl(newCloudAsrUrl, "http");
                    String[] funAsrParts = UrlUtils.parseAsrUrl(newFunAsrUrl, "ws");

                     // Get username and password
                     String username = etUsername.getText().toString().trim();
                     String password = etPassword.getText().toString().trim();
                     boolean autoSendOn = cbAutoSend.isChecked();
                     
                     // Create settings data
                     SettingsData settings = new SettingsData();
                     settings.opencodeIp = ip;
                     settings.opencodePort = port;
                     settings.opencodeUsername = username;
                     settings.opencodePassword = password;
                     settings.whisperModel = Constants.DEFAULT_WHISPER_MODEL;
                     settings.autoTestOnModelChange = false;
                     settings.autoSend = autoSendOn;
                     settings.asrBackend = newAsrBackend;
                    settings.cloudAsrUrl = newCloudAsrUrl;
                    settings.funAsrUrl = newFunAsrUrl;
                    settings.funAsrMode = newFunAsrMode;
                    settings.cloudAsrHost = cloudAsrParts[0];
                    settings.cloudAsrPort = Integer.parseInt(cloudAsrParts[1]);
                    settings.funAsrHost = funAsrParts[0];
                    settings.funAsrPort = Integer.parseInt(funAsrParts[1]);
                    
                    // Save settings via callback
                    if (callback != null) {
                        callback.onSettingsSaved(settings);
                    }
                    
                    // Show toast
                    mainHandler.post(() -> Toast.makeText(activity, "设置已保存", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("取消", null)
                .show();
        });
    }
    
    public void saveSettings(SettingsData settings) {
        SharedPreferences.Editor editor = activity.getSharedPreferences("settings", Activity.MODE_PRIVATE).edit();
        editor.putString("opencode_ip", settings.opencodeIp);
        editor.putInt("opencode_port", settings.opencodePort);
        editor.putString("opencode_username", settings.opencodeUsername);
        editor.putString("opencode_password", settings.opencodePassword);
        editor.putBoolean("auto_test_on_model_change", settings.autoTestOnModelChange);
        editor.putString("asr_backend", settings.asrBackend);
        editor.putString("cloud_asr_url", settings.cloudAsrUrl);
        editor.putString("cloud_asr_ip", settings.cloudAsrHost);
        editor.putInt("cloud_asr_port", settings.cloudAsrPort);
        editor.putString("funasr_url", settings.funAsrUrl);
        editor.putString("funasr_host", settings.funAsrHost);
        editor.putInt("funasr_port", settings.funAsrPort);
        editor.putString("funasr_mode", settings.funAsrMode);
        editor.putBoolean(Constants.KEY_AUTO_SEND, settings.autoSend);
        editor.apply();
        
        // Update managers with new settings
        if (cloudAsrManager != null) {
            cloudAsrManager.updateSettings(settings.cloudAsrHost, settings.cloudAsrPort);
        }
        if (funAsrManager != null) {
            funAsrManager.updateSettings(settings.funAsrHost, settings.funAsrPort, settings.funAsrMode);
        }
    }
}