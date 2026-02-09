package com.opencode.voiceassist.utils;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Diagnostic tool for testing Whisper transcription functionality
 * This tool helps diagnose issues with audio recording and transcription
 */
public class DiagnosticTool {
    
    /**
     * Run comprehensive diagnostic tests
     */
    public static void runDiagnostics() {
        System.out.println("=== Whisper Transcription Diagnostics ===");
        System.out.println();
        
        try {
            // Test 1: Check test WAV file
            testWavFile();
            
            // Test 2: Check WAV file format compatibility
            testWavFormatCompatibility();
            
            // Test 3: Simulate recording file creation
            testRecordingSimulation();
            
            // Test 4: File path validation
            testFilePathValidation();
            
            System.out.println("=== Diagnostics Completed ===");
            System.out.println("If all tests pass, the issue may be with:");
            System.out.println("1. Whisper model loading");
            System.out.println("2. Whisper library compatibility");
            System.out.println("3. Audio recording quality/volume");
            System.out.println("4. Device-specific issues");
            
        } catch (Exception e) {
            System.err.println("Diagnostic failed with exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Test 1: Verify test WAV file exists and is valid
     */
    private static void testWavFile() throws Exception {
        System.out.println("Test 1: Checking test WAV file...");
        
        // Load test WAV file from resources
        ClassLoader classLoader = DiagnosticTool.class.getClassLoader();
        InputStream wavStream = classLoader.getResourceAsStream("jfk.wav");
        
        if (wavStream == null) {
            throw new RuntimeException("Test WAV file (jfk.wav) not found in resources");
        }
        
        // Copy to temp file
        File tempWav = File.createTempFile("diagnostic_test", ".wav");
        tempWav.deleteOnExit();
        
        Files.copy(wavStream, tempWav.toPath(), StandardCopyOption.REPLACE_EXISTING);
        wavStream.close();
        
        // Basic validation
        if (!tempWav.exists()) {
            throw new RuntimeException("Failed to create temp WAV file");
        }
        
        long fileSize = tempWav.length();
        System.out.println("  ✓ WAV file loaded successfully");
        System.out.println("  File path: " + tempWav.getAbsolutePath());
        System.out.println("  File size: " + fileSize + " bytes");
        
        if (fileSize < 100000) {
            System.out.println("  ⚠ WARNING: File size is small (" + fileSize + " bytes)");
        }
        
        if (fileSize < 44) {
            throw new RuntimeException("File too small for WAV header");
        }
        
        System.out.println("  ✓ Test 1 PASSED");
        System.out.println();
    }
    
    /**
     * Test 2: Check WAV file format for Whisper compatibility
     */
    private static void testWavFormatCompatibility() throws Exception {
        System.out.println("Test 2: Checking WAV format compatibility...");
        
        ClassLoader classLoader = DiagnosticTool.class.getClassLoader();
        InputStream wavStream = classLoader.getResourceAsStream("jfk.wav");
        File tempWav = File.createTempFile("format_test", ".wav");
        tempWav.deleteOnExit();
        
        Files.copy(wavStream, tempWav.toPath(), StandardCopyOption.REPLACE_EXISTING);
        wavStream.close();
        
        // Read WAV header
        byte[] header = new byte[44];
        try (InputStream in = Files.newInputStream(tempWav.toPath())) {
            int bytesRead = in.read(header);
            if (bytesRead != 44) {
                throw new RuntimeException("Failed to read full WAV header");
            }
        }
        
        // Parse header
        String riff = new String(header, 0, 4);
        String wave = new String(header, 8, 4);
        String fmt = new String(header, 12, 4);
        
        System.out.println("  RIFF header: " + riff);
        System.out.println("  WAVE format: " + wave);
        System.out.println("  fmt chunk: " + fmt);
        
        if (!"RIFF".equals(riff)) {
            throw new RuntimeException("Invalid RIFF header: " + riff);
        }
        if (!"WAVE".equals(wave)) {
            throw new RuntimeException("Invalid WAVE format: " + wave);
        }
        
        // Check audio format (should be 1 for PCM)
        ByteBuffer bb = ByteBuffer.wrap(header, 20, 2);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        short audioFormat = bb.getShort();
        System.out.println("  Audio format: " + audioFormat + " (1 = PCM)");
        
        if (audioFormat != 1) {
            System.out.println("  ⚠ WARNING: Audio format is not PCM (1), Whisper may have issues");
        }
        
        // Get number of channels
        bb = ByteBuffer.wrap(header, 22, 2);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        short numChannels = bb.getShort();
        System.out.println("  Channels: " + numChannels);
        
        // Whisper works best with mono audio
        if (numChannels != 1) {
            System.out.println("  ⚠ WARNING: Audio is not mono, Whisper may have issues");
        }
        
        // Get sample rate
        bb = ByteBuffer.wrap(header, 24, 4);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        int sampleRate = bb.getInt();
        System.out.println("  Sample rate: " + sampleRate + " Hz");
        
        // Whisper expects 16kHz but can resample
        if (sampleRate != 16000) {
            System.out.println("  ⚠ WARNING: Sample rate is not 16kHz, Whisper will resample");
        }
        
        // Get bits per sample
        bb = ByteBuffer.wrap(header, 34, 2);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        short bitsPerSample = bb.getShort();
        System.out.println("  Bits per sample: " + bitsPerSample);
        
        // Whisper typically handles 16-bit PCM
        if (bitsPerSample != 16) {
            System.out.println("  ⚠ WARNING: Bits per sample is not 16, Whisper may have issues");
        }
        
        System.out.println("  ✓ Test 2 PASSED");
        System.out.println();
    }
    
    /**
     * Test 3: Simulate recording file creation
     */
    private static void testRecordingSimulation() throws Exception {
        System.out.println("Test 3: Simulating recording file creation...");
        
        // Create a simulated recording file (empty WAV header)
        File simulatedWav = File.createTempFile("simulated_recording", ".wav");
        simulatedWav.deleteOnExit();
        
        // Create a minimal WAV header (like AudioRecorder does)
        try (FileOutputStream fos = new FileOutputStream(simulatedWav)) {
            // Write minimal WAV header (44 bytes)
            byte[] header = new byte[44];
            
            // RIFF header
            header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
            // File size - 8 (placeholder)
            header[4] = 36; header[5] = 0; header[6] = 0; header[7] = 0;
            // WAVE
            header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
            
            // fmt chunk
            header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
            header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
            header[20] = 1; header[21] = 0; // PCM
            header[22] = 1; header[23] = 0; // Mono
            // Sample rate 16000
            header[24] = (byte) (16000 & 0xff);
            header[25] = (byte) ((16000 >> 8) & 0xff);
            header[26] = (byte) ((16000 >> 16) & 0xff);
            header[27] = (byte) ((16000 >> 24) & 0xff);
            // Byte rate
            int byteRate = 16000 * 1 * 16 / 8;
            header[28] = (byte) (byteRate & 0xff);
            header[29] = (byte) ((byteRate >> 8) & 0xff);
            header[30] = (byte) ((byteRate >> 16) & 0xff);
            header[31] = (byte) ((byteRate >> 24) & 0xff);
            // Block align
            header[32] = (byte) (1 * 16 / 8); header[33] = 0;
            // Bits per sample
            header[34] = 16; header[35] = 0;
            
            // data chunk
            header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
            // Data size (0 for empty file)
            header[40] = 0; header[41] = 0; header[42] = 0; header[43] = 0;
            
            fos.write(header);
        }
        
        System.out.println("  Simulated WAV file created: " + simulatedWav.getAbsolutePath());
        System.out.println("  File size: " + simulatedWav.length() + " bytes");
        
        // Verify it has proper WAV header
        if (simulatedWav.length() < 44) {
            throw new RuntimeException("Simulated WAV file too small");
        }
        
        System.out.println("  ✓ Test 3 PASSED");
        System.out.println();
    }
    
    /**
     * Test 4: Validate file paths and permissions
     */
    private static void testFilePathValidation() {
        System.out.println("Test 4: Validating file paths and permissions...");
        
        // Check temp directory
        String tempDir = System.getProperty("java.io.tmpdir");
        System.out.println("  Temp directory: " + tempDir);
        
        File tempDirFile = new File(tempDir);
        if (!tempDirFile.exists()) {
            throw new RuntimeException("Temp directory does not exist");
        }
        if (!tempDirFile.canWrite()) {
            throw new RuntimeException("Cannot write to temp directory");
        }
        
        System.out.println("  ✓ Temp directory is writable");
        
        // Check expected app paths (informational)
        System.out.println("  Expected app paths:");
        System.out.println("    Model: /data/user/0/com.opencode.voiceassist/files/whisper/ggml-tiny.en.bin");
        System.out.println("    Temp WAV: /data/user/0/com.opencode.voiceassist/cache/temp_recording.wav");
        
        System.out.println("  ✓ Test 4 PASSED");
        System.out.println();
    }
    
    /**
     * Run quick diagnostics from command line
     */
    public static void main(String[] args) {
        System.out.println("Running Whisper Transcription Diagnostics...");
        runDiagnostics();
    }
}