@echo off
setlocal enabledelayedexpansion

echo ========================================
echo Fast Android Build Script
echo ========================================
echo.

echo [1] Setting environment...
set "JAVA_HOME=D:\app\java\jdk-17"
set "ANDROID_SDK_ROOT=D:\app\android"
set "PATH=%JAVA_HOME%\bin;%ANDROID_SDK_ROOT%\platform-tools;%PATH%"

echo JAVA_HOME: %JAVA_HOME%
echo ANDROID_SDK_ROOT: %ANDROID_SDK_ROOT%
echo.

echo [2] Finding gradle cache directory...
set "GRADLE_CACHE_DIR=%USERPROFILE%\.gradle\wrapper\dists\gradle-8.0-bin"
if not exist "!GRADLE_CACHE_DIR!" (
    mkdir "!GRADLE_CACHE_DIR!"
)

REM Find the hash subdirectory
set "GRADLE_SUBDIR="
for /f "tokens=*" %%i in ('dir /b /ad "!GRADLE_CACHE_DIR!" 2^>nul') do (
    set "GRADLE_SUBDIR=%%i"
)

if "!GRADLE_SUBDIR!"=="" (
    echo Creating new gradle cache subdirectory...
    set "GRADLE_SUBDIR=gradle-8.0"
    mkdir "!GRADLE_CACHE_DIR!\!GRADLE_SUBDIR!"
)

set "GRADLE_ZIP_PATH=!GRADLE_CACHE_DIR!\!GRADLE_SUBDIR!\gradle-8.0-bin.zip"
echo Gradle zip path: !GRADLE_ZIP_PATH!
echo.

echo [3] Checking if gradle is already downloaded...
if exist "!GRADLE_ZIP_PATH!" (
    echo Gradle already exists. Checking size...
    for %%F in ("!GRADLE_ZIP_PATH!") do set "SIZE=%%~zF"
    if !SIZE! gtr 80000000 (
        echo Gradle zip size: !SIZE! bytes (OK)
    ) else (
        echo Gradle zip too small (!SIZE! bytes). Redownloading...
        del "!GRADLE_ZIP_PATH!" 2>nul
        del "!GRADLE_CACHE_DIR!\!GRADLE_SUBDIR!\*.lck" 2>nul
        del "!GRADLE_CACHE_DIR!\!GRADLE_SUBDIR!\*.part" 2>nul
        goto :download_gradle
    )
) else (
    echo Gradle not found. Downloading...
    :download_gradle
    echo Downloading gradle-8.0-bin.zip from Ali Cloud mirror...
    echo This may take a few minutes...
    
    powershell -Command "$ProgressPreference = 'SilentlyContinue'; Invoke-WebRequest -Uri 'https://mirrors.aliyun.com/gradle/gradle-8.0-bin.zip' -OutFile '!GRADLE_ZIP_PATH!' -TimeoutSec 300"
    
    if exist "!GRADLE_ZIP_PATH!" (
        for %%F in ("!GRADLE_ZIP_PATH!") do set "SIZE=%%~zF"
        echo Download successful. Size: !SIZE! bytes
    ) else (
        echo ERROR: Failed to download gradle
        exit /b 1
    )
)

echo.

echo [4] Setting gradle wrapper properties...
(
echo distributionBase=GRADLE_USER_HOME
echo distributionPath=wrapper/dists
echo distributionUrl=https\://mirrors.aliyun.com/gradle/gradle-8.0-bin.zip
echo networkTimeout=60000
echo zipStoreBase=GRADLE_USER_HOME
echo zipStorePath=wrapper/dists
) > gradle\wrapper\gradle-wrapper.properties

echo [5] Building project...
echo This will compile the app. Please wait...
echo.

call gradlew.bat assembleDebug --stacktrace --console=plain

if %errorlevel% neq 0 (
    echo.
    echo ========================================
    echo BUILD FAILED
    echo ========================================
    exit /b %errorlevel%
)

echo.
echo ========================================
echo BUILD SUCCESSFUL!
echo ========================================
echo.
echo APK location: app\build\outputs\apk\debug\app-debug.apk
echo.

echo [6] Installing to Mate9...
where adb >nul 2>&1
if %errorlevel% equ 0 (
    adb devices | find "device" >nul 2>&1
    if %errorlevel% equ 0 (
        echo Device found. Installing APK...
        adb install -r app\build\outputs\apk\debug\app-debug.apk
        echo Installation complete!
    ) else (
        echo No Android device found. Connect Mate9 and enable USB debugging.
    )
) else (
    echo ADB not found. Skipping installation.
)

echo.
echo Done!