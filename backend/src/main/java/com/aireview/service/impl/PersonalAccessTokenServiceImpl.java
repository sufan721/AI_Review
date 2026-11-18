package com.aireview.service.impl;

import com.aireview.dto.PatCreatedVO;
import com.aireview.dto.PatVO;
import com.aireview.entity.PersonalAccessToken;
import com.aireview.entity.User;
import com.aireview.mapper.PersonalAccessTokenMapper;
import com.aireview.mapper.UserMapper;
import com.aireview.service.PersonalAccessTokenService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalAccessTokenServiceImpl implements PersonalAccessTokenService {

    /** PAT 原始值前缀，用于在 Authorization 头中识别 PAT vs JWT。 */
    public static final String TOKEN_PREFIX = "memos_pat_";

    private static final SecureRandom RNG = new SecureRandom();
    private static final int RANDOM_BYTES = 32;

    private final PersonalAccessTokenMapper patMapper;
    private final UserMapper userMapper;

    public PersonalAccessTokenServiceImpl(PersonalAccessTokenMapper patMapper, UserMapper userMapper) {
        this.patMapper = patMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public PatCreatedVO issue(Long userId, String description, Duration ttl) {
        byte[] bytes = new byte[RANDOM_BYTES];
        RNG.nextBytes(bytes);
        String rawToken = TOKEN_PREFIX
            + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = sha256Hex(rawToken);
        long now = System.currentTimeMillis();
        Long expiresTs = ttl == null ? null : now + ttl.toMillis();

        PersonalAccessToken entity = new PersonalAccessToken();
        entity.setUid(java.util.UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setTokenHash(tokenHash);
        entity.setDescription(description);
        entity.setCreatedAt(now);
        entity.setExpiresTs(expiresTs);
        patMapper.insert(entity);

        return new PatCreatedVO(entity.getId(), entity.getUid(), rawToken,
            description, now, expiresTs);
    }

    @Override
    public Long verify(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(TOKEN_PREFIX)) {
            return null;
        }
        String hash = sha256Hex(rawToken);
        PersonalAccessToken pat = patMapper.selectOne(
            Wrappers.<PersonalAccessToken>lambdaQuery()
                .eq(PersonalAccessToken::getTokenHash, hash));
        if (pat == null) {
            return null;
        }
        if (pat.getExpiresTs() != null && pat.getExpiresTs() < System.currentTimeMillis()) {
            return null;
        }
        User user = userMapper.selectById(pat.getUserId());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            return null;
        }
        return pat.getUserId();
    }

    @Override
    public List<PatVO> list(Long userId) {
        return patMapper.selectList(Wrappers.<PersonalAccessToken>lambdaQuery()
                .eq(PersonalAccessToken::getUserId, userId)
                .orderByDesc(PersonalAccessToken::getCreatedAt))
            .stream()
            .map(this::toVO)
            .toList();
    }

    @Override
    @Transactional
    public boolean revoke(Long userId, Long id) {
        int deleted = patMapper.delete(Wrappers.<PersonalAccessToken>lambdaQuery()
            .eq(PersonalAccessToken::getId, id)
            .eq(PersonalAccessToken::getUserId, userId));
        return deleted > 0;
    }

    private PatVO toVO(PersonalAccessToken pat) {
        return new PatVO(pat.getId(), pat.getUid(), pat.getDescription(),
            pat.getCreatedAt(), pat.getExpiresTs());
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，理论上不会缺失
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
