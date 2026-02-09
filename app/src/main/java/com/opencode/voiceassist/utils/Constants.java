package com.opencode.voiceassist.utils;

public class Constants {
    
    // Whisper Model Configuration - Available models for selection
    public static final String[] WHISPER_MODEL_FILENAMES = {
        "ggml-tiny.en.bin",           // Original model (77MB, best accuracy)
        "ggml-tiny.en-q8_0.bin",      // INT8 quantized (42MB, faster, good accuracy)
        "ggml-tiny.en-q5_1.bin",      // Q5_1 quantized (31MB, fastest, good balance)
        "ggml-tiny.en-q4_0.bin"       // Q4_0 quantized (24MB, extreme speed, RTF ~1.3-1.7x)
    };
    
    public static final String[] WHISPER_MODEL_NAMES = {
        "原始模型 (77MB, 精度最高)",
        "INT8量化模型 (42MB, 速度较快)",
        "Q5_1量化模型 (31MB, 速度最快)",
        "Q4_0量化模型 (24MB, 极限速度)"
    };
    
    // Default model (Q5_1 for best speed/accuracy balance)
    public static final String DEFAULT_WHISPER_MODEL = "ggml-tiny.en-q5_1.bin";
    
    // Model Multi-Source Download URLs (No VPN required, prioritized)
    // Priority 1: HuggingFace China mirror (hf-mirror.com) - reliable in China
    public static final String WHISPER_MODEL_URL1 = "https://hf-mirror.com/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin";
    // Priority 2: whisper.cpp GitHub official repo (may be inaccessible in China)
    public static final String WHISPER_MODEL_URL2 = "https://raw.githubusercontent.com/ggerganov/whisper.cpp/master/models/ggml-tiny.en.bin";
    // Priority 3: Open source CDN mirror (may be inaccessible)
    public static final String WHISPER_MODEL_URL3 = "https://cdn.jsdelivr.net/gh/ggerganov/whisper.cpp@master/models/ggml-tiny.en.bin";
    
    // Multi-source download configuration (no VPN adaptation)
    public static final int MODEL_SINGLE_TIMEOUT = 15; // Single source timeout (s), short timeout to avoid waiting
    public static final String[] WHISPER_MODEL_URLS = {WHISPER_MODEL_URL1, WHISPER_MODEL_URL2, WHISPER_MODEL_URL3};
    
    // Skip download prompt
    public static final String TOAST_MODEL_DOWNLOAD_SKIP = "模型下载失败，已跳过！请手动放置模型后重启APP";
    
    // Asset model error
    public static final String TOAST_ASSET_MODEL_ERROR = "APK中模型文件缺失，请重新安装应用或手动放置模型文件";
    
    // OpenCode Default Configuration
    // Note: Change this to your computer's IP address on the same network
    public static final String DEFAULT_OPENCODE_IP = "192.168.1.100";
    public static final int DEFAULT_OPENCODE_PORT = 3000;
    
    // Audio Recording Configuration
    public static final int AUDIO_SAMPLE_RATE = 16000;
    public static final int AUDIO_CHANNELS = 1;
    
    // UI Constants
    public static final long TOAST_DURATION_SHORT = 2000;
    public static final long TOAST_DURATION_LONG = 3000;
}
