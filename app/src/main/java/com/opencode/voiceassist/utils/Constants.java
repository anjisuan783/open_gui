package com.opencode.voiceassist.utils;

public class Constants {
    
    // OpenCode Default Configuration
    public static final String DEFAULT_OPENCODE_IP = "127.0.0.1";
    public static final int DEFAULT_OPENCODE_PORT = 4096;
    public static final String DEFAULT_OPENCODE_USERNAME = "opencode_linaro_dev";
    public static final String DEFAULT_OPENCODE_PASSWORD = "abcd@1234";
    
    // Audio Recording Configuration
    public static final int AUDIO_SAMPLE_RATE = 16000;
    public static final int AUDIO_CHANNELS = 1;
    
    // UI Constants
    public static final long TOAST_DURATION_SHORT = 2000;
    public static final long TOAST_DURATION_LONG = 3000;

    // Cloud ASR Configuration
    public static final String DEFAULT_CLOUD_ASR_URL = "http://192.168.66.79:10095";
    public static final String DEFAULT_CLOUD_ASR_IP = "192.168.66.79";
    public static final int DEFAULT_CLOUD_ASR_PORT = 10095;
    
    // FunASR WebSocket Configuration
    public static final String DEFAULT_FUNASR_URL = "ws://67.0.0.5:10095";
    public static final String DEFAULT_FUNASR_HOST = "67.0.0.5";
    public static final int DEFAULT_FUNASR_PORT = 10095;
    public static final String DEFAULT_FUNASR_MODE = "2pass";
    
    // ASR Backend Types
    public static final String ASR_BACKEND_CLOUD_HTTP = "cloud_http";
    public static final String ASR_BACKEND_FUNASR_WS = "funasr_ws";
    public static final String DEFAULT_ASR_BACKEND = ASR_BACKEND_FUNASR_WS;
    
    // Audio Processor Types
    public static final String AUDIO_PROCESSOR_DIRECT = "direct";
    public static final String AUDIO_PROCESSOR_NOISE_REDUCTION = "noise_reduction";
    public static final String DEFAULT_AUDIO_PROCESSOR = AUDIO_PROCESSOR_DIRECT;
    
    // WebView Settings
    public static final String KEY_AUTO_SEND = "auto_send";
    public static final boolean DEFAULT_AUTO_SEND = true;
}