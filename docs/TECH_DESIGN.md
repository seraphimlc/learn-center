# 知行学堂 · 技术设计（TECH_DESIGN）

版本：2026-10-15 · 与现网代码对齐

---

## 1. 总览

### 1.1 技术栈

| 层 | 技术 |
|----|------|
| API | **Java 17 + Spring Boot 3**，**嵌入式 Tomcat**（`spring-boot:run` / 可执行 **jar**），**不是**外置/独立 Tomcat WAR 部署 |
| 用户端 | Vue 3 + Vite + Pinia（`:5173`） |
| 管理端 | Vue 3 + Vite + Element Plus（`:5174`，base `/admin/`） |
| DB | MySQL 8（JPA `ddl-auto: update`） |
| 缓存/缓冲 | Redis 7（Lettuce） |
| 媒体 | 一期本地 `uploads/`；OSS/CDN 配置预留 |
| AI | OpenAI 兼容 HTTP（`LLM_*` 环境变量） |

### 1.2 逻辑架构

```
Browser (user/admin)
    → nginx (TLS, / /admin/ /api/)
        → Spring Boot jar (embedded Tomcat :8080)
            → MySQL (权威数据)
            → Redis (进度缓冲 / 限流 / 首页缓存 / 微信 state)
            → 可选 OSS (私有桶短时签名)
            → 可选 LLM
```

### 1.3 包模块

```
com.vlp
  auth / auth.wechat     登录注册 JWT 微信
  security               Jwt* RateLimit AdminRoles Headers RequestId
  user                   User 实体
  content                课程新闻分类 Banner 配置 + PublicController
  learn                  进度笔记书签 + ProgressBufferService
  upload                 Upload MediaSigning MediaController
  admin                  AdminController AdminService Audit*
  ai                     AiService LlmClient AiCache
  cert / notify / stats / cache / config
```

---

## 2. 运行与部署拓扑

### 2.1 本地

- `docker compose up -d` → MySQL:3306 Redis:6379  
- `./mvnw spring-boot:run` → **embedded Tomcat** 8080  
- 两前端 `npm run dev`  

### 2.2 生产（现网约定）

- 路径：`/opt/visitworld-deploy/learn`  
- 站点：`https://learn.visitworld.me/`、`/admin/`  
- API 进程：Spring Boot **可执行 jar + 嵌入式 Tomcat**（host 网络或反代至 8080）  
- 静态：用户端/管理端 build 产物由 nginx 托管  
- **不要**打 WAR 丢进外置 Tomcat  

### 2.3 配置

- 密钥只走环境变量 / `.env`（gitignore）；仓库仅 `.env.example`  
- 关键项：`JWT_SECRET`、DB、Redis、`UPLOAD_DIR`、`WECHAT_*`、`LLM_*`、`OSS_*`、`CDN_*`、`PROGRESS_*`、`RL_*`、`PUBLIC_RESET_ENABLED`、`VLP_CORS_ORIGINS`  

---

## 3. API 风格

- 前缀 `/api`  
- 统一包装：`{ code: 0|业务码, message, data }`（`ApiResponse`）  
- 业务错误用 `BusinessException(code, msg)`；未知异常 HTTP 500 + 固定文案  
- 认证：`Authorization: Bearer <jwt>`  
- 公开：`/api/auth/login|register|reset-password`、`/api/auth/wechat/**`、`GET /api/public/**`、`GET /api/media/images/**`  
- 视频 media GET 过滤器层 permitAll，**控制器内**验签/员工  

---

## 4. 数据模型概览（ER）

无物理外键（逻辑关联，便于日后扩展）。核心实体：

```
users 1──* study_progress *──1 lessons *──1 courses *──1 categories
users 1──* certificates *──1 courses
users 1──* study_notes / bookmarks / notifications
courses 1──* lessons (videoPath → uploads 相对路径)
news / banners / site_config / audit_logs / visit_log / ai_cache / study_alerts
```

### 4.1 表与索引要点

| 表 | 关键约束/索引 | 备注 |
|----|----------------|------|
| users | username 唯一；wechatOpenId/UnionId 唯一 | passwordHash 可空 |
| courses | categoryId；status | completionRatio 默认 0.9 |
| lessons | courseId | videoPath opaque |
| study_progress | **uk(userId,lessonId)**；idx(userId,courseId)；idx(userId,updatedAt) | 热点表 |
| certificates | uk(userId,courseId)；certNo 唯一 | |
| audit_logs | operatorId；createdAt | 可按月归档 |
| visit_log | createdAt；path | 可按月归档 |
| ai_cache | 按业务 key | LLM 结果缓存 |

### 4.2 软删 / FK

- 一期：**硬删**；删课先删课时（AdminService）  
- 无 JPA `@ManyToOne` 强 FK，避免跨库难迁  
- P2：若合规需要，可加 `deleted_at`  

### 4.3 分片

**当前不做。** 原则与迁移见 [DATA_SCALE.md](./DATA_SCALE.md)。`SHARD_ENABLED=false`。

---

## 5. Redis 职责

| Key/用途 | TTL/结构 | 失败策略 |
|----------|----------|----------|
| `vlp:progress:{uid}:{lessonId}` | String JSON，7d | 写失败则同步落 MySQL |
| `vlp:progress:dirty` | Set | flush 2s / 批 200；值未变才清 dirty（防并发丢更新） |
| `vlp:cache:homeAnon` 等 | Cache TTL ~30–60s | 未命中打库 |
| `rl:{bucket}:{ip}:{minute}` | 计数 2min | fail-open |
| `vlp:wechat:oauth:state:{state}` | 10m | start/回调均依赖；宕机拒绝 OAuth |

权威数据在 MySQL。Redis 不做长期进度源。

### 5.1 进度并发

`flushDirty`：读 dirty → 写 DB → 再读 Redis；若与快照相同则清 dirty 并回写带 id 的 JSON；若不同则保留 dirty，避免覆盖更新心跳。

---

## 6. 媒体签名

- `MediaSigningService.mintPlayUrl`：课程须 PUBLISHED；登录用户；规范化 object key  
- 本地：`HMAC-SHA256(GET\\n{expires}\\n{key})` → `/api/media/{key}?expires&sig`  
- OSS 开：V1 签名 GET；对象保持私有  
- 封面可用 `CdnUrlService`；**播放禁止永久公网 URL**  

---

## 7. 安全设计摘要

详见 [SECURITY.md](./SECURITY.md)。要点：BCrypt、JWT、AdminRoles 细粒度、签名播放、限流、安全头、HtmlSanitizer、上传白名单。

CSRF：无 Cookie 会话故关闭。HSTS：生产 nginx。

---

## 8. AI 中心

- `LlmClient`：OpenAI 兼容 chat；无 key 则 unavailable  
- `AiService`：分析/推荐/知识点/测验/问答/摘要；失败 degraded  
- `ai_cache` 持久化部分结果；测验会话 `ConcurrentHashMap`（单机）  

---

## 9. 可观测性

| 项 | 说明 |
|----|------|
| Request-Id | `X-Request-Id` + MDC `rid=` |
| 日志 | auth.login.* / media.deny / progress flush warn；禁打密钥 |
| Actuator | `/actuator/health`、`/info`；勿对公网暴露更多 |
| 业务看板 | PV/UV、学习指标 |
| 建议盯 | Redis dirty 大小、Hikari active、Tomcat 线程、5xx 率、磁盘 uploads |

AlertScheduler：每日扫描长期未学并发通知。

---

## 10. 前端要点

- 用户端：`PlayerView` 3s 心跳 + play-url 过期前刷新；友好错误文案  
- 管理端：角色裁剪路由；课时工作台上传  
- 代理：dev 将 `/api` 转到 8080  

---

## 11. 已知 P2 债（摘自 CODE_REVIEW）

- 找回无 OTP（可关开关）  
- syncLessonDuration 重复；tryMaybeCert 可能延迟  
- AdminService/Controller 偏胖  
- 缺 RBAC / ProgressBuffer / play-url 自动化测试补强  
- 读写分离 / MQ flush / 真 OSS 凭证待商务确认  

---

## 12. 目录速查

```
backend/          Spring Boot jar（embedded Tomcat）
frontend-user/    学员 PC
frontend-admin/   管理端
docs/             PRD TECH COLD_START SECURITY DATA_SCALE …
uploads/          本地媒体（gitignore）
docker-compose.yml
.env.example
scripts/
```


---

## 13. 请求处理链（嵌入式 Tomcat）

Spring Boot 启动 `TomcatServletWebServerFactory`（默认），监听 `SERVER_PORT`（默认 8080）。过滤器顺序（概念）：

1. `RequestIdFilter`（最高优先级之一）写 MDC  
2. `SecurityHeadersFilter` + Spring Security headers  
3. `RateLimitFilter`  
4. `JwtAuthFilter`  
5. `SecurityFilterChain` 授权  
6. Controller → Service → Repository / Redis  

线程：`TOMCAT_MAX_THREADS` 默认 400，`max-connections` 10000，`accept-count` 200。连接池 Hikari 默认 50。这是单机 jar 调优，不是外置 Tomcat `server.xml`。

## 14. 进度状态机

```
上报 position, delta, heartbeat, ended, videoDuration
  → 加载 Redis JSON 或 DB 行
  → pos 截断（非 ended 且 > max+jump）
  → max = max(max, pos)；（ended 可用视频时长抬高 max）
  → studied += min(delta, 60)
  → 完成：ended OR max >= ceil(duration * ratio)
  → SET Redis + SADD dirty
  → 若 completed：尝试发证（可能因 DB 滞后延迟）

定时 flushDirty:
  → SPOP/随机抽样 batch
  → upsert study_progress
  → 若 Redis 值未变：写回 id 并 SREM dirty
  → 若变了：保留 dirty（下轮再刷）
```

读路径 `mergeCourseProgress`：DB 行 + overlay Redis。

## 15. JWT 载荷

```
sub = username
uid = userId (number)
role = USER|ADMIN|CONTENT_ADMIN|DATA_VIEWER
iat / exp
```

`ROLE_` 前缀由过滤器加给 Spring Security。角色变更后旧 token 仍有效至过期（无黑名单，P2）。

## 16. 配置键对照（application.yml ↔ env）

| yml | env | 默认意图 |
|-----|-----|----------|
| vlp.jwt.secret | JWT_SECRET | 生产必改 |
| vlp.auth.public-reset-enabled | PUBLIC_RESET_ENABLED | true |
| vlp.learn.progress-async-enabled | PROGRESS_ASYNC_ENABLED | true |
| vlp.learn.progress-flush-interval-ms | PROGRESS_FLUSH_INTERVAL_MS | 2000 |
| vlp.media.signed-ttl-seconds | MEDIA_SIGNED_TTL | 900 |
| vlp.rate-limit.auth-per-minute | RL_AUTH_PER_MIN | 30 |
| vlp.rate-limit.reset-per-minute | RL_RESET_PER_MIN | 10 |
| vlp.rate-limit.progress-per-minute | RL_PROGRESS_PER_MIN | 180 |
| vlp.cors.origins | VLP_CORS_ORIGINS | 本地+learn 域 |
| vlp.oss.* / vlp.cdn.* | OSS_* CDN_* | 默认关 |
| vlp.shard.enabled | SHARD_ENABLED | false |
| spring.datasource-readonly.enabled | DB_READONLY_ENABLED | false（未接线） |

## 17. 日志字段约定

控制台 pattern 含 `rid=%X{requestId}`。

| 事件 | 级别 | 字段 | 禁止 |
|------|------|------|------|
| auth.login.ok | INFO | userId username | 密码/token |
| auth.login.fail | WARN | username | 密码 |
| media.deny | INFO | path hasUser signed | 完整 sig 密钥 |
| progress flush failed | WARN | key message | 用户隐私正文 |
| wechat redis | WARN/ERROR | 原因 | AppSecret |
| unhandled | ERROR | rid + stack（仅服务端） | 回传客户端 |

## 18. 监控建议（ops）

1. `GET /actuator/health` → UP（MySQL+Redis）  
2. 日志 5xx / `Unhandled error` 计数  
3. Redis：`SCARD vlp:progress:dirty` 持续上涨说明 flush 跟不上  
4. MySQL：`study_progress` 行数、慢查询  
5. 磁盘：`UPLOAD_DIR`  
6. 证书/登录失败突增 → 撞库或配置错误  

未内置 Prometheus 端点暴露（避免公网 metrics 泄漏）。需要时在内网加 `management.endpoints` 并鉴权。

## 19. 异常 UX

- 后端：`GlobalExceptionHandler` 业务码映射 HTTP；未知 Exception → 500「服务器内部错误，请稍后重试」  
- 上传超限：400「上传文件超过大小限制」  
- 用户端 axios：识别 401/403/429/5xx/超时/断网，Toast 友好中文，过滤疑似堆栈字符串  
- 管理端：Element `ElMessage.error` 同样策略；401/403 清 token 回登录  

## 20. 已知安全相关实现选择

- 学员视频必须签名（本轮修复）；员工可 JWT 预览未上架调试仍受课程 PUBLISHED 约束于 play-url（直链员工预览走 media JWT）  
- HtmlSanitizer 为正则基线，非 jsoup 白名单  
- 限流按 IP 非按 user（NAT 下共享额度）  

## 21. P2 债务索引

完整列表见 [CODE_REVIEW.md](./CODE_REVIEW.md)。设计上接受：无 JWT 黑名单、无邮件 OTP、AdminService 聚合、测验内存会话、读写分离未接线、分片未实现。
