# 编译和调试工具

本目录包含 OpenCode 语音助手的编译、安装和调试脚本。

## 脚本列表

### 编译脚本

| 脚本名称 | 功能 | 使用场景 |
|---------|------|---------|
| `build.bat` | 快速编译 | 日常开发，自动查找 Java |
| `build_full.bat` | 完整编译 | 首次编译，检查环境 |
| `build_fast.bat` | 快速编译（阿里云镜像） | 国内网络环境，使用镜像加速 |

### 部署脚本

| 脚本名称 | 功能 | 使用场景 |
|---------|------|---------|
| `install.bat` | 安装 APK 到设备 | 编译完成后安装 |

### 调试脚本

| 脚本名称 | 功能 | 使用场景 |
|---------|------|---------|
| `debug.bat` | 检查应用状态 | 排查问题 |
| `collect_logs.bat` | 收集日志 | 问题分析 |

## 使用示例

### 快速开发流程

```batch
# 1. 编译项目
cd tools
build.bat

# 2. 安装到设备
install.bat

# 3. 收集日志（在另一个窗口）
collect_logs.bat
```

### 完整编译流程

```batch
# 完整环境检查和编译
build_full.bat
```

### 调试流程

```batch
# 检查应用状态
debug.bat

# 收集详细日志
collect_logs.bat
```

## 环境要求

- Windows 10/11
- Android SDK
- JDK 11 或更高版本
- ADB (Android Debug Bridge)

## 常见问题

### Q: ADB 未找到
**解决**: 确保 Android SDK 的 `platform-tools` 目录在系统 PATH 中

### Q: Java 未找到
**解决**: 设置 JAVA_HOME 环境变量，或在脚本中修改路径

### Q: 编译失败
**解决**: 
1. 检查 local.properties 是否存在
2. 运行 `build_full.bat` 进行完整环境检查
3. 查看 `logs` 目录中的编译日志

### Q: 安装失败
**解决**:
1. 确保设备已连接并开启 USB 调试
2. 检查 APK 是否存在
3. 卸载旧版本后重试
