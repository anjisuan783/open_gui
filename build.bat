@echo off
setlocal

echo Checking Java installation...

REM Try common Java locations
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    echo Found Java in Android Studio
) else if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-17"
    echo Found Java JDK 17
) else if exist "C:\Program Files\Java\jdk-11\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-11"
    echo Found Java JDK 11
) else if exist "C:\Program Files\Java\jdk-8\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-8"
    echo Found Java JDK 8
) else (
    echo Java not found. Please install JDK 11 or later.
    exit /b 1
)

echo JAVA_HOME=%JAVA_HOME%
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