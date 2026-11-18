package com.aireview.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 *
 * <p>所有 {@code /api/**} 都经过 {@link JwtInterceptor}；放行策略集中在拦截器与
 * {@link com.aireview.security.PublicAccessRegistry}，由其按 Bootstrap / Public
 * / Protected 三层判定，避免静态白名单无法区分 ACCESS 模式的问题。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    public WebConfig(JwtInterceptor jwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
            .addPathPatterns("/api/**");
    }
}
