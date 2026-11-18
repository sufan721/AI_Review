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
        return generateToken(userId, 0);
    }

    /**
     * 生成带会话版本号的 token。
     * 改密码/管理员重置密码时 token_version+1，旧 token 因 tv 不一致而失效。
     */
    public String generateToken(Long userId, int tokenVersion) {
        Date issuedAt = new Date();
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(String.valueOf(userId))
            .claim("tv", tokenVersion)
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

    /** 读取 claims 中的会话版本号；缺失视为 0（兼容旧 token）。 */
    public int parseTokenVersion(Claims claims) {
        if (claims == null) {
            return 0;
        }
        Object value = claims.get("tv");
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }
}
