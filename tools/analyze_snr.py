#!/usr/bin/env python3
"""
SNR Analysis Tool for WAV Recordings
Analyzes Signal-to-Noise Ratio for comparing audio quality
"""

import wave
import struct
import sys
import os
import numpy as np
from pathlib import Path

def read_wav(filepath):
    """Read WAV file and return sample rate, audio data"""
    with wave.open(filepath, 'rb') as wf:
        n_channels = wf.getnchannels()
        sample_width = wf.getsampwidth()
        sample_rate = wf.getframerate()
        n_frames = wf.getnframes()
        
        raw_data = wf.readframes(n_frames)
        
        if sample_width == 2:
            fmt = f'<{n_frames * n_channels}h'
            audio = np.array(struct.unpack(fmt, raw_data), dtype=np.float64)
        else:
            raise ValueError(f"Unsupported sample width: {sample_width}")
        
        if n_channels == 2:
            audio = audio.reshape(-1, 2).mean(axis=1)
        
        return sample_rate, audio

def estimate_noise_floor(audio, sample_rate, noise_duration=0.3):
    """Estimate noise floor from the quietest segments"""
    frame_size = int(0.05 * sample_rate)
    n_frames = len(audio) // frame_size
    
    if n_frames < 4:
        noise_samples = int(noise_duration * sample_rate)
        if len(audio) < noise_samples:
            noise_samples = len(audio) // 4
        noise_segment = audio[:noise_samples]
        noise_power = np.mean(noise_segment ** 2)
        return noise_power
    
    frame_powers = []
    for i in range(n_frames):
        start = i * frame_size
        end = start + frame_size
        frame = audio[start:end]
        frame_powers.append(np.mean(frame ** 2))
    
    frame_powers = np.array(frame_powers)
    sorted_indices = np.argsort(frame_powers)
    
    quiet_count = max(1, n_frames // 4)
    quiet_frames = sorted_indices[:quiet_count]
    
    noise_power = np.mean([frame_powers[i] for i in quiet_frames])
    return noise_power

def estimate_signal_power(audio, sample_rate, noise_power):
    """Estimate signal power (excluding noise floor)"""
    total_power = np.mean(audio ** 2)
    signal_power = max(total_power - noise_power, noise_power * 0.01)
    return signal_power

def calculate_snr(filepath):
    """Calculate SNR for a WAV file"""
    try:
        sample_rate, audio = read_wav(filepath)
        
        noise_power = estimate_noise_floor(audio, sample_rate)
        signal_power = estimate_signal_power(audio, sample_rate, noise_power)
        
        if noise_power > 0:
            snr_linear = signal_power / noise_power
            snr_db = 10 * np.log10(snr_linear)
        else:
            snr_db = float('inf')
        
        rms = np.sqrt(np.mean(audio ** 2))
        peak = np.max(np.abs(audio))
        duration = len(audio) / sample_rate
        
        return {
            'snr_db': snr_db,
            'noise_power': noise_power,
            'signal_power': signal_power,
            'rms': rms,
            'peak': peak,
            'duration': duration,
            'sample_rate': sample_rate
        }
    except Exception as e:
        return {'error': str(e)}

def analyze_file(filepath):
    """Analyze a single file and print results"""
    filepath = Path(filepath)
    if not filepath.exists():
        print(f"File not found: {filepath}")
        return None
    
    print(f"\n{'='*60}")
    print(f"File: {filepath.name}")
    print(f"{'='*60}")
    
    result = calculate_snr(str(filepath))
    
    if 'error' in result:
        print(f"Error: {result['error']}")
        return None
    
    print(f"Duration:     {result['duration']:.2f} seconds")
    print(f"Sample Rate:  {result['sample_rate']} Hz")
    print(f"Peak Level:   {result['peak']:.1f}")
    print(f"RMS Level:    {result['rms']:.1f}")
    print(f"Noise Power:  {result['noise_power']:.1f}")
    print(f"Signal Power: {result['signal_power']:.1f}")
    print(f"SNR:          {result['snr_db']:.2f} dB")
    
    return result

def compare_files(file1, file2):
    """Compare SNR between two files"""
    result1 = analyze_file(file1)
    result2 = analyze_file(file2)
    
    if result1 and result2:
        print(f"\n{'='*60}")
        print("COMPARISON")
        print(f"{'='*60}")
        
        snr_diff = result2['snr_db'] - result1['snr_db']
        
        name1 = Path(file1).stem
        name2 = Path(file2).stem
        
        print(f"{name1}: {result1['snr_db']:.2f} dB")
        print(f"{name2}: {result2['snr_db']:.2f} dB")
        print(f"Difference:   {snr_diff:+.2f} dB")
        
        if snr_diff > 0:
            print(f"\n=> {name2} has BETTER SNR by {snr_diff:.2f} dB")
        else:
            print(f"\n=> {name1} has BETTER SNR by {-snr_diff:.2f} dB")

if __name__ == "__main__":
    recordings_dir = Path(__file__).parent.parent / "recordings"
    
    if len(sys.argv) >= 3:
        file1 = sys.argv[1]
        file2 = sys.argv[2]
    else:
        wav_files = list(recordings_dir.glob("*.wav"))
        if len(wav_files) >= 2:
            ns_on = [f for f in wav_files if "NS_on" in f.name]
            ns_off = [f for f in wav_files if "NS_off" in f.name]
            
            if ns_on and ns_off:
                file1 = str(ns_on[0])
                file2 = str(ns_off[0])
            else:
                file1 = str(wav_files[0])
                file2 = str(wav_files[1])
        else:
            print(f"Usage: python {sys.argv[0]} <file1.wav> <file2.wav>")
            print(f"Or place WAV files in: {recordings_dir}")
            sys.exit(1)
    
    compare_files(file1, file2)