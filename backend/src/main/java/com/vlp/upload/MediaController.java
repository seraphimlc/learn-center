package com.vlp.upload;

import com.vlp.common.BusinessException;
import com.vlp.security.AdminRoles;
import com.vlp.security.AuthContext;
import com.vlp.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 媒体流式输出（支持 Range）。
 * <p>
 * 视频 ACL（一期基线，非 DRM）：
 * <ul>
 *   <li>封面图 /api/media/images/** 可匿名</li>
 *   <li>视频必须：短时签名 {@code expires+sig}，或管理端员工 JWT 预览</li>
 *   <li>普通学员仅持 JWT、无签名时不可凭 opaque videoPath 直下整文件</li>
 *   <li>软 Referer 校验；前端 nodownload / 禁右键仅为威慑</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {
    private final UploadService uploadService;
    private final MediaSigningService mediaSigningService;
    private static final long CHUNK = 1024 * 1024;

    @Value("${vlp.cors.origins}")
    private String allowedOrigins;

    @GetMapping("/**")
    public ResponseEntity<ResourceRegion> media(jakarta.servlet.http.HttpServletRequest request,
                                                @RequestHeader HttpHeaders headers,
                                                @RequestParam(required = false) String expires,
                                                @RequestParam(required = false) String sig) throws IOException {
        String uri = request.getRequestURI();
        boolean isImage = uri.contains("/api/media/images/");
        String prefix = "/api/media/";
        int idx = uri.indexOf(prefix);
        String rel = idx >= 0 ? uri.substring(idx + prefix.length()) : "";
        if (rel.contains("..")) throw new BusinessException("非法路径");

        if (!isImage) {
            assertVideoAccess(request, rel, expires, sig);
        }

        Path file = uploadService.getRoot().resolve(rel).normalize();
        if (!file.startsWith(uploadService.getRoot()) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        long length = resource.contentLength();
        String contentType = Files.probeContentType(file);
        if (contentType == null) contentType = isImage ? "application/octet-stream" : "video/mp4";

        ResourceRegion region;
        HttpStatus status;
        if (headers.getRange().isEmpty()) {
            region = new ResourceRegion(resource, 0, length);
            status = HttpStatus.OK;
        } else {
            HttpRange range = headers.getRange().get(0);
            long start = range.getRangeStart(length);
            long end = range.getRangeEnd(length);
            long rangeLength = Math.min(CHUNK, end - start + 1);
            region = new ResourceRegion(resource, start, rangeLength);
            status = HttpStatus.PARTIAL_CONTENT;
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(HttpHeaders.CACHE_CONTROL, isImage ? "public, max-age=3600" : "private, no-store")
                .body(region);
    }

    /**
     * 视频：优先校验短时签名；仅管理端员工可用 JWT 预览未签发 URL。
     * 普通 USER 即使已登录，无有效 sig 也不可按路径拉取。
     */
    private void assertVideoAccess(jakarta.servlet.http.HttpServletRequest request,
                                   String rel, String expires, String sig) {
        boolean signedOk = mediaSigningService.verifyLocalSignature(rel, expires, sig);
        UserPrincipal user = AuthContext.optional();
        boolean staffPreview = AdminRoles.isStaff(user);
        if (!signedOk && !staffPreview) {
            log.info("media.deny path={} hasUser={} signed={}", rel, user != null, signedOk);
            throw new BusinessException(401, "未登录或播放链接已过期");
        }
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank() && allowedOrigins != null) {
            boolean ok = false;
            for (String origin : allowedOrigins.split(",")) {
                String o = origin.trim();
                if (!o.isEmpty() && referer.startsWith(o)) {
                    ok = true;
                    break;
                }
            }
            if (!ok && (referer.contains("localhost") || referer.contains("127.0.0.1")
                    || referer.contains("visitworld.me"))) {
                ok = true;
            }
            if (!ok) {
                throw new BusinessException(403, "非法来源");
            }
        }
    }
}
