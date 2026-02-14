# 新机器快速启动指南

本指南帮助你在新机器上快速编译和运行 OpenCode Voice Assistant。

## 快速开始 (3步)

### 第1步：检查环境
```batch
cd tools
check_setup.bat
```
此脚本会检查：
- ✅ JDK 21 是否安装
- ✅ Android SDK 是否安装
- ✅ 项目文件是否完整

### 第2步：编译项目
```batch
build.bat
```
脚本会自动：
- 检测 JDK 21 安装位置
- 检测 Android SDK
- 清理旧缓存避免错误
- 下载 Gradle 8.5 (首次)
- 编译 APK

### 第3步：安装并运行
```batch
install.bat
```
脚本会自动：
- 检查 APK 文件
- 连接 Android 设备
- 安装 APK
- 启动应用

## 完整脚本列表

| 脚本 | 功能 | 使用时机 |
|-----|------|---------|
| `check_setup.bat` ✅ | 环境检查 | **新机器首次使用** |
| `build.bat` ✅ | 自动编译项目 | **每次代码修改后** |
| `install.bat` ✅ | 安装并启动应用 | 编译完成后 |
| `debug.bat` ✅ | 查看应用状态 | 排查问题时 |
| `collect_logs.bat` ✅ | 收集运行日志 | 分析问题 |

## 环境准备 (如缺少)

### 1. 安装 JDK 21
下载: https://learn.microsoft.com/java/openjdk/download

推荐安装到: `D:\app\jdk-21.0.8+9`

然后创建符号链接 (避免+号问题):
```batch
mklink /D D:\app\jdk21 D:\app\jdk-21.0.8+9
```

### 2. 安装 Android SDK
下载 Android Studio 或仅 SDK Tools:
https://developer.android.com/studio

推荐安装到: `D:\app\android_sdk`

确保包含:
- Android SDK Platform 34
- Android SDK Build-Tools 33+

### 3. 配置环境变量 (可选)
系统环境变量添加:
```
JAVA_HOME=D:\app\jdk21
ANDROID_SDK_ROOT=D:\app\android_sdk
PATH=%JAVA_HOME%\bin;%ANDROID_SDK_ROOT%\platform-tools;%PATH%
```

**注意**: build.bat 会自动检测这些路径，不配置也能工作。

## 详细使用说明

### 1. 环境检查 check_setup.bat
```batch
cd tools
check_setup.bat
```
**输出示例**:
```
[SUCCESS] All checks passed!
You're ready to build. Run:
  cd tools
  build.bat
```

**如果有问题会显示**:
```
[WARNING] Some checks failed!
Please fix the issues above...
```

### 2. 编译 build.bat
```batch
cd tools
build.bat
```
**首次运行**:
- 自动下载 Gradle 8.5 (约 100MB)
- 下载 Android SDK 依赖 (如缺失)
- 编译时间：2-5分钟

**后续运行**:
- 使用缓存，编译更快
- 时间：10-30秒

**编译输出**:
```
[SUCCESS] BUILD COMPLETED
APK Location: app\build\outputs\apk\debug\app-debug.apk
```

### 3. 安装 install.bat
```batch
cd tools
install.bat
```
**输出示例**:
```
[1] Checking APK file...
APK found: ..\app\build\outputs\apk\debug\app-debug.apk

[2] Checking ADB...
ADB is ready.

[3] Checking connected devices...
List of devices attached
pjijmn95oncqdmu4	device

[4] Installing APK...
Performing Streamed Install
Success

Installation successful!

[5] Starting app...
Starting: Intent { cmp=com.opencode.voiceassist/.MainActivity }
App started!
```

### 4. 调试 debug.bat
```batch
cd tools
debug.bat
```
显示信息:
- 连接的设备
- 应用是否在运行
- 进程信息和内存使用
- 最近的日志

### 5. 收集日志 collect_logs.bat
```batch
cd tools
collect_logs.bat
```
功能:
- 清除旧日志
- 开始收集新日志
- 保存到 `logs/logcat_YYYYMMDD_HHMMSS.txt`
- 按 Ctrl+C 停止

## 常见问题

### Q: check_setup.bat 找不到 Java
```
[ERROR] Java NOT found!
```
**解决**: 
1. 下载安装 JDK 21
2. 或使用现有 JDK 路径，修改 build.bat 中的路径

### Q: 编译失败 - 版本不兼容
```
Unsupported class file major version 65
```
**解决**: 
- 使用 JDK 21 (已内置检测)
- build.bat 会自动清理旧缓存

### Q: 安装失败
```
INSTALL_FAILED_UPDATE_INCOMPATIBLE
```
**解决**:
```batch
adb uninstall com.opencode.voiceassist
install.bat
```

### Q: 设备未授权
```
unauthorized
```
**解决**:
1. 手机弹窗点击"允许"
2. 勾选"始终允许此计算机"
3. 重新运行 install.bat

## 项目配置说明

**gradle-wrapper.properties** (已配置):
```properties
distributionUrl=https\://mirrors.huaweicloud.com/gradle/gradle-8.5-bin.zip
```

**gradle.properties** (已配置):
```properties
android.useAndroidX=true
org.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8
```

这些配置已提交到仓库，新机器无需修改。

## 文件结构

```
open_gui/
├── tools/
│   ├── check_setup.bat    ← 环境检查 (NEW)
│   ├── build.bat          ← 编译脚本 (NEW)
│   ├── install.bat        ← 安装脚本
│   ├── debug.bat          ← 调试脚本
│   ├── collect_logs.bat   ← 日志收集
│   └── README.md          ← 本文件
├── app/
│   └── build.gradle       ← 应用配置
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── build.gradle           ← 项目配置
├── gradle.properties      ← Gradle 配置
└── settings.gradle        ← 项目设置
```

## 相关文档

- [项目 README](../README.md) - 项目介绍和功能说明
- [AGENTS.md](../AGENTS.md) - 代码规范和开发指南

## 一键命令 (复制即用)

```batch
:: 完整流程：检查 + 编译 + 安装 + 启动
cd tools
check_setup.bat && build.bat && install.bat

:: 仅检查环境
cd tools
check_setup.bat

:: 编译并安装
cd tools
build.bat && install.bat

:: 重新安装 (覆盖)
cd tools
adb uninstall com.opencode.voiceassist && install.bat

:: 查看日志
cd tools
collect_logs.bat
```

## 验证安装

运行以下命令验证环境:
```batch
java -version    :: 应显示 Java 21
adb version      :: 应显示 Android Debug Bridge
```

如果都正常，即可直接使用 `build.bat` 编译。

## 版本历史

- 2025-02-14: 新增 check_setup.bat 和 build.bat，修复新机器编译问题
- 旧版本: build.bat, build_fast.bat, build_full.bat (已删除，因路径硬编码无法在新机器运行)
