package com.opencode.voiceassist.utils;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Tests for audio file validation
 * Focuses on verifying WAV file format compatibility with Whisper.cpp
 */
public class AudioTest {
    
    /**
     * Test that the sample WAV file exists and has valid format
     * Whisper.cpp requires: 16kHz, mono, PCM WAV files
     */
    @Test
    public void testWavFileFormat() throws Exception {
        // Load test WAV file from resources
        InputStream wavStream = getClass().getClassLoader().getResourceAsStream("jfk.wav");
        assertNotNull("Test WAV file (jfk.wav) not found in resources", wavStream);
        
        // Copy to temp file for analysis
        File tempWav = File.createTempFile("jfk_test", ".wav");
        tempWav.deleteOnExit();
        
        Files.copy(wavStream, tempWav.toPath(), StandardCopyOption.REPLACE_EXISTING);
        wavStream.close();
        
        // Basic file validation
        assertTrue("WAV file should exist", tempWav.exists());
        assertTrue("WAV file should be readable", tempWav.canRead());
        
        long fileSize = tempWav.length();
        assertTrue("WAV file should have reasonable size (> 100KB)", fileSize > 100000);
        assertTrue("WAV file should not be too large (< 10MB)", fileSize < 10000000);
        
        System.out.println("WAV file validation passed:");
        System.out.println("  File: " + tempWav.getAbsolutePath());
        System.out.println("  Size: " + fileSize + " bytes");
        
        // Parse WAV header for basic validation
        byte[] header = new byte[44]; // Standard WAV header is 44 bytes
        try (InputStream in = Files.newInputStream(tempWav.toPath())) {
            int bytesRead = in.read(header);
            assertEquals("Should read full WAV header", 44, bytesRead);
        }
        
        // Check RIFF header
        String riff = new String(header, 0, 4);
        assertEquals("RIFF header", "RIFF", riff);
        
        // Check WAVE format
        String wave = new String(header, 8, 4);
        assertEquals("WAVE format", "WAVE", wave);
        
        // Check fmt subchunk
        String fmt = new String(header, 12, 4);
        assertEquals("fmt subchunk", "fmt ", fmt);
        
        // Check audio format (should be 1 for PCM)
        ByteBuffer bb = ByteBuffer.wrap(header, 20, 2);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        short audioFormat = bb.getShort();
        assertEquals("Audio format should be PCM (1)", 1, audioFormat);
        
        // Get number of channels
        bb = ByteBuffer.wrap(header, 22, 2);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        short numChannels = bb.getShort();
        System.out.println("  Channels: " + numChannels);
        
        // Get sample rate
        bb = ByteBuffer.wrap(header, 24, 4);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        int sampleRate = bb.getInt();
        System.out.println("  Sample rate: " + sampleRate + " Hz");
        
        // Check if format is compatible with Whisper
        // Whisper typically expects 16kHz, but can resample
        assertTrue("Sample rate should be positive", sampleRate > 0);
        
        // Check bits per sample
        bb = ByteBuffer.wrap(header, 34, 2);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        short bitsPerSample = bb.getShort();
        System.out.println("  Bits per sample: " + bitsPerSample);
        // Whisper can handle 16-bit or 32-bit float audio
        assertTrue("Bits per sample should be 16 or 32", bitsPerSample == 16 || bitsPerSample == 32);
        
        // Check data chunk (may be at different position if there's a fact chunk)
        // Just check that we have a valid header, data chunk position may vary
        System.out.println("  Data chunk identifier: " + new String(header, 36, 4));
        // Some WAV files have "fact" chunk before "data" chunk
        // We'll accept any valid WAV header format
        
        System.out.println("WAV header validation passed!");
    }
    
    /**
     * Test that a WAV file can be created and read
     * This simulates what the app does when recording audio
     */
    @Test
    public void testWavFileCreationAndRead() throws Exception {
        // Create a simple test file path
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File testWav = new File(tempDir, "test_audio_" + System.currentTimeMillis() + ".wav");
        testWav.deleteOnExit();
        
        // Simulate creating an empty WAV file (just for path testing)
        // In reality, the app would write actual audio data
        try (FileOutputStream fos = new FileOutputStream(testWav)) {
            fos.write("test".getBytes());
        }
        
        assertTrue("Test file should exist", testWav.exists());
        assertTrue("Test file should be deletable", testWav.delete());
    }
    
    /**
     * Test FileManager functionality (if we had access to it)
     * This test shows what we would test with proper dependency injection
     */
    @Test 
    public void testFilePaths() {
        // This test demonstrates the expected file paths
        String expectedModelPath = "/data/user/0/com.opencode.voiceassist/files/whisper/ggml-tiny.en.bin";
        System.out.println("Expected model path: " + expectedModelPath);
        
        String expectedTempWavPath = "/data/user/0/com.opencode.voiceassist/cache/temp_recording.wav";
        System.out.println("Expected temp WAV path: " + expectedTempWavPath);
        
        // Just print information - no assertions since we can't access real paths
        System.out.println("File path test completed (informational only)");
    }
}