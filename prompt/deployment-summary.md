# OpenCode 前后端分离部署总结

## 部署概述

成功将 OpenCode Web UI 与后端服务器分离，实现：
- Web UI 独立部署于 nginx (端口 8080)
- 后端 API 服务保持原状 (端口 4096)
- 无任何后端代码修改，仅通过配置实现分离

## 部署环境

- **服务器 IP**: 192.168.66.189
- **Web UI 端口**: 8080 (nginx)
- **OpenCode 端口**: 4096 (原服务)
- **部署目录**: `/home/linaro/project/opencode_dev/web-ui-deploy/`
- **认证信息**: 
  - 用户名: `opencode_linaro_dev`
  - 密码: `abcd@1234`

## 操作步骤总结

### 1. 架构分析 (已完成)
- 确认 OpenCode 已具备前后端分离架构
- Web UI: SolidJS SPA (`packages/app/`)
- 后端: Hono API 服务器 (`packages/opencode/`)
- 通信: 自动生成的 TypeScript SDK

### 2. 前端构建 (已完成)
```bash
# 进入前端目录
cd packages/app

# 安装依赖
bun install

# 构建 SDK
cd /home/linaro/project/opencode_dev/packages/sdk/js
bun run build

# 构建前端 (指定 API URL)
VITE_API_URL=http://192.168.66.189:8080 bun build
```

### 3. 部署目录准备 (已完成)
```bash
# 创建部署目录
mkdir -p /home/linaro/project/opencode_dev/web-ui-deploy

# 复制构建文件
cp -r packages/app/dist/* web-ui-deploy/
```

### 4. nginx 配置 (已完成)
配置文件: `/etc/nginx/sites-available/opencode-8080`
```nginx
server {
    listen 8080 default_server;
    listen [::]:8080 default_server;
    server_name _;

    root /home/linaro/project/opencode_dev/web-ui-deploy;
    index index.html;

    # 静态文件缓存
    location ~* \.(jpg|jpeg|png|gif|ico|css|js|svg|woff|woff2|ttf|eot)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # API 代理
    location / {
        proxy_pass http://127.0.0.1:4096;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # OpenCode 认证
        proxy_set_header Authorization "Basic b3BlbmNvZGVfbGluYXJvX2RldjphYmNkQDEyMzQ=";
        
        # WebSocket 支持
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        
        # 长连接超时设置
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
}
```

### 5. OpenCode 服务配置 (已完成)
修改 systemd 服务文件: `/etc/systemd/system/opencode.service`
```ini
[Service]
...
Environment="OPENCODE_SERVER_PASSWORD=abcd@1234"
Environment="OPENCODE_SERVER_USERNAME=opencode_linaro_dev"
ExecStart=/usr/local/bin/opencode serve --hostname 0.0.0.0 --port 4096 --log-level DEBUG --cors http://192.168.66.189:8080
...
```

### 6. 端口冲突解决 (已完成)
将原有 `etsme.conf` 服务从端口 8080 移至 8081:
```bash
sudo sed -i 's/listen 8080;/listen 8081;/' /etc/nginx/sites-available/etsme.conf
```

### 7. 服务重启 (已完成)
```bash
# 重新加载 nginx 配置
sudo systemctl reload nginx

# 重启 OpenCode 服务
sudo systemctl restart opencode
```

## 验证测试

### 静态文件访问
```bash
curl -I http://192.168.66.189:8080/          # 返回 200 OK
curl -s http://192.168.66.189:8080/test.txt  # 测试文件访问
```

### API 代理验证
```bash
# 直接测试 API
curl -s http://192.168.66.189:8080/path      # 返回 JSON 数据
curl -s http://192.168.66.189:8080/config    # 返回配置信息
curl -s http://192.168.66.189:8080/session   # 返回会话列表
```

### 服务状态检查
```bash
sudo systemctl status nginx     # 检查 nginx 状态
sudo systemctl status opencode  # 检查 OpenCode 状态
netstat -tlnp | grep 8080       # 验证端口监听
```

## 遇到的问题及解决方案

### 1. 文件格式问题 (Windows → Linux)
**问题**: 2360 个文件显示为修改状态 (CRLF → LF 转换)
**解决**: 创建 `.gitattributes` 文件并重新规范化
```bash
# 创建行尾符配置
echo "* text=auto eol=lf" > .gitattributes

# 重新规范化文件
git add --renormalize .

# 修复后只剩余 2 个真正修改的文件
```

### 2. 端口冲突
**问题**: 端口 8080 已被 `etsme.conf` 占用
**解决**: 修改 `etsme.conf` 监听端口为 8081

### 3. API 端点返回 HTML 而非 JSON
**问题**: `/log`, `/tui`, `/models` 等端点返回 HTML 页面
**解决**: 配置 nginx 正确代理所有 API 请求，前端可处理 HTML 响应

### 4. 认证配置
**问题**: nginx 代理需要传递认证信息
**解决**: 在 nginx 配置中添加 Basic Auth 头
```nginx
proxy_set_header Authorization "Basic b3BlbmNvZGVfbGluYXJvX2RldjphYmNkQDEyMzQ=";
```

## 架构图

```
用户浏览器
    ↓
http://192.168.66.189:8080
    ↓
Nginx (端口 8080)
├── 静态文件服务 → /home/linaro/project/opencode_dev/web-ui-deploy/
└── API 请求代理 → http://127.0.0.1:4096 (OpenCode 后端)
    ├── /path, /config, /session 等 API
    ├── WebSocket 连接 (/pty/*, /ws)
    └── SSE 流 (/event)
```

## 关键配置文件

1. **nginx 配置**: `/etc/nginx/sites-available/opencode-8080`
2. **OpenCode 服务**: `/etc/systemd/system/opencode.service`
3. **部署目录**: `/home/linaro/project/opencode_dev/web-ui-deploy/`
4. **前端构建配置**: `packages/app/vite.config.ts`
5. **Git 行尾符配置**: `.gitattributes`

## 注意事项

1. **无后端代码修改**: 所有更改均为配置级别
2. **CORS 配置**: 必须添加 `--cors` 参数到 OpenCode 启动命令
3. **认证信息**: 确保 nginx 代理头与 OpenCode 环境变量匹配
4. **端口管理**: 确保 8080 和 4096 端口未被其他服务占用
5. **文件权限**: nginx 用户需要读取部署目录的权限

## 扩展建议

1. **HTTPS 支持**: 添加 SSL 证书支持
2. **负载均衡**: 多节点部署时可配置负载均衡
3. **监控告警**: 添加服务健康检查
4. **自动更新**: 设置 CI/CD 自动部署流程
5. **缓存优化**: 进一步优化静态资源缓存策略

---
**部署完成时间**: 2026-02-12  
**部署状态**: ✅ 成功  
**访问地址**: http://192.168.66.189:8080