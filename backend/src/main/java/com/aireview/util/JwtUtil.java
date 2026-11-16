package com.aireview.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {
    private final SecretKey key;
    private final long accessTtlSeconds;

    public JwtUtil(@Value("${jwt.secret}") String secret,
        @Value("${jwt.access-ttl-seconds:1800}") long accessTtlSeconds) {
        // 密钥过短时 Keys.hmacShaKeyFor 直接抛异常，让配置问题在启动期暴露
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public String generateToken(Long userId) {
        Date issuedAt = new Date();
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(String.valueOf(userId))
            .issuedAt(issuedAt)
            .expiration(new Date(issuedAt.getTime() + Duration.ofSeconds(accessTtlSeconds).toMillis()))
            .signWith(key)
            .compact();
    }

    /** 解析失败（签名不符、过期、格式错误）返回 null，由调用方统一按未登录处理。 */
    public Long parseUserId(String token) {
        Claims claims = parseClaims(token);
        return claims == null ? null : Long.valueOf(claims.getSubject());
    }

    /** 解析出完整载荷；失败返回 null。用于登出时读取 jti 以加入黑名单。 */
    public Claims parseClaims(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            return null;
        }
    }
}
