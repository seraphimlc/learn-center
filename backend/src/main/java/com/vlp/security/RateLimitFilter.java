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
 * Redis 固定窗口限流。auth/login/reset 更严以抗撞库；progress 默认 180 覆盖 3s 心跳。
 * Redis 宕机时 fail-open，保证站点可用（依赖其它层防护）。
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {
    private final StringRedisTemplate redis;

    @Value("${vlp.rate-limit.auth-per-minute:30}")
    private int authLimit;

    @Value("${vlp.rate-limit.reset-per-minute:10}")
    private int resetLimit;

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
        if (path.equals("/api/auth/reset-password")) {
            bucket = "reset";
            limit = resetLimit;
        } else if (path.startsWith("/api/auth/")) {
            bucket = "auth";
            limit = authLimit;
        } else if (path.startsWith("/api/admin/upload") || path.endsWith("/upload")) {
            // 收窄：避免 path.contains("/upload") 误伤无关路径
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
                // Redis down: fail open
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * 取 X-Forwarded-For 首跳；nginx 必须覆盖而非追加客户端伪造值。
     * 校验形态，防止畸形 XFF 打散限流桶。
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
