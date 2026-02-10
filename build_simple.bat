@echo off
setlocal

echo Setting environment variables...
set "JAVA_HOME=D:\app\java\jdk-17"
set "ANDROID_SDK_ROOT=D:\app\android"
set "ANDROID_HOME=D:\app\android"
set "PATH=%JAVA_HOME%\bin;%ANDROID_SDK_ROOT%\platform-tools;%PATH%"

echo JAVA_HOME: %JAVA_HOME%
echo ANDROID_SDK_ROOT: %ANDROID_SDK_ROOT%
echo.

echo Building OpenCode Voice Assistant...
echo This may take a few minutes...
echo.

gradlew.bat assembleDebug --stacktrace --console=plain

if %errorlevel% neq 0 (
    echo Build failed with error code %errorlevel%
    exit /b %errorlevel%
)

echo Build successful!
echo APK: app\build\outputs\apk\debug\app-debug.apk
echo.

echo Installing to Mate9...
where adb >nul 2>&1
if %errorlevel% equ 0 (
    adb devices | find "device" >nul 2>&1
    if %errorlevel% equ 0 (
        echo Device found. Installing...
        adb install -r app\build\outputs\apk\debug\app-debug.apk
        echo Installation complete!
    ) else (
        echo No Android device found.
    )
) else (
    echo ADB not found.
)

endlocal