package com.aireview.service.impl;

import com.aireview.common.BusinessException;
import com.aireview.common.ResultCode;
import com.aireview.dto.CaptchaVO;
import com.aireview.dto.GeneralSetting;
import com.aireview.dto.LoginRequest;
import com.aireview.dto.LoginResponse;
import com.aireview.dto.RefreshRequest;
import com.aireview.dto.RegisterRequest;
import com.aireview.dto.SsoLoginRequest;
import com.aireview.dto.UserVO;
import com.aireview.entity.User;
import com.aireview.mapper.UserMapper;
import com.aireview.security.CaptchaService;
import com.aireview.security.PermissionService;
import com.aireview.security.TokenService;
import com.aireview.service.AuthService;
import com.aireview.service.InstanceSettingService;
import com.aireview.service.LoginLogService;
import com.aireview.service.SsoBindingService;
import com.aireview.util.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final int MAX_FAIL = 5;
    private static final int LOCK_MINUTES = 15;

    // 用户不存在时也走一次 BCrypt 比对，防时序侧信道枚举；只要格式合法即可
    private static final String FAKE_HASH =
        "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2";

    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String USER_ROLE = "ROLE_USER";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final CaptchaService captchaService;
    private final LoginLogService loginLogService;
    private final SsoBindingService ssoBindingService;
    private final InstanceSettingService instanceSettingService;
    private final PermissionService permissionService;

    public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder,
        TokenService tokenService, CaptchaService captchaService, LoginLogService loginLogService,
        SsoBindingService ssoBindingService, InstanceSettingService instanceSettingService,
        PermissionService permissionService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.captchaService = captchaService;
        this.loginLogService = loginLogService;
        this.ssoBindingService = ssoBindingService;
        this.instanceSettingService = instanceSettingService;
        this.permissionService = permissionService;
    }

    @Override
    public CaptchaVO captcha() {
        return captchaService.generate();
    }

    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (findByUsername(request.username()) != null) {
            throw new BusinessException(ResultCode.USERNAME_TAKEN.code(), "该用户名已被注册");
        }
        // 对齐 memos CreateUser：空库 → 首个账号授 ADMIN；否则按注册策略建 USER。
        // 此处 selectCount 与后续 insert 在同一事务内，但仍可能并发撞唯一索引，
        // 故下方 insert 仍以 DuplicateKeyException 兜底。
        long count = userMapper.selectCount(null);
        boolean firstUser = count == 0;

        // 非首用户：受实例设置约束；首用户引导注册不读开关，避免冷启动锁死
        if (!firstUser) {
            GeneralSetting setting = instanceSettingService.getGeneral();
            if (setting.disallowUserRegistration()) {
                throw new BusinessException(ResultCode.FORBIDDEN, "用户注册已关闭");
            }
            if (setting.disallowPasswordAuth()) {
                throw new BusinessException(ResultCode.FORBIDDEN, "密码注册已关闭");
            }
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.username());
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            // 并发下先查后插仍可能撞唯一索引，以索引为准
            throw new BusinessException(ResultCode.USERNAME_TAKEN.code(), "该用户名已被注册");
        }
        userMapper.bindRole(user.getId(), firstUser ? ADMIN_ROLE : USER_ROLE);
        log.info("注册新用户 username={} firstUser={} role={}",
            user.getUsername(), firstUser, firstUser ? ADMIN_ROLE : USER_ROLE);
        return tokenService.issue(userMapper.selectById(user.getId()));
    }

    @Override
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        captchaService.validateAndConsume(request.captchaKey(), request.captchaCode());

        User user = findByUsername(request.username());
        if (user == null) {
            passwordEncoder.matches(request.password(), FAKE_HASH);
            loginLogService.record(request.username(), "PASSWORD", false, ip, userAgent, "用户不存在");
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(LocalDateTime.now())) {
            loginLogService.record(request.username(), "PASSWORD", false, ip, userAgent, "账号锁定中");
            throw new BusinessException(ResultCode.LOCKED);
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            loginLogService.record(request.username(), "PASSWORD", false, ip, userAgent, "账号禁用");
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFail(user, ip, userAgent);
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        // 普通用户被禁密码登录时拒绝；admin 密码通道始终保留，否则管理员无法登回实例
        GeneralSetting setting = instanceSettingService.getGeneral();
        if (setting.disallowPasswordAuth() && !permissionService.hasRole(user.getId(), ADMIN_ROLE)) {
            loginLogService.record(request.username(), "PASSWORD", false, ip, userAgent, "密码登录已关闭");
            throw new BusinessException(ResultCode.FORBIDDEN, "密码登录已关闭，请使用 SSO");
        }
        if ((user.getFailCount() != null && user.getFailCount() > 0) || user.getLockUntil() != null) {
            userMapper.resetFailState(user.getId());
        }
        loginLogService.record(request.username(), "PASSWORD", true, ip, userAgent, "登录成功");
        return tokenService.issue(user);
    }

    @Override
    public LoginResponse refresh(RefreshRequest request) {
        return tokenService.refresh(request.refreshToken());
    }

    @Override
    public void logout(String accessToken) {
        tokenService.logout(accessToken);
    }

    @Override
    public LoginResponse ssoLogin(SsoLoginRequest request, String ip, String userAgent) {
        User user = ssoBindingService.resolveOrBind(request.provider(), request.subject(),
            request.username(), request.email());
        loginLogService.record(user.getUsername(), "SSO", true, ip, userAgent, "登录成功");
        return tokenService.issue(user);
    }

    @Override
    public UserVO currentUser() {
        User user = userMapper.selectById(UserContext.require());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return tokenService.toVO(user);
    }

    private void handleFail(User user, String ip, String userAgent) {
        int current = user.getFailCount() == null ? 0 : user.getFailCount();
        int newCount = current + 1;
        LocalDateTime lockUntil = null;
        if (newCount >= MAX_FAIL) {
            lockUntil = LocalDateTime.now().plusMinutes(LOCK_MINUTES);
            newCount = 0;
        }
        userMapper.updateFailState(user.getId(), newCount, lockUntil);
        loginLogService.record(user.getUsername(), "PASSWORD", false, ip, userAgent, "密码错误");
        log.warn("登录失败-密码错误 username={} ip={} failCount={} lockUntil={}",
            user.getUsername(), ip, newCount, lockUntil);
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
    }
}
