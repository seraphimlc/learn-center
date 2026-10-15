# 知行学堂 · 代码自审记录

日期：2026-10-15（Asia/Shanghai）

## 本次已修（P0 / P1）

| 级别 | 项 | 处理 |
|------|----|------|
| P0 | 管理端 RBAC：`CONTENT_ADMIN` / `DATA_VIEWER` 可走全部写接口 | `AdminRoles` + `AdminService` 写路径校验；管理端菜单/路由按角色裁剪 |
| P1 | Redis 进度 flush 与心跳并发丢失更新 | `ProgressBufferService.flushDirty`：仅当 Redis 值未变才清 dirty；并发时保留 dirty 且不覆盖新心跳 |
| P1 | `LearnService.reportProgress` 空操作死代码 | 删除无意义 if 块 |
| P1 | 限流 IP 伪造（畸形 XFF） | `RateLimitFilter` 校验首跳是否像 IP；依赖 nginx **覆盖**而非追加 XFF |

## P2 待办（未改代码 / 较大重构）

### 安全
- 公开找回密码：仅校验 username+email，无邮件 OTP / 验证码（一期有意简化）。
- 管理端视频预览把 JWT 放在 `?token=`（`<video>` 无法带 Authorization）——可能进 access log / Referer；建议改为后台专用短时签名 URL（允许未上架课时）。
- `MediaController`：任意登录用户凭路径可读视频（公开课详情会泄露 opaque `videoPath`）；一期无付费墙，可接受；付费/报名后应收紧为仅 signed URL。
- 默认 `JWT_SECRET` / DB 密码写在 `application.yml` 默认值中——生产必须用环境变量覆盖（已有 `.env.example`）。
- `reset-password` 与登录同属 `/api/auth/**` 限流桶，可接受但可拆分。

### 正确性 / 可靠性
- `syncLessonDuration` 在 `LearnService` 与 `ProgressBufferService` **重复**；心跳路径上写课时/课程元数据会放大 DB 写——应收口到单一服务并降频。
- `tryMaybeCert` 在 Redis 未 flush 前用 DB 统计完成数，可能延迟发证（flush 后会再查）。
- `RateLimitFilter` 中 `path.contains("/upload")` 过宽，可能误伤无关路径。
- WeChat Redis 宕机时 state 降级为「长度≥16」弱校验（已有日志）——可改为本地 HMAC state。

### 风格 / 分层（架构债）
- `AdminService`（~300 行）聚合用户/新闻/课程/上传/AI/导入导出——建议拆 `AdminUserService` / `AdminContentService` / `AdminOpsService`，Controller 保持薄（当前尚可接受）。
- `AdminController` 单一类覆盖全部 `/api/admin/**`——可按资源拆多个 Controller（P2）。
- 进度合并逻辑在 `LearnService` / `ProgressBufferService` / `ContentService` 各有一份「continueLessonId」——抽共享 helper。
- 管理端 `LessonsView.vue` 偏胖（上传+列表+预览）；可抽 `useLessonUpload` composable。
- 用户端 `PlayerView.vue` 同时管播放地址刷新、心跳、笔记书签——可拆 `usePlayUrl` / `useProgressHeartbeat`。
- `UserPrincipal.isAdmin()` 已 `@Deprecated`（语义含只读角色）；调用方应改用 `isStaff()` / `canWriteContent()`。
- 前端几乎无共享 `utils` 重复（toast/http 已集中）——保持即可。
- 包边界清晰：`auth` / `learn` / `content` / `admin` / `upload` / `security`；`MediaSigningService` 放在 `upload` 合理，若播放鉴权变复杂可迁到 `learn` 或 `media`。

### 测试缺口
- 缺 RBAC 集成测试（DATA_VIEWER POST 应 403）。
- 缺 ProgressBuffer 并发 flush 单测。
- 缺 play-url / media sig 过期用例。

## 前端核对（本轮观察，多数已健康）

- Player 使用 `/learn/play-url`，非永久 OSS 公链 ✅
- Login / Register 微信按钮依赖 `/auth/wechat/status` ✅
- 移动端 Header 汉堡菜单已有 ✅
