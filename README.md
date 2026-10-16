# 知行学堂 · 视频学习平台（一期）

Spring Boot 3（**嵌入式 Tomcat** 可执行 jar，非外置 WAR）+ Vue3 用户端 + Vue3 管理后台 + Docker MySQL/Redis。  
视频一期使用本地 `uploads/` 存储；OSS/CDN 配置项已在 `application.yml` 预留。

线上：https://learn.visitworld.me/ · 管理端 `/admin/`  
规模口径：业务约 **3 万**注册 / DID 设计 **30 万** / 峰值约 **1 万**在线（**当前不分片**）。

## 文档

| 文档 | 说明 |
|------|------|
| [docs/PRD.md](docs/PRD.md) | 产品需求（功能/角色/验收） |
| [docs/TECH_DESIGN.md](docs/TECH_DESIGN.md) | 技术设计（架构/数据模型/安全/部署） |
| [docs/COLD_START.md](docs/COLD_START.md) | 新人 / Agent 冷启动 |
| [docs/SECURITY.md](docs/SECURITY.md) | 安全控制与残余风险（审计向） |
| [docs/DATA_SCALE.md](docs/DATA_SCALE.md) | 规模与分片原则（诚实：现阶段不分片） |
| [docs/OVERNIGHT_REVIEW.md](docs/OVERNIGHT_REVIEW.md) | 隔夜自审纪要 |
| [docs/FEATURE_GAP.md](docs/FEATURE_GAP.md) | RFP 差距与交付说明 |
| [docs/CODE_REVIEW.md](docs/CODE_REVIEW.md) | 代码自审与 P2 债 |
| [docs/HIGH_CONCURRENCY.md](docs/HIGH_CONCURRENCY.md) | 高并发已落地能力与切点 |
| [docs/CACHE_ANALYSIS.md](docs/CACHE_ANALYSIS.md) | MySQL 读热点与 Redis 缓存设计 |

## 目录结构

```
video-learning-platform/
├── backend/                 # Spring Boot API（embedded Tomcat /api）
├── frontend-user/           # 用户端 PC（Vite :5173）
├── frontend-admin/          # 管理后台（Vite :5174）
├── docs/                    # 需求与设计文档
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

### 2. 后端（嵌入式 Tomcat jar 方式）

```bash
cp .env.example .env   # 填写后使用；勿提交 .env
set -a && source .env && set +a
export UPLOAD_DIR=/workspace/video-learning-platform/uploads

cd backend
./mvnw spring-boot:run
```

健康检查：`http://localhost:8080/actuator/health`

### 3. 用户端

```bash
cd frontend-user && npm install && npm run dev
# http://localhost:5173
```

### 4. 管理后台

```bash
cd frontend-admin && npm install && npm run dev
# http://localhost:5174
```

也可：`scripts/start-all.sh`。

## 预置账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | Admin@123 |
| 演示学员 | demo | Demo@123 |

## 设计取向

- 品牌「知行学堂」：青绿 Teal `#0F766E` + 深 slate  
- 用户端自研设计系统；管理端 Element Plus 主题  
- 中文文案；骨架/空态/友好错误提示  

## 自测

```bash
cd backend && ./mvnw test
bash scripts/e2e-smoke.sh
```

## 关键能力摘要

- **JWT + RBAC**：ADMIN / CONTENT_ADMIN / DATA_VIEWER；写路径 `AdminRoles` 校验  
- **进度**：约 3s 心跳 → **仅 Redis**（TTL 默认 14d；完成稀疏写 MySQL；无心跳 flush）  
- **播放**：`/api/learn/play-url/{lessonId}` 短时签名；学员无签名不可直下视频  
- **防下载**：基线威慑（非 DRM）  
- **微信**：开放平台网站应用扫码；未配置则降级  
- **AI**：OpenAI 兼容；失败 degraded  
- **结业**：`completionRatio`（默认 0.9）发证  

## 微信登录

PC 使用微信开放平台「网站应用」扫码（`snsapi_login`）。配置见 `.env.example`（`WECHAT_*`）。未配置时 `GET /api/auth/wechat/status` 返回 `{enabled:false}`。

## 密钥

**不要**把 `.env`、真实 AppId/Secret、LLM Key、生产 JWT 提交进仓库。仅保留 `.env.example`。
