package com.aireview.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    /** 无需登录即可访问的路径。新增公开接口时必须显式加到这里。 */
    static final String[] PUBLIC_PATHS = {
        "/api/health",
        "/api/auth/register",
        "/api/auth/login",
        "/api/auth/captcha",
        "/api/auth/refresh",
        "/api/auth/sms/send",
        "/api/auth/sms/login",
        "/api/auth/email/send",
        "/api/auth/email/login",
        "/api/auth/forgot/send-code",
        "/api/auth/forgot/reset",
        "/api/auth/sso/login",
        "/api/auth/sso/mock"
    };

    private final JwtInterceptor jwtInterceptor;

    public WebConfig(JwtInterceptor jwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(PUBLIC_PATHS);
    }
}
