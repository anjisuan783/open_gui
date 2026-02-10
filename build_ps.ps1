# PowerShell build script with Ali Cloud mirrors

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Android Build with Ali Cloud Mirrors" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Set environment variables
$env:JAVA_HOME = "D:\app\java\jdk-17"
$env:ANDROID_SDK_ROOT = "D:\app\android"
$env:ANDROID_HOME = "D:\app\android"
$env:GRADLE_USER_HOME = "$env:USERPROFILE\.gradle"

# Add to PATH
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:PATH"

Write-Host "[1] Environment setup:" -ForegroundColor Yellow
Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "ANDROID_SDK_ROOT: $env:ANDROID_SDK_ROOT"
Write-Host ""

Write-Host "[2] Checking Java..." -ForegroundColor Yellow
java -version
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Java not working" -ForegroundColor Red
    exit 1
}
Write-Host ""

Write-Host "[3] Setting up gradle wrapper with Ali Cloud mirror..." -ForegroundColor Yellow
$gradleWrapperProps = @"
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://mirrors.huaweicloud.com/gradle/gradle-8.0-bin.zip
networkTimeout=120000
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"@
$gradleWrapperProps | Out-File -FilePath "gradle\wrapper\gradle-wrapper.properties" -Encoding ASCII

Write-Host "[4] Checking gradle cache..." -ForegroundColor Yellow
$gradleCacheDir = "$env:GRADLE_USER_HOME\wrapper\dists\gradle-8.0-bin"
if (-not (Test-Path $gradleCacheDir)) {
    New-Item -ItemType Directory -Path $gradleCacheDir -Force | Out-Null
}

# Find hash subdirectory
$subDirs = Get-ChildItem -Path $gradleCacheDir -Directory
if ($subDirs.Count -eq 0) {
    $hashDir = "$gradleCacheDir\gradle-8.0"
    New-Item -ItemType Directory -Path $hashDir -Force | Out-Null
} else {
    $hashDir = $subDirs[0].FullName
}

$gradleZipPath = "$hashDir\gradle-8.0-bin.zip"
Write-Host "Gradle cache: $gradleZipPath"

if (Test-Path $gradleZipPath) {
    $fileSize = (Get-Item $gradleZipPath).Length
    Write-Host "Gradle zip exists. Size: $fileSize bytes"
    if ($fileSize -lt 80000000) {
        Write-Host "File too small, redownloading..." -ForegroundColor Yellow
        Remove-Item $gradleZipPath -Force
    }
}

if (-not (Test-Path $gradleZipPath)) {
    Write-Host "[5] Downloading gradle from Ali Cloud mirror..." -ForegroundColor Yellow
    Write-Host "This may take a few minutes..." -ForegroundColor Yellow
    
    try {
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri "https://mirrors.huaweicloud.com/gradle/gradle-8.0-bin.zip" `
                         -OutFile $gradleZipPath `
                         -TimeoutSec 300
        $fileSize = (Get-Item $gradleZipPath).Length
        Write-Host "Download successful. Size: $fileSize bytes" -ForegroundColor Green
    } catch {
        Write-Host "ERROR: Failed to download gradle: $_" -ForegroundColor Red
        exit 1
    }
}
Write-Host ""

Write-Host "[6] Building project with Ali Cloud mirrors..." -ForegroundColor Yellow
Write-Host "This will take several minutes for first build..." -ForegroundColor Yellow
Write-Host ""

# Create gradle.properties with mirror settings
$gradleProps = @"
systemProp.gradle.wrapperUser=anonymous
systemProp.gradle.wrapperPassword=anonymous
android.offline=false
org.gradle.daemon=false
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
"@
$gradleProps | Out-File -FilePath "gradle.properties" -Encoding ASCII

# Run gradle build
try {
    & .\gradlew.bat assembleDebug --stacktrace --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code: $LASTEXITCODE"
    }
} catch {
    Write-Host "ERROR: Build failed: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "BUILD SUCCESSFUL!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "APK location: app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Green
Write-Host ""

Write-Host "[7] Installing to Mate9..." -ForegroundColor Yellow
$adbPath = Get-Command adb -ErrorAction SilentlyContinue
if ($adbPath) {
    $devices = adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }
    if ($devices) {
        Write-Host "Device found. Installing APK..." -ForegroundColor Green
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