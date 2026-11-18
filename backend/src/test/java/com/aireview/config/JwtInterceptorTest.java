package com.aireview.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aireview.dto.AccessSetting;
import com.aireview.security.PermissionService;
import com.aireview.security.PublicAccessRegistry;
import com.aireview.security.RequireRole;
import com.aireview.security.TokenService;
import com.aireview.service.InstanceSettingService;
import com.aireview.service.PersonalAccessTokenService;
import com.aireview.util.JwtUtil;
import com.aireview.util.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class JwtInterceptorTest {
    private JwtUtil jwtUtil;
    private TokenService tokenService;
    private PermissionService permissionService;
    private PublicAccessRegistry publicAccessRegistry;
    private InstanceSettingService instanceSettingService;
    private PersonalAccessTokenService patService;
    private JwtInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        tokenService = mock(TokenService.class);
        permissionService = mock(PermissionService.class);
        publicAccessRegistry = mock(PublicAccessRegistry.class);
        instanceSettingService = mock(InstanceSettingService.class);
        patService = mock(PersonalAccessTokenService.class);
        // 默认所有请求都不是引导接口、都不是公开资源；具体用例按需覆盖
        when(publicAccessRegistry.isBootstrap(any())).thenReturn(false);
        when(publicAccessRegistry.isPublicResource(any())).thenReturn(false);
        interceptor = new JwtInterceptor(jwtUtil, new ObjectMapper(), tokenService, permissionService,
            publicAccessRegistry, instanceSettingService, patService);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void optionsRequestIsAlwaysAllowedWithoutToken() throws Exception {
        MockHttpServletRequest request =
            new MockHttpServletRequest(HttpMethod.OPTIONS.name(), "/api/knowledge-bases");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(jwtUtil, never()).parseClaims(anyString());
    }

    @Test
    void bootstrapEndpointIsAllowedWithoutToken() throws Exception {
        MockHttpServletRequest request =
            new MockHttpServletRequest(HttpMethod.GET.name(), "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(publicAccessRegistry.isBootstrap(any())).thenReturn(true);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(jwtUtil, never()).parseClaims(anyString());
    }

    @Test
    void anonymousPublicResourceIsAllowedWhenAccessModePublic() throws Exception {
        MockHttpServletRequest request =
            new MockHttpServletRequest(HttpMethod.GET.name(), "/api/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(publicAccessRegistry.isPublicResource(any())).thenReturn(true);
        when(instanceSettingService.getAccess())
            .thenReturn(new AccessSetting(AccessSetting.Mode.PUBLIC));

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(jwtUtil, never()).parseClaims(anyString());
    }

    @Test
    void anonymousPublicResourceIsRejectedWhenAccessModePrivate() throws Exception {
        MockHttpServletRequest request =
            new MockHttpServletRequest(HttpMethod.GET.name(), "/api/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(publicAccessRegistry.isPublicResource(any())).thenReturn(true);
        when(instanceSettingService.getAccess())
            .thenReturn(new AccessSetting(AccessSetting.Mode.PRIVATE));

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void anonymousProtectedEndpointIsRejected() throws Exception {
        MockHttpServletRequest request =
            new MockHttpServletRequest(HttpMethod.GET.name(), "/api/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void protectedRequestWithNonHandlerMethodIsForbidden() throws Exception {
        MockHttpServletRequest request = requestWithToken();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        verify(permissionService, never()).hasRole(anyLong(), anyString());
        verify(permissionService, never()).hasPermission(anyLong(), anyString());
    }

    @Test
    void protectedRequestWithHandlerMethodAndNoAnnotationIsAllowed() throws Exception {
        MockHttpServletRequest request = requestWithToken();
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = new HandlerMethod(new ProbeController(), ProbeController.class.getMethod("open"));

        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void patTokenAuthenticatesAndWritesUserContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.GET.name(), "/api/notes");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer memos_pat_abcdef");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(patService.verify("memos_pat_abcdef")).thenReturn(7L);
        HandlerMethod handler = new HandlerMethod(new ProbeController(), ProbeController.class.getMethod("open"));

        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(UserContext.require()).isEqualTo(7L);
        // PAT 分支不应触碰 JWT 解析
        verify(jwtUtil, never()).parseClaims(anyString());
    }

    @Test
    void invalidPatTokenIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.GET.name(), "/api/notes");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer memos_pat_invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(patService.verify("memos_pat_invalid")).thenReturn(null);

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void protectedRequestWithRequiredRoleIsForbiddenWhenRoleMissing() throws Exception {
        MockHttpServletRequest request = requestWithToken();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(permissionService.hasRole(1L, "ROLE_ADMIN")).thenReturn(false);
        HandlerMethod handler =
            new HandlerMethod(new ProbeController(), ProbeController.class.getMethod("adminOnly"));

        assertThat(interceptor.preHandle(request, response, handler)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    private MockHttpServletRequest requestWithToken() {
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.GET.name(), "/api/notes");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1");
        when(claims.getId()).thenReturn(null);
        when(jwtUtil.parseClaims("token")).thenReturn(claims);
        return request;
    }

    static class ProbeController {
        @RequireRole("ROLE_ADMIN")
        public void adminOnly() {
        }

        public void open() {
        }
    }
}
