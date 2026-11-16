package com.aireview.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aireview.security.PermissionService;
import com.aireview.security.RequireRole;
import com.aireview.security.TokenService;
import com.aireview.util.JwtUtil;
import com.aireview.util.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
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
    private JwtInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        tokenService = mock(TokenService.class);
        permissionService = mock(PermissionService.class);
        interceptor = new JwtInterceptor(jwtUtil, new ObjectMapper(), tokenService, permissionService);
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
