# 日志文件目录

本目录存放 OpenCode 语音助手开发过程中的各种日志文件。

## 日志分类

### 编译日志

| 文件名 | 说明 | 生成方式 |
|-------|------|---------|
| `build_*.log` | 编译输出日志 | 编译脚本生成 |
| `build_error.txt` | 编译错误汇总 | 手动收集 |

### 运行时日志

| 文件名 | 说明 | 生成方式 |
|-------|------|---------|
| `logcat_*.txt` | Android 设备日志 | `collect_logs.bat` |
| `logcat_full.txt` | 完整日志 | ADB logcat |
| `logcat_errors.txt` | 错误日志筛选 | 手动筛选 |

### 测试日志

| 文件名 | 说明 | 生成方式 |
|-------|------|---------|
| `*_test.txt` | 测试结果日志 | 测试脚本生成 |
| `audio_test_*.txt` | 音频测试日志 | 音频测试 |

## 日志命名规范

```
logcat_YYYYMMDD_HHMMSS.txt    - 按时间命名的日志文件
build_YYYYMMDD.log            - 按日期命名的编译日志
<模块>_<类型>_<日期>.txt       - 功能模块日志
```

## 日志收集方法

### 使用脚本自动收集

```batch
cd tools
collect_logs.bat
```

### 手动收集日志

```batch
# 清除旧日志
adb logcat -c

# 收集日志
adb logcat -v threadtime > logs\logcat_manual.txt

# 按标签筛选
adb logcat -s MainActivity:D
```

## 日志分析

### 常用筛选命令

```batch
# 筛选应用相关日志
adb logcat | findstr "com.opencode.voiceassist"

# 筛选特定标签
adb logcat | findstr "MainActivity"

# 筛选错误信息
adb logcat | findstr "E/"

# 筛选警告信息
adb logcat | findstr "W/"
```

### PowerShell 筛选

```powershell
# 读取日志并筛选
Get-Content logcat.txt | Select-String "MainActivity|OpenCode|Whisper"

# 保存筛选结果
Get-Content logcat.txt | Select-String "ERROR|FATAL" > errors.txt
```

## 日志清理

建议定期清理旧日志，保留最近 30 天的日志：

```powershell
# PowerShell 清理脚本示例
Get-ChildItem *.txt | Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-30) } | Remove-Item
```

## 注意事项

1. **日志大小**: logcat 日志可能很大，注意磁盘空间
2. **敏感信息**: 日志中可能包含敏感信息，分享前请脱敏
3. **时间同步**: 确保设备时间和电脑时间同步，便于分析
4. **定期备份**: 重要的测试日志建议备份到外部存储
