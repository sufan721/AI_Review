package com.aireview.service;

import com.aireview.dto.CaptchaVO;
import com.aireview.dto.LoginRequest;
import com.aireview.dto.LoginResponse;
import com.aireview.dto.RefreshRequest;
import com.aireview.dto.RegisterRequest;
import com.aireview.dto.SsoLoginRequest;
import com.aireview.dto.UserVO;

public interface AuthService {
    CaptchaVO captcha();

    /** 注册成功即签发令牌，前端无需再调一次登录。 */
    LoginResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request, String ip, String userAgent);

    LoginResponse refresh(RefreshRequest request);

    void logout(String accessToken);

    LoginResponse ssoLogin(SsoLoginRequest request, String ip, String userAgent);

    UserVO currentUser();
}
