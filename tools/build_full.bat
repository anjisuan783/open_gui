@echo off
setlocal enabledelayedexpansion

echo ========================================
echo OpenCode Voice Assistant - Full Build
echo ========================================
echo.

echo [1] Setting up environment...
set "JAVA_HOME=D:\app\java\jdk-17"
set "ANDROID_SDK_ROOT=D:\app\android"
set "ANDROID_HOME=D:\app\android"
set "PATH=%JAVA_HOME%\bin;%ANDROID_SDK_ROOT%\cmdline-tools\latest\bin;%ANDROID_SDK_ROOT%\platform-tools;%PATH%"

echo JAVA_HOME: %JAVA_HOME%
echo ANDROID_SDK_ROOT: %ANDROID_SDK_ROOT%
echo.

echo [2] Checking Java installation...
java -version 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java not found or not working
    exit /b 1
)
echo.

echo [3] Checking Android SDK tools...
where sdkmanager 2>nul
if %errorlevel% neq 0 (
    echo WARNING: sdkmanager not found in PATH
    echo Trying alternative location...
    if exist "%ANDROID_SDK_ROOT%\cmdline-tools\bin\sdkmanager.bat" (
        echo Found sdkmanager in cmdline-tools\bin
    ) else (
        echo ERROR: sdkmanager not found
    )
)
echo.

echo [4] Checking Android build-tools...
dir "%ANDROID_SDK_ROOT%\build-tools" /b
echo.

echo [5] Checking Android platforms...
dir "%ANDROID_SDK_ROOT%\platforms" /b
echo.

echo [6] Checking local.properties...
if exist "..\local.properties" (
    echo local.properties exists
    type ..\local.properties
) else (
    echo ERROR: local.properties not found
    exit /b 1
)
echo.

echo [7] Starting Gradle build...
echo This may take several minutes...
echo.

call ..\gradlew.bat clean assembleDebug --stacktrace

if %errorlevel% neq 0 (
    echo.
    echo ========================================
    echo BUILD FAILED with error code %errorlevel%
    echo ========================================
    exit /b %errorlevel%
)

echo.
echo ========================================
echo BUILD SUCCESSFUL!
echo ========================================
echo.
echo APK location: ..\app\build\outputs\apk\debug\app-debug.apk
echo.

endlocal
