package com.opencode.voiceassist;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static org.junit.Assert.*;

public class CloudAsrTest {
    
    private static final String TEST_WAV_PATH = "test_zh.wav";
    private static final String CLOUD_ASR_URL = "http://192.168.66.79:10095/api/asr";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    
    @Test
    public void testCloudAsrHttpRequest() throws Exception {
        File wavFile = findWavFile();
        if (wavFile == null) {
            System.out.println("WAV file not found, skipping test");
            return;
        }
        
        System.out.println("=== Cloud ASR HTTP Test ===");
        System.out.println("WAV file: " + wavFile.getAbsolutePath());
        System.out.println("WAV size: " + wavFile.length() + " bytes");
        
        byte[] wavBytes = readFile(wavFile);
        String wavBase64 = Base64.getEncoder().encodeToString(wavBytes);
        System.out.println("Base64 size: " + wavBase64.length() + " chars");
        
        String jsonPayload = "{\"wav_base64\":\"" + wavBase64 + "\"}";
        System.out.println("JSON payload size: " + jsonPayload.length() + " chars");
        
        RequestBody body = RequestBody.create(jsonPayload, JSON);
        Request request = new Request.Builder()
                .url(CLOUD_ASR_URL)
                .post(body)
                .build();
        
        System.out.println("\nSending request to: " + CLOUD_ASR_URL);
        long startTime = System.currentTimeMillis();
        
        try (Response response = client.newCall(request).execute()) {
            long elapsed = System.currentTimeMillis() - startTime;
            
            System.out.println("\n=== Response ===");
            System.out.println("Status: " + response.code());
            System.out.println("Time: " + elapsed + "ms");
            System.out.println("Headers: " + response.headers());
            
            String responseBody = response.body() != null ? response.body().string() : "";
            System.out.println("Body length: " + responseBody.length());
            System.out.println("Body: " + responseBody);
            
            if (response.isSuccessful()) {
                System.out.println("\n✓ Request successful!");
            } else {
                System.out.println("\n✗ Request failed with status: " + response.code());
            }
            
        } catch (Exception e) {
            System.out.println("\n✗ Request failed: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Test
    public void testCloudAsrHttpRequestWithWavField() throws Exception {
        File wavFile = findWavFile();
        if (wavFile == null) {
            System.out.println("WAV file not found, skipping test");
            return;
        }
        
        System.out.println("\n=== Testing 'wav' field name ===");
        
        byte[] wavBytes = readFile(wavFile);
        String wavBase64 = Base64.getEncoder().encodeToString(wavBytes);
        
        String jsonPayload = "{\"wav\":\"" + wavBase64 + "\"}";
        
        RequestBody body = RequestBody.create(jsonPayload, JSON);
        Request request = new Request.Builder()
                .url(CLOUD_ASR_URL)
                .post(body)
                .build();
        
        System.out.println("Sending to: " + CLOUD_ASR_URL);
        
        try (Response response = client.newCall(request).execute()) {
            System.out.println("Status: " + response.code());
            System.out.println("Body: " + (response.body() != null ? response.body().string() : "null"));
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
    
    @Test
    public void testCloudAsrHttpRequestWithAudioField() throws Exception {
        File wavFile = findWavFile();
        if (wavFile == null) {
            System.out.println("WAV file not found, skipping test");
            return;
        }
        
        System.out.println("\n=== Testing 'audio' field name ===");
        
        byte[] wavBytes = readFile(wavFile);
        String wavBase64 = Base64.getEncoder().encodeToString(wavBytes);
        
        String jsonPayload = "{\"audio\":\"" + wavBase64 + "\"}";
        
        RequestBody body = RequestBody.create(jsonPayload, JSON);
        Request request = new Request.Builder()
                .url(CLOUD_ASR_URL)
                .post(body)
                .build();
        
        System.out.println("Sending to: " + CLOUD_ASR_URL);
        
        try (Response response = client.newCall(request).execute()) {
            System.out.println("Status: " + response.code());
            System.out.println("Body: " + (response.body() != null ? response.body().string() : "null"));
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
    
    @Test
    public void testCloudAsrWithRawPcm() throws Exception {
        File wavFile = findWavFile();
        if (wavFile == null) {
            System.out.println("WAV file not found, skipping test");
            return;
        }
        
        System.out.println("\n=== Cloud ASR Raw PCM Test ===");
        
        byte[] wavBytes = readFile(wavFile);
        byte[] pcmBytes = new byte[wavBytes.length - 44];
        System.arraycopy(wavBytes, 44, pcmBytes, 0, pcmBytes.length);
        
        String pcmBase64 = Base64.getEncoder().encodeToString(pcmBytes);
        System.out.println("PCM size: " + pcmBytes.length + " bytes");
        System.out.println("Base64 size: " + pcmBase64.length() + " chars");
        
        String jsonPayload = "{\"pcm_base64\":\"" + pcmBase64 + "\",\"sample_rate\":16000}";
        
        RequestBody body = RequestBody.create(jsonPayload, JSON);
        Request request = new Request.Builder()
                .url(CLOUD_ASR_URL)
                .post(body)
                .build();
        
        System.out.println("Sending PCM request...");
        
        try (Response response = client.newCall(request).execute()) {
            System.out.println("Status: " + response.code());
            System.out.println("Body: " + (response.body() != null ? response.body().string() : "null"));
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
    
    @Test
    public void testAlternativeEndpoints() throws Exception {
        File wavFile = findWavFile();
        if (wavFile == null) {
            System.out.println("WAV file not found, skipping test");
            return;
        }
        
        byte[] wavBytes = readFile(wavFile);
        String wavBase64 = Base64.getEncoder().encodeToString(wavBytes);
        
        String[] endpoints = {
            CLOUD_ASR_URL,
            "http://192.168.66.79:10095/asr",
            "http://192.168.66.79:10095/transcribe"
        };
        
        String[] fieldNames = {"wav_base64", "wav", "audio", "data", "file"};
        
        System.out.println("\n=== Testing Alternative Endpoints ===");
        
        for (String endpoint : endpoints) {
            for (String field : fieldNames) {
                String jsonPayload = "{\"" + field + "\":\"" + wavBase64 + "\"}";
                
                RequestBody body = RequestBody.create(jsonPayload, JSON);
                Request request = new Request.Builder()
                        .url(endpoint)
                        .post(body)
                        .build();
                
                System.out.println("\nTrying: " + endpoint + " with field: " + field);
                
                try (Response response = client.newCall(request).execute()) {
                    String respBody = response.body() != null ? response.body().string() : "";
                    System.out.println("  Status: " + response.code());
                    System.out.println("  Body: " + truncate(respBody, 200));
                } catch (Exception e) {
                    System.out.println("  Error: " + e.getMessage());
                }
            }
        }
    }
    
    private File findWavFile() {
        String[] paths = {
            TEST_WAV_PATH,
            "../" + TEST_WAV_PATH,
            "../../" + TEST_WAV_PATH,
            "E:/project/open_gui/" + TEST_WAV_PATH
        };
        
        for (String path : paths) {
            File f = new File(path);
            if (f.exists()) {
                return f;
            }
        }
        return null;
    }
    
    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
    
    private byte[] readFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = fis.read(data);
            return data;
        }
    }
}