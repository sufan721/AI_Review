package com.aireview.service;

import com.aireview.common.BusinessException;
import com.aireview.common.ResultCode;
import com.aireview.dto.GeneralSetting;
import com.aireview.entity.IdentityProvider;
import com.aireview.entity.SsoBinding;
import com.aireview.entity.User;
import com.aireview.mapper.IdentityProviderMapper;
import com.aireview.mapper.SsoBindingMapper;
import com.aireview.mapper.UserMapper;
import com.aireview.service.InstanceSettingService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 统一身份认证绑定：按 (provider, subject) 定位本地用户，首次登录自动建号并绑定默认角色。
 *
 * <p>未命中绑定时受两层约束：
 * <ol>
 *   <li>{@code identifierFilter}：若 provider 对应身份源配置了过滤正则，
 *       subject 不匹配则拒绝（对齐 memos 的 identifierFilter）；</li>
 *   <li>{@code disallowUserRegistration}：实例关闭注册时拒绝自动建号，
 *       提示联系管理员建号/绑定。</li>
 * </ol>
 *
 * <p>匹配键只用 (provider, extern_uid)，绝不因用户名相同关联已有账号。
 */
@Service
public class SsoBindingService {
    private static final Logger log = LoggerFactory.getLogger(SsoBindingService.class);

    private final SsoBindingMapper bindingMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final InstanceSettingService instanceSettingService;
    private final IdentityProviderMapper identityProviderMapper;

    public SsoBindingService(SsoBindingMapper bindingMapper, UserMapper userMapper,
        PasswordEncoder passwordEncoder, InstanceSettingService instanceSettingService,
        IdentityProviderMapper identityProviderMapper) {
        this.bindingMapper = bindingMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.instanceSettingService = instanceSettingService;
        this.identityProviderMapper = identityProviderMapper;
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

        // 未命中绑定：先校验身份源 identifierFilter（provider 对应身份源不存在时跳过，
        // 兼容 mock IdP 等未在 identity_provider 表登记的 provider）
        applyIdentifierFilter(provider, subject);

        // 再校验实例注册开关：关闭时拒绝自动建号
        GeneralSetting setting = instanceSettingService.getGeneral();
        if (setting.disallowUserRegistration()) {
            log.info("SSO 首次登录被拒：注册已关闭 provider={} subject={}", provider, subject);
            throw new BusinessException(ResultCode.FORBIDDEN, "用户注册已关闭，请联系管理员建号或绑定账号");
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

    /** provider 对应身份源配置了 identifierFilter 时，subject 必须匹配正则，否则拒绝。 */
    private void applyIdentifierFilter(String provider, String subject) {
        IdentityProvider idp = identityProviderMapper.selectOne(
            Wrappers.<IdentityProvider>lambdaQuery()
                .eq(IdentityProvider::getUid, provider));
        if (idp == null) {
            return;
        }
        String filter = idp.getIdentifierFilter();
        if (filter == null || filter.isBlank()) {
            return;
        }
        if (!Pattern.compile(filter).matcher(subject).matches()) {
            log.info("SSO 登录被拒：subject 不匹配 identifierFilter provider={} subject={}",
                provider, subject);
            throw new BusinessException(ResultCode.FORBIDDEN, "身份标识不符合该身份源的准入规则");
        }
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
