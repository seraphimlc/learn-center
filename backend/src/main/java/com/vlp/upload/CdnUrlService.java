package com.vlp.upload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Cover / image URLs only.
 * <p>
 * Video playback must NOT go through {@link #publicUrl(String)} — that would expose
 * permanent public CDN/OSS links. Use {@link MediaSigningService#mintPlayUrl} instead
 * (short-lived signed URL or tokenized {@code /api/media} stream).
 */
@Service
public class CdnUrlService {
    @Value("${vlp.cdn.enabled:false}")
    private boolean cdnEnabled;

    @Value("${vlp.cdn.domain:}")
    private String cdnDomain;

    /** Rewrite image/cover paths to CDN domain when enabled. Do not use for video objects. */
    public String publicUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) return pathOrUrl;
        if (!cdnEnabled || cdnDomain == null || cdnDomain.isBlank()) return pathOrUrl;
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) return pathOrUrl;
        String base = cdnDomain.endsWith("/") ? cdnDomain.substring(0, cdnDomain.length() - 1) : cdnDomain;
        String path = pathOrUrl.startsWith("/") ? pathOrUrl : "/" + pathOrUrl;
        return base + path;
    }
}
