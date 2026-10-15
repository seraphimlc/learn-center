package com.vlp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 为每个请求注入 X-Request-Id / MDC.requestId，便于日志串联与排障。
 * 若客户端已带合法 request id 则复用（最长 64，仅字母数字与 -_）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String rid = request.getHeader(HEADER);
        if (rid == null || rid.isBlank() || rid.length() > 64 || !rid.matches("[A-Za-z0-9\\-_]+")) {
            rid = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(MDC_KEY, rid);
        response.setHeader(HEADER, rid);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
