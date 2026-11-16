package com.aireview.config;

import com.aireview.common.Result;
import com.aireview.common.ResultCode;
import com.aireview.security.PermissionService;
import com.aireview.security.RequirePermission;
import com.aireview.security.RequireRole;
import com.aireview.security.TokenService;
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

/** 登录态与权限校验：解析 JWT → 写入 {@link UserContext}，再按注解校验角色/权限。 */
@Component
public class JwtInterceptor implements HandlerInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final TokenService tokenService;
    private final PermissionService permissionService;

    public JwtInterceptor(JwtUtil jwtUtil, ObjectMapper objectMapper, TokenService tokenService,
        PermissionService permissionService) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.tokenService = tokenService;
        this.permissionService = permissionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
        throws IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response);
            return false;
        }
        Claims claims = jwtUtil.parseClaims(header.substring(BEARER_PREFIX.length()));
        Long userId = parseUserId(claims);
        if (userId == null) {
            writeUnauthorized(response);
            return false;
        }
        if (claims.getId() != null && tokenService.isBlacklisted(claims.getId())) {
            writeUnauthorized(response);
            return false;
        }
        UserContext.set(userId);

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            writeForbidden(response);
            return false;
        }
        if (!authorized(userId, handlerMethod)) {
            writeForbidden(response);
            return false;
        }
        return true;
    }

    private boolean authorized(Long userId, HandlerMethod handlerMethod) {
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
