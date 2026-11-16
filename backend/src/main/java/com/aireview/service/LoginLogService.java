package com.aireview.service;

import com.aireview.entity.LoginLog;
import com.aireview.mapper.LoginLogMapper;
import org.springframework.stereotype.Service;

/** 登录审计：只写不读，具体失败原因进日志，不进对外响应。 */
@Service
public class LoginLogService {
    private final LoginLogMapper loginLogMapper;

    public LoginLogService(LoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    public void record(String username, String loginType, boolean success, String ip,
        String userAgent, String message) {
        LoginLog log = new LoginLog();
        log.setUsername(username);
        log.setLoginType(loginType);
        log.setSuccess(success);
        log.setClientIp(ip);
        log.setUserAgent(userAgent);
        log.setMessage(message);
        loginLogMapper.insert(log);
    }
}
