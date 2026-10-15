package com.vlp.upload;

import com.vlp.common.BusinessException;
import com.vlp.content.entity.Course;
import com.vlp.content.entity.Lesson;
import com.vlp.content.repo.CourseRepository;
import com.vlp.content.repo.LessonRepository;
import com.vlp.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Short-lived media URLs for playback.
 * <p>
 * Honest limits: this is access control + mild anti-hotlink, not DRM.
 * A determined user can still capture bytes from a signed URL during its TTL
 * or record the screen. True DRM needs specialized players / Widevine etc.
 * <p>
 * When OSS/CDN is off: HMAC query ({@code expires}+{@code sig}) on {@code /api/media/...}.
 * When OSS is on: Aliyun OSS V1-style signed GET URL (time-limited); never return permanent public object URLs to clients.
 */
@Service
@RequiredArgsConstructor
public class MediaSigningService {
    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;

    @Value("${vlp.jwt.secret}")
    private String signingSecret;

    @Value("${vlp.cdn.enabled:false}")
    private boolean cdnEnabled;

    @Value("${vlp.cdn.domain:}")
    private String cdnDomain;

    @Value("${vlp.oss.enabled:false}")
    private boolean ossEnabled;

    @Value("${vlp.oss.endpoint:}")
    private String ossEndpoint;

    @Value("${vlp.oss.bucket:}")
    private String ossBucket;

    @Value("${vlp.oss.access-key:}")
    private String ossAccessKey;

    @Value("${vlp.oss.secret-key:}")
    private String ossSecretKey;

    @Value("${vlp.media.signed-ttl-seconds:900}")
    private int signedTtlSeconds;

    @Value("${vlp.media.public-api-base:}")
    private String publicApiBase;

    /**
     * Mint a short-lived play URL for a logged-in user.
     * Access rule: course must be PUBLISHED; user must be logged in.
     * {@code freePreview} is reserved for future enroll bypass — today there is no paywall,
     * so login + published is enough for all lessons (preview and non-preview alike).
     */
    public Map<String, Object> mintPlayUrl(UserPrincipal user, Long lessonId) {
        if (user == null) throw new BusinessException(401, "未登录");
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException("课时不存在"));
        Course course = courseRepository.findById(lesson.getCourseId())
                .orElseThrow(() -> new BusinessException("课程不存在"));
        if (!"PUBLISHED".equals(course.getStatus())) {
            throw new BusinessException(403, "课程未上架");
        }
        String key = lesson.getVideoPath();
        if (key == null || key.isBlank()) {
            throw new BusinessException("本节暂无视频");
        }
        // Never expand to a permanent public OSS/CDN HTTP URL here.
        key = normalizeObjectKey(key);
        long expiresAt = Instant.now().getEpochSecond() + Math.max(60, signedTtlSeconds);
        String playUrl;
        if (ossEnabled && hasOssCreds()) {
            playUrl = signOssGetUrl(key, expiresAt);
        } else {
            playUrl = signLocalMediaUrl(key, expiresAt);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lessonId", lessonId);
        out.put("playUrl", playUrl);
        out.put("expiresAt", expiresAt);
        out.put("ttlSeconds", signedTtlSeconds);
        out.put("freePreview", Boolean.TRUE.equals(lesson.getFreePreview()));
        return out;
    }

    /** Opaque storage key only — never a permanent public HTTP URL. */
    public String opaqueVideoKey(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) return pathOrUrl;
        return normalizeObjectKey(pathOrUrl);
    }

    public boolean verifyLocalSignature(String relativePath, String expiresStr, String sig) {
        if (relativePath == null || expiresStr == null || sig == null) return false;
        long expires;
        try {
            expires = Long.parseLong(expiresStr);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Instant.now().getEpochSecond() > expires) return false;
        String expect = hmacHex("GET\n" + expires + "\n" + normalizeObjectKey(relativePath));
        return constantTimeEquals(expect, sig);
    }

    public String signLocalMediaUrl(String objectKey, long expiresAt) {
        String key = normalizeObjectKey(objectKey);
        String sig = hmacHex("GET\n" + expiresAt + "\n" + key);
        String path = "/api/media/" + key;
        String q = "expires=" + expiresAt + "&sig=" + urlEncode(sig);
        if (publicApiBase != null && !publicApiBase.isBlank()) {
            String base = publicApiBase.endsWith("/") ? publicApiBase.substring(0, publicApiBase.length() - 1) : publicApiBase;
            return base + path + "?" + q;
        }
        // relative — browser same-origin (nginx / vite proxy)
        return path + "?" + q;
    }

    /**
     * Aliyun OSS URL signature (V1, GET). Object ACL should stay private;
     * only this temporary URL is handed to the player.
     */
    public String signOssGetUrl(String objectKey, long expiresAt) {
        String key = normalizeObjectKey(objectKey);
        String resource = "/" + ossBucket + "/" + key;
        String stringToSign = "GET\n\n\n" + expiresAt + "\n" + resource;
        String signature = hmacSha1Base64(ossSecretKey, stringToSign);
        String host = ossPublicHost();
        return host + "/" + key
                + "?Expires=" + expiresAt
                + "&OSSAccessKeyId=" + urlEncode(ossAccessKey)
                + "&Signature=" + urlEncode(signature);
    }

    private String ossPublicHost() {
        String ep = ossEndpoint == null ? "" : ossEndpoint.trim();
        if (ep.startsWith("http://") || ep.startsWith("https://")) {
            // endpoint may be https://oss-cn-hangzhou.aliyuncs.com → virtual-host style
            String withoutScheme = ep.replaceFirst("^https?://", "");
            String scheme = ep.startsWith("http://") ? "http://" : "https://";
            if (cdnEnabled && cdnDomain != null && !cdnDomain.isBlank()) {
                String d = cdnDomain.endsWith("/") ? cdnDomain.substring(0, cdnDomain.length() - 1) : cdnDomain;
                return d.startsWith("http") ? d : ("https://" + d);
            }
            return scheme + ossBucket + "." + withoutScheme;
        }
        if (cdnEnabled && cdnDomain != null && !cdnDomain.isBlank()) {
            String d = cdnDomain.endsWith("/") ? cdnDomain.substring(0, cdnDomain.length() - 1) : cdnDomain;
            return d.startsWith("http") ? d : ("https://" + d);
        }
        return "https://" + ossBucket + "." + ep;
    }

    private boolean hasOssCreds() {
        return ossBucket != null && !ossBucket.isBlank()
                && ossAccessKey != null && !ossAccessKey.isBlank()
                && ossSecretKey != null && !ossSecretKey.isBlank()
                && ossEndpoint != null && !ossEndpoint.isBlank();
    }

    private String normalizeObjectKey(String pathOrUrl) {
        String p = pathOrUrl.trim();
        if (p.startsWith("http://") || p.startsWith("https://")) {
            // strip scheme/host — keep path as object key; never pass through as public URL
            int idx = p.indexOf('/', p.indexOf("://") + 3);
            p = idx >= 0 ? p.substring(idx + 1) : p;
        }
        if (p.startsWith("/api/media/")) p = p.substring("/api/media/".length());
        while (p.startsWith("/")) p = p.substring(1);
        if (p.contains("..")) throw new BusinessException("非法路径");
        return p;
    }

    private String hmacHex(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException("签名失败");
        }
    }

    private static String hmacSha1Base64(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException("OSS 签名失败");
        }
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}
