@echo off
setlocal

echo ========================================
echo Collect Android Logs - OpenCode Voice Assistant
echo ========================================
echo.

set "LOG_FILE=..\logs\logcat_%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%.txt"
set "LOG_FILE=%LOG_FILE: =0%"

echo Log file will be saved to: %LOG_FILE%
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

echo [3] Clearing old logs...
adb logcat -c
echo Old logs cleared.
echo.

echo [4] Starting log collection...
echo Press Ctrl+C to stop logging.
echo.
adb logcat -v threadtime | findstr "MainActivity AndroidVoiceAssist OpenCode Whisper" > "%LOG_FILE%"

echo.
echo Log collection stopped.
echo Log saved to: %LOG_FILE%

endlocal
