package com.aireview.controller;

import com.aireview.common.Result;
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
import com.aireview.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.ok(authService.captcha());
    }

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
        HttpServletRequest http) {
        return Result.ok(authService.login(request, http.getRemoteAddr(), userAgent(http)));
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest http) {
        authService.logout(bearerToken(http));
        return Result.ok();
    }

    @PostMapping("/sms/send")
    public Result<Void> smsSend(@Valid @RequestBody SmsSendRequest request) {
        authService.smsSend(request);
        return Result.ok();
    }

    @PostMapping("/sms/login")
    public Result<LoginResponse> smsLogin(@Valid @RequestBody SmsLoginRequest request,
        HttpServletRequest http) {
        return Result.ok(authService.smsLogin(request, http.getRemoteAddr(), userAgent(http)));
    }

    @PostMapping("/email/send")
    public Result<Void> emailSend(@Valid @RequestBody EmailSendRequest request) {
        authService.emailSend(request);
        return Result.ok();
    }

    @PostMapping("/email/login")
    public Result<LoginResponse> emailLogin(@Valid @RequestBody EmailLoginRequest request,
        HttpServletRequest http) {
        return Result.ok(authService.emailLogin(request, http.getRemoteAddr(), userAgent(http)));
    }

    @PostMapping("/forgot/send-code")
    public Result<Void> forgotSendCode(@Valid @RequestBody ForgotSendCodeRequest request) {
        authService.forgotSendCode(request);
        return Result.ok();
    }

    @PostMapping("/forgot/reset")
    public Result<Void> forgotReset(@Valid @RequestBody ForgotResetRequest request) {
        authService.forgotReset(request);
        return Result.ok();
    }

    @PostMapping("/sso/login")
    public Result<LoginResponse> ssoLogin(@Valid @RequestBody SsoLoginRequest request,
        HttpServletRequest http) {
        return Result.ok(authService.ssoLogin(request, http.getRemoteAddr(), userAgent(http)));
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.currentUser());
    }

    private static String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
