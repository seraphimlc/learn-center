# 知行学堂 · 隔夜自审纪要（供次日晨会）

日期：2026-10-15（Asia/Shanghai）  
仓库：`github.com/seraphimlc/learn-center`  
部署：https://learn.visitworld.me/ （API 为 **Spring Boot 嵌入式 Tomcat** 可执行 jar，**不是**外置 Tomcat WAR）

## 一句话结论

一期功能与规模口径（业务约 **3 万**注册 / DID 设计 **30 万** / 峰值约 **1 万**在线）匹配；**当前不需要分片**。本轮补齐文档（PRD/TECH/COLD_START/SECURITY/DATA_SCALE）、关键注释、安全加固与可观测性基线；代码已本地提交并打 bundle，待 Mac 推送 GitHub。

## 决策摘要

| 主题 | 决策 | 依据 |
|------|------|------|
| 分片 | **不做**（仅设计开关 + DATA_SCALE 原则） | 3 万业务 / 30 万 DID 下 `study_progress` 远未到分片门槛；过早分片增加运维成本 |
| 进度写 | **Redis-only 播放进度（无 MySQL flush）** | 用户决策 2026-10-16：心跳永不落库；TTL 过期可丢；完成稀疏写一次 |
| 公开找回密码 | **默认可开，可环境变量关闭** | 无 OTP 是已知弱项；`PUBLIC_RESET_ENABLED=false` 即可改管理员重置 |
| 视频 ACL | **学员必须短时签名**；员工 JWT 可预览 | 堵住「登录后凭 videoPath 直下」；非 DRM |
| 运行时 | **embedded Tomcat jar** | 与现网 Docker/host 部署一致 |
| Redis | **进度权威（ephemeral）/ 限流 / 首页缓存 / 微信 state** | 播放 position 以 Redis 为唯一源；见 TECH_DESIGN §5 |

## 本轮已落地的安全修复（P0/P1）

1. 视频：无签名且非员工 → 401；学员不可仅凭 JWT 读任意路径  
2. `?token=` 仅允许 `/api/media/**`（缩小 JWT 进 access log / Referer 面）  
3. 导出学习 CSV / 用户导入模板：RBAC（ADMIN/DATA_VIEWER 或 ADMIN）  
4. 密码策略 ≥8 且字母+数字；找回统一「账号或邮箱不匹配」防枚举  
5. 微信 OAuth：Redis 宕机时**拒绝**回调（不再弱校验 state）  
6. 安全响应头 + CSP 基线；CORS 白名单  
7. 上传：禁 SVG、魔数抽检、路径穿越防护；富文本入库 HtmlSanitizer  
8. 限流：auth 30/min、reset 10/min；upload 匹配收窄  
9. Request-Id / MDC；未处理异常不回堆栈；前后端友好错误文案  

## 库表 / 索引（诚实评价）

- **优点**：`study_progress(userId,lessonId)` 唯一 + `(userId,courseId)` / `(userId,updatedAt)`；审计与访问日志有时间索引  
- **无物理 FK**：JPA 逻辑关联，便于日后分库；应用层保证一致性（删课先删课时）  
- **无软删**：一期硬删；若合规要求可加 `deleted_at`（P2）  
- **visit_log / audit_logs**：会随时间膨胀 → 按月归档即可，**不必**先分片  

## 日志与监控（运维该看什么）

- `rid=` 请求串联；`auth.login.*`、`media.deny`、进度 Redis 写失败 warn  
- Actuator：`/actuator/health`（已暴露）；**不要**对公网开放全部 actuator  
- 看板：今日 PV/UV、学习人数（完成稀疏行）；Redis 进度 key/内存；Hikari 活跃连接  
- AlertScheduler：连续 N 天未学发通知（已有）  

## 残余风险（接受或二期）

- 公开找回无邮件 OTP（可关）  
- 管理端预览仍可能把 JWT 放 query（已限媒体路径）  
- 防下载仅为基线威慑，非 Widevine  
- 菜单级 RBAC 前端已裁剪，后端写路径已校验；可再补集成测试  
- `AdminService` 偏胖（架构债，非安全 blocker）  

## 文档地图

| 文档 | 用途 |
|------|------|
| [PRD.md](./PRD.md) | 完整产品需求与验收 |
| [TECH_DESIGN.md](./TECH_DESIGN.md) | 架构 / 数据模型 / 安全 / 部署 |
| [COLD_START.md](./COLD_START.md) | 新人 / 另一 Agent 冷启动 |
| [SECURITY.md](./SECURITY.md) | 审计用控制项与残余风险 |
| [DATA_SCALE.md](./DATA_SCALE.md) | 规模与分片原则（不做假分片） |
| [FEATURE_GAP.md](./FEATURE_GAP.md) | RFP 差距 |
| [CODE_REVIEW.md](./CODE_REVIEW.md) | P2 债 |
| [HIGH_CONCURRENCY.md](./HIGH_CONCURRENCY.md) | 并发切点 |

## 推送状态

见最终报告：`PUSH_NEEDED` + bundle 路径 + commit SHA。

## 附录：2026-10-16 进度 Redis-only（用户决策）

- **播放进度**（position / maxPosition / studied / 续播 / 我的学习%）**只写 Redis**，key `vlp:progress:{uid}:{lessonId}` + 用户索引 `vlp:progress:idx:{uid}`。  
- **TTL** 默认 **14 天**（`PROGRESS_TTL_DAYS`，建议 7–30）。Redis 重启或 TTL 过期后进度可丢失，**可接受**，从 0 或残留 key 续播。  
- **已移除** dirty-set + 定时 `flushDirty`；3s 心跳**永不**打 MySQL。  
- **课时完成**：客户端达到完成阈值时**稀疏写一次** `study_progress.completed`（供证书 / 管理导出）；发证统计改读 Redis 完成标记。  
- 学情预警 / AI 学情若仍查 `study_progress`，只能看到稀疏完成行，**不是**实时播放位置。
