package com.aireview.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        // cost=12 比默认 10 更抗离线破解；盐值内嵌于哈希，无需单独存盐
        return new BCryptPasswordEncoder(12);
    }
}
