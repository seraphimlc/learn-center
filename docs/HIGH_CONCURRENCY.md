# 知行学堂 · 高并发架构（设计容量约 30 万注册 / 峰值约 1 万在线）

目标量级：注册目标约 **3 万**，设计容量约 **30 万**注册用户（DID），峰值约 **1 万并发在线**（心跳 / 进度上报 / 首页读）。本文档描述一期已落地能力与下一迭代切点。

## 已落地（一期上线）

| 能力 | 实现 |
|------|------|
| Redis 缓存 | 首页匿名壳（banners/hot/recommend/news/categories）`homeAnon` TTL≈30s；分类列表 `categories` TTL≈60s |
| 进度异步化 | `ProgressBufferService`：写入 Redis + dirty set，定时批量 flush 至 MySQL（默认 2s / 批 200） |
| 连接池 | Hikari `maximum-pool-size` 默认 50（可用 `HIKARI_MAX_POOL`）；Lettuce 连接池 max-active 64 |
| Tomcat 线程 | max 400 / min-spare 50 / accept-count 200 / max-connections 10000 |
| 接口限流 | Redis 固定窗口：auth / upload / progress（可配 `RL_*_PER_MIN`） |
| 视频 ACL/签名 | 播放经 `GET /api/learn/play-url/{lessonId}` 签发短时 URL；本地 HMAC 或 OSS V1；`/api/media` 校验 JWT/`expires+sig`；封面才走 `CdnUrlService` |
| DB 索引 | `study_progress(userId,lessonId)` 唯一；`(userId,courseId)`；`(userId,updatedAt)` |
| 读写分离配置项 | `spring.datasource-readonly.*` + `DB_READONLY_ENABLED`（下一迭代接路由） |
| 分片开关 | `vlp.shard.*`（`enabled=false`）；策略预留 `userId_mod` |

## 待做（下一迭代切点）

### 1. 学习进度表按 userId 分片

- **表**：`study_progress_0` … `study_progress_{N-1}`（建议 N=16 起，可扩到 64）
- **路由**：`shard = userId % N`（与 `vlp.shard.progress-mod` 对齐）
- **切点**：当单表行数接近千万级或进度 QPS flush 持续压满主库写（30 万用户设计下通常一期不必立刻分片）；启用前需双写/校验工具
- **访问日志 / audit_logs**：按月分区或 `userId % N` 分表；冷数据归档 OSS

### 2. 消息队列（可选升级）

当前为 **Spring 定时 + Redis dirty set**。若多实例竞争 flush，下一迭代改为：

- Redis Stream / Kafka topic `progress.report`
- 消费者组批量写 DB；API 只 `XADD` / `LPUSH`

### 3. 读写分离

- 读：首页缓存未命中、课程列表、新闻 → readonly 数据源
- 写：进度 flush、注册、管理后台 → primary
- 切点：配置 `DB_READONLY_ENABLED=true` 并接入 AbstractRoutingDataSource

### 4. 会话与 JWT

- 一期 JWT 无状态；下一迭代：登出黑名单 / 设备会话放 Redis（key `vlp:sess:{uid}`）

### 5. 视频分发

- 开启 `CDN_ENABLED` + `CDN_DOMAIN`：封面经 `CdnUrlService`；**播放**必须走短时签名（`MediaSigningService`），禁止把永久公网 OSS URL 回传客户端
- 大文件直传 OSS（`OSS_ENABLED`），API 只存 object key；对象 ACL 保持私有

### 6. 水平扩展 API

- 当前单机 Docker `--network host` + jar；下一迭代：多副本 + 前置 LB，Redis/MySQL 外置；uploads 迁对象存储

## 容量粗算（参考）

- 用户规模：业务 **3 万** 注册；DID 设计 **30 万**；分库分表/读写分离按 30 万预留开关，非约 30 万（DID）级默认开启
- 1 万在线 × 每 **3s** 心跳 ≈ **~3333 QPS** 进度写 → Redis 缓冲吸收；MySQL flush ≈ 批量 200/2s；单用户 ≈20 req/min，`RL_PROGRESS_PER_MIN` 默认 180
- 首页读：缓存命中后 API≈内存级；未命中打库由 TTL 收敛
- 连接：Tomcat 400 线程 + Hikari 50 足够单机峰值；再高优先加副本而非盲目加线程

## 运维开关（env）

```
PROGRESS_ASYNC_ENABLED=true
PROGRESS_FLUSH_INTERVAL_MS=2000
HIKARI_MAX_POOL=50
TOMCAT_MAX_THREADS=400
CACHE_HOME_TTL=30
CDN_ENABLED=false
SHARD_ENABLED=false
DB_READONLY_ENABLED=false
```
