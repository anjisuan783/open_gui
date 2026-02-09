# OpenCode Voice Assistant - Android App

基于 Java 原生的 OpenCode 语音编程助手 Android 应用。

## 功能特性

- 🎤 **按住说话，抬手发送** - 核心交互体验
- 🔄 **上滑取消** - 快速取消当前录音
- 🌐 **多源模型下载** - GitHub + Gitee + CDN 镜像，无需梯子
- ⚡ **NNAPI 硬件加速** - 适配骁龙 8 系芯片
- 🔧 **自动跳过下载** - 下载失败不阻塞，可手动放置模型
- 📱 **OpenCode API 集成** - 完整的 HTTP API 调用

## 技术栈

- 开发语言: Java 原生
- 最低版本: Android 8.0 (API 24)
- 语音转文字: Whisper (ggml-tiny.en)
- 网络请求: OkHttp
- UI: AndroidX + RecyclerView

## 项目结构

```
app/src/main/java/com/opencode/voiceassist/
├── MainActivity.java           # 主界面
├── manager/
│   ├── WhisperManager.java     # Whisper 模型管理
│   ├── OpenCodeManager.java    # OpenCode API 管理
│   └── AudioRecorder.java      # 音频录制
├── model/
│   └── Message.java            # 消息数据模型
├── ui/
│   └── MessageAdapter.java     # 消息列表适配器
└── utils/
    ├── Constants.java          # 常量配置
    └── FileManager.java        # 文件管理
```

## 快速开始

### 1. 环境要求

- Android Studio Arctic Fox 或更高版本
- JDK 11 或更高版本
- Android SDK 24 或更高版本

### 2. 构建项目

```bash
# Windows
gradlew.bat assembleDebug

# Linux/Mac
./gradlew assembleDebug
```

### 3. 安装到设备

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 模型部署

### 自动下载（推荐）

APP 启动时会自动尝试从以下地址下载模型：
1. GitHub 官方仓库
2. Gitee 国内镜像（速度快）
3. CDN 镜像（高可用）

### 手动放置（无网络）

如果所有下载源都失败，APP 会跳过下载并提示手动放置模型：

**方法一（开发阶段）**：
将 `ggml-tiny.en.bin` 放入 `app/src/main/assets/whisper/` 目录

**方法二（已安装 APP）**：
通过手机文件管理器，将模型文件拷贝到：
```
Android/data/com.opencode.voiceassist/files/whisper/
```

**模型下载地址**：
- GitHub: https://raw.githubusercontent.com/ggerganov/whisper.cpp/master/models/ggml-tiny.en.bin
- Gitee: https://gitee.com/mirrors/whisper.cpp/raw/master/models/ggml-tiny.en.bin

## 配置 OpenCode

1. 点击右上角设置图标
2. 输入 OpenCode 服务器 IP 地址和端口
3. 保存配置，APP 会自动重新连接

默认配置：
- IP: 127.0.0.1
- 端口: 3000

## 使用说明

1. **按住录音按钮** - 开始录音，按钮变为蓝色并显示转圈动画
2. **抬手** - 停止录音，自动进行语音识别并发送到 OpenCode
3. **上滑超过 50dp** - 触发取消，按钮变为红色并显示"取消"文字
4. **松开** - 取消当前录音，不发送任何内容

## 注意事项

1. 确保设备已开启录音和网络权限
2. 确保 OpenCode 服务已启动并可访问
3. 首次启动需要下载模型（约 75MB）或手动放置
4. 模型下载失败时，仅禁用录音功能，配置功能仍可正常使用

## 故障排查

### 模型下载失败
- 检查网络连接
- 尝试手动放置模型
- 重启 APP 重新尝试下载

### OpenCode 连接失败
- 检查 IP 地址和端口配置
- 确认 OpenCode 服务已启动
- 检查设备与服务器网络连通性

### 录音无响应
- 检查录音权限是否已授权
- 检查模型是否成功加载
- 查看吐司提示信息

## 许可证

MIT License
