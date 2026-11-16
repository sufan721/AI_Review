package com.aireview.service;

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

public interface AuthService {
    CaptchaVO captcha();

    /** 注册成功即签发令牌，前端无需再调一次登录。 */
    LoginResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request, String ip, String userAgent);

    void smsSend(SmsSendRequest request);

    LoginResponse smsLogin(SmsLoginRequest request, String ip, String userAgent);

    void emailSend(EmailSendRequest request);

    LoginResponse emailLogin(EmailLoginRequest request, String ip, String userAgent);

    void forgotSendCode(ForgotSendCodeRequest request);

    void forgotReset(ForgotResetRequest request);

    LoginResponse refresh(RefreshRequest request);

    void logout(String accessToken);

    LoginResponse ssoLogin(SsoLoginRequest request, String ip, String userAgent);

    UserVO currentUser();
}
