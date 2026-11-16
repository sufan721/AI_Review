package com.aireview.service.impl;

import com.aireview.common.BusinessException;
import com.aireview.common.ResultCode;
import com.aireview.dto.CaptchaVO;
import com.aireview.dto.EmailLoginRequest;
import com.aireview.dto.EmailSendRequest;
import com.aireview.dto.ForgotResetRequest;
import com.aireview.dto.ForgotSendCodeRequest;
import com.aireview.dto.LoginRequest;
import com.aireview.dto.LoginResponse;
import com.aireview.dto.RefreshRequest;
import com.aireview.dto.RegisterRequest;
import com.aireview.dto.SmsLoginRequest;
import com.aireview.dto.SmsSendRequest;
import com.aireview.dto.SsoLoginRequest;
import com.aireview.dto.UserVO;
import com.aireview.entity.User;
import com.aireview.mapper.UserMapper;
import com.aireview.security.AuthStateStore;
import com.aireview.security.CaptchaService;
import com.aireview.security.TokenService;
import com.aireview.service.AuthService;
import com.aireview.service.LoginLogService;
import com.aireview.service.SsoBindingService;
import com.aireview.util.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final int MAX_FAIL = 5;
    private static final int LOCK_MINUTES = 15;
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration RESET_CODE_TTL = Duration.ofMinutes(10);
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);

    private static final String SMS_CODE_PREFIX = "sms:code:";
    private static final String SMS_LIMIT_PREFIX = "sms:limit:";
    private static final String EMAIL_CODE_PREFIX = "mail:code:";
    private static final String EMAIL_LIMIT_PREFIX = "mail:limit:";
    private static final String RESET_CODE_PREFIX = "reset:code:";

    // 用户不存在时也走一次 BCrypt 比对，防时序侧信道枚举；只要格式合法即可
    private static final String FAKE_HASH =
        "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final CaptchaService captchaService;
    private final LoginLogService loginLogService;
    private final SsoBindingService ssoBindingService;
    private final AuthStateStore store;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean mailEnabled;
    private final SecureRandom random = new SecureRandom();

    public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder,
        TokenService tokenService, CaptchaService captchaService, LoginLogService loginLogService,
        SsoBindingService ssoBindingService, AuthStateStore store,
        ObjectProvider<JavaMailSender> mailSenderProvider,
        @Value("${ai-review.auth.mail-enabled:false}") boolean mailEnabled) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.captchaService = captchaService;
        this.loginLogService = loginLogService;
        this.ssoBindingService = ssoBindingService;
        this.store = store;
        this.mailSenderProvider = mailSenderProvider;
        this.mailEnabled = mailEnabled;
    }

    @Override
    public CaptchaVO captcha() {
        return captchaService.generate();
    }

    @Override
    public LoginResponse register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "两次输入的密码不一致");
        }
        // 短信验证码一次性消费；先校验口令一致性，避免因手误白白烧掉 60 秒冷却的验证码
        consumeCode(SMS_CODE_PREFIX + request.phone(), request.smsCode());
        if (findByPhone(request.phone()) != null) {
            throw new BusinessException(ResultCode.USERNAME_TAKEN.code(), "该手机号已被使用");
        }
        if (request.email() != null && findByEmail(request.email()) != null) {
            throw new BusinessException(ResultCode.USERNAME_TAKEN.code(), "该邮箱已被使用");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null || request.nickname().isBlank()
            ? request.username()
            : request.nickname());
        user.setPhone(blankToNull(request.phone()));
        user.setEmail(blankToNull(request.email()));
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            // 并发下先查后插仍可能撞唯一索引，以索引为准
            throw new BusinessException(ResultCode.USERNAME_TAKEN.code(), "用户名或联系方式已被注册");
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
    public void smsSend(SmsSendRequest request) {
        if (!store.setIfAbsent(SMS_LIMIT_PREFIX + request.phone(), "1", SEND_COOLDOWN)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        String code = randomCode();
        store.put(SMS_CODE_PREFIX + request.phone(), code, CODE_TTL);
        log.info("""
            ================ 短信验证码 ================
            手机号: {}
            验证码: {}
            有效期: 5 分钟
            ============================================
            """, request.phone(), code);
    }

    @Override
    public LoginResponse smsLogin(SmsLoginRequest request, String ip, String userAgent) {
        consumeCode(SMS_CODE_PREFIX + request.phone(), request.smsCode());
        User user = findByPhone(request.phone());
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "该手机号未注册");
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        loginLogService.record(request.phone(), "SMS", true, ip, userAgent, "登录成功");
        return tokenService.issue(user);
    }

    @Override
    public void emailSend(EmailSendRequest request) {
        if (!store.setIfAbsent(EMAIL_LIMIT_PREFIX + request.email(), "1", SEND_COOLDOWN)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        String code = randomCode();
        store.put(EMAIL_CODE_PREFIX + request.email(), code, CODE_TTL);
        log.info("""
            ================ 邮件验证码 ================
            邮箱: {}
            验证码: {}
            有效期: 5 分钟
            ============================================
            """, request.email(), code);
        if (mailEnabled) {
            JavaMailSender sender = mailSenderProvider.getIfAvailable();
            if (sender != null) {
                try {
                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setTo(request.email());
                    message.setSubject("【备忘录】登录验证码");
                    message.setText("您的登录验证码为：" + code + "，5 分钟内有效。如非本人操作请忽略。");
                    sender.send(message);
                } catch (RuntimeException exception) {
                    log.warn("邮件发送失败，验证码已打印到控制台: {}", exception.getMessage());
                }
            }
        }
    }

    @Override
    public LoginResponse emailLogin(EmailLoginRequest request, String ip, String userAgent) {
        consumeCode(EMAIL_CODE_PREFIX + request.email(), request.emailCode());
        User user = findByEmail(request.email());
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "该邮箱未注册");
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        loginLogService.record(request.email(), "EMAIL", true, ip, userAgent, "登录成功");
        return tokenService.issue(user);
    }

    @Override
    public void forgotSendCode(ForgotSendCodeRequest request) {
        // 防枚举：无论账号是否存在都返回成功；不存在时静默不生成
        User user = resolveAccount(request.account());
        if (user == null) {
            return;
        }
        String code = randomCode();
        store.put(RESET_CODE_PREFIX + request.account(), code, RESET_CODE_TTL);
        log.info("""
            ================ 找回密码验证码 ================
            账号: {}
            验证码: {}
            有效期: 10 分钟
            ==============================================
            """, request.account(), code);
    }

    @Override
    public void forgotReset(ForgotResetRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "两次输入的密码不一致");
        }
        consumeCode(RESET_CODE_PREFIX + request.account(), request.resetCode());
        User user = resolveAccount(request.account());
        if (user == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "重置码错误或已过期");
        }
        userMapper.updatePassword(user.getId(), passwordEncoder.encode(request.newPassword()));
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

    private void consumeCode(String key, String code) {
        String saved = store.getAndDelete(key);
        if (saved == null || !saved.equals(code)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "验证码错误或已过期");
        }
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
    }

    private User findByPhone(String phone) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getPhone, phone));
    }

    private User findByEmail(String email) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
    }

    private User resolveAccount(String account) {
        User user = findByEmail(account);
        return user != null ? user : findByPhone(account);
    }

    private String randomCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
