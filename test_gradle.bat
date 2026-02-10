@echo off
set JAVA_HOME=D:\app\java\jdk-17
set PATH=%JAVA_HOME%\bin;%PATH%
gradlew.bat --version > output.txt 2>&1
type output.txt