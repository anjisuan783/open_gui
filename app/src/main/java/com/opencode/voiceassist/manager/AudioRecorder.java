package com.opencode.voiceassist.manager;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("MissingPermission")
public class AudioRecorder {

    private static final String TAG = "AudioRecorder";

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    private AudioRecord audioRecord;
    private ExecutorService executor;
    private volatile boolean isRecording = false;
    private volatile boolean isReady = true;  // Track if ready for next recording
    private File currentWavFile;
    
    public AudioRecorder() {
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    @SuppressLint("MissingPermission")
    public void startRecording(File wavFile) {
        if (isRecording || !isReady) {
            Log.w(TAG, "Cannot start recording - isRecording=" + isRecording + ", isReady=" + isReady);
            return;
        }

        this.currentWavFile = wavFile;
        this.isRecording = true;
        this.isReady = false;

        executor.execute(() -> {
            int retryCount = 0;
            final int maxRetries = 3;
            final long retryDelayMs = 100;

            try {
                // Retry AudioRecord initialization
                while (retryCount < maxRetries) {
                    audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        BUFFER_SIZE
                    );

                    // Check if AudioRecord was properly initialized
                    if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                        Log.d(TAG, "AudioRecord initialized successfully on attempt " + (retryCount + 1));
                        break;
                    }

                    // Initialization failed, release and retry
                    Log.w(TAG, "AudioRecord initialization failed on attempt " + (retryCount + 1) + ", retrying...");
                    audioRecord.release();
                    audioRecord = null;
                    retryCount++;

                    if (retryCount < maxRetries) {
                        try {
                            Thread.sleep(retryDelayMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }

                // Check if we successfully initialized after retries
                if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord initialization failed after " + maxRetries + " attempts");
                    isRecording = false;
                    isReady = true;
                    return;
                }

                audioRecord.startRecording();
                
                FileOutputStream fos = new FileOutputStream(wavFile);
                byte[] buffer = new byte[BUFFER_SIZE];
                
                // Write WAV header placeholder
                writeWavHeader(fos, 0);
                
                int totalAudioLen = 0;
                
                while (isRecording) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        fos.write(buffer, 0, read);
                        totalAudioLen += read;
                    }
                }
                
                fos.close();
                
                // Update WAV header with actual data size
                updateWavHeader(wavFile, totalAudioLen);
                
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (audioRecord != null) {
                    try {
                        if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                                audioRecord.stop();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error stopping AudioRecord", e);
                    } finally {
                        try {
                            audioRecord.release();
                        } catch (Exception e) {
                            Log.e(TAG, "Error releasing AudioRecord", e);
                        }
                        audioRecord = null;
                    }
                }
                isReady = true;
                Log.d(TAG, "Recording thread completed, isReady set to true");
            }
        });
    }
    
    public void stopRecording() {
        isRecording = false;
    }
    
    private void writeWavHeader(FileOutputStream out, long totalAudioLen) throws IOException {
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = SAMPLE_RATE;
        int channels = 1;
        long byteRate = 16 * SAMPLE_RATE * channels / 8;
        
        byte[] header = new byte[44];
        
        // RIFF/WAVE header
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        
        // 'fmt ' chunk
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0; // PCM
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8); header[33] = 0;
        header[34] = 16; header[35] = 0;
        
        // data chunk
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        
        out.write(header, 0, 44);
    }
    
    private void updateWavHeader(File wavFile, long totalAudioLen) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(wavFile, "rw");
        raf.seek(4);
        raf.writeInt(Integer.reverseBytes((int) (totalAudioLen + 36)));
        raf.seek(40);
        raf.writeInt(Integer.reverseBytes((int) totalAudioLen));
        raf.close();
    }
    
    public void release() {
        stopRecording();
        executor.shutdown();
    }
    
    public boolean isRecording() {
        return isRecording;
    }

    public boolean isReady() {
        return isReady;
    }
}
