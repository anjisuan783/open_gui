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
- Model files should be placed in assets or downloaded at runtime
- Requires RECORD_AUDIO and INTERNET permissions

## Before Committing

1. Run `./gradlew test` to ensure all tests pass
2. Build debug APK successfully with `./gradlew assembleDebug`
3. Check for Android Studio warnings (yellow highlights)
4. Ensure no hardcoded IP addresses or credentials in new code
5. Verify proper resource cleanup in `onDestroy()` methods
