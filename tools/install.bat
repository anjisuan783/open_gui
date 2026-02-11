@echo off
setlocal

echo ========================================
echo Install APK to Android Device
echo ========================================
echo.

set "APK_PATH=..\app\build\outputs\apk\debug\app-debug.apk"

echo [1] Checking APK file...
if not exist "%APK_PATH%" (
    echo ERROR: APK not found at %APK_PATH%
    echo Please build the project first using build.bat
    exit /b 1
)
echo APK found: %APK_PATH%
echo.

echo [2] Checking ADB...
adb version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: ADB not found. Please install Android SDK.
    exit /b 1
)
echo ADB is ready.
echo.

echo [3] Checking connected devices...
adb devices
echo.

echo [4] Installing APK...
adb install -r "%APK_PATH%"

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Installation failed!
    echo Please check:
    echo   - Device is connected and USB debugging is enabled
    echo   - APK is not corrupted
    exit /b 1
)

echo.
echo Installation successful!
echo.
echo [5] Starting app...
adb shell am start -n com.opencode.voiceassist/.MainActivity

echo App started!

endlocal
