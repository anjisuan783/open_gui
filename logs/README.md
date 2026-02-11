OpenCode 开发日志整理指南

本目录用于整理在调试、构建、测试过程中的日志文件，便于追踪问题和回溯历史。

日志分类示例
- logcat-<date>.txt: 设备日志（adb logcat）导出
- build-<date>.log: 构建输出日志
- test-<date>.log: 测试输出日志
- session-<date>.txt: 某次会话的汇总日志

整理建议
- 按会话时间创建单独的归档文件夹：logs/archive/session-YYYYMMDD_HHMMSS/
- 将相关日志复制/移动到该文件夹下，避免混乱
- 更新日志汇总（如 logs/SESSION_SUMMARY.md）以记录本次会话的要点、关键问题与结论

示例：
- logs/archive/session_20260211_134500/ 目录下放置相关日志
- logs/SESSION_SUMMARY.md 记录本次会话要点

如需快速整理，请运行 collect_logs.bat / 脚本来导出日志，然后手动归档到官方格式。
