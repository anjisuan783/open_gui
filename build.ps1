# PowerShell build script
$env:JAVA_HOME = "D:\app\java\jdk-17"
$env:ANDROID_SDK_ROOT = "D:\app\android"
$env:ANDROID_HOME = "D:\app\android"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\cmdline-tools\latest\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:PATH"

Write-Host "Setting environment variables..."
Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "ANDROID_SDK_ROOT: $env:ANDROID_SDK_ROOT"

Write-Host "Checking Java..."
java -version

Write-Host "Starting Gradle build..."
.\gradlew.bat assembleDebug --stacktrace --info

if ($LASTEXITCODE -eq 0) {
    Write-Host "Build successful!"
    Write-Host "APK: app\build\outputs\apk\debug\app-debug.apk"
} else {
    Write-Host "Build failed with exit code: $LASTEXITCODE"
    exit $LASTEXITCODE
}