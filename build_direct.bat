@echo off
setlocal

echo ========================================
echo Direct Android Build Script
echo ========================================
echo.

echo [1] Setting up local environment...
set "JAVA_HOME=D:\app\java\jdk-17"
set "ANDROID_SDK_ROOT=D:\app\android"
set "ANDROID_HOME=D:\app\android"

echo JAVA_HOME: %JAVA_HOME%
echo ANDROID_SDK_ROOT: %ANDROID_SDK_ROOT%
echo.

echo [2] Checking Android SDK components...
if exist "%ANDROID_SDK_ROOT%\build-tools\34.0.0\dx.bat" (
    echo Build tools 34.0.0 found
) else (
    echo ERROR: Build tools 34.0.0 not found
    exit /b 1
)

if exist "%ANDROID_SDK_ROOT%\platforms\android-34\android.jar" (
    echo Platform android-34 found
) else (
    echo ERROR: Platform android-34 not found
    exit /b 1
)

echo.

echo [3] Creating offline gradle properties...
(
echo systemProp.gradle.wrapperUser=anonymous
echo systemProp.gradle.wrapperPassword=anonymous
echo org.gradle.daemon=true
echo org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
echo android.builder.sdkDownload=false
echo android.offline=true
) > gradle.properties

echo [4] Attempting to build with offline mode...
echo Using local gradle wrapper...
echo.

call gradlew.bat assembleDebug --offline --no-daemon --stacktrace --console=plain

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Offline build failed. Trying alternative approach...
    
    echo [5] Attempting to use gradle from cache...
    if exist "%USERPROFILE%\.gradle\wrapper\dists\gradle-8.0-bin\*\gradle-8.0\bin\gradle.bat" (
        echo Found cached gradle, trying direct execution...
        for /f "tokens=*" %%i in ('dir /b /ad "%USERPROFILE%\.gradle\wrapper\dists\gradle-8.0-bin\*" 2^>nul') do (
            set "GRADLE_HOME=%USERPROFILE%\.gradle\wrapper\dists\gradle-8.0-bin\%%i\gradle-8.0"
        )
        if defined GRADLE_HOME (
            echo Using gradle from: %GRADLE_HOME%
            "%GRADLE_HOME%\bin\gradle.bat" assembleDebug
        )
    ) else (
        echo ERROR: No cached gradle found
        echo.
        echo Please try:
        echo 1. Run 'gradlew.bat --version' first to download gradle
        echo 2. Or manually download gradle-8.0-bin.zip
        echo.
        exit /b 1
    )
)

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo BUILD SUCCESSFUL!
    echo ========================================
    echo.
    echo APK location: app\build\outputs\apk\debug\app-debug.apk
    echo.
) else (
    echo.
    echo ========================================
    echo BUILD FAILED
    echo ========================================
    echo.
)

endlocal