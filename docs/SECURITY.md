# 知行学堂 · 安全说明（审计向）

版本：2026-10-15 · 部署 https://learn.visitworld.me/

本文面向客户安全评审 / 内部审计，描述**已实施控制**与**残余风险**。防下载声明：仅为基线访问控制与威慑，**不是**商业 DRM。

## 1. 认证（AuthN）

| 控制 | 实现 |
|------|------|
| 密码存储 | BCrypt（Spring `BCryptPasswordEncoder`） |
| 会话 | 无状态 JWT（HS256），claims：`uid` + `role`；默认 72h |
| 密钥 | `JWT_SECRET` 环境变量；启动时要求 ≥32 字节 |
| 微信登录 | 开放平台网站应用 OAuth；`state` Redis TTL 10m 一次性消费 |
| 微信-only 账号 | `passwordHash` 可空，禁止密码登录 |
| Query Token | **仅** `/api/media/**` 接受 `?token=`（`<video>` 预览）；其它 API 必须 Bearer |
| 公开找回 | 用户名+邮箱（无 OTP）；可用 `PUBLIC_RESET_ENABLED=false` 关闭 |
| 密码策略 | ≥8 位且同时含字母与数字 |

## 2. 授权（AuthZ / RBAC）

| 角色 | 能力 |
|------|------|
| USER | 学员端学习 / AI / 证书 / 通知 |
| ADMIN | 全部管理写 + 用户/角色/配置/导入 |
| CONTENT_ADMIN | 内容 CMS 写；不可管用户与站点配置 |
| DATA_VIEWER | 看板/列表/学习导出只读 |

- Spring Security：`/api/admin/**` 需上述三员工角色之一  
- **细粒度写权限**在 `AdminRoles` + `AdminService`（DATA_VIEWER 写接口 403）  
- 学习数据导出：仅 ADMIN / DATA_VIEWER  
- 用户导入模板：仅 ADMIN  
- 角色降权：禁止取消唯一超级管理员  

## 3. 传输与接口面

- HTTPS：生产由 nginx 终结（建议 HSTS 在 nginx 配置）  
- CSRF：SPA + Bearer，无 Cookie 会话 → CSRF 关闭；**勿**改为 Cookie 存 JWT 而不开 CSRF  
- CORS：`VLP_CORS_ORIGINS` 白名单，禁止 `*`  
- 安全头：`X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`、Referrer-Policy、Permissions-Policy、CSP 基线  
- Actuator：仅暴露 `health` / `info`  

## 4. 媒体与防下载基线

1. 公开课程 API 的 `videoPath` 为 **opaque storage key**，非永久公网 URL  
2. 播放：`GET /api/learn/play-url/{lessonId}` → TTL 默认 900s 的 HMAC 或 OSS V1 签名 URL  
3. `/api/media/videos/**`：必须有效 `expires+sig`，**或**员工 JWT 预览；普通登录用户无签名 → 401  
4. 图片可匿名；视频 `Cache-Control: private, no-store`  
5. 软 Referer 校验；前端 `controlsList=nodownload` + 禁右键  
6. **诚实限制**：TTL 内仍可抓包或录屏；要 DRM 需 Widevine 等二期方案  

## 5. 注入与输入

| 风险 | 控制 |
|------|------|
| SQL | Spring Data JPA 参数绑定；无字符串拼 SQL |
| XSS（存储） | 新闻/课程描述入库 `HtmlSanitizer`；用户端仍有 `v-html` → 防御在入库侧 |
| 路径穿越 | 上传 UUID 文件名；media resolve 后必须在 upload root 下；拒绝 `..` |
| SSRF | 出站仅微信 OAuth / 配置的 LLM base-url；无用户任意 URL fetch |
| 上传 | 扩展名白名单、体积上限、魔数抽检；**禁止 SVG** |

## 6. 限流与滥用

| 桶 | 默认 | 说明 |
|----|------|------|
| auth | 30/min/IP | 登录/注册等 |
| reset | 10/min/IP | 公开找回 |
| upload | 30/min/IP | 管理端上传 |
| progress | 180/min/IP | 覆盖 3s 心跳 |

X-Forwarded-For：取首跳并校验形态；nginx **必须覆盖**而非追加客户端值。Redis 宕机时限流 fail-open。

## 7. 密钥与配置

- `.env` / 真实 AppId/Secret / LLM key：**不入库**；仅 `.env.example`  
- `application.yml` 含开发默认 JWT/DB 密码 → **生产必须环境变量覆盖**  
- 测试配置使用独立 test secret，LLM key 为空  

## 8. 错误与日志

- `GlobalExceptionHandler`：未知异常返回固定「服务器内部错误」，堆栈只进日志  
- 日志带 `rid=`（`X-Request-Id` / MDC）；`auth.login.*`、`media.deny`  
- **禁止**把 JWT、密码、AppSecret 打进日志  

## 9. 残余风险清单（需业务接受）

1. 公开找回无邮件 OTP（可关开关）  
2. 管理端 video 预览 query token 可能进 access log（已限路径）  
3. 一期无付费墙 / 报名模型；付费后应收紧 play-url 授权  
4. 非 DRM  
5. 菜单级 RBAC 可再补自动化测试  
6. WeChat / LLM 依赖外网可用性  

## 10. 建议的生产检查清单

- [ ] `JWT_SECRET` 已轮换为高强度随机串  
- [ ] DB / Redis 不绑定公网  
- [ ] nginx HTTPS + HSTS + 覆盖 XFF  
- [ ] `PUBLIC_RESET_ENABLED` 按客户要求设置  
- [ ] OSS 桶私有 + 短时签名（若启用）  
- [ ] 备份与审计日志保留策略  
