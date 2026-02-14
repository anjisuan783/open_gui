@echo off
setlocal enabledelayedexpansion

echo ========================================
echo OpenCode Voice Assistant - Setup Check
echo ========================================
echo.
echo This script checks your environment setup
echo and provides guidance for new machines.
echo.

set "ALL_OK=1"

REM ============================================================================
REM Check Java
REM ============================================================================
echo [1] Checking Java installation...
echo.

set "JAVA_FOUND=0"
set "JAVA_PATH="

REM Check common locations
if exist "D:\app\jdk21\bin\java.exe" (
    set "JAVA_FOUND=1"
    set "JAVA_PATH=D:\app\jdk21"
    echo [OK] Found: D:\app\jdk21
) else if exist "D:\app\jdk-21.0.8+9\bin\java.exe" (
    set "JAVA_FOUND=1"
    set "JAVA_PATH=D:\app\jdk-21.0.8+9"
    echo [OK] Found: D:\app\jdk-21.0.8+9
) else if exist "D:\app\jdk-11\bin\java.exe" (
    set "JAVA_FOUND=1"
    set "JAVA_PATH=D:\app\jdk-11"
    echo [WARNING] Found JDK 11 at D:\app\jdk-11
    echo           ^(Recommended: JDK 21^)
) else if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_FOUND=1"
    set "JAVA_PATH=C:\Program Files\Android\Android Studio\jbr"
    echo [OK] Found Android Studio bundled JDK
    echo       Location: C:\Program Files\Android\Android Studio\jbr
)

if %JAVA_FOUND% equ 0 (
    echo [ERROR] Java NOT found!
    echo.
    echo Please install JDK 21:
    echo   https://learn.microsoft.com/java/openjdk/download
    echo.
    echo Install to: D:\app\jdk-21.0.8+9
    echo Then run: mklink /D D:\app\jdk21 D:\app\jdk-21.0.8+9
    echo.
    set "ALL_OK=0"
    goto :after_java_check
)

REM Check Java version
echo|set /p="Checking Java version... "
"%JAVA_PATH%\bin\java.exe" -version 2>&1 | findstr "21" >nul
if !errorlevel! equ 0 (
    echo [OK] Java 21
) else (
    echo [WARNING] Not Java 21 ^(Recommended for best compatibility^)
)

:after_java_check
echo.

REM ============================================================================
REM Check Android SDK
REM ============================================================================
echo [2] Checking Android SDK installation...
echo.

set "SDK_FOUND=0"

if exist "D:\app\android_sdk\platform-tools\adb.exe" (
    set "SDK_FOUND=1"
    echo [OK] Found: D:\app\android_sdk
) else if exist "D:\app\android\platform-tools\adb.exe" (
    set "SDK_FOUND=1"
    echo [OK] Found: D:\app\android
) else if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set "SDK_FOUND=1"
    echo [OK] Found: %%LOCALAPPDATA%%\Android\Sdk
) else if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe" (
    set "SDK_FOUND=1"
    echo [OK] Found: %%USERPROFILE%%\AppData\Local\Android\Sdk
)

if %SDK_FOUND% equ 0 (
    echo [ERROR] Android SDK NOT found!
    echo.
    echo Please install Android Studio or SDK Tools:
    echo   https://developer.android.com/studio
    echo.
    echo Recommended: D:\app\android_sdk
    echo.
    echo Required components:
    echo   - Android SDK Platform 34
    echo   - Android SDK Build-Tools 33.0.1+
    echo   - Android SDK Platform-Tools
    echo.
    set "ALL_OK=0"
)
echo.

REM ============================================================================
REM Check Project Files
REM ============================================================================
echo [3] Checking project structure...
echo.

cd /d "%~dp0\.."

if exist "gradlew.bat" (
    echo [OK] gradlew.bat found
) else (
    echo [ERROR] gradlew.bat NOT found!
    echo           Are you in the correct directory?
    set "ALL_OK=0"
)

if exist "app\build.gradle" (
    echo [OK] app\build.gradle found
) else (
    echo [ERROR] app\build.gradle NOT found!
    set "ALL_OK=0"
)

if exist "settings.gradle" (
    echo [OK] settings.gradle found
) else (
    echo [ERROR] settings.gradle NOT found!
    set "ALL_OK=0"
)
echo.

REM ============================================================================
REM Summary
REM ============================================================================
echo ========================================

if %ALL_OK% equ 1 (
    echo [SUCCESS] All checks passed!
    echo.
    echo You're ready to build. Run:
    echo   cd tools
    echo   build.bat
    echo.
    exit /b 0
) else (
    echo [WARNING] Some checks failed!
    echo.
    echo Please fix the issues above, then run:
    echo   cd tools
    echo   build.bat
    echo.
    exit /b 1
)

endlocal
