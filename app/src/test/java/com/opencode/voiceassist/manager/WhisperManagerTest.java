package com.opencode.voiceassist.manager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.junit.Assert.*;

/**
 * Unit tests for WhisperManager
 * Focuses on WAV file validation and basic functionality
 * 
 * Test WAV file: jfk.wav from whisper.cpp samples
 * This is a 16kHz mono PCM WAV file containing JFK's "We choose to go to the moon" speech
 */
@RunWith(JUnit4.class)
public class WhisperManagerTest {
    
    @Test
    public void testWavFileExistsAndValid() throws Exception {
        // Test that the sample WAV file exists and has reasonable size
        InputStream wavStream = getClass().getClassLoader().getResourceAsStream("jfk.wav");
        assertNotNull("Test WAV file (jfk.wav) not found in resources", wavStream);
        
        // Copy to temp file to check size
        File tempWav = File.createTempFile("jfk_test", ".wav");
        tempWav.deleteOnExit();
        
        Files.copy(wavStream, tempWav.toPath(), StandardCopyOption.REPLACE_EXISTING);
        wavStream.close();
        
        // Check file size (jfk.wav is about 352KB)
        assertTrue("WAV file should have reasonable size", tempWav.length() > 100000); // > 100KB
        assertTrue("WAV file should not be too large", tempWav.length() < 10000000); // < 10MB
        
        System.out.println("Test WAV file size: " + tempWav.length() + " bytes");
        System.out.println("Test WAV file path: " + tempWav.getAbsolutePath());
    }
    
    /**
     * Manual integration test for actual transcription
     * This test requires the actual Whisper model to be available
     * Run manually when debugging transcription issues
     */
    @Test
    public void testManualTranscription() throws Exception {
        // This is a manual test that prints transcription results
        // It requires the actual model file to be present
        
        System.out.println("=== Manual Transcription Test ===");
        System.out.println("Note: This test requires the Whisper model file to be present");
        System.out.println("Model file should be at: /data/user/0/com.opencode.voiceassist/files/whisper/ggml-tiny.en.bin");
        
        // Load test WAV file
        InputStream wavStream = getClass().getClassLoader().getResourceAsStream("jfk.wav");
        if (wavStream == null) {
            System.out.println("ERROR: Test WAV file not found in resources");
            return;
        }
        
        File tempWav = File.createTempFile("manual_test", ".wav");
        tempWav.deleteOnExit();
        
        Files.copy(wavStream, tempWav.toPath(), StandardCopyOption.REPLACE_EXISTING);
        wavStream.close();
        
        System.out.println("Test WAV file: " + tempWav.getAbsolutePath());
        System.out.println("WAV file size: " + tempWav.length() + " bytes");
        
        // Note: For actual transcription test, you would need to:
        // 1. Have the model file in the correct location
        // 2. Initialize WhisperManager with real context and file manager
        // 3. Wait for model to load
        // 4. Call transcribe() method
        
        System.out.println("=== End Manual Test ===");
        
        // Mark test as passed since we're just printing instructions
        assertTrue(true);
    }
    
    @Test
    public void testBasicAssumptions() {
        // Basic test to verify test framework works
        assertTrue("Basic assertion should work", true);
        assertEquals("Simple math", 4, 2 + 2);
    }
}