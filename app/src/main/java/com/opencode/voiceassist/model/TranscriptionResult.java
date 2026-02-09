package com.opencode.voiceassist.model;

public class TranscriptionResult {
    private String text;
    private double audioLengthSeconds;
    private long processingTimeMs;
    private double realtimeFactor;
    
    public TranscriptionResult(String text, double audioLengthSeconds, long processingTimeMs, double realtimeFactor) {
        this.text = text;
        this.audioLengthSeconds = audioLengthSeconds;
        this.processingTimeMs = processingTimeMs;
        this.realtimeFactor = realtimeFactor;
    }
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    public double getAudioLengthSeconds() {
        return audioLengthSeconds;
    }
    
    public void setAudioLengthSeconds(double audioLengthSeconds) {
        this.audioLengthSeconds = audioLengthSeconds;
    }
    
    public long getProcessingTimeMs() {
        return processingTimeMs;
    }
    
    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }
    
    public double getRealtimeFactor() {
        return realtimeFactor;
    }
    
    public void setRealtimeFactor(double realtimeFactor) {
        this.realtimeFactor = realtimeFactor;
    }
    
    @Override
    public String toString() {
        return String.format("Text: %s (Audio: %.2fs, Process: %dms, RTF: %.1fx)", 
            text, audioLengthSeconds, processingTimeMs, realtimeFactor);
    }
}