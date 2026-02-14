@echo off
setlocal enabledelayedexpansion

echo ========================================
echo OpenCode Voice Assistant - Build Script
echo ========================================
echo.

REM ============================================================================
REM Configuration Section - Modify these paths for your machine
REM ============================================================================

REM Try to auto-detect Java installations
set "JAVA_HOME="

REM Check common Java locations in order of preference
if exist "D:\app\jdk21" (
    set "JAVA_HOME=D:\app\jdk21"
    echo [OK] Found Java at D:\app\jdk21
) else if exist "D:\app\jdk-21.0.8+9" (
    set "JAVA_HOME=D:\app\jdk-21.0.8+9"
    echo [OK] Found Java at D:\app\jdk-21.0.8+9
) else if exist "D:\app\jdk-11" (
    set "JAVA_HOME=D:\app\jdk-11"
    echo [OK] Found Java at D:\app\jdk-11
) else if exist "C:\Program Files\Android\Android Studio\jbr" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    echo [OK] Found Java in Android Studio
) else (
    echo [ERROR] Java not found!
    echo.
    echo Please install JDK 21 or set JAVA_HOME manually.
    echo Download from: https://learn.microsoft.com/java/openjdk/download
    exit /b 1
)

echo JAVA_HOME=%JAVA_HOME%
echo.

REM Set up PATH
set "PATH=%JAVA_HOME%\bin;%PATH%"

REM Verify Java version
java -version 2>&1 | findstr "21" >nul
if %errorlevel% equ 0 (
    echo [OK] Java 21 detected
) else (
    java -version 2>&1 | findstr "17\|11" >nul
    if %errorlevel% equ 0 (
        echo [WARNING] Java version is not 21. Build may fail.
        echo [WARNING] Recommended: JDK 21 for best compatibility
    ) else (
        echo [ERROR] Unsupported Java version. Please use JDK 21.
        exit /b 1
    )
)
echo.

REM Check Android SDK
set "ANDROID_SDK_ROOT="
if exist "D:\app\android_sdk" (
    set "ANDROID_SDK_ROOT=D:\app\android_sdk"
) else if exist "D:\app\android" (
    set "ANDROID_SDK_ROOT=D:\app\android"
) else if exist "%LOCALAPPDATA%\Android\Sdk" (
    set "ANDROID_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk"
)

if defined ANDROID_SDK_ROOT (
    echo [OK] Android SDK found at %ANDROID_SDK_ROOT%
    set "PATH=%ANDROID_SDK_ROOT%\platform-tools;%PATH%"
) else (
    echo [WARNING] Android SDK not found. Install scripts may fail.
)
echo.

REM ============================================================================
REM Build Section
REM ============================================================================

echo [1] Preparing build environment...
echo.

REM Go to project root
cd /d "%~dp0\.."

REM Clear problematic Gradle caches if they exist
if exist "%USERPROFILE%\.gradle\caches\8.0" (
    echo [INFO] Clearing old Gradle 8.0 cache...
    rmdir /s /q "%USERPROFILE%\.gradle\caches\8.0" 2>nul
)

REM Use a fresh Gradle user home to avoid cache corruption
set "GRADLE_USER_HOME=%USERPROFILE%\.gradle_opencode"
echo [INFO] Using Gradle cache: %GRADLE_USER_HOME%
echo.

echo [2] Starting Gradle build...
echo This may take several minutes on first run...
echo.

call gradlew.bat assembleDebug --no-daemon

if %errorlevel% neq 0 (
    echo.
    echo ========================================
    echo [ERROR] BUILD FAILED
    echo ========================================
    echo.
    echo Common issues:
    echo   1. Java version mismatch - Use JDK 21
    echo   2. Network issues - Check internet connection
    echo   3. Gradle cache corruption - Already cleared, retry
    echo   4. Android SDK not found - Install Android SDK
    echo.
    echo For detailed error, run: gradlew.bat assembleDebug --stacktrace
    exit /b %errorlevel%
)

echo.
echo ========================================
echo [SUCCESS] BUILD COMPLETED
echo ========================================
echo.
echo APK Location: app\build\outputs\apk\debug\app-debug.apk
echo.
echo Next steps:
echo   1. Connect Android device with USB debugging enabled
echo   2. Run: cd tools ^&^& install.bat
echo.

endlocal
