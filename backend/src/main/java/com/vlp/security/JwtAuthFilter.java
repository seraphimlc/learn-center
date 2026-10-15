package com.vlp.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 解析过滤器。优先 Authorization: Bearer；
 * 仅 /api/media/** 允许 ?token=（&lt;video&gt; 无法带 Header 的管理端预览场景），
 * 避免 JWT 泄漏到 Referer / 访问日志的攻击面扩大到全站 API。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = null;
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        } else {
            String uri = request.getRequestURI();
            // 限制 query-token 仅媒体流，降低 access log / Referer 泄漏面
            if (uri != null && uri.startsWith("/api/media/") && request.getParameter("token") != null) {
                token = request.getParameter("token");
            }
        }
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parse(token);
                Long uid = claims.get("uid", Number.class).longValue();
                String role = claims.get("role", String.class);
                UserPrincipal principal = new UserPrincipal(uid, claims.getSubject(), role);
                var auth = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {
                // token 无效：按匿名继续，由后续鉴权决定 401/403
            }
        }
        chain.doFilter(request, response);
    }
}
