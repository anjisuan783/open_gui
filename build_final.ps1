# Final build script
$ErrorActionPreference = "Stop"

Write-Host "Building OpenCode Voice Assistant..." -ForegroundColor Cyan
Write-Host ""

# Environment
$env:JAVA_HOME = "D:\app\java\jdk-17"
$env:ANDROID_SDK_ROOT = "D:\app\android"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:PATH"

Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "ANDROID_SDK_ROOT: $env:ANDROID_SDK_ROOT"
Write-Host ""

Write-Host "Checking Java..."
java -version
Write-Host ""

Write-Host "Starting Gradle build..."
Write-Host "This will take a few minutes..."
Write-Host ""

& .\gradlew.bat assembleDebug --stacktrace --console=plain

if ($LASTEXITCODE -ne 0) {
    Write-Host "BUILD FAILED with exit code: $LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "BUILD SUCCESSFUL!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "APK location: app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Green
Write-Host ""

# Install to Mate9
$adbPath = Get-Command adb -ErrorAction SilentlyContinue
if ($adbPath) {
    $devices = adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }
    if ($devices) {
        Write-Host "Installing to Mate9..." -ForegroundColor Yellow
        adb install -r app\build\outputs\apk\debug\app-debug.apk
        Write-Host "Installation complete!" -ForegroundColor Green
    } else {
        Write-Host "No Android device found. Connect Mate9 and enable USB debugging." -ForegroundColor Yellow
    }
} else {
    Write-Host "ADB not found. Skipping installation." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Done!" -ForegroundColor Cyan