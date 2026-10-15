# 知行学堂 · 一期需求差距与交付说明

对照客户 RFP §5.1 / §5.4 与正式报价软件模块；部署站点：https://learn.visitworld.me/

## RFP 功能对照表

| RFP 功能 | 状态 | 说明 |
|---|---|---|
| 注册 / 登录 | 已实现 | `/register` `/login`，JWT |
| 密码找回 | 已实现 | API `/api/auth/reset-password` + 用户端 `/forgot-password` |
| 个人中心（信息 + 改密） | 已实现 | `/profile` |
| 新闻列表 / 详情 | 已实现 | `/news` |
| 多级分类 + 课程列表 | 已实现 | 分类树 + `/courses` |
| 视频播放 + 进度心跳 | 已实现 | 约 **3s** 心跳 + pause/ended/unmount；完成判定按课时完成比例 |
| 我的学习 / 继续学习 | 已实现 | 课程完成率=完成课时/总课时；继续学习指向首个未完成课时 |
| 首页 / 导航 / 搜索 | 已实现 | Header 搜索、首页运营位 |
| 关于我们 | 已实现 | `/about` + 页脚链接 |
| 后台用户 CRUD / 禁用 / 重置密码 | 已实现 | 用户管理页 |
| 新闻 CMS / 分类 / 课程 / 视频上传 / 发布 | 已实现 | 课时页强化上传（快速添加、进度、预览） |
| 访问 UV/PV + 趋势图 | 部分→已加强 | `visit_log` + 看板今日 PV/UV + 14 日趋势；来源维度未做细拆 |
| 学习进度统计 + 导出 | 已实现 | 看板学习指标 + CSV 导出 |
| 角色（超管/内容/数据） | 部分 | 已支持 `ADMIN` / `CONTENT_ADMIN` / `DATA_VIEWER` 登录与改角色；菜单级细权限仍偏粗 |
| 操作审计日志 | 已实现 | `/admin/audit` |
| H5 移动端 | 未做 | 二期 |
| OSS/CDN 真接通 | 部分 | 签名播放链路已就绪；OSS/CDN 开关默认关，待客户凭证后开启私有桶+短时签名 |

## 本次优先修复（用户反馈）

### 1. 课程管理 · 视频上传入口
- 课程列表主按钮改为 **「管理课时/视频」**；新建课程保存后自动跳转课时页。
- 课时页改为课程详情工作台：快速添加（标题+视频）、逐节「上传本节视频」、上传进度、上传后预览、拖拽上传。
- 课程编辑弹窗明确提示：视频在课时里上传，一门课可多节。

### 2. 学习进度
- 课程进度 = 已完成课时 / 总课时；完成条件 = 播放位置达 `duration * completionRatio` 或视频 `ended`。
- 播放器上报 `videoDurationSeconds`，纠正课时时长元数据；心跳更勤 + pause/ended 上报。
- 「我的学习」继续学习 → `continueLessonId`（首个未完成课时）；课程详情/播放页进度勾选与 Redis 缓冲合并读取。

## AI 能力（本轮新增/强化）

| 接口 | 说明 |
|---|---|
| `GET /api/ai/analysis` | 结构化：summary / strengths / weaknesses / suggestions / weeklyPlan |
| `POST /api/ai/knowledge-points` | 课程/课时知识点 |
| `POST /api/ai/quiz` + `/quiz/grade` | 出题与判分 + 反馈 |
| `POST /api/ai/ask` | 课程问答（ grounding 课程资料） |
| `POST /api/ai/lesson-digest` | 本课摘要 + keyTakeaways |
| `GET /api/ai/recommend` | 推荐课程 + reason |
| Admin `POST /api/admin/ai/course/{id}` | 原有课程摘要/提纲 |
| Admin `POST /api/admin/ai/news/{id}` | 新闻导语辅助 |
| 持久化 | `ai_cache` 表缓存知识点/摘要/测验 |

用户端 **AI 学情中心**（`/ai`）分 Tab；课程详情与播放页提供「知识点 / 去提问 / 本课摘要」入口。LLM 失败时 `degraded` 规则兜底。

## 已部署内容与试用入口

- 用户端：https://learn.visitworld.me/
- 管理端：https://learn.visitworld.me/admin/
- 账号：`admin/Admin@123`，`demo/Demo@123`
- 建议验证：
  1. 后台 → 课程 → **管理课时/视频** → 快速添加上传
  2. demo 登录 → 播放至结束 → 我的学习进度与「继续学习」跳转
  3. `/ai` 学情分析 / 问答 / 知识点 / 测验
  4. `/forgot-password`、`/about`
  5. 后台看板今日 PV/UV 与趋势

## 有意保留的差距

- 二期 H5 / 小程序
- 完整菜单级 RBAC（内容管理员只见内容、数据员只读统计）仍可再收紧
- OSS/CDN 生产凭证、访问来源分析、独立高可用机房方案：商务与客户确认后实施
- 视频防下载仅为基线威慑（短时签名 + UI 限制），非商业 DRM
- 访问统计「来源」字段未接入 UTM/Referer 细拆

## 媒体访问控制（本轮）

- 未登录访问 `/api/media/videos/...` → **401**
- 登录后 `GET /api/learn/play-url/{lessonId}` 签发 10–30 分钟级短时 URL（本地 HMAC 或 OSS V1）
- 公开课程 API 的 `videoPath` 仅为存储 key，不返回永久公网 OSS 地址
- 进度限流 `RL_PROGRESS_PER_MIN` 默认 180，覆盖 3s 心跳（≈20/min/人）

更新时间：2026-10-15（Asia/Shanghai）

## 规模口径（2026-10-15 修订）

- 业务注册用户：**约 3 万**（对齐客户 RFP）
- 设计容量（DID）：**按 30 万用户**预留缓存/异步/扩容切点
- **不再**按百万注册设计
- 峰值并发在线：仍参考 RFP 约 1 万（合同配额以商务约定为准）
