# AGENTS.md - OpenVoice Voice Assistant (Android)

Guidelines for AI agents working on this Android Java project.

## Important Rules

- **NEVER use `taskkill` command** - it kills all node processes. Use the provided `kill-vite.bat` script instead.

## Build Commands

```bash
# Build debug/release APK
./gradlew.bat assembleDebug          # Windows
./gradlew assembleDebug              # Linux/Mac
./gradlew.bat assembleRelease

# Clean build
./gradlew.bat clean

# Install to device
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Test Commands

```bash
# Run all unit tests
./gradlew.bat test

# Run single test class
./gradlew.bat test --tests "com.opencode.voiceassist.manager.WhisperManagerTest"

# Run single test method
./gradlew.bat test --tests "com.opencode.voiceassist.manager.WhisperManagerTest.testWavFileExistsAndValid"
```

## Code Style

### Formatting
- **Indentation**: 4 spaces (no tabs)
- **Max line length**: 120 characters
- **Braces**: Opening brace on same line

### Naming
- Classes: PascalCase (`RecordingManager`)
- Methods/variables: camelCase (`startRecording()`)
- Constants: UPPER_SNAKE_CASE
- Private fields: no underscore prefix
- TAG: `private static final String TAG`

### Imports (grouped)
```java
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import com.opencode.voiceassist.manager.WhisperManager;
```

### Architecture
- **Manager Pattern**: RecordingManager, WhisperManager, WebViewManager, SettingsManager
- **Callbacks**: Define as inner interfaces
```java
public interface RecordingCallback {
    void onRecordingStateChanged(ButtonState state);
    void onTranscriptionComplete(TranscriptionResult result);
}
```

### Error Handling
- Use try-catch for IO operations
- Log with `Log.e(TAG, "message", exception)`
- Show Toast on UI thread
- Validate file existence before operations

### Threading
- Use `Handler` with `Looper.getMainLooper()` for UI updates
- Run heavy operations in background threads
- Use `runOnUiThread()` or `mainHandler.post()` for UI updates

### Android Specific
- Extend `AppCompatActivity`
- Runtime permissions for Android 6.0+
- Clean up resources in `onDestroy()`

### Testing
- Unit tests in `app/src/test/java/`
- JUnit 4 with `@RunWith(JUnit4.class)`
- Mock external dependencies

## Project Structure

```
app/src/main/java/com/opencode/voiceassist/
├── MainActivity.java
├── manager/        # Business logic
├── model/          # Data models
├── ui/             # Adapters and custom views
└── utils/          # Utilities and constants

app/src/main/res/
├── drawable/, layout/, values/, xml/
```

## Dependencies
- AndroidX AppCompat, Material, ConstraintLayout
- OkHttp 4.11.0 for HTTP/WebSocket
- JUnit 4.13.2 + Mockito for testing

## SDK
- Min SDK: 24 (Android 8.0)
- Target SDK: 34
- Java: 8

## Important Notes
- Supports multiple ASR backends: Local Whisper, Cloud HTTP, FunASR WebSocket
- Native libraries (.so files) may need to be added to `app/src/main/jniLibs/`
- Model files managed via Git LFS in `app/src/main/assets/whisper/`

## Before Committing

1. Run `./gradlew test` - all tests must pass
2. Run `./gradlew assembleDebug` - build must succeed
3. No hardcoded IPs or credentials
4. Verify resource cleanup in `onDestroy()`
