# 知行学堂 · 冷启动指南（COLD_START）

给：**另一名开发 / Agent** 在不熟悉仓库时快速上手。  
先读本文 → [PRD.md](./PRD.md) → [TECH_DESIGN.md](./TECH_DESIGN.md) → [SECURITY.md](./SECURITY.md) → [FEATURE_GAP.md](./FEATURE_GAP.md) → [CODE_REVIEW.md](./CODE_REVIEW.md) → [HIGH_CONCURRENCY.md](./HIGH_CONCURRENCY.md) → [DATA_SCALE.md](./DATA_SCALE.md) → [OVERNIGHT_REVIEW.md](./OVERNIGHT_REVIEW.md)。

---

## 1. 30 秒认知

- 视频学习平台「知行学堂」：Spring Boot 3 **嵌入式 Tomcat jar** + Vue3 用户端 + Vue3 管理端  
- 远程：`github.com/seraphimlc/learn-center`  
- 现网：`https://learn.visitworld.me/` 与 `/admin/`；服务器目录 `/opt/visitworld-deploy/learn`  
- 规模：业务 ~3 万注册，DID 设计 30 万，峰值 ~1 万；**当前不分片**  

---

## 2. 目录地图

| 路径 | 做什么 |
|------|--------|
| `backend/src/main/java/com/vlp/security/` | JWT、限流、RBAC、安全头、RequestId |
| `.../auth/` `.../auth/wechat/` | 账密与微信 |
| `.../learn/async/ProgressBufferService` | 进度 Redis-only（必读；无 flush） |
| `.../upload/MediaSigningService` `MediaController` | 播放签名与 ACL |
| `.../admin/AdminService` `AdminRoles` | 管理写权限 |
| `.../ai/` | AI 中心 |
| `frontend-user/src/views/PlayerView.vue` | 心跳与 play-url |
| `frontend-admin/src/views/LessonsView.vue` | 课时上传工作台 |
| `docs/` | 需求与设计 |
| `.env.example` | 环境变量模板（无真密钥） |

---

## 3. 本地运行

### 3.1 依赖

- JDK 17+、Maven Wrapper（`./mvnw`）、Node 18+、Docker  

### 3.2 基础设施

```bash
cd /workspace/video-learning-platform   # 或你的 clone 路径
docker compose up -d
# MySQL: vlp/vlp123 @ video_learning ; Redis :6379
```

### 3.3 后端（embedded Tomcat）

```bash
cp .env.example .env   # 填写 JWT_SECRET 等；不要提交 .env
set -a && source .env && set +a
export UPLOAD_DIR="$(pwd)/uploads"
cd backend && ./mvnw spring-boot:run
# 健康检查 http://localhost:8080/actuator/health
```

**确认**：进程是 Spring Boot 内嵌 Tomcat，无需安装外置 Tomcat，无需 WAR。

### 3.4 前端

```bash
cd frontend-user && npm i && npm run dev    # :5173
cd frontend-admin && npm i && npm run dev   # :5174
```

或 `scripts/start-all.sh`（需依赖已装、Docker 已起）。

### 3.5 测试

```bash
cd backend && ./mvnw test
bash scripts/e2e-smoke.sh
```

---

## 4. 演示账号（种子数据）

| 用户 | 密码 | 角色 |
|------|------|------|
| admin | Admin@123 | ADMIN |
| demo | Demo@123 | USER |

由 `DataInitializer` 在库空时写入。**不要编造生产密钥**；微信/LLM 未配时功能降级即可。

---

## 5. 环境变量（常用）

见 `.env.example` 与 `application.yml`：

- `JWT_SECRET`（≥32 字符）  
- `SPRING_DATASOURCE_*` / Redis  
- `UPLOAD_DIR`  
- `WECHAT_ENABLED` / `WECHAT_APP_ID` / `WECHAT_APP_SECRET` / `WECHAT_REDIRECT_URI`  
- `LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL`  
- `PUBLIC_RESET_ENABLED`  
- `PROGRESS_ASYNC_ENABLED` / `PROGRESS_TTL_DAYS` / `MEDIA_SIGNED_TTL` / `RL_*`  
- `VLP_CORS_ORIGINS`  

**禁止**把真实 AppSecret、LLM Key、生产 JWT 提交进 git。

---

## 6. 建议第一批阅读的代码流

1. **登录**：`AuthController` → `AuthService` → `JwtService` → `JwtAuthFilter`  
2. **播放**：`PlayerView` → `GET /learn/play-url` → `MediaSigningService` → `MediaController`  
3. **进度**：`POST /learn/progress` → `LearnService` → `ProgressBufferService.accept`（**仅 Redis**；完成时稀疏写 MySQL）  
4. **管理 RBAC**：`SecurityConfig` hasRole → `AdminService` + `AdminRoles.require*`  
5. **微信**：`WechatAuthService` state Redis → callback upsert user  

---

## 7. 部署笔记

- 构建：后端 `./mvnw -DskipTests package` 得 jar；前端 `npm run build`  
- nginx：`/` 用户端，`/admin/` 管理端，`/api/` 反代 jar  
- uploads 目录持久化挂载；开启 OSS 后对象私有  
- X-Forwarded-For：**覆盖**写入  
- HSTS 在 nginx 配  

---

## 8. 常见坑

| 坑 | 处理 |
|----|------|
| 播放 401 | 是否先调 play-url；签名是否过期；学员不能只靠 JWT 下视频 |
| 进度不涨 | Redis 是否通；TTL 是否过期（过期=从 0）；完成比例与 duration |
| 管理 403 | DATA_VIEWER 不能写；CONTENT_ADMIN 不能管用户 |
| 微信 503 | 未配 AppId/Secret 或 Redis 挂 |
| CORS | 把前端源加入 `VLP_CORS_ORIGINS` |
| 误当 WAR 部署 | 用 jar + embedded Tomcat |
| 提交密钥 | 检查 `.gitignore`；只交 `.env.example` |
| 分片幻想 | `SHARD_ENABLED` 默认 false，无分表 |

---

## 9. 改动后自检

- [ ] `./mvnw test`  
- [ ] 演示账号走完上传→播放→进度→证书  
- [ ] DATA_VIEWER 写接口 403  
- [ ] 未登录视频 401  
- [ ] 无密钥进 diff  

祝冷启动顺利。有歧义以 PRD + 现网行为 + FEATURE_GAP 为准。


---

## 10. 模块阅读顺序（建议 2 小时）

| 顺序 | 文件 | 你要搞懂的问题 |
|------|------|----------------|
| 1 | `application.yml` | 开关与默认值 |
| 2 | `SecurityConfig` + `AdminRoles` | 谁能访问什么 |
| 3 | `AuthService` + `JwtService` | token 怎么发 |
| 4 | `WechatAuthService` | state 为什么必须 Redis |
| 5 | `ProgressBufferService` | 为什么心跳**永不**写 MySQL；TTL；稀疏完成 |
| 6 | `LearnService.myLearning` | continueLessonId |
| 7 | `MediaSigningService` + `MediaController` | 为什么不能把 OSS 公链给前端 |
| 8 | `AdminService` 写方法开头的 require* | RBAC 落点 |
| 9 | `AiService` 任一方法 + degraded | LLM 挂了怎么办 |
| 10 | `PlayerView.vue` + 两端 `api/http.js` | 心跳与错误文案 |

## 11. 数据从哪来

- 空库首次启动：`DataInitializer` 写 admin/demo、示例分类课程新闻  
- 表结构：Hibernate `ddl-auto: update`（开发方便；生产变更建议改为受控 SQL，P2）  
- 媒体文件：`uploads/videos` `uploads/images`  

## 12. 不要做的事

- 不要提交 `.env`、pem、真实微信/LLM 密钥  
- 不要把 `SHARD_ENABLED=true` 当已实现分片  
- 不要改成 WAR + 外置 Tomcat 除非有单独迁移设计  
- 不要在日志打印 Authorization 头  
- 不要把学员视频 ACL 改回「任意登录即可按 path 下载」  

## 13. 与文档的关系

| 你想知道 | 读 |
|----------|----|
| 产品要什么 | PRD.md |
| 代码怎么分层 | TECH_DESIGN.md |
| 安全审计怎么答 | SECURITY.md |
| 要不要分库分表 | DATA_SCALE.md（答案：现在不要） |
| 和 RFP 差在哪 | FEATURE_GAP.md |
| 还没改的债 | CODE_REVIEW.md |
| 并发数字 | HIGH_CONCURRENCY.md |
| 昨晚改了什么 | OVERNIGHT_REVIEW.md |
