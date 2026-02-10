@echo off
setlocal

echo Setting environment variables...
set "JAVA_HOME=D:\app\java\jdk-17"
set "ANDROID_SDK_ROOT=D:\app\android"
set "ANDROID_HOME=D:\app\android"
set "GRADLE_WRAPPER_TIMEOUT=300000"
set "GRADLE_OPTS=-Dorg.gradle.daemon=false -Dorg.gradle.internal.http.connectionTimeout=120000 -Dorg.gradle.internal.http.socketTimeout=120000"

set "PATH=%JAVA_HOME%\bin;%ANDROID_SDK_ROOT%\cmdline-tools\latest\bin;%ANDROID_SDK_ROOT%\platform-tools;%PATH%"

echo JAVA_HOME: %JAVA_HOME%
echo ANDROID_SDK_ROOT: %ANDROID_SDK_ROOT%
echo GRADLE_WRAPPER_TIMEOUT: %GRADLE_WRAPPER_TIMEOUT%
echo.

echo Starting Gradle build with extended timeout...
echo This may take several minutes for first build...
echo.

call gradlew.bat assembleDebug --no-daemon --stacktrace --console=plain

if %errorlevel% neq 0 (
    echo.
    echo Build failed with error code %errorlevel%
    exit /b %errorlevel%
)

echo.
echo ========================================
echo BUILD SUCCESSFUL!
echo ========================================
echo.
echo APK location: app\build\outputs\apk\debug\app-debug.apk
echo.

endlocal