package com.aireview.security;

import com.aireview.common.BusinessException;
import com.aireview.dto.CaptchaVO;
import com.aireview.util.CaptchaImageUtil;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 图形验证码：生成时写状态层，校验时一次性消费，无论成败都删除，防止暴力重试。 */
@Service
public class CaptchaService {
    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);

    private final AuthStateStore store;
    private final boolean enabled;

    public CaptchaService(AuthStateStore store,
        @Value("${ai-review.auth.captcha-enabled:true}") boolean enabled) {
        this.store = store;
        this.enabled = enabled;
    }

    public CaptchaVO generate() {
        CaptchaImageUtil.Captcha captcha = CaptchaImageUtil.generate();
        String key = UUID.randomUUID().toString();
        store.put(CAPTCHA_PREFIX + key, captcha.answer(), CAPTCHA_TTL);
        return new CaptchaVO(key, captcha.base64Image());
    }

    public void validateAndConsume(String key, String code) {
        if (!enabled) {
            return;
        }
        if (key == null || key.isBlank() || code == null || code.isBlank()) {
            throw new BusinessException(400, "验证码错误或已过期");
        }
        String answer = store.getAndDelete(CAPTCHA_PREFIX + key);
        if (answer == null || !answer.trim().equalsIgnoreCase(code.trim())) {
            throw new BusinessException(400, "验证码错误或已过期");
        }
    }
}
