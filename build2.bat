@echo off
setlocal enabledelayedexpansion

echo Setting up environment...

set "JAVA_HOME=D:\app\java\jdk-17"
set "ANDROID_SDK_ROOT=D:\app\android"
set "PATH=%JAVA_HOME%\bin;%ANDROID_SDK_ROOT%\tools;%ANDROID_SDK_ROOT%\platform-tools;%PATH%"

echo JAVA_HOME=%JAVA_HOME%
echo ANDROID_SDK_ROOT=%ANDROID_SDK_ROOT%
echo.

echo Checking Java version...
java -version 2>&1
echo.

echo Building OpenCode Voice Assistant...
call gradlew.bat assembleDebug --stacktrace

if %errorlevel% neq 0 (
    echo Build failed with error code %errorlevel%
    exit /b %errorlevel%
)

echo Build successful!
echo APK location: app\build\outputs\apk\debug\app-debug.apk

endlocal