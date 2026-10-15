# 知行学堂 · 视频学习平台（一期）

Spring Boot 3 + Vue3 用户端 + Vue3 管理后台 + Docker MySQL/Redis。  
视频一期使用本地 `uploads/` 存储；OSS/CDN 配置项已在 `application.yml` 预留。

## 目录结构

```
video-learning-platform/
├── backend/                 # Spring Boot API（/api）
├── frontend-user/           # 用户端 PC（Vite :5173）
├── frontend-admin/          # 管理后台（Vite :5174，Element Plus 主题）
├── uploads/                 # 本地视频/图片（gitignore）
├── docker-compose.yml       # MySQL + Redis
├── .env.example             # 环境变量示例（不含密钥）
└── scripts/                 # 启动与自测脚本
```

## 快速启动

### 1. 基础设施

```bash
cd /workspace/video-learning-platform
docker compose up -d
```

### 2. 后端

密钥从本地文件注入（**不要写入仓库**）：

```bash
set -a
source /home/box/agent-data/secrets/xyb2b-llm.env   # 或复制 .env.example 为 .env 后填写
set +a
export UPLOAD_DIR=/workspace/video-learning-platform/uploads

cd backend
./mvnw spring-boot:run
```

健康检查：`http://localhost:8080/actuator/health`

### 3. 用户端

```bash
cd frontend-user
npm install
npm run dev
# http://localhost:5173
```

### 4. 管理后台

```bash
cd frontend-admin
npm install
npm run dev
# http://localhost:5174
```

也可使用：`scripts/start-all.sh`（需已安装依赖且 Docker 已启动）。

## 预置账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | Admin@123 |
| 演示学员 | demo | Demo@123 |

## 设计取向

- 品牌名「知行学堂」：青绿（Teal `#0F766E`）主色 + 深 slate 中性色
- 用户端：自研设计系统（卡片、空态、轮播、播放页），PC 优先响应式
- 管理后台：Element Plus 二次主题（覆盖 `--el-color-primary`），深色侧栏 + 浅色内容区
- 文案中文专业；加载骨架 / 空态 / Toast 错误提示齐全

## 自测

```bash
cd backend && ./mvnw test
bash scripts/e2e-smoke.sh
```

## 说明

- LLM：OpenAI 兼容接口；密钥仅环境变量；不可达时 AI 接口优雅降级为规则文案
- 视频：本地上传至 `uploads/`；播放先调 `/api/learn/play-url/{lessonId}` 拿短时签名 URL，再 Range 流式 `/api/media/**`（JWT 或 `expires+sig`）；进度心跳约 **3 秒**
- 防下载：基线威慑（nodownload / 禁右键 / 短 TTL / 无私有对象公网 ACL），**非** DRM
- 结业：按课程 `completionRatio`（默认 0.9）生成证书编号记录

## 微信登录（开放平台网站应用扫码）

PC 用户端使用**微信开放平台「网站应用」** OAuth 扫码登录（`snsapi_login` / `qrconnect`）。

1. 在 [微信开放平台](https://open.weixin.qq.com/) 创建网站应用，拿到 **AppID / AppSecret**。
2. 授权回调域 / 回调 URI 白名单填写与 `WECHAT_REDIRECT_URI` 一致，例如：
   `https://learn.visitworld.me/login/wechat/callback`
3. 在服务器环境变量中配置（勿把真实密钥写入仓库）：

```bash
WECHAT_ENABLED=true
WECHAT_APP_ID=你的开放平台网站应用AppId
WECHAT_APP_SECRET=你的AppSecret
WECHAT_REDIRECT_URI=https://learn.visitworld.me/login/wechat/callback
# 可选：公众号网页授权（H5）hook
# WECHAT_MP_APP_ID=
# WECHAT_MP_APP_SECRET=
```

4. 重启 API。未配置 AppId/Secret 时，`GET /api/auth/wechat/status` 返回 `{enabled:false}`，扫码接口返回 **503「未配置微信登录」**，不影响账号密码登录。

相关接口：`/api/auth/wechat/status`、`/qr-url`（或 `/start`）、`/callback`、可选 `/mp-url`。

