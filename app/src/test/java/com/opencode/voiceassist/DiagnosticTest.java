package com.opencode.voiceassist;

import com.opencode.voiceassist.utils.DiagnosticTool;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Diagnostic test suite for Whisper transcription
 * Runs comprehensive diagnostics to identify issues
 */
public class DiagnosticTest {
    
    @Test
    public void testDiagnostics() {
        System.out.println("Running comprehensive diagnostics...");
        
        try {
            // Run diagnostic tool
            DiagnosticTool.runDiagnostics();
            
            // If we get here without exceptions, diagnostics passed
            assertTrue("Diagnostics completed successfully", true);
            
        } catch (Exception e) {
            System.err.println("Diagnostic test failed: " + e.getMessage());
            e.printStackTrace();
            fail("Diagnostic test failed: " + e.getMessage());
        }
    }
    
    @Test
    public void testBasicInfrastructure() {
        // Basic test to ensure test framework works
        System.out.println("Testing basic infrastructure...");
        
        // Check that we can access resources
        ClassLoader classLoader = getClass().getClassLoader();
        assertNotNull("ClassLoader should not be null", classLoader);
        
        // Check that test WAV file exists in resources
        java.io.InputStream wavStream = classLoader.getResourceAsStream("jfk.wav");
        assertNotNull("Test WAV file should exist in resources", wavStream);
        
        try {
            wavStream.close();
        } catch (java.io.IOException e) {
            // Ignore
        }
        
        System.out.println("Basic infrastructure test passed");
        assertTrue(true);
    }
    
    @Test
    public void testWavFileValidation() throws Exception {
        System.out.println("Testing WAV file validation...");
        
        ClassLoader classLoader = getClass().getClassLoader();
        java.io.InputStream wavStream = classLoader.getResourceAsStream("jfk.wav");
        assertNotNull(wavStream);
        
        // Copy to temp file
        java.io.File tempWav = java.io.File.createTempFile("validation_test", ".wav");
        tempWav.deleteOnExit();
        
        java.nio.file.Files.copy(wavStream, tempWav.toPath(), 
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        wavStream.close();
        
        // Validate file
        assertTrue("WAV file should exist", tempWav.exists());
        assertTrue("WAV file should be readable", tempWav.canRead());
        
        long fileSize = tempWav.length();
        assertTrue("WAV file should have reasonable size", fileSize > 100000);
        assertTrue("WAV file should not be too large", fileSize < 10000000);
        
        // Check WAV header
        byte[] header = new byte[44];
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(tempWav.toPath())) {
            int bytesRead = in.read(header);
            assertEquals("Should read full WAV header", 44, bytesRead);
        }
        
        // Check RIFF header
        String riff = new String(header, 0, 4);
        assertEquals("RIFF header", "RIFF", riff);
        
        System.out.println("WAV file validation passed");
        System.out.println("File: " + tempWav.getAbsolutePath());
        System.out.println("Size: " + fileSize + " bytes");
    }
}