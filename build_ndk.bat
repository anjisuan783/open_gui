@echo off
setlocal enabledelayedexpansion

echo ===========================================
echo OpenCode Voice Assistant - NDK r26 极致优化
echo 针对骁龙 8+ Gen 1 (taro) ARMv8.2-a+fp16+dotprod
echo ===========================================

:: 检查 NDK 路径
if not exist "D:\app\android\ndk\26.3.11579264" (
    echo 错误: NDK r26.3 未找到
    echo 请确认 NDK 路径: D:\app\android\ndk\26.3.11579264
    echo 可用版本:
    dir /B "D:\app\android\ndk\"
    exit /b 1
)

echo 1. 下载 whisper.cpp 源码...
if not exist "whisper.cpp" (
    git clone https://github.com/ggerganov/whisper.cpp.git
    if errorlevel 1 (
        echo 错误: 克隆 whisper.cpp 失败
        exit /b 1
    )
)

cd whisper.cpp

echo 2. 清理旧的构建...
if exist "build-android" rmdir /s /q build-android
mkdir build-android
cd build-android

echo 3. 配置 CMake (针对骁龙 8+ Gen 1 优化)...
:: 使用 NDK 的 CMake 工具链
set ANDROID_NDK=D:\app\android\ndk\26.3.11579264
set ANDROID_ABI=arm64-v8a
set ANDROID_PLATFORM=android-24

echo NDK路径: %ANDROID_NDK%
echo ABI: %ANDROID_ABI%
echo 平台: %ANDROID_PLATFORM%

:: 骁龙 8+ Gen 1 专用优化标志
:: -march=armv8.2-a+fp16+dotprod : ARMv8.2 指令集 + 半精度浮点 + 点积运算
:: -mtune=cortex-x2 : 针对 Cortex-X2 大核优化
:: -O3 : 最高级别优化
:: -ffast-math : 快速数学运算 (精度略有损失)
set OPTIMIZATION_FLAGS=-O3 -march=armv8.2-a+fp16+dotprod -mtune=cortex-x2 -ffast-math

cmake .. ^
    -DCMAKE_TOOLCHAIN_FILE="%ANDROID_NDK%\build\cmake\android.toolchain.cmake" ^
    -DANDROID_ABI=%ANDROID_ABI% ^
    -DANDROID_PLATFORM=%ANDROID_PLATFORM% ^
    -DANDROID_ARM_NEON=ON ^
    -DANDROID_ARM_MODE=arm ^
    -DCMAKE_BUILD_TYPE=Release ^
    -DWHISPER_BUILD_EXAMPLES=OFF ^
    -DWHISPER_BUILD_TESTS=OFF ^
    -DCMAKE_C_FLAGS="%OPTIMIZATION_FLAGS%" ^
    -DCMAKE_CXX_FLAGS="%OPTIMIZATION_FLAGS%"

if errorlevel 1 (
    echo 错误: CMake 配置失败
    exit /b 1
)

echo 4. 编译优化版 whisper.cpp...
cmake --build . --config Release --parallel 4

if errorlevel 1 (
    echo 错误: 编译失败
    exit /b 1
)

echo 5. 复制优化后的库文件...
if exist ".\libwhisper.so" (
    copy /Y ".\libwhisper.so" "..\..\app\src\main\jniLibs\arm64-v8a\libwhisper_snapdragon_opt.so"
    echo 已生成优化版: libwhisper_snapdragon_opt.so
    
    :: 同时替换默认库文件（可选）
    copy /Y ".\libwhisper.so" "..\..\app\src\main\jniLibs\arm64-v8a\libwhisper.so"
    echo 已替换默认库: libwhisper.so
) else (
    echo 警告: 未找到 libwhisper.so 文件
)

cd ..\..

echo 6. 下载 Q4_0 量化模型...
if not exist "app\src\main\assets\whisper\ggml-tiny.en-q4_0.bin" (
    echo 正在从 HuggingFace 下载 Q4_0 模型...
    
    :: 尝试多个镜像源
    set Q4_MODEL_URL1=https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en-q4_0.bin
    set Q4_MODEL_URL2=https://hf-mirror.com/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en-q4_0.bin
    
    curl -L "%Q4_MODEL_URL2%" -o "app\src\main\assets\whisper\ggml-tiny.en-q4_0.bin" --connect-timeout 30
    if errorlevel 1 (
        echo 警告: Q4_0 模型下载失败，将使用现有模型
        echo 您可以从以下链接手动下载:
        echo   %Q4_MODEL_URL1%
        echo   %Q4_MODEL_URL2%
    ) else (
        echo Q4_0 模型下载成功 (约 24MB)
    )
)

echo ===========================================
echo NDK 极致优化完成！
echo ===========================================
echo 优化内容:
echo 1. ARMv8.2-a+fp16+dotprod 指令集启用
echo 2. Cortex-X2 大核优化 (mtune=cortex-x2)
echo 3. O3 最高级别编译优化
echo 4. 快速数学运算 (ffast-math)
echo 5. Q4_0 量化模型支持 (如下载成功)
echo 
echo 下一步: 重新编译 Android 应用
echo   运行: gradlew.bat assembleDebug
echo ===========================================

endlocal