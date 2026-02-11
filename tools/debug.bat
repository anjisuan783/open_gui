@echo off
setlocal

echo ========================================
echo Debug OpenCode Voice Assistant
echo ========================================
echo.

echo [1] Checking ADB...
adb version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: ADB not found. Please install Android SDK.
    exit /b 1
)
echo ADB is ready.
echo.

echo [2] Checking connected devices...
adb devices
echo.

echo [3] Checking if app is running...
adb shell ps | findstr "com.opencode.voiceassist" >nul
if %errorlevel% equ 0 (
    echo App is running.
    echo.
    echo [4] Collecting app information...
    echo.
    echo --- Process Info ---
    adb shell ps | findstr "com.opencode.voiceassist"
    echo.
    echo --- Memory Usage ---
    adb shell dumpsys meminfo com.opencode.voiceassist | findstr "Total"
    echo.
    echo --- Recent Logs ---
    adb logcat -d -t 50 | findstr "MainActivity OpenCode Whisper"
) else (
    echo App is NOT running.
    echo.
    echo Please install and start the app first:
    echo   install.bat
)

echo.
echo Debug info collection complete.

endlocal
