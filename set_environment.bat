@echo off
echo ========================================
echo Setting Android Development Environment
echo ========================================
echo.

echo [1] Setting JAVA_HOME...
setx JAVA_HOME "D:\app\java\jdk-17"
if %errorlevel% equ 0 (
    echo JAVA_HOME set to: D:\app\java\jdk-17
) else (
    echo ERROR: Failed to set JAVA_HOME
)

echo.

echo [2] Setting ANDROID_SDK_ROOT...
setx ANDROID_SDK_ROOT "D:\app\android"
if %errorlevel% equ 0 (
    echo ANDROID_SDK_ROOT set to: D:\app\android
) else (
    echo ERROR: Failed to set ANDROID_SDK_ROOT
)

echo.

echo [3] Setting ANDROID_HOME...
setx ANDROID_HOME "D:\app\android"
if %errorlevel% equ 0 (
    echo ANDROID_HOME set to: D:\app\android
) else (
    echo ERROR: Failed to set ANDROID_HOME
)

echo.

echo [4] Updating PATH variable...
echo Note: Adding Android tools to PATH...
echo You may need to restart command prompt for PATH changes to take effect.
echo.

echo [5] Current settings:
echo JAVA_HOME: %JAVA_HOME%
echo ANDROID_SDK_ROOT: %ANDROID_SDK_ROOT%
echo.

echo ========================================
echo IMPORTANT: Restart command prompt for changes to take effect!
echo ========================================

pause