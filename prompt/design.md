# OpenCode 语音编程助手 - 设计文档

## 项目概述

OpenCode 语音编程助手是一款基于 Java 原生开发的 Android 应用，提供语音输入功能，集成 Whisper 语音转文字模型，并通过 HTTP API 与 OpenCode 服务进行交互。

## 核心功能设计

### 1. 语音交互设计

#### 1.1 按住说话，抬手发送
- **触发方式**: 触摸屏幕底部的录音按钮
- **ACTION_DOWN**: 开始录音，按钮变为蓝色并显示转圈动画
- **ACTION_UP**: 停止录音，自动进行语音识别
- **上滑取消**: 上滑超过 50dp 触发取消逻辑，按钮变为红色显示"取消"

#### 1.2 录音状态机
```
默认状态 → 按住录音状态 → [抬手] → 转文/发送中状态 → 默认状态
                           ↓
                    [上滑超过50dp] → 上滑取消状态 → 默认状态
```

### 2. 模型部署设计

#### 2.1 多源下载策略
为了解决国内用户无法访问 Hugging Face 的问题，采用三级下载源：

| 优先级 | 源地址 | 说明 |
|-------|--------|------|
| 1 | GitHub 官方仓库 | 原始源，无需梯子 |
| 2 | Gitee 国内镜像 | 速度快，首选备用 |
| 3 | CDN 镜像 | 高可用性 |

#### 2.2 下载失败处理
- **单源超时**: 15 秒超时，自动切换下一个源
- **全部失败**: 跳过下载，不阻塞 APP 后续流程
- **状态**: 禁用录音按钮，显示提示"模型未部署，请手动放置后重启"

#### 2.3 手动放置方案
```
方案1（开发阶段）: app/src/main/assets/whisper/ggml-tiny.en.bin
方案2（已安装 APP）: Android/data/com.opencode.voiceassist/files/whisper/ggml-tiny.en.bin
```

### 3. 技术架构设计

#### 3.1 整体架构
```
┌─────────────────────────────────────────────────────────┐
│                    UI 层 (MainActivity)                  │
├─────────────────────────────────────────────────────────┤
│  MessageAdapter  │  录音按钮  │  设置界面  │  Toast提示  │
├─────────────────────────────────────────────────────────┤
│                   业务逻辑层                              │
├─────────────────────────────────────────────────────────┤
│  WhisperManager  │  OpenCodeManager  │  AudioRecorder   │
├─────────────────────────────────────────────────────────┤
│                   数据/工具层                             │
├─────────────────────────────────────────────────────────┤
│  FileManager  │  Constants  │  Message(数据模型)        │
└─────────────────────────────────────────────────────────┘
```

#### 3.2 模块职责

**WhisperManager**
- 模型文件检测与完整性校验
- 多源下载管理（GitHub → Gitee → CDN）
- 模型加载与 NNAPI 加速配置
- 语音识别执行

**OpenCodeManager**
- HTTP API 调用封装
- 会话创建与管理
- 消息发送与接收
- 错误处理与重试

**AudioRecorder**
- 音频录制控制
- WAV 文件生成
- 录音状态管理
- 文件清理

**FileManager**
- 模型文件路径管理
- 缓存文件操作
- 文件完整性校验

**Constants**
- 全局常量集中管理
- 下载源 URL 配置
- 超时时间与阈值配置

### 4. 数据流设计

#### 4.1 启动流程
```
APP启动
  ↓
初始化UI
  ↓
检测Whisper模型
  ├─ 存在 → 校验完整性 → 加载模型 → 启用录音按钮
  └─ 不存在 → 多源下载
      ├─ 任一源成功 → 部署模型 → 启用录音按钮
      └─ 全部失败 → 跳过下载 → 禁用录音 → 继续初始化
  ↓
初始化OpenCode配置
  ↓
APP就绪
```

#### 4.2 录音流程
```
按住录音按钮
  ↓
启动录音 + 生成WAV缓存
  ↓
抬手（无滑动）
  ↓
停止录音
  ↓
WAV文件校验
  ├─ 成功 → 子线程Whisper转文
  │         ├─ 成功 → 调用OpenCode API发送 → 展示结果 → 删除WAV
  │         └─ 失败 → Toast提示 → 删除WAV
  └─ 失败 → Toast提示 → 删除WAV
```

### 5. UI/UX 设计

#### 5.1 界面布局
```
┌────────────────────────────┐
│  OpenCode 语音助手     [设置] │  ← 顶部标题栏
├────────────────────────────┤
│                            │
│      对话消息列表区域         │  ← 对话区（RecyclerView）
│                            │
├────────────────────────────┤
│         [录音按钮]          │  ← 底部交互栏
│        按住说话            │
└────────────────────────────┘
```

#### 5.2 录音按钮状态设计

| 状态 | 视觉表现 | 文字提示 |
|------|---------|---------|
| 默认 | 灰色底色，无动画 | 无 |
| 按住录音 | 高亮蓝色，转圈动画 | 无 |
| 上滑取消 | 浅红色底色 | "取消" |
| 转文/发送中 | 浅灰色，转圈动画 | 无 |
| 禁用 | 深灰色，无动画 | 按钮下方显示禁用原因 |

#### 5.3 Toast 提示规范
- **时长**: 短提示 2 秒，重要提示 3 秒
- **位置**: 屏幕底部居中
- **样式**: 半透明黑色背景，白色文字

### 6. 网络设计

#### 6.1 OpenCode API 规范
```
基础URL: http://{ip}:{port}
默认配置: 127.0.0.1:3000

API端点:
- POST /api/session - 创建会话
- POST /api/message - 发送消息
- GET  /api/health  - 健康检查
```

#### 6.2 请求配置
- 连接超时: 10 秒
- 读取超时: 30 秒
- 重试机制: 失败后自动重试 1 次

### 7. 异常处理设计

#### 7.1 异常分类
| 类型 | 处理方式 | 用户反馈 |
|------|---------|---------|
| 模型下载失败 | 跳过下载，继续初始化 | Toast: 模型下载失败，已跳过！请手动放置模型后重启APP |
| 录音权限拒绝 | 引导用户开启权限 | Toast: 请开启录音/网络权限后使用 |
| OpenCode连接失败 | 禁用发送，允许重新配置 | Toast: OpenCode连接失败，请检查配置 |
| 语音识别失败 | 删除缓存，提示重试 | Toast: 语音识别失败，请重试 |

#### 7.2 非阻塞设计
- 所有耗时操作均在子线程执行
- 下载失败不中断 APP 流程
- 仅禁用相关功能，保持配置功能可用

### 8. 文件管理设计

#### 8.1 模型文件
- **路径**: `getFilesDir()/whisper/ggml-tiny.en.bin`
- **大小**: 约 75MB
- **校验**: 文件大小与 MD5 校验

#### 8.2 临时文件
- **路径**: `getCacheDir()/audio_temp/`
- **格式**: WAV (16kHz, 16bit, 单声道)
- **清理**: 使用后立即删除

### 9. 性能设计

#### 9.1 硬件加速
- **NNAPI**: 适配骁龙 8 系芯片
- **多线程**: 语音识别在后台线程执行
- **UI线程**: 仅处理界面更新

#### 9.2 内存管理
- 模型懒加载
- 大文件流式处理
- 临时文件及时清理

### 10. 安全设计

#### 10.1 权限管理
- `RECORD_AUDIO` - 录音权限
- `INTERNET` - 网络权限
- `WRITE_EXTERNAL_STORAGE` - 文件写入（Android 10 以下）

#### 10.2 数据传输
- HTTP 明文传输（本地服务）
- 敏感数据本地处理
- 无用户数据上传

## 10. WebView 集成设计

### 10.1 WebView 架构

```
┌─────────────────────────────────────────────────────────┐
│                    Native App (Java)                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ MainActivity │  │ AudioRecorder│  │ WhisperMgr  │  │
│  └──────┬───────┘  └──────────────┘  └──────────────┘  │
│         │                                               │
│  ┌──────▼───────┐                                      │
│  │   WebView    │ ◄─── JavaScript Interface            │
│  └──────┬───────┘                                      │
└─────────┼───────────────────────────────────────────────┘
          │
          │ HTTP/HTTPS
          ▼
┌─────────────────────────────────────────────────────────┐
│              OpenCode Web UI (SolidJS)                   │
│  ┌───────────────────────────────────────────────────┐ │
│  │  contenteditable div [data-component="prompt-input"]│ │
│  └───────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

### 10.2 Native-WebView 通信机制

#### 通信方式对比

| 方式 | 延迟 | 可靠性 | 复杂度 | 适用场景 |
|------|------|--------|--------|----------|
| evaluateJavascript | 中 | 中 | 低 | 单向注入文本 |
| addJavascriptInterface | 低 | 高 | 中 | 双向通信 |
| WebMessagePort | 低 | 高 | 高 | 高频通信 |
| URL Scheme | 高 | 低 | 低 | 简单通知 |

#### 当前实现方案

**方案: evaluateJavascript + 重试机制**

```java
// 1. 注入 JavaScript 函数
webView.evaluateJavascript(
    "window.injectTextToOpenCode = function(text) { " +
    "  var input = document.querySelector('[data-component=\"prompt-input\"]'); " +
    "  if (input) { " +
    "    input.innerHTML = ''; " +
    "    input.appendChild(document.createTextNode(text)); " +
    "    input.dispatchEvent(new Event('input', {bubbles: true})); " +
    "    return 'success'; " +
    "  } " +
    "  return 'not_found'; " +
    "};",
    null
);

// 2. 调用注入函数（带重试）
private void injectTextWithRetry(String text, int maxRetries) {
    for (int i = 0; i < maxRetries; i++) {
        webView.evaluateJavascript(
            "window.injectTextToOpenCode('" + text + "')",
            result -> {
                if ("\"success\"".equals(result)) {
                    // 注入成功
                } else if (i < maxRetries - 1) {
                    // 重试
                    Thread.sleep(100);
                }
            }
        );
    }
}
```

### 10.3 WebView 配置

```java
WebSettings settings = webView.getSettings();
settings.setJavaScriptEnabled(true);
settings.setDomStorageEnabled(true);
settings.setCacheMode(WebSettings.LOAD_DEFAULT);
settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

// 启用调试
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
    WebView.setWebContentsDebuggingEnabled(true);
}

// 设置 WebViewClient
webView.setWebViewClient(new WebViewClient() {
    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        // 页面加载完成后注入 JavaScript
        injectJavaScriptBridge();
    }
    
    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, 
                                 WebResourceError error) {
        super.onReceivedError(view, request, error);
        // 错误处理
    }
});

// 设置 WebChromeClient
webView.setWebChromeClient(new WebChromeClient() {
    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        // 捕获 WebView 控制台日志
        Log.d("WebView", consoleMessage.message());
        return true;
    }
});
```

### 10.4 OpenCode 前端分析

#### DOM 结构
```html
<div 
  data-component="prompt-input"
  contenteditable="true"
  role="textbox"
  aria-multiline="true"
  class="...">
  <!-- 动态内容 -->
</div>
```

#### 关键特性
- **框架**: SolidJS（响应式框架）
- **输入方式**: contenteditable div（非传统 input）
- **事件监听**: `input` 事件，bubbles: true
- **状态管理**: 通过事件触发重新渲染

#### 注入要点
1. 清空现有内容：`input.innerHTML = ''`
2. 创建文本节点：`document.createTextNode(text)`
3. 触发 input 事件：`dispatchEvent(new Event('input', {bubbles: true}))`

### 10.5 触摸事件处理

#### 问题
WebView 会消费触摸事件，影响 Native 录音按钮的手势识别。

#### 解决方案
```java
webView.setOnTouchListener((v, event) -> {
    // 判断是否在录音按钮区域
    if (isInRecordButtonArea(event)) {
        // 将事件传递给 Native 处理
        return false; // 不消费事件
    }
    // WebView 正常处理
    return webView.onTouchEvent(event);
});
```

### 10.6 状态同步机制

```
Native App                    WebView
    │                            │
    │  1. 开始录音                │
    │  2. 显示录音中动画          │
    │  3. 停止录音                │
    │  4. 语音识别                │
    │  5. 注入文本 ─────────────► │
    │  6. 等待注入结果 ◄──────────│
    │  7. 显示转写完成            │
    │                            │
```

## 11. 双语音识别方案设计

### 11.1 方案对比

| 特性 | 本地 Whisper | 远程 FunASR |
|------|-------------|-------------|
| 网络依赖 | 不需要 | 需要 |
| 识别速度 | 中等（设备性能相关） | 快（服务器计算） |
| 准确率 | 85%（tiny 模型） | 95%+（大模型） |
| 隐私性 | 高（本地处理） | 中（音频上传） |
| 资源占用 | 高（75MB 模型） | 低 |

### 11.2 使用场景

- **离线场景**: 自动使用本地 Whisper
- **在线场景**: 优先使用 FunASR（更快更准）
- **FunASR 失败**: 自动降级到 Whisper

### 11.3 FunASR WebSocket 通信

```
┌─────────────┐         WebSocket          ┌─────────────┐
│  Android    │ ◄────────────────────────► │ FunASR      │
│  Client     │   1. 连接                  │ Server      │
│             │   2. 发送音频流 ──────────►│             │
│             │   3. 接收实时结果 ◄────────│             │
│             │   4. 接收最终结果 ◄────────│             │
└─────────────┘                            └─────────────┘
```

## 附录

### A. 常量配置
```java
// 模型下载源
WHISPER_MODEL_URL1 = "https://raw.githubusercontent.com/ggerganov/whisper.cpp/master/models/ggml-tiny.en.bin"
WHISPER_MODEL_URL2 = "https://gitee.com/mirrors/whisper.cpp/raw/master/models/ggml-tiny.en.bin"
WHISPER_MODEL_URL3 = "https://cdn.jsdelivr.net/gh/ggerganov/whisper.cpp@master/models/ggml-tiny.en.bin"

// 超时配置
MODEL_SINGLE_TIMEOUT = 15  // 秒
CONNECT_TIMEOUT = 10       // 秒
READ_TIMEOUT = 30          // 秒

// UI阈值
CANCEL_SLIDE_THRESHOLD = 50  // dp
```

### B. 依赖库
```gradle
// 核心依赖
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.8.0'
implementation 'androidx.recyclerview:recyclerview:1.3.0'

// 网络请求
implementation 'com.squareup.okhttp3:okhttp:4.10.0'

// JSON解析
implementation 'com.google.code.gson:gson:2.10.1'

// Whisper模型
implementation 'io.github.ggerganov:whispercpp-android:1.0.0'
```
