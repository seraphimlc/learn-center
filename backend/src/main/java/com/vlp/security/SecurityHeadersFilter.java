package com.vlp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 基线安全响应头。HSTS 由生产 nginx 在 HTTPS 终结处统一下发，避免本地 HTTP 误加。
 * CSP 偏宽松以兼容 Vue 内联与媒体；审计场景可在网关侧再收紧。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SecurityHeadersFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        // baseline CSP: allow self + media; block mixed framing / object plugins
        if (!response.containsHeader("Content-Security-Policy")) {
            response.setHeader("Content-Security-Policy",
                    "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
                            + "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https:; "
                            + "media-src 'self' blob: https:; font-src 'self' data:; "
                            + "connect-src 'self' https:; frame-ancestors 'none'; object-src 'none'; base-uri 'self'");
        }
        chain.doFilter(request, response);
    }
}
