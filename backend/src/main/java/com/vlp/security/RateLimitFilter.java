package com.vlp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Redis fixed-window rate limiter for auth / upload / progress endpoints.
 * Progress default allows ~3s heartbeats (≈20/min/user) with headroom (see RL_PROGRESS_PER_MIN).
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {
    private final StringRedisTemplate redis;

    @Value("${vlp.rate-limit.auth-per-minute:60}")
    private int authLimit;

    @Value("${vlp.rate-limit.upload-per-minute:30}")
    private int uploadLimit;

    @Value("${vlp.rate-limit.progress-per-minute:180}")
    private int progressLimit;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String bucket = null;
        int limit = 0;
        if (path.startsWith("/api/auth/")) {
            bucket = "auth";
            limit = authLimit;
        } else if (path.startsWith("/api/admin/upload") || path.contains("/upload")) {
            bucket = "upload";
            limit = uploadLimit;
        } else if (path.startsWith("/api/learn/progress")) {
            bucket = "progress";
            limit = progressLimit;
        }
        if (limit > 0) {
            String ip = clientIp(request);
            String key = "rl:" + bucket + ":" + ip + ":" + (System.currentTimeMillis() / 60000);
            try {
                Long n = redis.opsForValue().increment(key);
                if (n != null && n == 1L) {
                    redis.expire(key, Duration.ofMinutes(2));
                }
                if (n != null && n > limit) {
                    response.setStatus(429);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}");
                    return;
                }
            } catch (Exception ignored) {
                // Redis down: fail open so the site stays up
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Prefer X-Forwarded-For first hop when present (nginx must overwrite, not append client value).
     * Reject blank / obviously invalid tokens so spoofed garbage does not shard the bucket forever.
     */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (looksLikeIp(first)) return first;
        }
        return request.getRemoteAddr();
    }

    private static boolean looksLikeIp(String s) {
        if (s == null || s.length() < 3 || s.length() > 45) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isDigit(c) || c == '.' || c == ':' || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }
}
