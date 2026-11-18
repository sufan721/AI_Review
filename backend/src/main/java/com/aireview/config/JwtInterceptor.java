package com.aireview.config;

import com.aireview.common.Result;
import com.aireview.common.ResultCode;
import com.aireview.dto.AccessSetting;
import com.aireview.security.PermissionService;
import com.aireview.security.PublicAccessRegistry;
import com.aireview.security.RequirePermission;
import com.aireview.security.RequireRole;
import com.aireview.security.TokenService;
import com.aireview.service.InstanceSettingService;
import com.aireview.service.PersonalAccessTokenService;
import com.aireview.service.impl.PersonalAccessTokenServiceImpl;
import com.aireview.util.JwtUtil;
import com.aireview.util.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录态与权限校验（对齐 memos Authorizer 的两层结构）。
 *
 * <p>第一层（本拦截器）：仅判认证 + 匿名放行。
 * <ul>
 *   <li>已认证（有效 JWT 或 PAT）→ 写入 {@link UserContext}，放行；</li>
 *   <li>匿名 + 引导接口（Bootstrap）→ 恒放行；</li>
 *   <li>匿名 + 公开资源接口 + {@code ACCESS.mode=PUBLIC} → 放行；</li>
 *   <li>其余 → 401。</li>
 * </ul>
 *
 * <p>第二层（Service / 注解）：{@link RequireRole} / {@link RequirePermission} 管实例角色，
 * 资源归属由 OwnershipService 统一校验。本拦截器只在已认证时执行注解校验。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final TokenService tokenService;
    private final PermissionService permissionService;
    private final PublicAccessRegistry publicAccessRegistry;
    private final InstanceSettingService instanceSettingService;
    private final PersonalAccessTokenService patService;

    public JwtInterceptor(JwtUtil jwtUtil, ObjectMapper objectMapper, TokenService tokenService,
        PermissionService permissionService, PublicAccessRegistry publicAccessRegistry,
        InstanceSettingService instanceSettingService, PersonalAccessTokenService patService) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.tokenService = tokenService;
        this.permissionService = permissionService;
        this.publicAccessRegistry = publicAccessRegistry;
        this.instanceSettingService = instanceSettingService;
        this.patService = patService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
        throws IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        // 1) 引导接口恒放行：登录/注册/验证码/SSO/健康/实例 profile 读取
        if (publicAccessRegistry.isBootstrap(request)) {
            return true;
        }

        // 2) 已认证：解析凭证（JWT 或 PAT）→ UserContext，再按注解校验角色/权限
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());

            // PAT 分支：token 以 memos_pat_ 开头，按 SHA-256 摘要比对 personal_access_token
            if (token.startsWith(PersonalAccessTokenServiceImpl.TOKEN_PREFIX)) {
                Long patUserId = patService.verify(token);
                if (patUserId == null) {
                    writeUnauthorized(response);
                    return false;
                }
                UserContext.set(patUserId);
                if (!(handler instanceof HandlerMethod handlerMethod)) {
                    writeForbidden(response);
                    return false;
                }
                // 该方法只做注解声明的角色/权限校验，不含认证逻辑；统一在凭证解析后调用，
                // 无凭证分支一律 401，不存在绕过
                if (!checkRoleAndPermission(patUserId, handlerMethod)) {
                    writeForbidden(response);
                    return false;
                }
                return true;
            }

            // JWT 分支：解析 claims，校验黑名单与会话版本号
            Claims claims = jwtUtil.parseClaims(token);
            Long userId = parseUserId(claims);
            if (userId == null) {
                writeUnauthorized(response);
                return false;
            }
            if (claims.getId() != null && tokenService.isBlacklisted(claims.getId())) {
                writeUnauthorized(response);
                return false;
            }
            // 会话版本号校验：改密码/重置后 token_version+1，旧 token tv 不一致即 401
            int tokenVersion = jwtUtil.parseTokenVersion(claims);
            if (!tokenService.isTokenVersionValid(userId, tokenVersion)) {
                writeUnauthorized(response);
                return false;
            }
            UserContext.set(userId);
            if (!(handler instanceof HandlerMethod handlerMethod)) {
                writeForbidden(response);
                return false;
            }
            if (!checkRoleAndPermission(userId, handlerMethod)) {
                writeForbidden(response);
                return false;
            }
            return true;
        }

        // 3) 匿名：公开资源接口 + ACCESS.mode=PUBLIC 才放行；否则 401
        if (publicAccessRegistry.isPublicResource(request)) {
            AccessSetting access = instanceSettingService.getAccess();
            if (access.isPublic()) {
                return true;
            }
        }
        writeUnauthorized(response);
        return false;
    }

    /**
     * 按 Handler 上的注解校验实例角色/权限。这里只做鉴权，不做身份认证：
     * 认证已在上方由 JWT / PAT 解析完成，未认证请求在下方直接 401。
     */
    private boolean checkRoleAndPermission(Long userId, HandlerMethod handlerMethod) {
        RequireRole role = resolveAnnotation(handlerMethod, RequireRole.class);
        if (role != null && !permissionService.hasRole(userId, role.value())) {
            return false;
        }
        RequirePermission permission = resolveAnnotation(handlerMethod, RequirePermission.class);
        return permission == null || permissionService.hasPermission(userId, permission.value());
    }

    private <A extends java.lang.annotation.Annotation> A resolveAnnotation(HandlerMethod handlerMethod,
        Class<A> type) {
        A annotation = handlerMethod.getMethodAnnotation(type);
        return annotation != null ? annotation : handlerMethod.getBeanType().getAnnotation(type);
    }

    private static Long parseUserId(Claims claims) {
        if (claims == null || claims.getSubject() == null) {
            return null;
        }
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
        Exception exception) {
        // 线程复用，必须清理，否则会串号
        UserContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        writeError(response, ResultCode.UNAUTHORIZED);
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        writeError(response, ResultCode.FORBIDDEN);
    }

    private void writeError(HttpServletResponse response, ResultCode resultCode) throws IOException {
        response.setStatus(resultCode.code());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
            Result.fail(resultCode.code(), resultCode.message())));
    }
}
