# 知行学堂 · 产品需求文档（PRD）

版本：一期 · 2026-10-15（Asia/Shanghai）  
线上：用户端 https://learn.visitworld.me/ · 管理端 https://learn.visitworld.me/admin/  
对照：[FEATURE_GAP.md](./FEATURE_GAP.md) · 规模：业务约 **3 万**注册 / DID **30 万** / 峰值约 **1 万**在线

---

## 1. 产品概述

### 1.1 背景与目标

知行学堂是面向「旅客/学员」的视频学习平台，提供课程浏览、视频学习（进度心跳）、证书、新闻运营、AI 学情辅助，以及管理端 CMS / 统计 / RBAC。一期交付 PC Web；H5/小程序为二期。

### 1.2 成功指标（一期）

- 学员可完成：注册/登录 → 选课 → 播放 → 进度可见 → 达标发证  
- 管理员可完成：课程/课时上传发布 → 用户启停 → 看板 PV/UV 与学习导出  
- 安全基线：JWT + 管理 RBAC + 短时播放签名；无密钥入库  
- 峰值约 1 万在线心跳时，进度**只写 Redis**（不 flush MySQL）；TTL 过期可丢进度（可接受）  

### 1.3 角色定义

| 角色 | 端 | 说明 |
|------|----|------|
| 旅客/学员（USER） | 用户端 | 浏览公开内容；登录后学习、笔记、书签、AI、证书、通知 |
| 超级管理员（ADMIN） | 管理端 | 全部权限：用户/角色/配置/导入 + 内容写 |
| 内容管理员（CONTENT_ADMIN） | 管理端 | 新闻/分类/课程/课时/Banner/公告/AI 辅助；不可管用户与站点配置 |
| 数据查看员（DATA_VIEWER） | 管理端 | 看板、列表只读、学习数据导出；禁止写 |

---

## 2. 用户端功能（详细）

### 2.1 注册 / 登录 / 找回密码

**用户故事**  
- 作为新学员，我希望用用户名密码注册，以便开始学习。  
- 作为老学员，我希望登录后继续上次课程。  
- 作为忘记密码的学员，我希望用「用户名+注册邮箱」重置（一期简化，无邮件 OTP）。

**验收标准**
- `/register`：用户名 3–32；密码 ≥8 且含字母+数字；邮箱可选但唯一  
- `/login`：错误提示不区分「用户不存在/密码错误」以外的敏感信息（统一文案「用户名或密码错误」）；禁用账号明确提示  
- 微信-only 账号（无密码）密码登录被拒绝并提示扫码  
- `/forgot-password` → `POST /api/auth/reset-password`；账号或邮箱不匹配时统一文案；可用 `PUBLIC_RESET_ENABLED=false` 关闭公开找回  
- 登录成功下发 JWT，前端存本地；401 跳转登录  

### 2.2 微信扫码登录

**用户故事**  
- 作为学员，我希望在 PC 用微信扫码登录，免记密码。

**验收标准**
- `GET /api/auth/wechat/status`：`enabled=false` 时前端隐藏或禁用扫码，不影响账密登录  
- 已配置时：`/qr-url` 返回开放平台 `qrconnect` URL + state；回调页 `/login/wechat/callback` 换 JWT  
- 新用户自动创建 `wx_` 前缀账号，角色 USER；unionid/openid 去重绑定  
- 未配置返回 503「未配置微信登录」  
- 可选公众号网页授权 hook（`/mp-url`）供二期 H5  

### 2.3 首页 / 导航 / 搜索 / 关于

**验收标准**
- 首页：Banner 轮播、热门/推荐课程、最新新闻；匿名壳可缓存 ~30s  
- Header 搜索进入课程列表关键词  
- `/about` + 页脚链接可读  
- 访问上报 `POST /api/public/visit`（匿名）  

### 2.4 新闻

- 列表分页 `/news`；详情 `/news/:id`；仅已发布  
- 内容可为富文本（后端入库消毒）；验收：无 script 可执行  

### 2.5 分类与课程

- 多级分类树；课程列表按分类/关键词筛选  
- 课程状态：DRAFT / PUBLISHED / OFFLINE；用户端只见 PUBLISHED  
- 课程详情：简介、课时列表、登录后合并进度勾选；`videoPath` 不暴露永久公网地址  

### 2.6 视频播放与进度（核心）

**用户故事**  
- 作为学员，我想流畅看课，并在同一 Redis 会话窗口内（默认 14 天 TTL）刷新/换设备后从上次位置继续；Redis 丢失则从 0。  
- 作为平台，我要防止明显「拖进度条刷课」。

**领域规则**
- 心跳约 **3 秒**，另在 pause / ended / 卸载时上报  
- `maxPositionSeconds` 单调；非 ended 时向前跳跃超过 `progress-max-jump-seconds`（默认 120）则拒绝跃进  
- `studiedSeconds` 单次 delta 封顶 60s  
- 课时完成：`ended` **或** `maxPosition ≥ ceil(duration × completionRatio)`（课程级，默认 0.9）  
- 课程进度百分比 = 已完成课时数 / 总课时数  
- 「继续学习」→ `continueLessonId` = 首个未完成课时；全完成则最后一节  
- **进度存储**：position **仅 Redis**（TTL `PROGRESS_TTL_DAYS`，默认 14）；心跳不写 MySQL；课时完成稀疏写一次完成标记  

**播放 ACL**
- 先 `GET /api/learn/play-url/{lessonId}` 取短时 URL，再播  
- 未登录或签名过期不可拉视频流  

**防下载基线（非 DRM）**  
nodownload、禁右键、短 TTL、无永久公链。

### 2.7 我的学习 / 笔记 / 书签

- `/learning` 按最近活动列出课程+进度+继续入口  
- 笔记/书签按时点秒数；仅本人可删  

### 2.8 个人中心

- 改昵称/邮箱/手机/头像/简介；改密需旧密码  

### 2.9 证书

- 课程完成率达 `completionRatio` 后签发；唯一 `(userId,courseId)`；证书编号展示于 `/certs`  

### 2.10 通知

- 系统公告、学情预警（如连续未学 N 天）；列表已读能力以现网为准  

### 2.11 AI 学情中心（`/ai`）

| 能力 | 接口 | 验收 |
|------|------|------|
| 学情分析 | GET `/api/ai/analysis` | 结构化 summary/strengths/weaknesses/suggestions/weeklyPlan |
| 推荐 | GET `/api/ai/recommend` | 课程+reason |
| 知识点 | POST `/api/ai/knowledge-points` | courseId 必填 |
| 出题/判分 | `/quiz` `/quiz/grade` | 反馈可读 |
| 问答 | POST `/api/ai/ask` | 基于课程资料 grounding |
| 本课摘要 | POST `/api/ai/lesson-digest` | keyTakeaways |
| 降级 | LLM 失败 | `degraded` 规则文案，不 500 |

课程详情/播放页提供知识点/提问/摘要入口。

### 2.12 H5

- **一期未做**（二期）；PC 有基础响应式与汉堡菜单  

---

## 3. 管理端功能（详细）

### 3.1 登录与菜单裁剪

- 仅 ADMIN / CONTENT_ADMIN / DATA_VIEWER 可进  
- 路由 `meta.roles` + 侧栏按角色隐藏；后端写路径二次校验  

### 3.2 看板

- 用户数、启用用户、课程数、上架数、学习人数、总学习秒、完成课时、证书数  
- 今日 PV/UV + 约 14 日趋势（来源 UTM 细拆未做）  

### 3.3 用户管理（仅 ADMIN）

- 列表搜索；启用/禁用（不可禁管理员）；重置密码；改角色（USER/ADMIN/CONTENT_ADMIN/DATA_VIEWER）  
- 不可取消唯一超管  
- CSV 导入用户；导入模板下载  

### 3.4 内容 CMS（ADMIN / CONTENT_ADMIN）

- 新闻 CRUD / 发布  
- 分类 CRUD  
- 课程 CRUD；主按钮「管理课时/视频」；新建后进课时工作台  
- 课时：快速添加标题+视频、逐节上传、进度、预览、拖拽；一门课多节  
- Banner CRUD  
- 公告群发通知  

### 3.5 运营配置（仅 ADMIN）

- `SiteConfig` 键值（如预警天数）  

### 3.6 审计

- 管理写操作落 `audit_logs`（操作者、动作、资源、IP、时间）  

### 3.7 导入导出

- 学习进度 CSV 导出：ADMIN + DATA_VIEWER  
- 用户导入：仅 ADMIN  

### 3.8 证书只读列表

- 全角色员工可查看已签发证书  

### 3.9 管理端 AI

- 课程摘要/提纲辅助；新闻导语辅助；可选择写回  

---

## 4. 非功能需求

### 4.1 规模与性能

见 [DATA_SCALE.md](./DATA_SCALE.md)、[HIGH_CONCURRENCY.md](./HIGH_CONCURRENCY.md)。  
进度异步、首页缓存、限流、连接池按 30 万 DID / 1 万峰值设计；**当前不分片**。

### 4.2 安全

见 [SECURITY.md](./SECURITY.md)。

### 4.3 可用性

- LLM / 微信未配置时优雅降级  
- Redis 宕机：进度写返回不可用（无 MySQL 回退）；限流 fail-open；微信 state 拒绝（安全优先）  

### 4.4 文案与体验

- 中文专业；加载/空态/Toast；5xx 友好提示不露堆栈  

---

## 5. 验收场景清单（建议）

1. 后台上传课时视频 → demo 播放至结束 → 我的学习进度与继续学习  
2. `/ai` 各 Tab；LLM 关掉时仍有 degraded  
3. CONTENT_ADMIN 无法改用户角色；DATA_VIEWER POST 内容接口 403  
4. 未登录访问视频 URL → 401；过期 sig → 401  
5. `/forgot-password`；关闭 `PUBLIC_RESET_ENABLED` 后拒绝  
6. 看板 PV/UV；导出 CSV  
7. 微信未配置时 status.enabled=false  

---

## 6. 明确不在一期

- 独立 H5 / 小程序完整包  
- 商业 DRM / 付费报名墙  
- 访问来源 UTM 细拆  
- 真分片上线  
- 邮件 OTP 找回  

更新：与 FEATURE_GAP / 现网 2026-10-15 对齐。


---

## 7. 功能规格详表（用户故事 + 验收）

### 7.1 账号生命周期

| 场景 | 用户故事 | 验收 |
|------|----------|------|
| 注册 | 我用未占用用户名注册 | 201/成功返回 JWT；重复用户名失败；弱密码（<8 或纯数字）400 |
| 登录 | 我用正确密码进入 | 返回 token+role；错误密码统一文案；禁用账号不可登录 |
| 改密 | 我在个人中心改密 | 需旧密码；新密码满足策略 |
| 找回 | 我用用户名+邮箱设新密码 | 不匹配统一文案；开关关闭时 403 并提示联系管理员 |
| 微信新用户 | 我扫码首次进入 | 生成 wx_ 用户、无密码、角色 USER |
| 微信老用户 | 我再次扫码 | 命中 unionid 或 openid，更新空昵称/头像，不覆盖已设昵称 |

### 7.2 内容浏览

| 场景 | 验收 |
|------|------|
| 首页匿名 | 可见 banner/热门/推荐/新闻/分类；接口可缓存 |
| 首页登录 | 可带个性化推荐位（若实现则不破坏匿名壳） |
| 课程列表 | 分类、关键词、分页；只 PUBLISHED |
| 课程详情未登录 | 可见大纲与课时标题；无播放流；无进度写 |
| 课程详情已登录 | progressMap 来自 Redis（miss=0）；已完成打勾 |
| 新闻 | 仅发布态；详情富文本无脚本 |

### 7.3 播放与进度（逐步）

1. 进入 `/play/:courseId/:lessonId`（需登录，否则跳登录并带 redirect）  
2. 拉课程详情 + `GET /api/learn/play-url/{lessonId}`  
3. `<video src=playUrl>`；在过期前约 60s 刷新 URL  
4. 每 3s：`POST /api/learn/progress`，字段 `lessonId, positionSeconds, deltaSeconds, heartbeat=true, videoDurationSeconds`  
5. pause / ended / 离开页面再上报；ended=true 时允许把 max 拉到视频时长  
6. 服务端：拒绝过大前跳；delta 单次 ≤60；完成则 `completed=true`  
7. 「我的学习」显示 `progressPercent` 与 `continueLessonId`  

**反例验收**
- 把进度条拖到末尾（未真实播放到 max+jump 内）不应直接完成（除非 ended 或在允许跳跃内）  
- 未登录直接请求 `/api/media/videos/...` → 401  

### 7.4 证书

- 触发：某课时刚完成时检查「完成课时/总课时 ≥ completionRatio」  
- 幂等：同一用户同一课程只一张（唯一约束）  
- 展示：用户端 `/certs`；管理端证书列表只读  
- 发证：课时首次完成时用 Redis 完成数判定，并稀疏写 MySQL；不再依赖 flush  

### 7.5 通知与预警

- 管理员公告：全员或广播通知（现实现以 NotifyService 为准）  
- 每日 9:00（服务器时区 Asia/Shanghai）扫描超过配置天数未学习的用户，写 StudyAlert + 通知，避免同一用户重复轰炸（同次扫描去重）  

### 7.6 AI（逐接口）

| 接口 | 必填 | 成功 | 失败 |
|------|------|------|------|
| GET /api/ai/analysis | 登录 | 结构化学情 | degraded 规则 |
| GET /api/ai/recommend | 登录 | 列表+reason | 空或规则推荐 |
| POST /api/ai/knowledge-points | courseId | 知识点 | 兜底 |
| POST /api/ai/quiz | courseId | 题目（不含答案明文若设计如此） | 兜底 |
| POST /api/ai/quiz/grade | courseId+answers | 得分+反馈 | 明确错误 |
| POST /api/ai/ask | courseId+question | 基于课程资料回答 | 兜底 |
| POST /api/ai/lesson-digest | lessonId | 摘要+takeaways | 兜底 |
| POST /api/admin/ai/course/{id} | 内容写权限 | 提纲/摘要；apply 可写回 | 403 若只读角色 |
| POST /api/admin/ai/news/{id} | 内容写权限 | 导语辅助 | 同上 |

### 7.7 管理 CRUD 细则

**用户（ADMIN）**
- 搜索分页；启用/禁用；重置密码（空则默认 Reset@123 仅管理操作，须审计）；改角色白名单  
- 导入 CSV：跳过空行与已存在用户名；密码 bcrypt；角色强制 USER（防导入提权）  

**课程/课时（内容写）**
- 课程字段：标题、分类、封面、摘要、描述、状态、推荐/热门、排序、完成比例  
- 视频不在课程表单直传：必须进课时页  
- 上传类型：mp4/webm/mov/m4v；图片 jpg/png/gif/webp；禁 svg  

**新闻/Banner/分类/配置/公告**
- 新闻发布写 publishedAt  
- 配置仅超管  
- 公告写审计 ANNOUNCE  

**导出**
- 学习 CSV 列：userId, courseId, lessonId, position, studied, completed, updatedAt  
- 仅 ADMIN、DATA_VIEWER  

### 7.8 访问统计

- 埋点 path + visitorKey + 可选 userId + IP  
- 看板今日 PV（次数）UV（visitor 去重）与趋势  
- **未做**：Referer/UTM 来源拆分（FEATURE_GAP 保留）  

### 7.9 角色权限矩阵（写操作）

| 操作 | USER | DATA_VIEWER | CONTENT_ADMIN | ADMIN |
|------|------|-------------|---------------|-------|
| 学习/AI/证书自己的 | ✅ | 管理端不提供 | 同左 | 同左 |
| 看板/列表/审计读 | ❌ | ✅ | ✅ | ✅ |
| 新闻课程课时 Banner | ❌ | ❌ | ✅ | ✅ |
| 公告 | ❌ | ❌ | ✅ | ✅ |
| 站点配置 | ❌ | ❌ | ❌ | ✅ |
| 用户启停/角色/重置/导入 | ❌ | ❌ | ❌ | ✅ |
| 学习 CSV 导出 | ❌ | ✅ | ❌ | ✅ |

前端菜单必须与上表一致；以后端 403 为准。

### 7.10 非功能验收

- 3s 心跳在默认限流下单用户不被 429（约 20/min << 180）  
- 并发心跳以 Redis SET 覆盖为准；无 MySQL flush 竞态  
- 5xx 页面/Toast 文案为「服务暂时不可用」类，不含 Java 堆栈  
- 生产配置覆盖默认 JWT 与 DB 口令  

## 8. 规模假设（需求侧）

- 注册用户业务目标约 **3 万**  
- 架构预留按 **30 万** 设计，不按百万  
- 峰值同时在线参考约 **1 万**（心跳主导）  
- 合同配额以商务为准；超出时先加 API 副本与 Redis/MySQL 规格，再评估分片（见 DATA_SCALE）  

## 9. 一期/二期边界

| 一期必须 | 二期/可选 |
|----------|-----------|
| 账密+微信 hook | 完整 H5/小程序 |
| 课程视频进度证书 | 付费墙/报名 |
| 管理 CMS+RBAC 粗粒度已收紧 | 更细菜单权限测试补齐 |
| 签名播放基线 | DRM / 真 CDN 凭证 |
| AI 降级可用 | 更强评测集 |

