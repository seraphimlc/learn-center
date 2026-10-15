package com.vlp.upload;

import com.vlp.common.BusinessException;
import com.vlp.security.AuthContext;
import com.vlp.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
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
 * Authenticated / signed media streaming (Range).
 * <p>
 * Anti-download (practical, not DRM):
 * <ul>
 *   <li>No permanent public object ACL — clients get JWT or short-lived {@code expires}+{@code sig}</li>
 *   <li>Prefer Range streaming; full-file download is harder without a valid token</li>
 *   <li>Soft Referer check when header is present</li>
 *   <li>Player UI: {@code controlsList=nodownload} + contextmenu block (frontend)</li>
 * </ul>
 * Honest limit: browsers can still capture network bytes or screen-record during the TTL.
 * Specialized DRM (Widevine / FairPlay) is out of scope for phase 1.
 */
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
            // Prefer Range for video; still allow full GET for short clips / probes
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

    private void assertVideoAccess(jakarta.servlet.http.HttpServletRequest request,
                                   String rel, String expires, String sig) {
        UserPrincipal user = AuthContext.optional();
        boolean signedOk = mediaSigningService.verifyLocalSignature(rel, expires, sig);
        if (user == null && !signedOk) {
            throw new BusinessException(401, "未登录或播放链接已过期");
        }
        // Soft anti-hotlink: if Referer is sent, it should match an allowed origin
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
            // Also allow same-host relative / empty browser cases by not failing hard when
            // Referer is from our API host (media played via absolute URL on same site).
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
