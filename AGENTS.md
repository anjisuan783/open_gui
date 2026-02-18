# AGENTS.md - OpenCode Voice Assistant (Android)

This file provides guidelines for AI agents working on this Android Java project.

## Build Commands

```bash
# Build debug APK
./gradlew.bat assembleDebug          # Windows
./gradlew assembleDebug              # Linux/Mac

# Build release APK
./gradlew.bat assembleRelease        # Windows
./gradlew assembleRelease            # Linux/Mac

# Clean build
./gradlew.bat clean                  # Windows
./gradlew clean                      # Linux/Mac

# Install debug APK to connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Test Commands

```bash
# Run all unit tests
./gradlew.bat test                   # Windows
./gradlew test                       # Linux/Mac

# Run single test class
./gradlew.bat test --tests "com.opencode.voiceassist.manager.WhisperManagerTest"
./gradlew test --tests "com.opencode.voiceassist.manager.WhisperManagerTest"

# Run single test method
./gradlew.bat test --tests "com.opencode.voiceassist.manager.WhisperManagerTest.testWavFileExistsAndValid"
./gradlew test --tests "com.opencode.voiceassist.manager.WhisperManagerTest.testWavFileExistsAndValid"

# Run instrumented tests (requires connected device/emulator)
./gradlew.bat connectedAndroidTest   # Windows
./gradlew connectedAndroidTest       # Linux/Mac
```

## Code Style Guidelines

### Formatting
- **Indentation**: 4 spaces (no tabs)
- **Line endings**: LF (Unix-style)
- **Maximum line length**: 120 characters
- **Braces**: Opening brace on same line (Egyptian style)

### Naming Conventions
- **Classes**: PascalCase (e.g., `RecordingManager`, `MainActivity`)
- **Methods**: camelCase (e.g., `startRecording()`, `onCreate()`)
- **Variables**: camelCase (e.g., `isRecording`, `audioRecorder`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `DEFAULT_ASR_BACKEND`, `AUDIO_SAMPLE_RATE`)
- **Packages**: lowercase, reverse domain (e.g., `com.opencode.voiceassist`)
- **Private fields**: no underscore prefix (e.g., `private Handler mainHandler`)
- **Static final fields**: ALL_CAPS (e.g., `private static final String TAG`)

### Imports
- Group imports by package
- Android imports first, then third-party, then project
- Remove unused imports
- Use wildcard imports only for static constants

Example:
```java
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.opencode.voiceassist.manager.WhisperManager;
import com.opencode.voiceassist.utils.Constants;
```

### Architecture Patterns

#### Manager Pattern
Use Manager classes for modular functionality:
- `RecordingManager` - Audio recording and transcription
- `WhisperManager` - Whisper model operations
- `WebViewManager` - WebView configuration and control
- `SettingsManager` - User settings and preferences

#### Callback Pattern
Define callbacks as inner interfaces for loose coupling:
```java
public interface RecordingCallback {
    void onRecordingStateChanged(ButtonState state);
    void onTranscriptionComplete(TranscriptionResult result);
    void onTranscriptionError(String error);
}
```

### Error Handling
- Use `try-catch` blocks for IO operations
- Log errors with `Log.e(TAG, "message", exception)`
- Show user-friendly Toast messages on UI thread
- Handle null checks before method calls
- Validate file existence before operations

### Logging
- Use `Log.d()`, `Log.e()`, `Log.w()` from `android.util.Log`
- Define `TAG` constant in each class
- Include context in log messages
- Remove debug logs before committing production code

### Threading
- Use `Handler` with `Looper.getMainLooper()` for UI updates
- Run heavy operations (model loading, transcription) in background threads
- Use `runOnUiThread()` or `mainHandler.post()` for UI updates from background

### Android Specific
- Extend `AppCompatActivity` for activities
- Use `ActivityCompat.checkSelfPermission()` for permissions
- Request permissions at runtime for Android 6.0+
- Clean up resources in `onDestroy()`

### Comments
- Use Javadoc for public methods and classes
- Use inline comments for complex logic
- Use `// TODO:` for temporary workarounds
- Use Chinese comments for user-facing strings

### Testing
- Place unit tests in `app/src/test/java/`
- Use JUnit 4 with `@RunWith(JUnit4.class)`
- Mock external dependencies (Context, Android classes)
- Include test resources in `app/src/test/resources/`
- Name test methods descriptively: `testMethodNameScenario()`

## Project Structure

```
app/src/main/java/com/opencode/voiceassist/
├── MainActivity.java           # Main entry point
├── manager/                    # Business logic managers
├── model/                      # Data models
├── ui/                         # UI adapters and custom views
└── utils/                      # Utility classes and constants

app/src/main/res/
├── drawable/                   # XML drawables and images
├── layout/                     # XML layouts
├── values/                     # Strings, colors, dimensions
└── xml/                        # Configuration files
```

## Dependencies

Key dependencies (from `app/build.gradle`):
- AndroidX AppCompat, Material Design, ConstraintLayout
- RecyclerView for lists
- OkHttp 4.11.0 for HTTP/WebSocket
- JUnit 4.13.2 + Mockito for testing

## Important Notes

- Minimum SDK: API 24 (Android 8.0)
- Target SDK: API 34
- Java compatibility: Java 8 (VERSION_1_8)
- Uses whisper.cpp for local ASR
- Supports multiple ASR backends: Local Whisper, Cloud HTTP, FunASR WebSocket
- Model files are bundled in APK via Git LFS (see below)
- Requires RECORD_AUDIO and INTERNET permissions

## Git LFS (Large File Storage)

This project uses Git LFS to manage the Whisper model files:

### Setup for New Clones

```bash
# Install Git LFS (if not already installed)
git lfs install

# Clone the repository (LFS files will be downloaded automatically)
git clone <repository-url>

# If LFS files are missing after clone, pull them manually
git lfs pull
```

### Model Files

The following model is bundled in the APK:
- `app/src/main/assets/whisper/ggml-tiny.en-q8_0.bin` (42MB)
  - INT8 quantized model
  - Downloaded from: https://hf-mirror.com/ggerganov/whisper.cpp/
  - Recommended: Best balance of speed and accuracy

### Adding New Models

If you need to add a different model version:

```bash
# Download the model (e.g., from HuggingFace mirror)
curl -L -o app/src/main/assets/whisper/ggml-tiny.en.bin \
  "https://hf-mirror.com/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin"

# Git LFS will automatically track *.bin files
git add app/src/main/assets/whisper/ggml-tiny.en.bin
git commit -m "Add original Whisper model (77MB)"
```

### Native Libraries (whisper.cpp)

**⚠️ IMPORTANT: Native libraries (.so files) are currently MISSING from this repository!**

The project requires whisper.cpp native libraries to enable local speech recognition:
- `libwhisper.so` (base library)
- `libwhisper_v8fp16_va.so` (ARM64 with FP16 optimization)
- `libwhisper_vfpv4.so` (ARM32 with VFPv4 optimization)

### Expected Location

```
app/src/main/jniLibs/
├── arm64-v8a/
│   ├── libwhisper.so
│   ├── libwhisper_v8fp16_va.so
│   └── libwhisper_vfpv4.so
├── armeabi-v7a/
│   ├── libwhisper.so
│   └── libwhisper_vfpv4.so
├── x86/
│   └── libwhisper.so
└── x86_64/
    └── libwhisper.so
```

### How to Obtain Native Libraries

#### Option 1: Download Pre-compiled Binaries (Recommended)

Pre-compiled binaries may be available from:
- https://github.com/ggerganov/whisper.cpp/releases
- https://hf-mirror.com/ggerganov/whisper.cpp (China mirror)

Place downloaded .so files in the appropriate `jniLibs/<abi>/` directories.

#### Option 2: Build from Source

If you have the whisper.cpp source:

```bash
# Clone whisper.cpp
git clone https://github.com/ggerganov/whisper.cpp.git
cd whisper.cpp

# Build for Android (requires Android NDK)
mkdir build-android && cd build-android

cmake -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a \
      -DANDROID_PLATFORM=android-24 \
      -DCMAKE_BUILD_TYPE=Release \
      -DBUILD_SHARED_LIBS=ON \
      ..

make

# Copy the built libraries to jniLibs
cp libwhisper.so ../app/src/main/jniLibs/arm64-v8a/
```

#### Option 3: Use Alternative Backends

If you cannot obtain the native libraries, configure the app to use alternative ASR backends:

**Settings → ASR Backend → Select "云端HTTP" or "FunASR WebSocket"**

This bypasses the local Whisper library requirement.

## Troubleshooting

**Issue: "Whisper 原生库加载失败，录音禁用" (Native library load failed)**
- **Cause**: Missing `libwhisper.so` native libraries in APK
- **Solution**: 
  1. Add native libraries to `app/src/main/jniLibs/`
  2. Or switch to cloud ASR backend in app settings
  3. Rebuild APK: `./gradlew clean assembleDebug`

**Issue: Model file shows as text pointer instead of binary**
```bash
# Pull the actual LFS files
git lfs pull

# Or re-clone with LFS
GIT_LFS_SKIP_SMUDGE=0 git clone <repository-url>
```

**Issue: "模型未放置" error on device**
- The APK may not have been built with the model included
- Rebuild: `./gradlew clean assembleDebug`
- Verify APK contents: `unzip -l app-debug.apk | grep whisper`

## Before Committing

1. Run `./gradlew test` to ensure all tests pass
2. Build debug APK successfully with `./gradlew assembleDebug`
3. Check for Android Studio warnings (yellow highlights)
4. Ensure no hardcoded IP addresses or credentials in new code
5. Verify proper resource cleanup in `onDestroy()` methods
