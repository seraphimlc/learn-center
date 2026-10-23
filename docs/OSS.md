# 知行学堂 · OSS 媒体存储

版本：2026-10-23

## 现状

- **私有桶** + **短时签名 GET**（Aliyun OSS Signature V1，virtual-host：`{bucket}.oss-cn-hangzhou.aliyuncs.com`）
- 上传：`OssStorageService` HTTP PUT V1（无 SDK）；`UploadService` 在 `OSS_ENABLED` + 凭证齐全时走 OSS，否则本地 `uploads/`
- DB 存 opaque key：`learn/videos/{uuid}.ext` / `learn/images/{uuid}.ext`（前缀可配 `OSS_PREFIX`，默认 `learn/`，避免与同桶其它业务如 chat-uploads 冲突）
- 播放：`MediaSigningService.mintPlayUrl` → OSS 签名 URL；本地模式仍为 `/api/media/...?expires&sig`
- `/api/media/**`：本地有文件则 Range 流式；否则 OSS 就绪时 **302** 到短时签名 GET（封面图匿名可用）

## CDN

- `CDN_ENABLED` **保持 false**，直至业务方提供 `CDN_DOMAIN`
- 开启后签名 host 可切到 CDN 域名（仍应走签名或鉴权回源，禁止永久公网直链视频）

## 配置

见 `.env.example`：`OSS_*`、`CDN_*`、`MEDIA_SIGNED_TTL`。生产 env：`/opt/visitworld-deploy/learn/shared/env/app.env`（chmod 600）。

## 运维注意

- 勿把 AccessKey/Secret 写入仓库、日志或前端
- 桶 ACL 保持私有；勿对 `learn/` 开公共读
- 冒烟可用短时 PUT/GET/DELETE 探测对象后立即删除，勿留测试杂物
