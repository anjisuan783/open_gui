package com.whispercppdemo.whisper;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class WaveEncoder {
    private static final String TAG = "WaveEncoder";

    public static float[] decodeWaveFile(File file) throws IOException {
        Log.d(TAG, "Decoding WAV file: " + file.getAbsolutePath() + ", size: " + file.length() + " bytes");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(baos.toByteArray());
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

        // Read WAV header
        byte[] riff = new byte[4];
        byteBuffer.get(riff);
        if (!new String(riff).equals("RIFF")) {
            throw new IOException("Not a valid RIFF file");
        }
        
        byteBuffer.getInt(); // file size - 8
        
        byte[] wave = new byte[4];
        byteBuffer.get(wave);
        if (!new String(wave).equals("WAVE")) {
            throw new IOException("Not a valid WAVE file");
        }
        
        // Parse chunks to find fmt and data
        int channel = 1;
        int sampleRate = 16000; // Default, will be updated from fmt chunk
        int bitsPerSample = 16; // Default, will be updated from fmt chunk
        int dataStart = -1;
        int dataSize = 0;
        
        while (byteBuffer.remaining() >= 8) {
            int chunkStart = byteBuffer.position();
            byte[] chunkIdBytes = new byte[4];
            byteBuffer.get(chunkIdBytes);
            String chunkId = new String(chunkIdBytes);
            int chunkSize = byteBuffer.getInt();
            
            if (chunkId.equals("fmt ")) {
                // Read fmt chunk
                int audioFormat = byteBuffer.getShort() & 0xFFFF;
                channel = byteBuffer.getShort() & 0xFFFF;
                sampleRate = byteBuffer.getInt();
                int byteRate = byteBuffer.getInt();
                int blockAlign = byteBuffer.getShort() & 0xFFFF;
                bitsPerSample = byteBuffer.getShort() & 0xFFFF;
                
                Log.d(TAG, "WAV format: audioFormat=" + audioFormat + ", channels=" + channel + 
                      ", sampleRate=" + sampleRate + ", bitsPerSample=" + bitsPerSample);
                
                // Validate format for Whisper (expects 16kHz mono 16-bit PCM)
                if (audioFormat != 1) {
                    throw new IOException("Only PCM audio format supported (format=" + audioFormat + ")");
                }
                if (sampleRate != 16000) {
                    Log.w(TAG, "Sample rate is " + sampleRate + " Hz, Whisper expects 16000 Hz. May cause issues.");
                }
                if (bitsPerSample != 16) {
                    throw new IOException("Only 16-bit samples supported (bits=" + bitsPerSample + ")");
                }
                
                // Skip any extra fmt data
                int bytesRead = 16;
                if (chunkSize > bytesRead) {
                    byteBuffer.position(byteBuffer.position() + (chunkSize - bytesRead));
                }
            } else if (chunkId.equals("data")) {
                // Found data chunk
                dataStart = byteBuffer.position();
                dataSize = chunkSize;
                Log.d(TAG, "Found data chunk at position " + dataStart + ", size: " + dataSize + " bytes");
                Log.d(TAG, "Expected samples: " + (dataSize / (channel * 2)) + " (duration: " + 
                      (dataSize / (channel * 2.0 * sampleRate)) + " sec)");
                break;
            } else {
                // Skip other chunks (LIST, etc.)
                byteBuffer.position(byteBuffer.position() + chunkSize);
            }
            
            // Chunk sizes are padded to even bytes
            if (chunkSize % 2 != 0) {
                byteBuffer.position(byteBuffer.position() + 1);
            }
        }
        
        if (dataStart == -1) {
            throw new IOException("No data chunk found in WAV file");
        }
        
        // Set position to start of audio data
        byteBuffer.position(dataStart);
        
        // Calculate sample count based on bits per sample
        int bytesPerSample = bitsPerSample / 8;
        int sampleCount = dataSize / bytesPerSample; // Total samples across all channels
        
        // Read audio data
        if (bytesPerSample == 2) {
            // 16-bit samples - optimized direct conversion to float
            float[] output = new float[sampleCount / channel];
            
            if (channel == 1) {
                // Mono: direct conversion
                for (int i = 0; i < output.length; i++) {
                    short sample = byteBuffer.getShort();
                    output[i] = Math.max(-1f, Math.min(1f, sample / 32767.0f));
                }
            } else {
                // Stereo: average channels
                for (int i = 0; i < output.length; i++) {
                    short left = byteBuffer.getShort();
                    short right = byteBuffer.getShort();
                    output[i] = Math.max(-1f, Math.min(1f, (left + right) / 32767.0f / 2.0f));
                }
            }
            
            Log.d(TAG, "Decoded " + output.length + " audio samples (" + 
                  (output.length / (float)sampleRate) + " seconds)");
            return output;
        } else if (bytesPerSample == 1) {
            // 8-bit samples (unsigned)
            byte[] byteArray = new byte[sampleCount];
            byteBuffer.get(byteArray);
            
            float[] output = new float[sampleCount / channel];
            for (int index = 0; index < output.length; index++) {
                if (channel == 1) {
                    // Convert from unsigned 8-bit to signed float
                    output[index] = Math.max(-1f, Math.min(1f, ((byteArray[index] & 0xFF) - 128) / 127.0f));
                } else {
                    // Stereo: average channels
                    float left = ((byteArray[2 * index] & 0xFF) - 128) / 127.0f;
                    float right = ((byteArray[2 * index + 1] & 0xFF) - 128) / 127.0f;
                    output[index] = Math.max(-1f, Math.min(1f, (left + right) / 2.0f));
                }
            }
            
            Log.d(TAG, "Decoded " + output.length + " audio samples (" + 
                  (output.length / (float)sampleRate) + " seconds)");
            return output;
        } else {
            throw new IOException("Unsupported bits per sample: " + bitsPerSample);
        }
    }

    public static void encodeWaveFile(File file, short[] data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(headerBytes(data.length * 2));

            ByteBuffer buffer = ByteBuffer.allocate(data.length * 2);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.asShortBuffer().put(data);
            fos.write(buffer.array());
        }
    }

    private static byte[] headerBytes(int dataLength) {
        ByteBuffer buffer = ByteBuffer.allocate(44);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        // RIFF header
        buffer.put("RIFF".getBytes());
        buffer.putInt(dataLength + 36);
        buffer.put("WAVE".getBytes());
        // fmt subchunk
        buffer.put("fmt ".getBytes());
        buffer.putInt(16); // Subchunk size
        buffer.putShort((short) 1); // Audio format (PCM)
        buffer.putShort((short) 1); // Channels (mono)
        buffer.putInt(16000); // Sample rate (16kHz)
        buffer.putInt(16000 * 2); // Byte rate
        buffer.putShort((short) 2); // Block align
        buffer.putShort((short) 16); // Bits per sample
        // data subchunk
        buffer.put("data".getBytes());
        buffer.putInt(dataLength);
        return buffer.array();
    }
}