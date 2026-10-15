package com.vlp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * HS256 JWT 签发/校验。密钥必须 ≥32 字节；生产务必用环境变量 JWT_SECRET 覆盖默认值。
 * 无状态：登出不立即失效（P2 可加 Redis 黑名单）。claims 含 uid + role，供 RBAC 使用。
 */
@Service
public class JwtService {
    private final SecretKey key;
    private final long expireMs;

    public JwtService(@Value("${vlp.jwt.secret}") String secret,
                      @Value("${vlp.jwt.expire-hours}") long hours) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireMs = hours * 3600_000L;
    }

    public String issue(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claims(Map.of("uid", userId, "role", role))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMs))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
