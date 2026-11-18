package com.aireview.security;

import com.aireview.common.BusinessException;
import com.aireview.common.ResultCode;
import com.aireview.dto.LoginResponse;
import com.aireview.dto.UserVO;
import com.aireview.entity.User;
import com.aireview.mapper.UserMapper;
import com.aireview.util.JwtUtil;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 会话凭证：签发/刷新访问令牌、登出黑名单。刷新令牌只存状态层，不内嵌任何用户信息。 */
@Service
public class TokenService {
    private static final String REFRESH_PREFIX = "refresh:";
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final JwtUtil jwtUtil;
    private final AuthStateStore store;
    private final UserMapper userMapper;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    public TokenService(JwtUtil jwtUtil, AuthStateStore store, UserMapper userMapper,
        @Value("${jwt.access-ttl-seconds:1800}") long accessTtlSeconds,
        @Value("${jwt.refresh-ttl-seconds:604800}") long refreshTtlSeconds) {
        this.jwtUtil = jwtUtil;
        this.store = store;
        this.userMapper = userMapper;
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public LoginResponse issue(User user) {
        int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        String accessToken = jwtUtil.generateToken(user.getId(), tokenVersion);
        String refreshToken = UUID.randomUUID().toString();
        store.put(REFRESH_PREFIX + refreshToken, String.valueOf(user.getId()),
            Duration.ofSeconds(refreshTtlSeconds));
        return new LoginResponse(accessToken, refreshToken, accessTtlSeconds, toVO(user));
    }

    /** 刷新令牌一次性消费并轮换，防重放。 */
    public LoginResponse refresh(String refreshToken) {
        String userId = store.getAndDelete(REFRESH_PREFIX + refreshToken);
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "刷新令牌无效或已过期");
        }
        User user = userMapper.selectById(Long.valueOf(userId));
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "刷新令牌无效或已过期");
        }
        return issue(user);
    }

    /** 登出：把当前访问令牌的 jti 拉黑，直至其自然过期，避免 Redis 无限增长。 */
    public void logout(String accessToken) {
        Claims claims = jwtUtil.parseClaims(accessToken);
        if (claims == null || claims.getId() == null) {
            return;
        }
        long remain = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remain > 0) {
            store.put(BLACKLIST_PREFIX + claims.getId(), "1", Duration.ofMillis(remain));
        }
    }

    public boolean isBlacklisted(String jti) {
        return store.exists(BLACKLIST_PREFIX + jti);
    }

    /**
     * 校验 JWT 携带的会话版本号是否与库中一致。
     * 改密码/管理员重置密码时 token_version+1，旧 token tv 不一致即失效。
     * 用户禁用或不存在也视为失效。
     */
    public boolean isTokenVersionValid(Long userId, int tokenVersion) {
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            return false;
        }
        int current = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        return current == tokenVersion;
    }

    public UserVO toVO(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(),
            userMapper.selectRoleCodes(user.getId()), user.getCreatedAt());
    }
}
