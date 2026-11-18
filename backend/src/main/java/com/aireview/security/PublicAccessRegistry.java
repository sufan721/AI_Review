package com.aireview.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * 接口放行分组（对齐 memos 的引导接口 / 公开资源接口分层）。
 *
 * <ul>
 *   <li><b>Bootstrap</b>：实例无论 PUBLIC/PRIVATE 都必须匿名放行的引导接口。
 *       登录、注册、验证码、SSO 回调、健康检查、以及供登录页匿名读取的实例 profile
 *       （注册开关、访问模式）。PUT 这些设置仍走 Protected，仅 GET 放行。</li>
 *   <li><b>Public Resource</b>：公开资源接口（公开笔记/文档列表与详情）。
 *       仅当 {@code ACCESS.mode=PUBLIC} 时匿名放行；PRIVATE 模式下要求登录。</li>
 *   <li>其余接口默认 Protected，必须认证。</li>
 * </ul>
 *
 * <p>路径匹配用 {@link AntPathMatcher}，支持 {@code /api/notes/{id}} 等模式。
 */
@Component
public class PublicAccessRegistry {

    private final AntPathMatcher matcher = new AntPathMatcher();

    /** 恒放行的引导接口（不限方法）。注意：只放行绝对安全的入口。 */
    private static final List<String> BOOTSTRAP_ANY_METHOD = List.of(
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
    );

    /** 仅 GET 放行的引导接口：登录页/前端匿名读取实例 profile，避免把敏感设置暴露给匿名。 */
    private static final List<String> BOOTSTRAP_GET_ONLY = List.of(
        "/api/instance/settings/general",
        "/api/instance/settings/access"
    );

    /**
     * 公开资源接口（任意方法，但通常只对应 GET）。
     * 当前为空：Note/Document 的 service 层仍按本人 user_id 过滤，
     * 暴露匿名访问会因 UserContext.require() 抛 NPE。
     * 后续做 visibility 字段与公开资源 service 后再补这里。
     */
    private static final List<String> PUBLIC_RESOURCE = List.of(
        // 暂留空：/api/notes、/api/notes/{id} 等待公开资源 service 落地后再加
    );

    /** 判断请求是否属于引导接口（恒放行）。 */
    public boolean isBootstrap(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (matchesAny(BOOTSTRAP_ANY_METHOD, path)) {
            return true;
        }
        if (HttpMethod.GET.matches(request.getMethod())) {
            return matchesAny(BOOTSTRAP_GET_ONLY, path);
        }
        return false;
    }

    /** 判断请求是否属于公开资源接口（仅 PUBLIC 模式放行）。 */
    public boolean isPublicResource(HttpServletRequest request) {
        return matchesAny(PUBLIC_RESOURCE, request.getRequestURI());
    }

    private boolean matchesAny(List<String> patterns, String path) {
        for (String pattern : patterns) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
