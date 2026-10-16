# 知行学堂 · MySQL 读热点与 Redis 缓存分析

日期：2026-10-16  
约束：**不做 Elasticsearch**；播放进度继续对齐 e3ff4a6（Redis-only ephemeral，TTL 默认 14d，无心跳 flush）。

## 1. 读路径追踪

| 入口 | 主要下游 | 变更前 MySQL | 缓存现状（本迭代后） |
|------|----------|--------------|----------------------|
| `GET /api/public/home` | `ContentService.home` → `PublicContentCache.homeAnonPayload` +（登录）Redis progress | 匿名：banners/hot/recommend/news/categories 各 1 次；登录另 N+1 course/lesson | **已缓存** `homeAnon` TTL≈30s；continueLearning 仍 Redis+批量 MySQL |
| `GET /api/public/categories` | `categories()` | 每次查分类表 | **已缓存** `categories` TTL≈60s |
| `GET /api/public/courses` | `courseList` | 每次 `searchPublished` | **已缓存** `courseList` TTL≈30s（含 cat/kw/page/size） |
| `GET /api/public/courses/{id}` | `courseDetail` | course + lessons；登录再 Redis progress | **已缓存** 公开壳 `courseDetail` TTL≈60s；progress 仍 Redis-only |
| `GET /api/public/news` | `newsList` | 分页查新闻 | **已缓存** `newsList` TTL≈30s |
| `GET /api/public/news/{id}` | `newsDetail` | 读 + `viewCount++` 写 | **不缓存**（写路径） |
| `POST /api/public/visit` | `VisitService.track` | 每次 insert | **未缓存**（见延期） |
| `GET /api/learn/my` | `LearnService.myLearning` | 每课 course+lessons N+1 | lessons 走 `courseLessons`；courses `findAllById` 批量 |
| `POST /api/learn/progress` | `ProgressBufferService.accept` | 每心跳 lesson+course findById | **已缓存** `lessonById` / `courseById` TTL≈60s |
| `GET /api/learn/play-url/{id}` | `MediaSigningService.mintPlayUrl` | lesson+course | 同上 entity 缓存 |
| Admin dashboard / visit-stats | counts + visit 聚合 | 多 COUNT / dailyStats | **延期**（管理端、低频） |
| Auth `/me` 等 | `UserRepository.findById` | 按用户 | **延期**（个性化、短会话） |
| Admin configs / AlertScheduler | site_configs | 全表 / by key | **已缓存** `siteConfig` TTL≈120s |

## 2. 热点 / N+1 表

| 热点 | 症状 | 严重度 | 处理 |
|------|------|--------|------|
| 首页匿名壳 | 高 QPS 重复打 5 张表 | 高 | 已有 `homeAnon`；本迭代保留 + 写失效 |
| 课程列表 | 浏览/分类切换 | 高 | **新** `courseList` |
| 课程详情 | 详情页 + 播放页反复拉 shell | 高 | **新** `courseDetail`；progress 不进该 key |
| 心跳 lesson/course | ~3s × 在线用户 | **极高** | **新** `lessonById`/`courseById` |
| home `enrichProgress` | 每条 progress `findById` course+lesson | 中 | **批量** `findAllById` |
| my-learning | 每课 lessons 查询 | 中 | **`courseLessons` + 批量 courses** |
| newsDetail viewCount | 读改写 | 中 | 保持 MySQL；见延期 |
| visit track | 每 PV insert | 中高写 | 延期 Redis 缓冲 |
| visit stats / dashboard | 聚合扫 visit_log | 低（admin） | 延期短 TTL 或物化 |

## 3. 缓存设计（Redis-only，无 ES）

### Key / Cache name（Spring Cache 前缀 `vlp:cache:`）

| Cache | Key | TTL（默认） | 内容 | 失效 |
|-------|-----|-------------|------|------|
| `homeAnon` | `v1` | 30s | banners/hot/recommend/news/categories | admin 内容写 `evictAllPublic` |
| `categories` | `enabled` | 60s | 启用分类 | 同上 |
| `banners` | `enabled` | 60s | 启用 banner | 同上 |
| `courseList` | `{cat}:{kw}:{page}:{size}` | 30s | `PageResult<Course>` | 同上 |
| `courseDetail` | `{courseId}` | 60s | `{course, lessons}` 公开壳 | 同上；时长同步局部 evict |
| `courseLessons` | `{courseId}` | 60s | 课时列表 | 同上 |
| `courseById` / `lessonById` | id | 60s | 实体 | admin 写全清；duration sync 局部 |
| `newsList` | `{page}:{size}` | 30s | 新闻分页 | admin 新闻写 |
| `siteConfig` | `all` | 120s | 全量配置 | admin 配置写 |

### 故意留在 MySQL / 其他 Redis

| 数据 | 落点 | 原因 |
|------|------|------|
| 播放 position / completed 直播 | **Redis** `vlp:progress:{uid}:{lessonId}` + idx SET | e3ff4a6；可丢 |
| 课时完成稀疏行 | MySQL `study_progress` | 证书 / 导出 |
| 用户、权限、笔记、书签 | MySQL | 个性化、强一致 |
| news viewCount、visit_log | MySQL | 计数写；未做缓冲 |
| 管理端聚合 | MySQL | QPS 低 |

### 失效策略

- **主动**：`AdminService` 在 news/category/course/lesson/banner/config 的 save/delete 后调用 `PublicContentCache.evictAllPublic()`（短 TTL 下全清可接受）。
- **局部**：`ProgressBufferService.syncLessonDuration` → `evictAfterLessonMetaChange(lessonId, courseId)`。
- **被动**：各 cache TTL；未命中打库。

## 4. 本迭代已落地 vs 延期

### 已缓存 / 已改（quick wins）

1. 扩展 `PublicContentCache`：courseList、courseDetail、newsList、banners、siteConfig、courseLessons、courseById、lessonById  
2. Admin 写路径统一 evict  
3. home continueLearning、my-learning 消除/减轻 N+1  
4. 心跳与 play-url 走 entity 缓存  
5. `CacheConfig` / `application.yml` 增加可配 TTL  

### 延期（不做或下轮）

| 项 | 原因 |
|----|------|
| Elasticsearch / 全文检索 | **明确不做** |
| visit 异步缓冲 + Redis 计数 | 写路径改造面大；需单独设计丢数容忍 |
| newsDetail 缓存 + 异步 viewCount | 需拆读写；易脏读 |
| 用户态「我的学习」整页缓存 | 与 progress 强相关，易脏；progress 已在 Redis |
| visit stats / dashboard 物化 | admin 低频 |
| 读写分离路由 | 配置占位已有，非本迭代 |
| loadCourseProgress Redis MGET | Redis 侧优化，非 MySQL |

## 5. Before / After（预期）

| 场景 | Before | After |
|------|--------|-------|
| 匿名首页 QPS | 每请求 ≈5 次内容查询 | 命中后 0 次 MySQL（continue 空） |
| 课程列表翻页 | 每次 searchPublished | 同参数 30s 内命中 Redis |
| 课程详情（含播放页刷新） | 每次 course+lessons | 公开壳命中；登录仅加 Redis progress |
| 进度心跳 | 每 3s ×2 findById | entity cache 命中；完成稀疏写不变 |
| 管理改课/banner | 靠 TTL 自行过期 | 立即 evictAllPublic |

粗算（峰值 1 万在线、心跳 3s）：进度相关 MySQL 读从「每心跳 2 次」降为「缓存未命中偶发」；内容读由 TTL 窗口收敛。

## 6. 残余风险

1. **短时脏读**：admin 写后若 evict 失败，最多脏到 TTL（30–120s）。可接受。  
2. **courseList 搜索词污染**：冷门 keyword 也会占 key；TTL 30s + 体量小，可接受；若 Redis 内存紧张可改为「仅 kw 为空才缓存」。  
3. **Jackson 多态序列化**：`GenericJackson2JsonRedisSerializer` + default typing；实体字段变更需容忍反序列化失败（未命中即回源）。  
4. **`disableCachingNullValues`**：未发布课程的 `coursePublicDetail` 不缓存 null → 探测性 404 仍打库（低频）。  
5. **进度仍可丢**：与 e3ff4a6 一致；本缓存不改变该产品决策。  
6. **visit_log 增长**：未缓冲；长期需分区/归档（见 DATA_SCALE.md）。  
7. **多实例**：共享 Redis 即可；本地无第二级缓存。

## 7. 运维开关

```
CACHE_HOME_TTL=30
CACHE_CATEGORY_TTL=60
CACHE_COURSE_LIST_TTL=30
CACHE_COURSE_DETAIL_TTL=60
CACHE_SITE_CONFIG_TTL=120
CACHE_NEWS_LIST_TTL=30
CACHE_ENTITY_TTL=60
PROGRESS_TTL_DAYS=14
```

## 8. 相关代码

- `com.vlp.cache.PublicContentCache`
- `com.vlp.config.CacheConfig`
- `ContentService` / `LearnService` / `ProgressBufferService` / `AdminService` / `MediaSigningService`
