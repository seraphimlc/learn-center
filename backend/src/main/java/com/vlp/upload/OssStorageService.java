package com.vlp.upload;

import com.vlp.common.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Minimal Aliyun OSS client: HTTP PUT/DELETE with Signature V1 (Authorization header).
 * No SDK dependency. Objects stay private; clients get short-lived signed GET URLs
 * from {@link MediaSigningService}.
 */
@Slf4j
@Service
public class OssStorageService {
    private static final DateTimeFormatter RFC822 = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .withZone(ZoneOffset.UTC);

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

    /** Object key prefix, e.g. {@code learn/} — avoids colliding with other apps on same bucket. */
    @Value("${vlp.oss.prefix:learn/}")
    private String ossPrefix;

    private HttpClient http;
    private volatile boolean ready;

    @PostConstruct
    public void init() {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        ready = ossEnabled && hasCreds();
        if (ready) {
            log.info("OSS storage enabled bucket={} endpoint={} prefix={}",
                    ossBucket, sanitizeEndpoint(ossEndpoint), normalizePrefix(ossPrefix));
        } else if (ossEnabled) {
            log.warn("OSS_ENABLED=true but credentials/endpoint/bucket incomplete — local upload fallback");
        }
    }

    public boolean isReady() {
        return ready;
    }

    public String prefix() {
        return normalizePrefix(ossPrefix);
    }

    /**
     * PUT object to OSS. {@code objectKey} is the full key including prefix
     * (e.g. {@code learn/videos/uuid.mp4}).
     */
    public void putObject(String objectKey, Supplier<InputStream> bodySupplier, long contentLength, String contentType) {
        if (!ready) throw new BusinessException("OSS 未启用");
        String key = stripLeadingSlash(objectKey);
        if (key.isEmpty() || key.contains("..")) throw new BusinessException("非法对象键");
        String date = RFC822.format(ZonedDateTime.now(ZoneOffset.UTC));
        String ct = (contentType == null || contentType.isBlank())
                ? "application/octet-stream" : contentType;
        String resource = "/" + ossBucket + "/" + key;
        String stringToSign = "PUT\n\n" + ct + "\n" + date + "\n" + resource;
        String signature = hmacSha1Base64(ossSecretKey, stringToSign);
        String auth = "OSS " + ossAccessKey + ":" + signature;
        String url = virtualHostBase() + "/" + key;
        try {
            Supplier<InputStream> safe = () -> {
                try {
                    return bodySupplier.get();
                } catch (UncheckedIOException e) {
                    throw e;
                } catch (Exception e) {
                    throw new UncheckedIOException(e instanceof IOException io ? io : new IOException(e));
                }
            };
            HttpRequest.BodyPublisher publisher = contentLength >= 0
                    ? HttpRequest.BodyPublishers.fromPublisher(
                    HttpRequest.BodyPublishers.ofInputStream(safe), contentLength)
                    : HttpRequest.BodyPublishers.ofInputStream(safe);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(30))
                    .header("Date", date)
                    .header("Content-Type", ct)
                    .header("Authorization", auth)
                    .PUT(publisher)
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code < 200 || code >= 300) {
                log.warn("OSS PUT failed status={} key={} bodySnippet={}", code, key, truncate(resp.body(), 200));
                throw new BusinessException("OSS 上传失败 (" + code + ")");
            }
            log.info("OSS PUT ok key={} bytes={}", key, contentLength);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OSS PUT error key={}: {}", key, e.toString());
            throw new BusinessException("OSS 上传失败");
        }
    }

    public void deleteObject(String objectKey) {
        if (!ready) return;
        String key = stripLeadingSlash(objectKey);
        String date = RFC822.format(ZonedDateTime.now(ZoneOffset.UTC));
        String resource = "/" + ossBucket + "/" + key;
        String stringToSign = "DELETE\n\n\n" + date + "\n" + resource;
        String signature = hmacSha1Base64(ossSecretKey, stringToSign);
        String auth = "OSS " + ossAccessKey + ":" + signature;
        String url = virtualHostBase() + "/" + key;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Date", date)
                    .header("Authorization", auth)
                    .DELETE()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300 && resp.statusCode() != 404) {
                log.warn("OSS DELETE status={} key={}", resp.statusCode(), key);
            }
        } catch (Exception e) {
            log.warn("OSS DELETE error key={}: {}", key, e.toString());
        }
    }

    private String virtualHostBase() {
        String ep = sanitizeEndpoint(ossEndpoint);
        String scheme = ossEndpoint != null && ossEndpoint.trim().startsWith("http://") ? "http://" : "https://";
        return scheme + ossBucket + "." + ep;
    }

    private boolean hasCreds() {
        return notBlank(ossBucket) && notBlank(ossAccessKey) && notBlank(ossSecretKey) && notBlank(ossEndpoint);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return "learn/";
        String p = prefix.trim();
        while (p.startsWith("/")) p = p.substring(1);
        if (!p.endsWith("/")) p = p + "/";
        return p;
    }

    private static String stripLeadingSlash(String s) {
        String p = s == null ? "" : s.trim();
        while (p.startsWith("/")) p = p.substring(1);
        return p;
    }

    private static String sanitizeEndpoint(String ep) {
        if (ep == null) return "";
        return ep.trim().replaceFirst("^https?://", "").replaceAll("/+$", "");
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

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
