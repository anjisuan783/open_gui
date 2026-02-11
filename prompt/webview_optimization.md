# WebView 文本注入优化记录

## 优化日期
2026-02-11

## 优化目标
解决 Native App 与 WebView (OpenCode Web UI) 之间的文本注入不稳定问题

## 问题分析

### 原有问题
1. **文本转义不完整**: 仅处理了单引号和换行符，未处理双引号、反斜杠等特殊字符
2. **DOM 操作冲突**: 同时设置 `textContent` 和 `innerHTML` 可能导致不一致
3. **事件触发不完整**: 缺少 `beforeinput` 和 `InputEvent`，无法完整模拟用户输入
4. **缺少重试机制**: 注入失败时没有自动重试
5. **页面就绪检测缺失**: 在页面未完全加载时注入可能导致失败

### OpenCode Web UI 特性
- **框架**: SolidJS（响应式框架）
- **输入组件**: contenteditable div (`[data-component="prompt-input"]`)
- **事件监听**: `input` 事件，需要 `bubbles: true`
- **状态管理**: 通过 `handleInput()` 解析 DOM 并更新状态

## 优化方案

### 1. 创建 WebViewTextInjector 工具类
新建文件: `app/src/main/java/com/opencode/voiceassist/utils/WebViewTextInjector.java`

**主要功能**:
- ✅ JSON 安全转义（使用 `JSONObject.quote()`）
- ✅ 页面就绪检测
- ✅ 自动重试机制（最多 3 次，间隔 300ms）
- ✅ 完整事件序列（beforeinput → input → change）
- ✅ 光标位置保持
- ✅ 支持换行符（`<br>` 标签）

### 2. 核心改进点

#### A. 安全的文本转义
```java
// 旧代码（不安全）
String escapedText = text.replace("'", "\\'").replace("\n", "\\n");

// 新代码（安全）
String jsonText = JSONObject.quote(text);
```

#### B. 页面就绪检测
```javascript
window.isOpenCodeReady = function() {
    const input = document.querySelector('[data-component="prompt-input"]');
    if (!input) return false;
    if (!input.isConnected) return false;
    return input.offsetWidth > 0 && input.offsetHeight > 0;
};
```

#### C. 完整事件序列
```javascript
// 触发完整的事件序列，模拟真实用户输入
input.dispatchEvent(new Event('beforeinput', {bubbles: true}));
input.dispatchEvent(new InputEvent('input', {
    bubbles: true,
    inputType: 'insertText',
    data: text
}));
input.dispatchEvent(new Event('change', {bubbles: true}));
```

#### D. 重试机制
```java
private void injectWithRetry(String text, int attempt, InjectionCallback callback) {
    if (attempt < MAX_RETRIES) {
        // 重试
        mainHandler.postDelayed(() -> injectWithRetry(text, attempt + 1, callback), RETRY_DELAY_MS);
    } else {
        // 达到最大重试次数，返回失败
        callback.onFailure("注入失败");
    }
}
```

### 3. MainActivity 修改

#### 添加依赖
```java
import com.opencode.voiceassist.utils.WebViewTextInjector;
```

#### 初始化注入器
```java
// 在 configureWebView() 中
webViewInjector = new WebViewTextInjector(webView);
```

#### 更新注入方法
```java
// 使用新的注入器替代旧的直接注入
webViewInjector.injectText(text, new WebViewTextInjector.InjectionCallback() {
    @Override
    public void onSuccess(String text) {
        // 注入成功
    }
    
    @Override
    public void onFailure(String error) {
        // 注入失败
    }
    
    @Override
    public void onRetry(int attempt, int maxRetries) {
        // 正在重试
    }
});
```

## 技术细节

### JavaScript 注入流程
```
1. 检查页面就绪状态
   ↓
2. 查找输入框元素
   ↓
3. 保存当前光标位置
   ↓
4. 清空现有内容
   ↓
5. 创建文档片段（处理换行）
   ↓
6. 插入内容
   ↓
7. 恢复光标位置
   ↓
8. 触发事件序列
   ↓
9. 聚焦输入框
```

### 支持的文本格式
- ✅ 普通文本
- ✅ 包含换行符的文本（自动转换为 `<br>`）
- ✅ 包含特殊字符的文本（JSON 安全转义）
- ✅ 包含 Unicode 字符的文本

## 使用方法

### 基本注入
```java
WebViewTextInjector injector = new WebViewTextInjector(webView);
injector.injectText("Hello World", callback);
```

### 模拟打字（逐字输入）
```java
injector.simulateTyping("Hello World", 50, callback); // 每个字符间隔 50ms
```

### 预注入帮助函数
在 `onPageFinished` 中调用：
```java
webViewInjector.injectHelperFunctions();
```

## 回调接口

```java
public interface InjectionCallback {
    void onSuccess(String text);        // 注入成功
    void onFailure(String error);       // 注入失败（重试后仍失败）
    void onRetry(int attempt, int maxRetries);  // 正在重试
}
```

## 配置参数

```java
private static final int MAX_RETRIES = 3;          // 最大重试次数
private static final int RETRY_DELAY_MS = 300;     // 重试间隔（毫秒）
private static final int PAGE_READY_TIMEOUT_MS = 10000;  // 页面就绪超时
```

## 后续优化建议

1. **添加动画效果**: 注入文本时添加淡入动画，提升用户体验
2. **支持富文本**: 支持注入格式化的文本（粗体、斜体等）
3. **批量注入**: 支持一次注入多个文本片段
4. **撤销/重做**: 支持文本注入的撤销操作
5. **性能监控**: 添加注入耗时统计，监控性能

## 测试建议

1. 测试各种特殊字符（引号、反斜杠、换行符）
2. 测试长文本（1000+ 字符）
3. 测试网络不稳定情况下的注入
4. 测试快速连续注入
5. 测试页面未完全加载时的注入

## 相关文件

- `app/src/main/java/com/opencode/voiceassist/utils/WebViewTextInjector.java` - 注入器工具类
- `app/src/main/java/com/opencode/voiceassist/MainActivity.java` - 主活动（已更新）

## 分支信息

- **分支**: `webview`
- **状态**: 开发中
- **目标**: 解决 WebView 文本注入不稳定问题
