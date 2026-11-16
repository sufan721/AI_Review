package com.aireview.service;

import com.aireview.entity.SsoBinding;
import com.aireview.entity.User;
import com.aireview.mapper.SsoBindingMapper;
import com.aireview.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 统一身份认证绑定：按 (provider, subject) 定位本地用户，首次登录自动建号并绑定默认角色。 */
@Service
public class SsoBindingService {
    private final SsoBindingMapper bindingMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public SsoBindingService(SsoBindingMapper bindingMapper, UserMapper userMapper,
        PasswordEncoder passwordEncoder) {
        this.bindingMapper = bindingMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User resolveOrBind(String provider, String subject, String username, String email) {
        SsoBinding binding = findByProviderSubject(provider, subject);
        if (binding != null) {
            User bound = userMapper.selectById(binding.getUserId());
            if (bound != null) {
                return bound;
            }
        }

        String loginName = uniqueUsername(username == null || username.isBlank()
            ? provider.toLowerCase(Locale.ROOT) + "_" + subject
            : username);

        User user = new User();
        user.setUsername(loginName);
        user.setNickname(username);
        // SSO 用户无本地密码：写入不可登录的随机哈希，禁止密码登录该账号
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setEmail(uniqueEmail(email));
        user.setStatus(1);
        userMapper.insert(user);
        userMapper.bindRole(user.getId(), "ROLE_USER");

        try {
            SsoBinding newBinding = new SsoBinding();
            newBinding.setUserId(user.getId());
            newBinding.setSsoProvider(provider);
            newBinding.setSsoSubject(subject);
            bindingMapper.insert(newBinding);
        } catch (DuplicateKeyException exception) {
            // 并发下唯一索引兜底：返回先插入的那个绑定对应的用户
            return userMapper.selectById(findByProviderSubject(provider, subject).getUserId());
        }
        return user;
    }

    private SsoBinding findByProviderSubject(String provider, String subject) {
        return bindingMapper.selectOne(Wrappers.<SsoBinding>lambdaQuery()
            .eq(SsoBinding::getSsoProvider, provider)
            .eq(SsoBinding::getSsoSubject, subject));
    }

    private String uniqueUsername(String preferred) {
        if (userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, preferred)) == null) {
            return preferred;
        }
        return preferred + "_" + randomSuffix();
    }

    private String uniqueEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email)) == null
            ? email : null;
    }

    private static String randomSuffix() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
