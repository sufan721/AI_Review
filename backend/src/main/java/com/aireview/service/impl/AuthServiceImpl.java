package com.aireview.service.impl;

import com.aireview.common.BusinessException;
import com.aireview.common.ResultCode;
import com.aireview.dto.CaptchaVO;
import com.aireview.dto.LoginRequest;
import com.aireview.dto.LoginResponse;
import com.aireview.dto.RefreshRequest;
import com.aireview.dto.RegisterRequest;
import com.aireview.dto.SsoLoginRequest;
import com.aireview.dto.UserVO;
import com.aireview.entity.User;
import com.aireview.mapper.UserMapper;
import com.aireview.security.CaptchaService;
import com.aireview.security.TokenService;
import com.aireview.service.AuthService;
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

@Service
public class AuthServiceImpl implements AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final int MAX_FAIL = 5;
    private static final int LOCK_MINUTES = 15;

    // 用户不存在时也走一次 BCrypt 比对，防时序侧信道枚举；只要格式合法即可
    private static final String FAKE_HASH =
        "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final CaptchaService captchaService;
    private final LoginLogService loginLogService;
    private final SsoBindingService ssoBindingService;

    public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder,
        TokenService tokenService, CaptchaService captchaService, LoginLogService loginLogService,
        SsoBindingService ssoBindingService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.captchaService = captchaService;
        this.loginLogService = loginLogService;
        this.ssoBindingService = ssoBindingService;
    }

    @Override
    public CaptchaVO captcha() {
        return captchaService.generate();
    }

    @Override
    public LoginResponse register(RegisterRequest request) {
        if (findByUsername(request.username()) != null) {
            throw new BusinessException(ResultCode.USERNAME_TAKEN.code(), "该用户名已被注册");
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
        userMapper.bindRole(user.getId(), "ROLE_USER");
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
